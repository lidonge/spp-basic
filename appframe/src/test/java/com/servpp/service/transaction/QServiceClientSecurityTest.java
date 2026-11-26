package com.servpp.service.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * QService client transport security against a JDK {@link HttpsServer} that requires a client certificate and a
 * bearer token. Every certificate and key is generated here with BouncyCastle (test scope); none is checked in.
 */
public class QServiceClientSecurityTest {
    private static final String PASSWORD = "changeit-test-password";
    private static final String TOKEN = "token-0123456789abcdef";

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Pki pki;
    private HttpsServer server;
    private String endpoint;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    /** A certificate authority with a server certificate, a client certificate and a foreign CA. */
    private static final class Pki {
        Path serverKeyStore, trustedClientKeyStore, trustStore, foreignClientKeyStore, foreignTrustStore, passwordFile;
    }

    @Before
    public void startServer() throws Exception {
        pki = new Pki();
        passwordFile(PASSWORD);
        KeyPair caKeys = keys();
        X509Certificate ca = certificate("CN=QService Test CA", caKeys.getPublic(), "CN=QService Test CA", caKeys, true, null, false);
        KeyPair serverKeys = keys();
        X509Certificate server = certificate("CN=localhost", serverKeys.getPublic(), "CN=QService Test CA", caKeys, false,
                new GeneralNames(new GeneralName[]{new GeneralName(GeneralName.dNSName, "localhost"),
                        new GeneralName(GeneralName.iPAddress, "127.0.0.1")}), false);
        KeyPair clientKeys = keys();
        X509Certificate client = certificate("CN=basic-client", clientKeys.getPublic(), "CN=QService Test CA", caKeys, false, null, true);
        KeyPair foreignCaKeys = keys();
        X509Certificate foreignCa = certificate("CN=Foreign CA", foreignCaKeys.getPublic(), "CN=Foreign CA", foreignCaKeys, true, null, false);
        KeyPair foreignClientKeys = keys();
        X509Certificate foreignClient = certificate("CN=intruder", foreignClientKeys.getPublic(), "CN=Foreign CA", foreignCaKeys, false, null, true);

        pki.serverKeyStore = keyStore("server.p12", serverKeys, server, ca);
        pki.trustedClientKeyStore = keyStore("client.p12", clientKeys, client, ca);
        pki.trustStore = trustStore("trust.p12", ca);
        pki.foreignClientKeyStore = keyStore("foreign-client.p12", foreignClientKeys, foreignClient, foreignCa);
        pki.foreignTrustStore = trustStore("foreign-trust.p12", foreignCa);
        startHttps();
    }

    @After
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    private void startHttps() throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(pki.serverKeyStore)) { keys.load(in, PASSWORD.toCharArray()); }
        KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyFactory.init(keys, PASSWORD.toCharArray());
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(pki.trustStore)) { trust.load(in, PASSWORD.toCharArray()); }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyFactory.getKeyManagers(), trustFactory.getTrustManagers(), null);
        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override public void configure(HttpsParameters parameters) {
                var configured = context.getDefaultSSLParameters();
                configured.setNeedClientAuth(true);
                configured.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                parameters.setSSLParameters(configured);
            }
        });
        server.createContext("/", this::answer);
        server.start();
        endpoint = "https://localhost:" + server.getAddress().getPort();
    }

    private void answer(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(authorization == null ? "-" : authorization);
        String path = exchange.getRequestURI().getPath();
        byte[] request = exchange.getRequestBody().readAllBytes();
        if (!("Bearer " + TOKEN).equals(authorization)) {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer");
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        String body;
        if (path.equals("/capabilities")) {
            body = "{\"capabilities\":[\"ONE_PHASE\",\"PROVISIONING\"],\"contract\":\"spp-qservice-logical/1\"}";
        } else if (path.equals("/logical/commands")) {
            JsonNode command = new ObjectMapper().readTree(request);
            body = ("{\"namespace\":\"%s\",\"requestKey\":\"%s\",\"originalRequestKey\":null,\"boundaryResponse\":"
                    + "{\"conservationID\":42,\"originalConservationID\":null,\"operation\":\"ENTANGLE\",\"status\":\"SUCCESS\"}}")
                    .formatted(command.get("namespace").asText(), command.get("requestKey").asText());
        } else if (path.equals("/logical/provisions")) {
            JsonNode provision = new ObjectMapper().readTree(request);
            body = "{\"logicalKey\":%s,\"quantumID\":17,\"definition\":%s,\"physicalInitialValue\":\"1234\"}"
                    .formatted(provision.get("logicalKey"), provision.get("definition"));
        } else {
            String[] parts = path.split("/");
            body = ("{\"namespace\":\"%s\",\"requestKey\":\"%s\",\"originalRequestKey\":null,\"boundaryResponse\":"
                    + "{\"conservationID\":42,\"originalConservationID\":null,\"operation\":\"ENTANGLE\",\"status\":\"SUCCESS\"}}")
                    .formatted(parts[parts.length - 2], parts[parts.length - 1]);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private QServiceClientSecurity mutualTls(Path identity, Path trust, Path tokenFile) {
        return QServiceClientSecurity.of(new QServiceClientSecurity.Tls(
                identity, identity == null ? null : "PKCS12", identity == null ? null : pki.passwordFile,
                trust, "PKCS12", pki.passwordFile), tokenFile);
    }

    private Path token(String value) throws IOException {
        Path file = temp.newFile().toPath();
        Files.writeString(file, value + "\n");
        return file;
    }

    private HttpLogicalQuantumServiceClient client(QServiceClientSecurity security) {
        return new HttpLogicalQuantumServiceClient(endpoint, 5000, security);
    }

    private static LogicalQuantumServiceCommand command() {
        var mutation = new LogicalQuantumServiceCommand.Mutation(
                new LogicalQuantumServiceCommand.LogicalKey("commerce", "Account.balance", "A"),
                new LogicalQuantumServiceCommand.Definition("LONG", "0", "1000000", 0), "-10");
        var credit = new LogicalQuantumServiceCommand.Mutation(
                new LogicalQuantumServiceCommand.LogicalKey("commerce", "Order.balance", "B"),
                new LogicalQuantumServiceCommand.Definition("LONG", "0", "1000000", 0), "10");
        return new LogicalQuantumServiceCommand(LogicalQuantumServiceCommand.Operation.ENTANGLE, "commerce", "accounting",
                "REQ-TLS-1", null, LogicalQuantumServiceCommand.AtomicGroupShape.NONE, List.of(mutation, credit));
    }

    private static LogicalQuantumProvisioningRequest provisioning() {
        return new LogicalQuantumProvisioningRequest(new LogicalQuantumServiceCommand.LogicalKey("commerce", "Account.balance", "A"),
                new LogicalQuantumServiceCommand.Definition("LONG", "0", "1000000", 2), "12.34");
    }

    @Test
    public void certificateAndTokenReachCommandQueryCapabilitiesAndProvisioning() throws Exception {
        var security = mutualTls(pki.trustedClientKeyStore, pki.trustStore, token(TOKEN));
        var service = client(security);

        assertEquals(LogicalQuantumServiceResponse.Outcome.SUCCESS, service.execute(command()).boundaryResponse().status());
        assertEquals(LogicalQuantumServiceResponse.Outcome.SUCCESS, service.query("commerce", "REQ-TLS-1").boundaryResponse().status());
        assertEquals(LogicalQuantumCapabilities.CONTRACT, service.capabilities().contract());
        assertEquals("1234", new HttpLogicalQuantumProvisioningClient(endpoint, 5000, security).provision(provisioning()).physicalInitialValue());

        assertEquals(4, authorizations.size());
        assertTrue(authorizations.toString(), authorizations.stream().allMatch(("Bearer " + TOKEN)::equals));
    }

    @Test
    public void aCallerBuiltHttpClientIsAcceptedByBothClients() throws Exception {
        var security = mutualTls(pki.trustedClientKeyStore, pki.trustStore, token(TOKEN));
        var http = security.newHttpClient();
        var service = new HttpLogicalQuantumServiceClient(endpoint, 5000, new ObjectMapper(), http, security);
        var provisioning = new HttpLogicalQuantumProvisioningClient(endpoint, 5000, new ObjectMapper(), http, security);

        assertEquals(LogicalQuantumCapabilities.CONTRACT, service.capabilities().contract());
        assertEquals("1234", provisioning.provision(provisioning()).physicalInitialValue());
    }

    @Test
    public void noClientCertificateFailsTheHandshakeAsATransportFailure() throws Exception {
        var service = client(mutualTls(null, pki.trustStore, token(TOKEN)));
        var failure = assertThrows(IllegalStateException.class, () -> service.capabilities());
        assertFalse(failure instanceof QServiceAuthenticationException);
        assertTrue(failure.getCause() instanceof IOException);
        assertTrue(authorizations.toString(), authorizations.isEmpty());

        // The settlement turns it into UNKNOWN, which the bridge resolves with a query.
        var settlement = new QServiceQuantumSettlement("qsvc", service, new HttpLogicalQuantumProvisioningClient(
                endpoint, 5000, mutualTls(null, pki.trustStore, token(TOKEN))));
        var response = settlement.query(new SettlementKey("commerce", "REQ-TLS-1"));
        assertEquals(SettlementResponse.Outcome.UNKNOWN, response.outcome());
        assertEquals("TRANSPORT_FAILURE", response.failureReason().orElseThrow().code());
    }

    @Test
    public void anUntrustedCaFailsTheHandshakeInBothDirections() throws Exception {
        // The client does not trust the server's CA.
        var distrustsServer = client(mutualTls(pki.trustedClientKeyStore, pki.foreignTrustStore, token(TOKEN)));
        assertTrue(assertThrows(IllegalStateException.class, () -> distrustsServer.capabilities()).getCause() instanceof IOException);
        // The server does not trust the client certificate's CA.
        var intruder = client(mutualTls(pki.foreignClientKeyStore, pki.trustStore, token(TOKEN)));
        var failure = assertThrows(IllegalStateException.class, () -> intruder.capabilities());
        assertFalse(failure instanceof QServiceAuthenticationException);
        assertTrue(failure.getCause() instanceof IOException);
        assertTrue(authorizations.toString(), authorizations.isEmpty());
    }

    @Test
    public void aMissingOrWrongTokenIsAnAuthenticationFailureNotUnknown() throws Exception {
        for (Path tokenFile : new Path[]{null, token("a-wrong-token-0123456789")}) {
            var security = mutualTls(pki.trustedClientKeyStore, pki.trustStore, tokenFile);
            var service = client(security);
            var refused = assertThrows(QServiceAuthenticationException.class, () -> service.capabilities());
            assertEquals(401, refused.status());
            assertFalse(refused.getMessage(), refused.getMessage().contains("wrong-token"));
            assertEquals(401, assertThrows(QServiceAuthenticationException.class, () -> service.execute(command())).status());
            assertEquals(401, assertThrows(QServiceAuthenticationException.class,
                    () -> new HttpLogicalQuantumProvisioningClient(endpoint, 5000, security).provision(provisioning())).status());

            var settlement = new QServiceQuantumSettlement("qsvc", service, null);
            assertThrows(QServiceAuthenticationException.class, () -> settlement.query(new SettlementKey("commerce", "REQ-TLS-1")));
        }
    }

    @Test
    public void theTokenFileIsReadPerRequestSoRotationWorks() throws Exception {
        Path file = token("a-stale-token-0123456789");
        var service = client(mutualTls(pki.trustedClientKeyStore, pki.trustStore, file));
        assertThrows(QServiceAuthenticationException.class, () -> service.capabilities());
        Files.writeString(file, TOKEN);
        assertEquals(LogicalQuantumCapabilities.CONTRACT, service.capabilities().contract());
        Files.delete(file);
        var unreadable = assertThrows(QServiceAuthenticationException.class, () -> service.capabilities());
        assertEquals(0, unreadable.status());
        assertFalse(unreadable.getMessage(), unreadable.getMessage().contains(TOKEN));
    }

    @Test
    public void anHttpEndpointIsRefusedUnlessTheInsecureFlagIsSet() {
        assertThrows(IllegalArgumentException.class, () -> new HttpLogicalQuantumServiceClient("http://127.0.0.1:9", 1000));
        assertThrows(IllegalArgumentException.class, () -> new HttpLogicalQuantumProvisioningClient("http://127.0.0.1:9", 1000));
        assertThrows(IllegalArgumentException.class, () -> new HttpLogicalQuantumServiceClient(
                "http://127.0.0.1:9", 1000, QServiceClientSecurity.defaults()));
        assertNotNull(new HttpLogicalQuantumServiceClient("http://127.0.0.1:9", 1000, QServiceClientSecurity.insecure()));
        assertNotNull(new HttpLogicalQuantumProvisioningClient("http://127.0.0.1:9", 1000, QServiceClientSecurity.insecure()));
        // The same rule applies to a caller-built client.
        assertThrows(IllegalArgumentException.class, () -> new HttpLogicalQuantumServiceClient(
                "http://127.0.0.1:9", 1000, new ObjectMapper(), java.net.http.HttpClient.newHttpClient(), QServiceClientSecurity.defaults()));
    }

    @Test
    public void phyConfigKeysBuildTheSecurityAndRejectInconsistentSettings() throws Exception {
        Map<String, String> config = Map.of(
                "tlsKeyStore", pki.trustedClientKeyStore.toString(), "tlsKeyStorePasswordFile", pki.passwordFile.toString(),
                "tlsTrustStore", pki.trustStore.toString(), "tlsTrustStorePasswordFile", pki.passwordFile.toString(),
                "tokenFile", token(TOKEN).toString());
        assertTrue(QServiceClientSecurity.configErrors(config).isEmpty());
        assertEquals(LogicalQuantumCapabilities.CONTRACT, client(QServiceClientSecurity.fromConfig(config)).capabilities().contract());

        assertTrue(QServiceClientSecurity.fromConfig(Map.of("insecure", "true")).isInsecure());
        assertFalse(QServiceClientSecurity.configErrors(Map.of("insecure", "true", "tokenFile", "/x")).isEmpty());
        assertFalse(QServiceClientSecurity.configErrors(Map.of("insecure", "maybe")).isEmpty());
        assertFalse(QServiceClientSecurity.configErrors(Map.of("tlsKeyStore", "/x")).isEmpty());
        assertFalse(QServiceClientSecurity.configErrors(Map.of("tlsTrustStorePasswordFile", "/x")).isEmpty());
        assertFalse(QServiceClientSecurity.configErrors(Map.of("tokenFile", " ")).isEmpty());
    }

    @Test
    public void aWrongStorePasswordFailsWithoutRevealingAnyPassword() throws Exception {
        Path wrong = temp.newFile().toPath();
        Files.writeString(wrong, "not-the-password-xyz");
        var security = QServiceClientSecurity.of(new QServiceClientSecurity.Tls(
                pki.trustedClientKeyStore, "PKCS12", wrong, pki.trustStore, "PKCS12", pki.passwordFile), null);
        var failure = assertThrows(IllegalStateException.class, security::newHttpClient);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = String.valueOf(cause.getMessage());
            assertFalse(message, message.contains("not-the-password-xyz") || message.contains(PASSWORD));
        }
    }

    // ---- certificate generation (BouncyCastle, test scope)

    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(String subject, java.security.PublicKey publicKey, String issuer, KeyPair signer,
                                               boolean authority, GeneralNames names, boolean clientAuth) throws Exception {
        Date from = new Date(System.currentTimeMillis() - 3_600_000L);
        Date to = new Date(System.currentTimeMillis() + 86_400_000L);
        var builder = new JcaX509v3CertificateBuilder(new X500Name(issuer), new BigInteger(64, new java.security.SecureRandom()),
                from, to, new X500Name(subject), publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(authority));
        if (authority) {
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else {
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement));
            builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(
                    clientAuth ? KeyPurposeId.id_kp_clientAuth : KeyPurposeId.id_kp_serverAuth));
        }
        if (names != null) builder.addExtension(Extension.subjectAlternativeName, false, names);
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(signer.getPrivate())));
    }

    private void passwordFile(String password) throws IOException {
        pki.passwordFile = temp.newFile("store.password").toPath();
        Files.writeString(pki.passwordFile, password + "\n");
    }

    private Path keyStore(String name, KeyPair pair, X509Certificate certificate, X509Certificate authority) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("identity", pair.getPrivate(), PASSWORD.toCharArray(), new java.security.cert.Certificate[]{certificate, authority});
        return write(name, store);
    }

    private Path trustStore(String name, X509Certificate authority) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("ca", authority);
        return write(name, store);
    }

    private Path write(String name, KeyStore store) throws Exception {
        Path file = temp.getRoot().toPath().resolve(name);
        try (OutputStream out = Files.newOutputStream(file)) { store.store(out, PASSWORD.toCharArray()); }
        return file;
    }
}
