package com.finpulse.service;

import com.finpulse.exception.BusinessRuleException;
import com.finpulse.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AccountRecovery {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String frontend;

    public AccountRecovery(UserRepository users, PasswordEncoder encoder, @Value("${app.frontend-url}") String frontend) {
        this.users = users;
        this.encoder = encoder;
        this.frontend = frontend.replaceAll("/+$", "");
    }

    public record ResetMail(String email, String link) {}

    @Transactional
    public ResetMail issue(String email) {
        String address = email.strip().toLowerCase(Locale.ROOT);
        if (address.equals("demo@finpulse.ai")) return null;
        var user = users.lockByEmail(address).orElse(null);
        Instant now = Instant.now();
        if (user == null || (user.getResetRequestedAt() != null && user.getResetRequestedAt().plusSeconds(60).isAfter(now))) return null;
        String token = randomToken();
        user.setResetDigest(digest(token));
        user.setResetExpiry(now.plusSeconds(1800));
        user.setResetRequestedAt(now);
        return new ResetMail(user.getEmail(), frontend + "/auth?mode=reset#token=" + token);
    }

    @Transactional
    public void reset(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleException("Password must use at most 72 UTF-8 bytes.");
        }
        var user = users.findByResetDigest(digest(token))
                .filter(account -> account.getResetExpiry() != null && account.getResetExpiry().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessRuleException("This reset link is invalid or expired. Request a new one."));
        user.setPasswordHash(encoder.encode(password));
        user.setResetDigest(null);
        user.setResetExpiry(null);
        user.setPasswordVersion(randomToken());
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
