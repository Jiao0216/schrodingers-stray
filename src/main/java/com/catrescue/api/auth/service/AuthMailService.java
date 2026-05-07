package com.catrescue.api.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final String DEFAULT_FROM = "onboarding@resend.dev";

    private final HttpClient httpClient;
    private final String resendApiKey;

    public AuthMailService(@Value("${resend.api-key:}") String resendApiKey) {
        this.httpClient = HttpClient.newHttpClient();
        this.resendApiKey = resendApiKey;
    }

    public void sendVerificationCode(String email, String code) {
        String subject = "CatRescue verification code";
        String text = "Your verification code is: " + code + ". It expires in 5 minutes.";
        sendEmail(email, subject, text);
    }

    public void sendPasswordResetLink(String email, String link) {
        String subject = "CatRescue password reset";
        String text = "Reset your password using this link (valid for 30 minutes): " + link;
        sendEmail(email, subject, text);
    }

    private void sendEmail(String to, String subject, String text) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.info("Skip email send because RESEND_API_KEY is empty. to={}, subject={}", to, subject);
            return;
        }
        try {
            String jsonBody = """
                    {
                      "from": "%s",
                      "to": ["%s"],
                      "subject": "%s",
                      "text": "%s"
                    }
                    """.formatted(
                    escapeJson(DEFAULT_FROM),
                    escapeJson(to),
                    escapeJson(subject),
                    escapeJson(text)
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_URL))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Resend API rejected request. status=%d body=%s"
                        .formatted(response.statusCode(), response.body()));
            }
        } catch (Exception ex) {
            log.warn("Failed to send email. to={}, subject={}, reason={}", to, subject, ex.getMessage());
            throw new IllegalStateException("Failed to send email: " + ex.getMessage(), ex);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
