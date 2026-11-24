package com.finpulse.repository;

import com.finpulse.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByAuthorId(Long authorId);
    List<Feedback> findTop24ByOrderByUpdatedAtDesc();
}
