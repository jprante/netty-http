package org.xbib.netty.http.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.ServerCertificateProvider;
import org.xbib.netty.http.common.security.SecurityUtil;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.endpoint.HttpEndpoint;
import org.xbib.netty.http.server.endpoint.HttpEndpointResolver;
import org.xbib.netty.http.server.api.Filter;

import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code Domain} class represents a virtual server with a name, with or without SSL.
 */
public class Domain {

    private static final Logger logger = Logger.getLogger(Domain.class.getName());

    private final String name;

    private final Set<String> aliases;

    private final HttpAddress httpAddress;

    private final SslContext sslContext;

    private final List<HttpEndpointResolver> httpEndpointResolvers;

    /**
     * Constructs a {@code NamedServer} with the given name.
     *
     * @param name the name, or null if it is the default server
     * @param aliases alias names for the named server
     * @param httpAddress HTTP address, used for determining if named server is secure or not
     * @param httpEndpointResolvers the endpoint resolvers
     * @param sslContext SSL context or null
     */
    protected Domain(String name, Set<String> aliases,
                     HttpAddress httpAddress,
                     List<HttpEndpointResolver> httpEndpointResolvers,
                     SslContext sslContext) {
        this.httpAddress = httpAddress;
        this.name = name;
        this.sslContext = sslContext;
        this.aliases = Collections.unmodifiableSet(aliases);
        this.httpEndpointResolvers = httpEndpointResolvers;
    }

    public static Builder builder() {
        return builder(HttpAddress.http1("localhost", 8008));
    }

    public static Builder builder(HttpAddress httpAddress) {
        return builder(httpAddress, "*");
    }

    public static Builder builder(HttpAddress httpAddress, String serverName) {
        return new Builder(httpAddress, serverName);
    }

    public HttpAddress getHttpAddress() {
        return httpAddress;
    }

    /**
     * Returns the name.
     *
     * @return the name, or null if it is the default server
     */
    public String getName() {
        return name;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    /**
     * Returns the aliases.
     *
     * @return the (unmodifiable) set of aliases (which may be empty)
     */
    public Set<String> getAliases() {
        return aliases;
    }

    /**
     * Handle server requests.
     * @param serverRequest the server request
     * @param serverResponse the server response
     * @throws IOException if handling server request fails
     */
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        if (httpEndpointResolvers != null) {
            boolean found = false;
            for (HttpEndpointResolver httpEndpointResolver : httpEndpointResolvers) {
                List<HttpEndpoint> matchingEndpoints = httpEndpointResolver.matchingEndpointsFor(serverRequest);
                if (matchingEndpoints != null && !matchingEndpoints.isEmpty()) {
                    httpEndpointResolver.handle(matchingEndpoints, serverRequest, serverResponse);
                    found = true;
                    break;
                }
            }
            if (!found) {
                ServerResponse.write(serverResponse, HttpResponseStatus.NOT_IMPLEMENTED);
            }
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_IMPLEMENTED);
        }
    }

    @Override
    public String toString() {
        return name + " (" + httpAddress + ") " + aliases;
    }

    public static class Builder {

        private HttpAddress httpAddress;

        private String serverName;

        private Set<String> aliases;

        private List<HttpEndpointResolver> httpEndpointResolvers;

        private TrustManagerFactory trustManagerFactory;

        private KeyStore trustManagerKeyStore;

        private Provider sslContextProvider;

        private SslProvider sslProvider;

        private Iterable<String> ciphers;

        private CipherSuiteFilter cipherSuiteFilter;

        private InputStream keyCertChainInputStream;

        private InputStream keyInputStream;

        private String keyPassword;

        Builder(HttpAddress httpAddress, String serverName) {
            Objects.requireNonNull(httpAddress);
            Objects.requireNonNull(serverName);
            this.httpAddress = httpAddress;
            this.serverName = serverName;
            this.aliases = new LinkedHashSet<>();
            this.httpEndpointResolvers = new ArrayList<>();
            this.trustManagerFactory = SecurityUtil.Defaults.DEFAULT_TRUST_MANAGER_FACTORY;
            this.sslProvider = SecurityUtil.Defaults.DEFAULT_SSL_PROVIDER;
            this.ciphers = SecurityUtil.Defaults.DEFAULT_CIPHERS;
            this.cipherSuiteFilter = SecurityUtil.Defaults.DEFAULT_CIPHER_SUITE_FILTER;
        }

        public Builder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
            Objects.requireNonNull(trustManagerFactory);
            this.trustManagerFactory = trustManagerFactory;
            return this;
        }

        public Builder setTrustManagerKeyStore(KeyStore trustManagerKeyStore) {
            Objects.requireNonNull(trustManagerKeyStore);
            this.trustManagerKeyStore = trustManagerKeyStore;
            return this;
        }

        public Builder setSslContextProvider(Provider sslContextProvider) {
            Objects.requireNonNull(sslContextProvider);
            this.sslContextProvider = sslContextProvider;
            return this;
        }

        public Builder setSslProvider(SslProvider sslProvider) {
            Objects.requireNonNull(sslProvider);
            this.sslProvider = sslProvider;
            return this;
        }

        public Builder setCiphers(Iterable<String> ciphers) {
            Objects.requireNonNull(ciphers);
            this.ciphers = ciphers;
            return this;
        }

        public Builder setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
            Objects.requireNonNull(cipherSuiteFilter);
            this.cipherSuiteFilter = cipherSuiteFilter;
            return this;
        }

        public Builder setJdkSslProvider() {
            setSslProvider(SslProvider.JDK);
            setCiphers(SecurityUtil.Defaults.JDK_CIPHERS);
            return this;
        }

        public Builder setOpenSSLSslProvider() {
            setSslProvider(SslProvider.OPENSSL);
            setCiphers(SecurityUtil.Defaults.OPENSSL_CIPHERS);
            return this;
        }

        public Builder setKeyCertChainInputStream(InputStream keyCertChainInputStream) {
            Objects.requireNonNull(keyCertChainInputStream);
            this.keyCertChainInputStream = keyCertChainInputStream;
            return this;
        }

        public Builder setKeyInputStream(InputStream keyInputStream) {
            Objects.requireNonNull(keyInputStream);
            this.keyInputStream = keyInputStream;
            return this;
        }

        public Builder setKeyPassword(String keyPassword) {
            // null in keyPassword allowed, it means no password
            this.keyPassword = keyPassword;
            return this;
        }

        public Builder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
            Objects.requireNonNull(keyCertChainInputStream);
            Objects.requireNonNull(keyInputStream);
            setKeyCertChainInputStream(keyCertChainInputStream);
            setKeyInputStream(keyInputStream);
            return this;
        }

        public Builder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                  String keyPassword) {
            Objects.requireNonNull(keyCertChainInputStream);
            Objects.requireNonNull(keyInputStream);
            Objects.requireNonNull(keyPassword);
            setKeyCertChainInputStream(keyCertChainInputStream);
            setKeyInputStream(keyInputStream);
            setKeyPassword(keyPassword);
            return this;
        }

        public Builder setSelfCert() throws Exception {
            ServiceLoader<ServerCertificateProvider> serverCertificateProviders = ServiceLoader.load(ServerCertificateProvider.class);
            for (ServerCertificateProvider serverCertificateProvider : serverCertificateProviders) {
                if ("org.xbib.netty.http.bouncycastle.BouncyCastleSelfSignedCertificateProvider".equals(serverCertificateProvider.getClass().getName())) {
                    serverCertificateProvider.prepare(serverName);
                    setKeyCertChainInputStream(serverCertificateProvider.getCertificateChain());
                    setKeyInputStream(serverCertificateProvider.getPrivateKey());
                    setKeyPassword(serverCertificateProvider.getKeyPassword());
                    logger.log(Level.INFO, "self signed certificate installed");
                }
            }
            if (keyCertChainInputStream == null) {
                logger.log(Level.WARNING, "unable to install self signed certificate. Is netty-http-bouncycastle present?");
            }
            return this;
        }

        /**
         * Adds an alias for this virtual server.
         *
         * @param alias the alias
         * @return this builder
         */
        public Builder addAlias(String alias) {
            Objects.requireNonNull(alias);
            aliases.add(alias);
            return this;
        }

        public Builder addEndpointResolver(HttpEndpointResolver httpEndpointResolver) {
            Objects.requireNonNull(httpEndpointResolver);
            this.httpEndpointResolvers.add(httpEndpointResolver);
            return this;
        }

        public Builder singleEndpoint(String path, Filter filter) {
            Objects.requireNonNull(path);
            Objects.requireNonNull(filter);
            addEndpointResolver(HttpEndpointResolver.builder()
                    .addEndpoint(HttpEndpoint.builder()
                            .setPath(path)
                            .build())
                    .setDispatcher((endpoint, req, resp) -> filter.handle(req, resp))
                    .build());
            return this;
        }

        public Builder singleEndpoint(String prefix, String path, Filter filter) {
            Objects.requireNonNull(prefix);
            Objects.requireNonNull(path);
            Objects.requireNonNull(filter);
            addEndpointResolver(HttpEndpointResolver.builder()
                    .addEndpoint(HttpEndpoint.builder()
                            .setPrefix(prefix)
                            .setPath(path)
                            .build())
                    .setDispatcher((endpoint, req, resp) -> filter.handle(req, resp))
                    .build());
            return this;
        }

        public Builder singleEndpoint(String prefix, String path, Filter filter,
                                      String... methods) {
            Objects.requireNonNull(prefix);
            Objects.requireNonNull(path);
            Objects.requireNonNull(filter);
            addEndpointResolver(HttpEndpointResolver.builder()
                    .addEndpoint(HttpEndpoint.builder()
                            .setPrefix(prefix)
                            .setPath(path)
                            .setMethods(Arrays.asList(methods))
                            .build())
                    .setDispatcher((endpoint, req, resp) -> filter.handle(req, resp))
                    .build());
            return this;
        }

        public Domain build() {
            if (httpAddress.isSecure()) {
                try {
                    trustManagerFactory.init(trustManagerKeyStore);
                    SslContextBuilder sslContextBuilder = SslContextBuilder
                            .forServer(keyCertChainInputStream, keyInputStream, keyPassword)
                            .trustManager(trustManagerFactory)
                            .sslProvider(sslProvider)
                            .ciphers(ciphers, cipherSuiteFilter);
                    if (sslContextProvider != null) {
                        sslContextBuilder.sslContextProvider(sslContextProvider);
                    }
                    if (httpAddress.getVersion().majorVersion() == 2) {
                        sslContextBuilder.applicationProtocolConfig(newApplicationProtocolConfig());
                    }
                    return new Domain(serverName, aliases, httpAddress, httpEndpointResolvers, sslContextBuilder.build());
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            } else {
                return new Domain(serverName, aliases, httpAddress, httpEndpointResolvers, null);
            }
        }

        private static ApplicationProtocolConfig newApplicationProtocolConfig() {
            return new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1);
        }
    }
}
