package com.cqlplatform.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues short-lived, single-use tickets that can be exchanged for
 * long-lived-connection authentication (originally SSE, now WebSocket
 * after PAT-167).  This avoids passing long-lived JWT access tokens in
 * query parameters where they leak into server logs, browser history,
 * and proxy caches.  The {@code /api/auth/sse-ticket} URL is kept as
 * the issuance endpoint for backward compatibility — there is nothing
 * SSE-specific about the ticket itself; the same redeem-once-or-expire
 * semantics serve both transports.
 */
@Service
@Slf4j
public class SseTicketService {

    private static final long TICKET_TTL_MS = 30_000; // 30 seconds

    private record TicketEntry(String username, String role, Instant expiresAt) {}

    private final Map<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    /**
     * Create a one-time ticket for the given authenticated user.
     *
     * @return opaque ticket string (UUID)
     */
    public String issueTicket(String username, String role) {
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new TicketEntry(username, role,
                Instant.now().plusMillis(TICKET_TTL_MS)));
        return ticket;
    }

    /**
     * Consume a ticket.  Returns the username+role if the ticket is valid
     * and not yet consumed; empty otherwise.  The ticket is removed on
     * first use regardless of outcome (single-use).
     */
    public Optional<TicketPrincipal> redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        TicketEntry entry = tickets.remove(ticket);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            log.debug("SSE ticket expired for user {}", entry.username());
            return Optional.empty();
        }
        return Optional.of(new TicketPrincipal(entry.username(), entry.role()));
    }

    /** Purge expired tickets every 60 seconds. */
    @Scheduled(fixedRate = 60_000)
    public void purgeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    public record TicketPrincipal(String username, String role) {}
}
