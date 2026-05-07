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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Duration VERIFY_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESET_LINK_TTL = Duration.ofMinutes(30);
    private static final String VERIFY_CODE_PREFIX = "auth:verify:code:";
    private static final String RESET_TOKEN_PREFIX = "auth:reset:token:";

    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuthMailService authMailService;
    private final Random random = new Random();
    private final Map<String, FallbackCodeEntry> fallbackCodeStore = new ConcurrentHashMap<>();

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
        int code = 100000 + random.nextInt(900000);
        String codeText = String.valueOf(code);
        try {
            redisTemplate.opsForValue().set(nonNull(VERIFY_CODE_PREFIX + email), nonNull(codeText), nonNull(VERIFY_CODE_TTL));
        } catch (Exception ex) {
            fallbackCodeStore.put(email, new FallbackCodeEntry(codeText, Instant.now().plus(VERIFY_CODE_TTL)));
        }
        try {
            authMailService.sendVerificationCode(email, codeText);
        } catch (IllegalStateException ex) {
            removeVerificationCode(email);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
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
        if (code.isBlank()) {
            return false;
        }
        String key = VERIFY_CODE_PREFIX + email;
        try {
            String stored = redisTemplate.opsForValue().get(key);
            if (stored == null || stored.isBlank()) {
                return verifyFromFallback(email, code);
            }
            if (!stored.equals(code)) {
                return false;
            }
            redisTemplate.delete(key);
            return true;
        } catch (Exception ex) {
            return verifyFromFallback(email, code);
        }
    }

    private static <T> T nonNull(T value) {
        return Objects.requireNonNull(value);
    }

    private boolean verifyFromFallback(String email, String code) {
        FallbackCodeEntry entry = fallbackCodeStore.get(email);
        if (entry == null) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            fallbackCodeStore.remove(email);
            return false;
        }
        if (!entry.code().equals(code)) {
            return false;
        }
        fallbackCodeStore.remove(email);
        return true;
    }

    private void removeVerificationCode(String email) {
        try {
            redisTemplate.delete(VERIFY_CODE_PREFIX + email);
        } catch (Exception _ignore) {
        }
        fallbackCodeStore.remove(email);
    }

    private record FallbackCodeEntry(String code, Instant expiresAt) {
    }
}
