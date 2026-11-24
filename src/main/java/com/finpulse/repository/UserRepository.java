package com.finpulse.repository;

import com.finpulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSubject(String subject);
    Optional<User> findByAppleSubject(String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> lockByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByResetDigest(String resetDigest);

    boolean existsByEmail(String email);
}
