package com.finpulse.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;
    @Column(nullable = false, unique = true, length = 150)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(nullable = false, length = 10)
    private String currency = "INR";
    @Column(nullable = false, length = 30)
    private String role = "USER";
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;
    @Column(name = "apple_subject", unique = true, length = 255)
    private String appleSubject;
    @Column(name = "reset_digest", unique = true, length = 64)
    private String resetDigest;
    private java.time.Instant resetExpiry;
    private java.time.Instant resetRequestedAt;
    @Column(length = 43)
    private String passwordVersion;

    public String getGoogleSubject() { return googleSubject; }
    public void setGoogleSubject(String value) { googleSubject = value; }
    public String getAppleSubject() { return appleSubject; }
    public void setAppleSubject(String value) { appleSubject = value; }
    public String getResetDigest() { return resetDigest; }
    public void setResetDigest(String value) { resetDigest = value; }
    public java.time.Instant getResetExpiry() { return resetExpiry; }
    public void setResetExpiry(java.time.Instant value) { resetExpiry = value; }
    public java.time.Instant getResetRequestedAt() { return resetRequestedAt; }
    public void setResetRequestedAt(java.time.Instant value) { resetRequestedAt = value; }
    public String getPasswordVersion() { return passwordVersion; }
    public void setPasswordVersion(String value) { passwordVersion = value; }

    public User() {}
    public User(String fullName, String email, String passwordHash, String currency) {
        this.fullName = fullName; this.email = email; this.passwordHash = passwordHash;
        this.currency = currency != null ? currency : "INR";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
