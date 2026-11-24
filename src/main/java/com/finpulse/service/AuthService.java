package com.finpulse.service;

import com.finpulse.dto.AuthRequest;
import com.finpulse.dto.AuthResponse;
import com.finpulse.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
}
