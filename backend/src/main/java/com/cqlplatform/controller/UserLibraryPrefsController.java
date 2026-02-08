package com.cqlplatform.controller;

import com.cqlplatform.entity.UserFavoriteEntity;
import com.cqlplatform.entity.UserRecentEntity;
import com.cqlplatform.repository.UserFavoriteRepository;
import com.cqlplatform.repository.UserRecentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/cql/user-prefs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Library Preferences", description = "APIs for managing user favorites and recent libraries")
public class UserLibraryPrefsController {

    private final UserFavoriteRepository favoriteRepository;
    private final UserRecentRepository recentRepository;

    private static final int MAX_RECENT = 10;

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    @GetMapping("/favorites")
    @Operation(summary = "List favorites", description = "Returns user's favorite libraries with details")
    public ResponseEntity<?> getFavorites() {
        String username = getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<UserFavoriteEntity> favorites = favoriteRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFavoriteEntity fav : favorites) {
            String libId = fav.getLibraryId();
            String[] parts = parseLibraryId(libId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", fav.getId());
            item.put("libraryId", libId);
            if (parts != null) {
                item.put("libraryName", parts[0]);
                item.put("libraryVersion", parts[1]);
            } else {
                item.put("libraryName", libId);
                item.put("libraryVersion", "");
            }
            item.put("createdAt", fav.getCreatedAt());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /** Parse "Name-Version" library ID into [name, version], or null if invalid */
    private String[] parseLibraryId(String libraryId) {
        if (libraryId == null) return null;
        int lastDash = libraryId.lastIndexOf('-');
        if (lastDash <= 0 || lastDash == libraryId.length() - 1) return null;
        return new String[]{ libraryId.substring(0, lastDash), libraryId.substring(lastDash + 1) };
    }

    @PostMapping("/favorites/{libraryId}")
    @Operation(summary = "Add favorite", description = "Add a library to user's favorites")
    public ResponseEntity<?> addFavorite(@PathVariable String libraryId) {
        String username = getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (favoriteRepository.existsByUsernameAndLibraryId(username, libraryId)) {
            return ResponseEntity.ok(Map.of("message", "Already favorited"));
        }
        UserFavoriteEntity fav = UserFavoriteEntity.builder()
                .username(username)
                .libraryId(libraryId)
                .build();
        favoriteRepository.save(fav);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Favorited"));
    }

    @DeleteMapping("/favorites/{libraryId}")
    @Transactional
    @Operation(summary = "Remove favorite", description = "Remove a library from user's favorites")
    public ResponseEntity<?> removeFavorite(@PathVariable String libraryId) {
        String username = getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        favoriteRepository.deleteByUsernameAndLibraryId(username, libraryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recent")
    @Operation(summary = "List recent", description = "Returns user's recently accessed libraries")
    public ResponseEntity<?> getRecent() {
        String username = getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<UserRecentEntity> recents = recentRepository.findByUsernameOrderByAccessedAtDesc(username);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserRecentEntity r : recents) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("libraryId", r.getLibraryId());
            item.put("libraryName", r.getLibraryName());
            item.put("libraryVersion", r.getLibraryVersion());
            item.put("accessedAt", r.getAccessedAt());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/recent/{libraryId}")
    @Transactional
    @Operation(summary = "Add recent", description = "Add/update a library in user's recent list")
    public ResponseEntity<?> addRecent(@PathVariable String libraryId) {
        String username = getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String[] parts = parseLibraryId(libraryId);
        String libraryName = parts != null ? parts[0] : libraryId;
        String libraryVersion = parts != null ? parts[1] : "";

        Optional<UserRecentEntity> existing = recentRepository.findByUsernameAndLibraryId(username, libraryId);
        if (existing.isPresent()) {
            UserRecentEntity r = existing.get();
            r.setAccessedAt(LocalDateTime.now());
            r.setLibraryName(libraryName);
            r.setLibraryVersion(libraryVersion);
            recentRepository.save(r);
        } else {
            UserRecentEntity r = UserRecentEntity.builder()
                    .username(username)
                    .libraryId(libraryId)
                    .libraryName(libraryName)
                    .libraryVersion(libraryVersion)
                    .accessedAt(LocalDateTime.now())
                    .build();
            recentRepository.save(r);

            // Trim to MAX_RECENT
            List<UserRecentEntity> all = recentRepository.findByUsernameOrderByAccessedAtDesc(username);
            if (all.size() > MAX_RECENT) {
                List<UserRecentEntity> toRemove = all.subList(MAX_RECENT, all.size());
                recentRepository.deleteAll(toRemove);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Added to recent"));
    }

    @DeleteMapping("/recent")
    @Transactional
    @Operation(summary = "Clear recent", description = "Clear all recent libraries for user")
    public ResponseEntity<?> clearRecent() {
        String username = getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        recentRepository.deleteAllByUsername(username);
        return ResponseEntity.noContent().build();
    }
}
