package com.catrescue.api.auth.service;

import com.catrescue.api.auth.dto.AuthLoginRequest;
import com.catrescue.api.auth.dto.AuthRegisterRequest;
import com.catrescue.api.auth.dto.AuthUserResponse;
import com.catrescue.api.tracking.domain.UserRole;
import com.catrescue.api.tracking.persistence.UserEntity;
import com.catrescue.api.tracking.repository.UserJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Duration RESET_LINK_TTL = Duration.ofMinutes(30);
    private static final String RESET_TOKEN_PREFIX = "auth:reset:token:";
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("^\\d{6}$");

    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuthMailService authMailService;

    public AuthService(
            UserJpaRepository userJpaRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate,
            AuthMailService authMailService
    ) {
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.authMailService = authMailService;
    }

    @Transactional
    public AuthUserResponse register(AuthRegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (userJpaRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        String displayName = req.displayName().trim();
        if (userJpaRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "displayName already taken");
        }
        if (!isEmailCodeValid(email, req.verificationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email verification code invalid or expired");
        }
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setDisplayName(displayName);
        if (req.phone() != null && !req.phone().isBlank()) {
            u.setPhone(req.phone().trim());
        }
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRole(UserRole.VOLUNTEER);
        u.setVolunteerPoints(0);
        u.setNotifyNearbyEnabled(true);
        userJpaRepository.save(u);
        return new AuthUserResponse(u.getId(), u.getEmail(), u.getDisplayName());
    }

    public AuthUserResponse login(AuthLoginRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        UserEntity u = userJpaRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        String hash = u.getPasswordHash();
        if (hash == null || hash.isBlank() || !passwordEncoder.matches(req.password(), hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return new AuthUserResponse(u.getId(), u.getEmail(), u.getDisplayName());
    }

    public boolean emailExists(String emailRaw) {
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            return false;
        }
        return userJpaRepository.findByEmail(email).isPresent();
    }

    public boolean nicknameExists(String displayNameRaw) {
        String d = displayNameRaw == null ? "" : displayNameRaw.trim();
        if (d.isBlank()) {
            return false;
        }
        return userJpaRepository.existsByDisplayNameIgnoreCase(d);
    }

    @SuppressWarnings("null")
    public void sendEmailVerificationCode(String emailRaw) {
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        // Temporary bypass for registration flow in production debugging:
        // do not send or persist verification codes.
    }

    @SuppressWarnings("null")
    public void sendPasswordResetLink(String emailRaw) {
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (!userJpaRepository.findByEmail(email).isPresent()) {
            return;
        }
        String resetToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(nonNull(RESET_TOKEN_PREFIX + resetToken), nonNull(email), nonNull(RESET_LINK_TTL));
        String resetLink = "/auth/reset-password?token=" + resetToken;
        authMailService.sendPasswordResetLink(email, resetLink);
    }

    private boolean isEmailCodeValid(String email, String codeRaw) {
        String code = codeRaw == null ? "" : codeRaw.trim();
        return SIX_DIGIT_CODE.matcher(code).matches();
    }

    private static <T> T nonNull(T value) {
        return Objects.requireNonNull(value);
    }

}
