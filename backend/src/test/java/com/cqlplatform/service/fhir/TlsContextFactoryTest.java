package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.EhrConnectionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TlsContextFactoryTest {

    private TlsContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TlsContextFactory();
    }

    @Test
    void createSslContext_tlsDisabled_returnsNull() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(false);

        SSLContext result = factory.createSslContext(connection);
        assertNull(result);
    }

    @Test
    void createSslContext_tlsEnabledButNoCerts_returnsNull() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(true);

        SSLContext result = factory.createSslContext(connection);
        assertNull(result);
    }

    @Test
    void createSslContext_tlsEnabledBlankCerts_returnsNull() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(true);
        connection.setCaCertPem("   ");
        connection.setClientCertPem("   ");

        SSLContext result = factory.createSslContext(connection);
        assertNull(result);
    }

    @Test
    void createHostnameVerifier_hostnameVerificationEnabled_returnsNull() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(true);
        connection.setHostnameVerification(true);

        HostnameVerifier result = factory.createHostnameVerifier(connection);
        assertNull(result);
    }

    @Test
    void createHostnameVerifier_hostnameVerificationDisabled_returnsPermissiveVerifier() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(true);
        connection.setHostnameVerification(false);

        HostnameVerifier result = factory.createHostnameVerifier(connection);
        assertNotNull(result);
        assertTrue(result.verify("any.host.com", null));
    }

    @Test
    void createHostnameVerifier_tlsDisabled_returnsNull() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(false);
        connection.setHostnameVerification(false);

        HostnameVerifier result = factory.createHostnameVerifier(connection);
        assertNull(result);
    }

    @Test
    void parsePrivateKey_invalidKey_throwsException() {
        assertThrows(Exception.class, () -> factory.parsePrivateKey("not-a-valid-key"));
    }

    @Test
    void parseCertificates_emptyPem_returnsEmptyList() throws Exception {
        List<X509Certificate> certs = factory.parseCertificates("");
        assertTrue(certs.isEmpty());
    }

    @Test
    void parseCertificates_invalidPem_returnsEmptyList() throws Exception {
        List<X509Certificate> certs = factory.parseCertificates("not a certificate");
        assertTrue(certs.isEmpty());
    }

    @Test
    void buildTrustManagers_noCerts_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> factory.buildTrustManagers(""));
    }

    @Test
    void createSslContext_withValidMinVersion_noCerts_returnsNull() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setTlsEnabled(true);
        connection.setTlsMinVersion("TLSv1.3");
        // No certs configured — should return null
        SSLContext result = factory.createSslContext(connection);
        assertNull(result);
    }
}
