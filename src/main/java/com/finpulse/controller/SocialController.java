package com.finpulse.controller;

import com.finpulse.dto.AuthResponse;
import com.finpulse.security.SocialLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/social")
public class SocialController {
    private final SocialLogin social;
    public SocialController(SocialLogin social) { this.social = social; }
    public record Exchange(@jakarta.validation.constraints.NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String code,
                           @jakarta.validation.constraints.NotBlank @Pattern(regexp = "[a-f0-9]{64}") String verifier,
                           @Size(max = 72) String password) {}

    @GetMapping("/providers")
    public List<String> providers() { return social.enabledProviders(); }

    @PostMapping("/exchange")
    public AuthResponse exchange(@Valid @RequestBody Exchange request) {
        return social.exchange(request.code(), request.verifier(), request.password());
    }
}
