package com.servpp.service.transaction;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Transport security of a QService client: mutual TLS and an optional bearer token.
 *
 * <p>Only the JDK key and trust manager factories are used. Passwords and the token live in files; the
 * password files are read when the store is loaded, the token file on every request so that rotation works.
 * Neither a password nor the token is ever logged, kept in a message, or retained after use.
 * An {@code https://} endpoint is required unless {@code insecure} is set, which is for development only
 * and excludes TLS settings and a token.
 */
public final class QServiceClientSecurity {
    /** The only protocols offered by a client built from this configuration. */
    public static final List<String> PROTOCOLS = List.of("TLSv1.3", "TLSv1.2");
    public static final String DEFAULT_STORE_TYPE = "PKCS12";
    /** PHY {@code config} keys of the QService settlement technical service. */
    public static final List<String> CONFIG_KEYS = List.of("insecure", "tlsKeyStore", "tlsKeyStoreType",
            "tlsKeyStorePasswordFile", "tlsTrustStore", "tlsTrustStoreType", "tlsTrustStorePasswordFile", "tokenFile");

    /** Client identity and server trust; either store may be omitted (JVM default trust, no client certificate). */
    public record Tls(Path keyStore, String keyStoreType, Path keyStorePasswordFile,
                      Path trustStore, String trustStoreType, Path trustStorePasswordFile) {
        public Tls {
            if (keyStore != null && keyStorePasswordFile == null)
                throw new IllegalArgumentException("QService TLS key store requires a password file.");
            if (trustStore != null && trustStorePasswordFile == null)
                throw new IllegalArgumentException("QService TLS trust store requires a password file.");
            if (keyStore == null && (keyStorePasswordFile != null || keyStoreType != null))
                throw new IllegalArgumentException("QService TLS key store settings require a key store path.");
            if (trustStore == null && (trustStorePasswordFile != null || trustStoreType != null))
                throw new IllegalArgumentException("QService TLS trust store settings require a trust store path.");
            keyStoreType = keyStore == null ? null : blankToDefault(keyStoreType);
            trustStoreType = trustStore == null ? null : blankToDefault(trustStoreType);
        }
    }

    private final boolean insecure;
    private final Tls tls;
    private final Path tokenFile;

    private QServiceClientSecurity(boolean insecure, Tls tls, Path tokenFile) {
        if (insecure && (tls != null || tokenFile != null))
            throw new IllegalArgumentException("QService insecure mode excludes TLS settings and a token.");
        this.insecure = insecure;
        this.tls = tls;
        this.tokenFile = tokenFile;
    }

    /** https with the JVM default trust and no client certificate or token. */
    public static QServiceClientSecurity defaults() {
        return new QServiceClientSecurity(false, null, null);
    }

    /** Development only: plain http (or https) without TLS settings or a token. */
    public static QServiceClientSecurity insecure() {
        return new QServiceClientSecurity(true, null, null);
    }

    public static QServiceClientSecurity of(Tls tls, Path tokenFile) {
        return new QServiceClientSecurity(false, tls, tokenFile);
    }

    public boolean isInsecure() {
        return insecure;
    }

    /** Reads the PHY config keys of {@link #CONFIG_KEYS}; all are optional. */
    public static QServiceClientSecurity fromConfig(Map<String, String> config) {
        List<String> errors = configErrors(config);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));
        if (flag(config, "insecure")) return insecure();
        Tls tls = null;
        if (present(config, "tlsKeyStore") || present(config, "tlsTrustStore")
                || present(config, "tlsKeyStoreType") || present(config, "tlsTrustStoreType")
                || present(config, "tlsKeyStorePasswordFile") || present(config, "tlsTrustStorePasswordFile")) {
            tls = new Tls(path(config, "tlsKeyStore"), value(config, "tlsKeyStoreType"), path(config, "tlsKeyStorePasswordFile"),
                    path(config, "tlsTrustStore"), value(config, "tlsTrustStoreType"), path(config, "tlsTrustStorePasswordFile"));
        }
        return of(tls, path(config, "tokenFile"));
    }

    /** Static validation of the config keys; reads no file. */
    public static List<String> configErrors(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        String insecure = value(config, "insecure");
        if (config.containsKey("insecure") && (insecure == null
                || !(insecure.equalsIgnoreCase("true") || insecure.equalsIgnoreCase("false"))))
            errors.add("config insecure must be true or false.");
        boolean security = false;
        for (String key : CONFIG_KEYS) {
            if (key.equals("insecure")) continue;
            if (config.containsKey(key) && !present(config, key)) errors.add("config " + key + " must not be blank when declared.");
            security |= present(config, key);
        }
        if (flag(config, "insecure") && security)
            errors.add("config insecure = true excludes the TLS settings and tokenFile.");
        if (present(config, "tlsKeyStore") && !present(config, "tlsKeyStorePasswordFile"))
            errors.add("config tlsKeyStore requires tlsKeyStorePasswordFile.");
        if (present(config, "tlsTrustStore") && !present(config, "tlsTrustStorePasswordFile"))
            errors.add("config tlsTrustStore requires tlsTrustStorePasswordFile.");
        if (!present(config, "tlsKeyStore") && (present(config, "tlsKeyStorePasswordFile") || present(config, "tlsKeyStoreType")))
            errors.add("config tlsKeyStorePasswordFile and tlsKeyStoreType require tlsKeyStore.");
        if (!present(config, "tlsTrustStore") && (present(config, "tlsTrustStorePasswordFile") || present(config, "tlsTrustStoreType")))
            errors.add("config tlsTrustStorePasswordFile and tlsTrustStoreType require tlsTrustStore.");
        return errors;
    }

    /** Rejects an endpoint whose scheme the mode does not allow. */
    public void requireEndpoint(URI endpoint) {
        if (!insecure && !"https".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException(
                    "Quantum service endpoint must use https unless insecure = true is set for development.");
    }

    /** The client for this configuration, restricted to TLSv1.3 and TLSv1.2. */
    public HttpClient newHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (insecure) return builder.build();
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            KeyManager[] keys = null;
            TrustManager[] trust = null;
            if (tls != null && tls.keyStore() != null) {
                KeyStore store = load(tls.keyStore(), tls.keyStoreType(), tls.keyStorePasswordFile());
                KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                char[] password = password(tls.keyStorePasswordFile());
                try { factory.init(store, password); } finally { Arrays.fill(password, '\0'); }
                keys = factory.getKeyManagers();
            }
            if (tls != null && tls.trustStore() != null) {
                KeyStore store = load(tls.trustStore(), tls.trustStoreType(), tls.trustStorePasswordFile());
                TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(store);
                trust = factory.getTrustManagers();
            }
            context.init(keys, trust, null);
            SSLParameters parameters = new SSLParameters();
            parameters.setProtocols(PROTOCOLS.toArray(String[]::new));
            return builder.sslContext(context).sslParameters(parameters).build();
        } catch (GeneralSecurityException | IOException e) {
            // Names the failing step, never a password.
            throw new IllegalStateException("QService TLS configuration could not be loaded: "
                    + e.getClass().getSimpleName() + ".", e);
        }
    }

    /** Adds {@code Authorization: Bearer} read from the token file now; a rotated token is picked up. */
    void authorize(HttpRequest.Builder request) {
        if (tokenFile == null) return;
        String token;
        try {
            token = Files.readString(tokenFile, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new QServiceAuthenticationException("QService token file cannot be read: " + tokenFile + ".", 0, e);
        }
        if (token.isEmpty() || token.chars().anyMatch(c -> c < 0x21 || c > 0x7e))
            throw new QServiceAuthenticationException("QService token file holds no valid bearer token: " + tokenFile + ".", 0, null);
        request.header("Authorization", "Bearer " + token);
    }

    /** HTTP 401 and 403 are an authentication failure, not a transport failure. */
    static void requireAuthorized(int status, String service) {
        if (status == 401 || status == 403)
            throw new QServiceAuthenticationException(service + " authentication failed: HTTP " + status + ".", status, null);
    }

    private static KeyStore load(Path path, String type, Path passwordFile) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        char[] password = password(passwordFile);
        try (InputStream in = Files.newInputStream(path)) {
            store.load(in, password);
        } finally {
            Arrays.fill(password, '\0');
        }
        return store;
    }

    private static char[] password(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            int end = text.length();
            while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) end--;
            return text.substring(0, end).toCharArray();
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String blankToDefault(String type) {
        return type == null || type.isBlank() ? DEFAULT_STORE_TYPE : type.trim();
    }

    private static boolean present(Map<String, String> config, String key) {
        String value = config.get(key);
        return value != null && !value.isBlank();
    }

    private static boolean flag(Map<String, String> config, String key) {
        return "true".equalsIgnoreCase(value(config, key));
    }

    private static String value(Map<String, String> config, String key) {
        String value = config.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Path path(Map<String, String> config, String key) {
        String value = value(config, key);
        return value == null ? null : Path.of(value);
    }
}
