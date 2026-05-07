package com.catrescue.api.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the SPA-style auth UI under {@code /auth/index.html}; {@code /auth} redirects for bookmark-friendly URLs.
 */
@Controller
public class AuthPageController {

    @GetMapping("/auth")
    public String authPage() {
        return "redirect:/auth/index.html";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "redirect:/auth/index.html?mode=register";
    }

    @GetMapping("/volunteer")
    public String volunteerPage() {
        return "redirect:/?panel=panel-volunteer";
    }
}
