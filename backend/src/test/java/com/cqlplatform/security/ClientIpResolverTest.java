package com.cqlplatform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void publicPeerIsUsedAsIsAndAttackerXffIgnored() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");                 // request straight from a public IP
        req.addHeader("X-Forwarded-For", "8.8.8.8");      // attacker-supplied spoof — must be ignored
        assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void trustedPrivatePeerHonoursRightmostNonPrivateXff() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.5");                  // docker bridge = trusted proxy
        req.addHeader("X-Forwarded-For", "198.51.100.9, 203.0.113.7");
        assertThat(ClientIpResolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void privatePeerWithoutXffFallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        assertThat(ClientIpResolver.resolve(req)).isEqualTo("10.0.0.9");
    }

    @Test
    void allPrivateXffFallsBackToFirstEntry() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "10.1.1.1, 192.168.1.2");
        assertThat(ClientIpResolver.resolve(req)).isEqualTo("10.1.1.1");
    }

    @Test
    void twoClientsBehindSameProxyResolveDistinctly() {
        MockHttpServletRequest a = new MockHttpServletRequest();
        a.setRemoteAddr("172.18.0.5");
        a.addHeader("X-Forwarded-For", "203.0.113.1");

        MockHttpServletRequest b = new MockHttpServletRequest();
        b.setRemoteAddr("172.18.0.5");
        b.addHeader("X-Forwarded-For", "203.0.113.2");

        assertThat(ClientIpResolver.resolve(a)).isNotEqualTo(ClientIpResolver.resolve(b));
    }

    @Test
    void nonIpLiteralIsNonPrivateWithoutDnsLookup() {
        // A hostname / mock value must NOT trigger a DNS resolution and must be
        // classified non-private (so it is used verbatim, never trusting XFF).
        assertThat(ClientIpResolver.isPrivateAddress("client-a")).isFalse();
        assertThat(ClientIpResolver.isPrivateAddress("10.0.0.1")).isTrue();
        assertThat(ClientIpResolver.isPrivateAddress("192.168.1.1")).isTrue();
        assertThat(ClientIpResolver.isPrivateAddress("203.0.113.7")).isFalse();
        assertThat(ClientIpResolver.isPrivateAddress(null)).isFalse();
    }
}
