package com.finpulse.service.impl;

import com.finpulse.dto.AuthRequest;
import com.finpulse.dto.AuthResponse;
import com.finpulse.dto.RegisterRequest;
import com.finpulse.entity.Account;
import com.finpulse.entity.User;
import com.finpulse.exception.BusinessRuleException;
import com.finpulse.repository.AccountRepository;
import com.finpulse.repository.UserRepository;
import com.finpulse.security.JwtUtil;
import com.finpulse.service.AuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @org.springframework.beans.factory.annotation.Value("${app.demo-enabled}")
    private boolean demoEnabled;

    public AuthServiceImpl(UserRepository userRepository, AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email.toLowerCase(java.util.Locale.ROOT).trim();
        if (email.equals("demo@finpulse.ai") || userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }
        if (req.password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleException("Password must use at most 72 UTF-8 bytes.");
        }
        User user = userRepository.save(new User(req.fullName.trim(), email,
                passwordEncoder.encode(req.password), req.currency));
        accountRepository.save(new Account(user, "Primary Bank Account", "BANK", BigDecimal.ZERO));
        return new AuthResponse(jwtUtil.generateToken(user.getEmail(), user.getPasswordVersion()), user.getId(),
                user.getFullName(), user.getEmail(), user.getCurrency());
    }

    @Override
    public AuthResponse login(AuthRequest req) {
        String email = req.email.toLowerCase(java.util.Locale.ROOT).trim();
        if (!demoEnabled && email.equals("demo@finpulse.ai")) throw new BadCredentialsException("Invalid credentials");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (req.password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(req.password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new AuthResponse(jwtUtil.generateToken(user.getEmail(), user.getPasswordVersion()), user.getId(),
                user.getFullName(), user.getEmail(), user.getCurrency());
    }
}
