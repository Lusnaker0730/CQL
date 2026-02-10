package com.cqlplatform.config;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            UserEntity admin = UserEntity.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin"))
                    .role(UserEntity.Role.ADMIN)
                    .enabled(true)
                    .build();
            admin.setEmailWithHash("admin@cqlplatform.com");

            userRepository.save(admin);
            log.warn("=== Default admin user created (username: admin, password: admin). CHANGE THIS IN PRODUCTION! ===");
        }
    }
}
