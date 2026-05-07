package com.catrescue.api.security;

import com.catrescue.api.config.ShareAccessProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Lightweight shared-password gate for temporary friend testing.
 *
 * Usage:
 * 1) Configure password in env: CAT_RESCUE_SHARE_PASSWORD=...
 * 2) Enable gate: CAT_RESCUE_SHARE_ENABLED=true
 * 3) Share link: https://your-domain/?access_key=the-password
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ShareAccessFilter extends OncePerRequestFilter {

    private final ShareAccessProperties props;

    public ShareAccessFilter(ShareAccessProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configuredPassword = props.getPassword() == null ? "" : props.getPassword().trim();
        // If a non-blank password is configured, keep gate active even when enabled flag is not set.
        // This avoids accidental exposure when terminal env vars are partially loaded.
        if (configuredPassword.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health") || path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!requiresProtectedAccess(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String expectedToken = sha256Hex(configuredPassword);
        String cookieToken = readCookie(request, props.getCookieName());
        if (safeEquals(expectedToken, cookieToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String submitted = firstNonBlank(
                request.getParameter("access_key"),
                request.getParameter("password")
        );
        if (submitted != null && safeEquals(configuredPassword, submitted.trim())) {
            Cookie cookie = new Cookie(props.getCookieName(), expectedToken);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(60 * 60 * 8); // 8h test session
            response.addCookie(cookie);

            String redirect = request.getRequestURI();
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                // Drop access_key from URL after login.
                response.sendRedirect(redirect);
            } else {
                response.sendRedirect(redirect);
            }
            return;
        }

        renderPasswordPage(response, path);
    }

    private static void renderPasswordPage(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/html; charset=UTF-8");
        String action = escapeHtml(path == null || path.isBlank() ? "/" : path);
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Test Access</title>
                  <style>
                    body { font-family: Arial, sans-serif; background:#0b1020; color:#e2e8f0; display:flex; min-height:100vh; margin:0; align-items:center; justify-content:center; }
                    .box { width:min(420px,92vw); background:#111827; border:1px solid #334155; border-radius:12px; padding:18px; }
                    h1 { margin:0 0 8px; font-size:18px; }
                    p { margin:0 0 14px; color:#94a3b8; font-size:13px; }
                    input { width:100%; padding:10px; border-radius:8px; border:1px solid #475569; background:#0f172a; color:#e2e8f0; }
                    button { margin-top:12px; width:100%; border:0; border-radius:8px; padding:10px; background:#22d3ee; color:#0f172a; font-weight:700; cursor:pointer; }
                    small { display:block; margin-top:10px; color:#64748b; }
                  </style>
                </head>
                <body>
                  <form class="box" method="get" action="__ACTION__">
                    <h1>Test Access Password</h1>
                    <p>Please enter the temporary password shared by the author.<br/>请输入作者提供的临时访问密码。</p>
                    <input type="password" name="access_key" placeholder="Password / 密码" required />
                    <button type="submit">Enter / 进入</button>
                    <small>Protected test mode is enabled.</small>
                  </form>
                </body>
                </html>
                """;
        response.getWriter().write(html.replace("__ACTION__", action));
    }

    private static boolean requiresProtectedAccess(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        // REST APIs use their own auth (JWT, institution tokens, etc.); do not gate JSON behind the HTML share form.
        if (p.startsWith("/api/")) {
            return false;
        }
        // Keep homepage/public static assets accessible, but protect sensitive data routes.
        return p.contains("/heatmap")
                || p.contains("/feeding-stations")
                || p.contains("/sightings/upload");
    }

    private static String readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] out = d.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#039;");
    }
}
