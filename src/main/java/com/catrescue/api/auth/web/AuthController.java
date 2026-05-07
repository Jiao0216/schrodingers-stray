package com.catrescue.api.auth.web;

import com.catrescue.api.auth.dto.AuthLoginRequest;
import com.catrescue.api.auth.dto.AuthEmailExistsResponse;
import com.catrescue.api.auth.dto.AuthForgotPasswordRequest;
import com.catrescue.api.auth.dto.AuthNicknameExistsResponse;
import com.catrescue.api.auth.dto.AuthRegisterRequest;
import com.catrescue.api.auth.dto.AuthSimpleMessageResponse;
import com.catrescue.api.auth.dto.AuthUserResponse;
import com.catrescue.api.auth.dto.AuthVerificationSendResponse;
import com.catrescue.api.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserResponse register(@Valid @RequestBody AuthRegisterRequest body) {
        return authService.register(body);
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody AuthLoginRequest body) {
        return authService.login(body);
    }

    @PostMapping("/email-exists")
    public AuthEmailExistsResponse emailExists(@RequestParam("email") String email) {
        return new AuthEmailExistsResponse(authService.emailExists(email));
    }

    @PostMapping("/nickname-exists")
    public AuthNicknameExistsResponse nicknameExists(@RequestParam("displayName") String displayName) {
        return new AuthNicknameExistsResponse(authService.nicknameExists(displayName));
    }

    @PostMapping({"/send-code", "/send-code/"})
    public AuthVerificationSendResponse sendCode(@RequestParam("email") String email) {
        authService.sendEmailVerificationCode(email);
        return new AuthVerificationSendResponse(true, "verification code sent");
    }

    @PostMapping({"/send-email-code", "/send-email-code/"})
    public AuthVerificationSendResponse sendEmailCodeCompat(@RequestParam("email") String email) {
        authService.sendEmailVerificationCode(email);
        return new AuthVerificationSendResponse(true, "verification code sent");
    }

    @PostMapping({"/forgot-password", "/forgot-password/", "/send-reset-link", "/send-reset-link/"})
    public AuthSimpleMessageResponse forgotPassword(@Valid @RequestBody AuthForgotPasswordRequest body) {
        authService.sendPasswordResetLink(body.email());
        return new AuthSimpleMessageResponse(true, "If the email exists, a reset link has been sent.");
    }
}
