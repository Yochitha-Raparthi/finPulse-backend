package com.finpulse.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workspace_feedback")
public class Feedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User author;
    @Column(nullable = false)
    private int rating;
    @Column(nullable = false, length = 600)
    private String message;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Feedback() {}
    public Feedback(User author) { this.author = author; }
    public void update(int rating, String message) {
        this.rating = rating;
        this.message = message;
        this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public int getRating() { return rating; }
    public String getMessage() { return message; }
    public Instant getUpdatedAt() { return updatedAt; }
}
