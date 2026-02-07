package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.auth.AuthResponse;
import com.cqlplatform.model.auth.LoginRequest;
import com.cqlplatform.model.auth.RegisterRequest;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(UserEntity.Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "role", user.getRole().name()
        ));
    }
}
