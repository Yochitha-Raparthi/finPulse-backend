package com.finpulse.service;

import com.finpulse.dto.AuthResponse;
import com.finpulse.entity.Account;
import com.finpulse.entity.User;
import com.finpulse.exception.BusinessRuleException;
import com.finpulse.repository.AccountRepository;
import com.finpulse.repository.UserRepository;
import com.finpulse.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class SocialAccounts {
    public record Identity(String provider, String subject, String email, String name) {}

    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final JwtUtil tokens;

    public SocialAccounts(UserRepository users, AccountRepository accounts, PasswordEncoder encoder, JwtUtil tokens) {
        this.users = users;
        this.accounts = accounts;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    @Transactional
    public AuthResponse signIn(Identity identity, String password) {
        boolean google = identity.provider().equals("google");
        var linked = google ? users.findByGoogleSubject(identity.subject()) : users.findByAppleSubject(identity.subject());
        if (linked.isPresent()) return session(linked.get());
        if (identity.email().equals("demo@finpulse.ai")) throw new BusinessRuleException("Use your own account.");
        User user = users.lockByEmail(identity.email()).orElse(null);
        if (user == null) {
            user = users.save(new User(identity.name(), identity.email(), encoder.encode(AccountRecovery.randomToken()), "INR"));
            accounts.save(new Account(user, "Primary Bank Account", "BANK", BigDecimal.ZERO));
        } else {
            if (google ? user.getGoogleSubject() != null : user.getAppleSubject() != null) {
                throw new BusinessRuleException("This Veltro account already has a different identity linked for that provider.");
            }
            if (password == null || password.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Enter your existing Veltro password to connect this account.");
            }
            if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || !encoder.matches(password, user.getPasswordHash())) {
                throw new BusinessRuleException("The password was incorrect. Start the provider sign-in again.");
            }
        }
        if (google) user.setGoogleSubject(identity.subject()); else user.setAppleSubject(identity.subject());
        return session(user);
    }

    private AuthResponse session(User user) {
        return new AuthResponse(tokens.generateToken(user.getEmail(), user.getPasswordVersion()), user.getId(), user.getFullName(),
                user.getEmail(), user.getCurrency());
    }
}
