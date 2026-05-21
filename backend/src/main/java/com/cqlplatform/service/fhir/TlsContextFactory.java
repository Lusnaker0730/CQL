package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.EhrConnectionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Factory for creating SSLContext instances from PEM-encoded certificates
 * stored on EHR connections. Supports custom CA trust stores and mTLS
 * client certificates.
 */
@Component
@Slf4j
public class TlsContextFactory {

    /**
     * Master kill-switch for the per-connection {@code hostnameVerification=false}
     * option. When false (default), the DB column is ignored and strict hostname
     * verification always applies — protects against an admin accidentally / maliciously
     * disabling verification on a prod EHR connection and silently accepting MITM
     * traffic. Operators who genuinely need lax verification (lab environments with
     * self-signed certs against odd hostnames) must explicitly set
     * {@code security.tls.allow-disable-hostname-verification=true} in non-prod yml.
     */
    @Value("${security.tls.allow-disable-hostname-verification:false}")
    private boolean allowDisableHostnameVerification;

    /**
     * Build an SSLContext for the given EHR connection's TLS configuration.
     * Returns null if TLS is not enabled or no custom certificates are configured.
     */
    public SSLContext createSslContext(EhrConnectionEntity connection) {
        if (!connection.isTlsEnabled()) {
            return null;
        }

        boolean hasCaCert = connection.getCaCertPem() != null && !connection.getCaCertPem().isBlank();
        boolean hasClientCert = connection.getClientCertPem() != null && !connection.getClientCertPem().isBlank()
                && connection.getClientKeyPem() != null && !connection.getClientKeyPem().isBlank();

        if (!hasCaCert && !hasClientCert) {
            return null;
        }

        try {
            TrustManager[] trustManagers = null;
            KeyManager[] keyManagers = null;

            if (hasCaCert) {
                trustManagers = buildTrustManagers(connection.getCaCertPem());
            }

            if (hasClientCert) {
                keyManagers = buildKeyManagers(connection.getClientCertPem(), connection.getClientKeyPem());
            }

            String protocol = connection.getTlsMinVersion() != null ? connection.getTlsMinVersion() : "TLSv1.2";
            SSLContext sslContext = SSLContext.getInstance(protocol);
            sslContext.init(keyManagers, trustManagers, new SecureRandom());

            log.info("Created SSLContext for EHR connection {} (CA cert: {}, client cert: {}, protocol: {})",
                    connection.getId(), hasCaCert, hasClientCert, protocol);

            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SSL context for EHR connection " + connection.getId(), e);
        }
    }

    /**
     * Determine if hostname verification should be used for the connection.
     * Returns null when default (strict) hostname verification should be used.
     */
    public HostnameVerifier createHostnameVerifier(EhrConnectionEntity connection) {
        if (!connection.isTlsEnabled() || connection.isHostnameVerification()) {
            return null; // Use default (strict) hostname verification
        }
        // PAT-142: never honour the per-connection disable flag unless the operator
        // has explicitly opted in via security.tls.allow-disable-hostname-verification.
        // A misconfigured DB column on a prod EHR connection used to silently turn into
        // an accept-all hostname verifier — catastrophic against MITM.
        if (!allowDisableHostnameVerification) {
            log.warn("EHR connection {} has hostnameVerification=false but global "
                    + "security.tls.allow-disable-hostname-verification is false — "
                    + "applying strict hostname verification anyway", connection.getId());
            return null;
        }
        log.warn("Hostname verification disabled for EHR connection {} — use only in test environments", connection.getId());
        return (hostname, session) -> true;
    }

    TrustManager[] buildTrustManagers(String caCertPem)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        List<X509Certificate> caCerts = parseCertificates(caCertPem);
        if (caCerts.isEmpty()) {
            throw new IllegalArgumentException("No valid CA certificates found in PEM data");
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        for (int i = 0; i < caCerts.size(); i++) {
            trustStore.setCertificateEntry("ca-" + i, caCerts.get(i));
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    KeyManager[] buildKeyManagers(String clientCertPem, String clientKeyPem)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
            InvalidKeySpecException, UnrecoverableKeyException {
        List<X509Certificate> clientCerts = parseCertificates(clientCertPem);
        if (clientCerts.isEmpty()) {
            throw new IllegalArgumentException("No valid client certificates found in PEM data");
        }

        PrivateKey privateKey = parsePrivateKey(clientKeyPem);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        Certificate[] chain = clientCerts.toArray(new Certificate[0]);
        char[] password = "changeit".toCharArray();
        keyStore.setKeyEntry("client", privateKey, password, chain);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        return kmf.getKeyManagers();
    }

    List<X509Certificate> parseCertificates(String pem) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certs = new ArrayList<>();

        // Handle multiple certificates in a single PEM string
        byte[] pemBytes = pem.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pemBytes)) {
            while (bais.available() > 0) {
                try {
                    Certificate cert = cf.generateCertificate(bais);
                    if (cert instanceof X509Certificate x509) {
                        certs.add(x509);
                    }
                } catch (CertificateException e) {
                    // End of certificates or invalid data
                    break;
                }
            }
        } catch (IOException e) {
            throw new CertificateException("Failed to read PEM data", e);
        }
        return certs;
    }

    PrivateKey parsePrivateKey(String pem) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        // Try RSA first, then EC
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (InvalidKeySpecException e) {
            try {
                return KeyFactory.getInstance("EC").generatePrivate(keySpec);
            } catch (InvalidKeySpecException e2) {
                throw new InvalidKeySpecException(
                        "Private key is not a valid PKCS#8 RSA or EC key. Ensure the key is in PKCS#8 format (BEGIN PRIVATE KEY).");
            }
        }
    }
}
