package com.cqlplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class SseTicketServiceTest {

    private SseTicketService service;

    @BeforeEach
    void setUp() {
        service = new SseTicketService();
    }

    @Test
    void issueAndRedeem_shouldReturnPrincipal() {
        String ticket = service.issueTicket("alice", "ADMIN");

        Optional<SseTicketService.TicketPrincipal> result = service.redeem(ticket);

        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("alice");
        assertThat(result.get().role()).isEqualTo("ADMIN");
    }

    @Test
    void redeem_sameTicketTwice_shouldFailSecondTime() {
        String ticket = service.issueTicket("bob", "USER");

        assertThat(service.redeem(ticket)).isPresent();
        assertThat(service.redeem(ticket)).isEmpty(); // single-use
    }

    @Test
    void redeem_unknownTicket_shouldReturnEmpty() {
        assertThat(service.redeem("nonexistent-uuid")).isEmpty();
    }

    @Test
    void redeem_nullOrBlank_shouldReturnEmpty() {
        assertThat(service.redeem(null)).isEmpty();
        assertThat(service.redeem("")).isEmpty();
        assertThat(service.redeem("  ")).isEmpty();
    }

    @Test
    void purgeExpired_shouldNotAffectValidTickets() {
        String ticket = service.issueTicket("carol", "USER");

        service.purgeExpired();

        assertThat(service.redeem(ticket)).isPresent();
    }

    @Test
    void multipleTickets_shouldBeIndependent() {
        String t1 = service.issueTicket("user1", "USER");
        String t2 = service.issueTicket("user2", "ADMIN");

        // Redeem t2 first
        assertThat(service.redeem(t2)).isPresent()
                .hasValueSatisfying(p -> assertThat(p.username()).isEqualTo("user2"));

        // t1 still valid
        assertThat(service.redeem(t1)).isPresent()
                .hasValueSatisfying(p -> assertThat(p.username()).isEqualTo("user1"));
    }
}
