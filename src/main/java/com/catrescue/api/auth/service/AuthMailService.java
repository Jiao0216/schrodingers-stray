package com.catrescue.api.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public AuthMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
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
        if (mailSender == null) {
            throw new IllegalStateException(
                    "Mail sender is not configured. Set spring.mail.host (e.g. MAIL_HOST=smtp.gmail.com) and credentials for local SMTP.");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send email. to={}, subject={}, reason={}", to, subject, ex.getMessage());
            throw new IllegalStateException("Failed to send email: " + ex.getMessage(), ex);
        }
    }
}
