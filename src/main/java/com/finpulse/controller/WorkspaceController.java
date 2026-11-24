package com.finpulse.controller;

import com.finpulse.entity.Feedback;
import com.finpulse.entity.User;
import com.finpulse.repository.FeedbackRepository;
import com.finpulse.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkspaceController {
    private final UserRepository users;
    private final FeedbackRepository feedback;

    public WorkspaceController(UserRepository users, FeedbackRepository feedback) {
        this.users = users;
        this.feedback = feedback;
    }

    public record Profile(String fullName, String email, String currency, boolean google, boolean apple) {}
    public record ProfileInput(@NotBlank @Size(max = 120) String fullName) {}
    public record FeedbackInput(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 600) String message) {}
    public record Review(Long id, int rating, String message, Instant updatedAt) {}
    public record CommunityFeedback(Review mine, List<Review> recent) {}

    @GetMapping("/profile")
    public Profile profile(Principal principal) {
        return view(users.findByEmail(principal.getName()).orElseThrow());
    }

    @PutMapping("/profile")
    @Transactional
    public Profile update(Principal principal, @Valid @RequestBody ProfileInput input) {
        User user = editableUser(principal);
        user.setFullName(input.fullName().strip());
        return view(user);
    }

    @GetMapping("/feedback")
    public CommunityFeedback feedback(Principal principal) {
        User user = users.findByEmail(principal.getName()).orElseThrow();
        return new CommunityFeedback(feedback.findByAuthorId(user.getId()).map(this::view).orElse(null),
                feedback.findTop24ByOrderByUpdatedAtDesc().stream().map(this::view).toList());
    }

    @PutMapping("/feedback")
    @Transactional
    public Review saveFeedback(Principal principal, @Valid @RequestBody FeedbackInput input) {
        User user = editableUser(principal);
        Feedback entry = feedback.findByAuthorId(user.getId()).orElseGet(() -> new Feedback(user));
        entry.update(input.rating(), input.message().strip());
        return view(feedback.save(entry));
    }

    @DeleteMapping("/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteFeedback(Principal principal) {
        User user = editableUser(principal);
        feedback.findByAuthorId(user.getId()).ifPresent(feedback::delete);
    }

    private User editableUser(Principal principal) {
        if ("demo@finpulse.ai".equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign in with your own account to make changes.");
        }
        return users.lockByEmail(principal.getName()).orElseThrow();
    }

    private Profile view(User user) {
        return new Profile(user.getFullName(), user.getEmail(), user.getCurrency(),
                user.getGoogleSubject() != null, user.getAppleSubject() != null);
    }

    private Review view(Feedback entry) {
        return new Review(entry.getId(), entry.getRating(), entry.getMessage(), entry.getUpdatedAt());
    }
}
