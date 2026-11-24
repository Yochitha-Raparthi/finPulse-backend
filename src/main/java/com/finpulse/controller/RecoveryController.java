package com.finpulse.controller;

import com.finpulse.service.AccountRecovery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/auth")
public class RecoveryController {
    private static final Logger LOG = LoggerFactory.getLogger(RecoveryController.class);
    private final AccountRecovery recovery;
    private final JavaMailSender mailSender;
    private final String from;
    private final ThreadPoolTaskExecutor executor;

    public RecoveryController(AccountRecovery recovery, ObjectProvider<JavaMailSender> sender,
                              @Value("${app.mail-from}") String from, ThreadPoolTaskExecutor recoveryExecutor) {
        this.recovery = recovery;
        this.mailSender = sender.getIfAvailable();
        this.from = from;
        this.executor = recoveryExecutor;
    }

    public record Forgot(@NotBlank @Email @Size(max = 150) String email) {}
    public record Reset(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                        @NotBlank @Size(min = 8, max = 72) String password) {}

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody Forgot request) {
        if (mailSender == null || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password reset email is temporarily unavailable.");
        }
        try {
            executor.execute(() -> send(request.email()));
        } catch (RejectedExecutionException exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please try again in a moment.");
        }
        return Map.of("message", "If this email is registered, you will receive a reset link. Check your inbox and spam folder.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> reset(@Valid @RequestBody Reset request) {
        recovery.reset(request.token(), request.password());
        return Map.of("message", "Password updated. You can now sign in.");
    }

    private void send(String email) {
        try {
            var reset = recovery.issue(email);
            if (reset == null) return;
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(reset.email());
            message.setSubject("Reset your Veltro password");
            message.setText("Use this single-use link to set a new password within 30 minutes:\n\n"
                    + reset.link() + "\n\nIf you did not request this, ignore this email.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOG.warn("Password reset email processing failed ({})", exception.getClass().getSimpleName());
        }
    }
}
