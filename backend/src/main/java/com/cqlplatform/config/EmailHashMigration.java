package com.cqlplatform.config;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time backfill of email_hash for existing users who have an email but no hash.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Run after DataInitializer
public class EmailHashMigration implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        try {
            List<UserEntity> users = userRepository.findAll();
            int updated = 0;

            for (UserEntity user : users) {
                if (user.getEmail() != null && !user.getEmail().isBlank() && user.getEmailHash() == null) {
                    user.setEmailHash(UserEntity.computeEmailHash(user.getEmail()));
                    userRepository.save(user);
                    updated++;
                }
            }

            if (updated > 0) {
                log.info("Backfilled email_hash for {} existing users", updated);
            }
        } catch (Exception e) {
            log.warn("EmailHashMigration skipped due to error (e.g. encryption key mismatch): {}", e.getMessage());
        }
    }
}
