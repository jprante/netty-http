package org.xbib.netty.http.server.endpoint;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.SecurityUtil;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.service.Service;
import org.xbib.netty.http.server.security.tls.SelfSignedCertificate;

import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@code NamedServer} class represents a virtual server, with or without SSL.
 */
public class NamedServer {

    private final HttpAddress httpAddress;

    private final String name;

    private final SslContext sslContext;

    private final Set<String> aliases;

    private final List<EndpointResolver> endpointResolvers;

    protected NamedServer(HttpAddress httpAddress, String name, Set<String> aliases,
                          List<EndpointResolver> endpointResolvers) {
        this(httpAddress, name, aliases, endpointResolvers, null);
    }

    /**
     * Constructs a {@code NamedServer} with the given name.
     *
     * @param httpAddress HTTP address, used for determining if named server is secure or not
     * @param name the name, or null if it is the default server
     * @param aliases alias names for the named server
     * @param endpointResolvers the endpoint resolvers
     * @param sslContext SSL context or null
     */
    protected NamedServer(HttpAddress httpAddress, String name, Set<String> aliases,
                          List<EndpointResolver> endpointResolvers,
                          SslContext sslContext) {
        this.httpAddress = httpAddress;
        this.name = name;
        this.sslContext = sslContext;
        this.aliases = aliases;
        this.endpointResolvers = endpointResolvers;
    }

    public static Builder builder() {
        return new Builder(HttpAddress.http1("localhost", 8008), "*");
    }

    public static Builder builder(HttpAddress httpAddress) {
        return new Builder(httpAddress, "*");
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
        return Collections.unmodifiableSet(aliases);
    }

    public void execute(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        if (endpointResolvers != null && !endpointResolvers.isEmpty()) {
            for (EndpointResolver endpointResolver : endpointResolvers) {
                endpointResolver.resolve(serverRequest, serverResponse);
            }
        } else {
            serverResponse.writeError(HttpResponseStatus.NOT_IMPLEMENTED);
        }
    }

    public static class Builder {

        private HttpAddress httpAddress;

        private String serverName;

        private Set<String> aliases;

        private List<EndpointResolver> endpointResolvers;

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
            this.httpAddress = httpAddress;
            this.serverName = serverName;
            this.aliases = new LinkedHashSet<>();
            this.endpointResolvers = new ArrayList<>();
            this.trustManagerFactory = SecurityUtil.Defaults.DEFAULT_TRUST_MANAGER_FACTORY; // InsecureTrustManagerFactory.INSTANCE;
            this.sslProvider = SecurityUtil.Defaults.DEFAULT_SSL_PROVIDER;
            this.ciphers = SecurityUtil.Defaults.DEFAULT_CIPHERS;
            this.cipherSuiteFilter = SecurityUtil.Defaults.DEFAULT_CIPHER_SUITE_FILTER;
        }

        public Builder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
            this.trustManagerFactory = trustManagerFactory;
            return this;
        }

        public Builder setTrustManagerKeyStore(KeyStore trustManagerKeyStore) {
            this.trustManagerKeyStore = trustManagerKeyStore;
            return this;
        }

        public Builder setSslContextProvider(Provider sslContextProvider) {
            this.sslContextProvider = sslContextProvider;
            return this;
        }

        public Builder setSslProvider(SslProvider sslProvider) {
            this.sslProvider = sslProvider;
            return this;
        }

        public Builder setCiphers(Iterable<String> ciphers) {
            this.ciphers = ciphers;
            return this;
        }

        public Builder setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
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
            this.keyCertChainInputStream = keyCertChainInputStream;
            return this;
        }

        public Builder setKeyInputStream(InputStream keyInputStream) {
            this.keyInputStream = keyInputStream;
            return this;
        }

        public Builder setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
            return this;
        }

        public Builder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
            setKeyCertChainInputStream(keyCertChainInputStream);
            setKeyInputStream(keyInputStream);
            return this;
        }

        public Builder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                  String keyPassword) {
            setKeyCertChainInputStream(keyCertChainInputStream);
            setKeyInputStream(keyInputStream);
            setKeyPassword(keyPassword);
            return this;
        }

        public Builder setSelfCert() throws Exception {
            SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate(serverName);
            setKeyCertChainInputStream(selfSignedCertificate.certificate());
            setKeyInputStream(selfSignedCertificate.privateKey());
            setKeyPassword(null);
            return this;
        }

        /**
         * Adds an alias for this virtual server.
         *
         * @param alias the alias
         * @return this builder
         */
        public Builder addAlias(String alias) {
            aliases.add(alias);
            return this;
        }

        public Builder addEndpointResolver(EndpointResolver endpointResolver) {
            this.endpointResolvers.add(endpointResolver);
            return this;
        }

        public Builder singleEndpoint(String path, Service service) {
            addEndpointResolver(EndpointResolver.builder()
                    .addEndpoint(Endpoint.builder().setPath(path).addFilter(service).build()).build());
            return this;
        }

        public Builder singleEndpoint(String prefix, String path, Service service) {
            addEndpointResolver(EndpointResolver.builder()
                    .addEndpoint(Endpoint.builder().setPrefix(prefix).setPath(path).addFilter(service).build()).build());
            return this;
        }

        public NamedServer build() {
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
                    return new NamedServer(httpAddress, serverName, aliases, endpointResolvers, sslContextBuilder.build());
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            } else {
                return new NamedServer(httpAddress, serverName, aliases, endpointResolvers);
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
