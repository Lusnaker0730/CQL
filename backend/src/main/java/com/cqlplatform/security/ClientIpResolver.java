package com.cqlplatform.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;

/**
 * Single source of truth for "what is the real client IP of this request",
 * shared by every per-IP filter (rate limiting, audit) so they can never
 * disagree about who the client is.
 *
 * <p><b>Why this exists:</b> {@link RateLimitFilter} previously keyed buckets on
 * {@code request.getRemoteAddr()} directly. Behind the reverse proxy chain
 * (Cloudflare → VM nginx → frontend nginx → backend) that address is always the
 * <em>proxy container's</em> IP, so every external client collapsed into a single
 * shared bucket — one attacker could exhaust the whole site's AUTH bucket and lock
 * everyone out. {@link AuditFilter} already resolved the true client correctly; this
 * class extracts that logic so both filters use identical, correct handling.
 *
 * <p><b>Trust model (H9):</b> {@code X-Forwarded-For} is only honoured when the
 * direct TCP peer ({@code remoteAddr}) is a private / loopback address — i.e. a
 * trusted reverse proxy on the same host or internal network. A request arriving
 * straight from a public IP uses {@code remoteAddr} as-is, so an attacker cannot
 * spoof their identity by injecting an XFF header. When XFF is trusted we take the
 * <em>rightmost</em> non-private entry, which is the address our own proxy appended.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * Resolve the real client IP for {@code request}. Never returns {@code null}
     * for a well-formed request (falls back to {@code remoteAddr}).
     */
    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // Only trust X-Forwarded-For when the direct peer is a trusted (private) proxy.
        if (isPrivateAddress(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] ips = xff.split(",");
                for (int i = ips.length - 1; i >= 0; i--) {
                    String ip = ips[i].trim();
                    if (!ip.isEmpty() && !isPrivateAddress(ip)) {
                        return ip;
                    }
                }
                return ips[0].trim(); // all entries private — use the first
            }
        }
        return remoteAddr;
    }

    /**
     * True if {@code ip} is a loopback / site-local / link-local address.
     *
     * <p>Only IP literals are classified; non-literal strings (hostnames, mock
     * values like {@code "client-a"}) return {@code false} <em>without</em> a DNS
     * lookup — resolving them would be both slow/flaky and an SSRF-shaped side
     * effect driven by request-controlled input. Real servlet {@code remoteAddr}
     * values are always literals, so behaviour is unchanged for real traffic.
     */
    public static boolean isPrivateAddress(String ip) {
        if (ip == null || !looksLikeIpLiteral(ip)) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /** IPv4 = digits + dots; IPv6 = contains a colon. Cheap literal check, no DNS. */
    private static boolean looksLikeIpLiteral(String s) {
        if (s.indexOf(':') >= 0) {
            return true; // IPv6 literal (possibly with zone id)
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && c != '.') {
                return false;
            }
        }
        return !s.isEmpty();
    }
}
