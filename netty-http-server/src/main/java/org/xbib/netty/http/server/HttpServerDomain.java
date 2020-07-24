package org.xbib.netty.http.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.api.Domain;
import org.xbib.netty.http.server.api.EndpointResolver;
import org.xbib.netty.http.server.api.security.ServerCertificateProvider;
import org.xbib.netty.http.common.security.SecurityUtil;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.endpoint.HttpEndpoint;
import org.xbib.netty.http.server.endpoint.HttpEndpointResolver;
import org.xbib.netty.http.server.api.Filter;
import org.xbib.netty.http.server.security.CertificateUtils;
import org.xbib.netty.http.server.security.PrivateKeyUtils;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code HttpServerDomain} class represents a virtual server with a name.
 */
public class HttpServerDomain implements Domain<HttpEndpointResolver> {

    private static final Logger logger = Logger.getLogger(HttpServerDomain.class.getName());

    private static final String EMPTY = "";

    private final String name;

    private final HttpAddress httpAddress;

    private final SslContext sslContext;

    private final Collection<HttpEndpointResolver> httpEndpointResolvers;

    private final Collection<? extends X509Certificate> certificates;

    /**
     * Constructs a {@code NamedServer} with the given name.
     *
     * @param name the name, or null if it is the default server
     * @param httpAddress HTTP address, used for determining if named server is secure or not
     * @param httpEndpointResolvers the endpoint resolvers
     * @param sslContext SSL context or null
     */
    private HttpServerDomain(String name,
                             HttpAddress httpAddress,
                             Collection<HttpEndpointResolver> httpEndpointResolvers,
                             SslContext sslContext,
                             Collection<? extends X509Certificate> certificates) {
        this.name = name;
        this.httpAddress = httpAddress;
        this.httpEndpointResolvers = httpEndpointResolvers;
        this.sslContext = sslContext;
        this.certificates = certificates;
    }

    public static Builder builder(HttpAddress httpAddress) {
        return builder(httpAddress, httpAddress.getInetSocketAddress().getHostString());
    }

    public static Builder builder(HttpAddress httpAddress, String serverName) {
        return new Builder(httpAddress).setServerName(serverName);
    }

    public static Builder builder(Domain<? extends EndpointResolver<?>> domain) {
        return new Builder(domain);
    }

    /**
     * The address this domain binds to.
     *
     * @return the HTTP address
     */
    @Override
    public HttpAddress getHttpAddress() {
        return httpAddress;
    }

    /**
     * Returns the name.
     *
     * @return the name, or null if it is the default server
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<HttpEndpointResolver> getHttpEndpointResolvers() {
        return httpEndpointResolvers;
    }

    /**
     * Returns SSL context.
     * @return the SSL context
     */
    @Override
    public SslContext getSslContext() {
        return sslContext;
    }

    /**
     * Get certificate chain.
     * @return the certificate chain or null if not secure
     */
    @Override
    public Collection<? extends X509Certificate> getCertificateChain() {
        return certificates;
    }

    /**
     * Evaluate the context path of a given request.
     * The request is not dispatched.
     * URI request parameters are evaluated.
     * @param serverRequest the server request
     * @return the context path
     * @throws IOException if handling fails
     */
    @Override
    public String findContextPathOf(ServerRequest serverRequest) throws IOException {
        if (serverRequest == null) {
            return EMPTY;
        }
        Map.Entry<HttpEndpointResolver, List<HttpEndpoint>> resolved = resolve(serverRequest);
        if (resolved != null) {
            resolved.getKey().resolve(resolved.getValue(), serverRequest);
            return serverRequest.getContextPath();
        }
        return null;
    }

    /**
     * Handle server requests by resolving and handling a server request.
     * @param serverRequest the server request
     * @param serverResponse the server response
     * @throws IOException if handling server request fails
     */
    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        Map.Entry<HttpEndpointResolver, List<HttpEndpoint>> resolved = resolve(serverRequest);
        if (resolved != null) {
            resolved.getKey().handle(resolved.getValue(), serverRequest, serverResponse);
        } else {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_FOUND,
                    "text/plain", "No endpoint found to match request");
        }
    }

    @Override
    public String toString() {
        return name + " (" + httpAddress + ")";
    }

    /**
     * Just resolve a server request to a matching endpoint resolver with endpoints matched.
     * @param serverRequest the server request
     * @return the endpoint resolver together with the matching endpoints
     */
    private Map.Entry<HttpEndpointResolver, List<HttpEndpoint>> resolve(ServerRequest serverRequest) {
        for (HttpEndpointResolver httpEndpointResolver : httpEndpointResolvers) {
            List<HttpEndpoint> matchingEndpoints = httpEndpointResolver.matchingEndpointsFor(serverRequest);
            if (!matchingEndpoints.isEmpty()) {
                return Map.entry(httpEndpointResolver, matchingEndpoints);
            }
        }
        return null;
    }

    public static class Builder {

        private final HttpAddress httpAddress;

        private String serverName;

        private final Collection<HttpEndpointResolver> httpEndpointResolvers;

        private SslContext sslContext;

        private TrustManagerFactory trustManagerFactory;

        private KeyStore trustManagerKeyStore;

        private Provider sslContextProvider;

        private SslProvider sslProvider;

        private Iterable<String> ciphers;

        private CipherSuiteFilter cipherSuiteFilter;

        private Collection<? extends X509Certificate> keyCertChain;

        private PrivateKey privateKey;

        private Builder(HttpAddress httpAddress) {
            Objects.requireNonNull(httpAddress);
            this.httpAddress = httpAddress;
            this.httpEndpointResolvers = new ArrayList<>();
            this.trustManagerFactory = SecurityUtil.Defaults.DEFAULT_TRUST_MANAGER_FACTORY;
            this.sslProvider = SecurityUtil.Defaults.DEFAULT_SSL_PROVIDER;
            this.ciphers = SecurityUtil.Defaults.DEFAULT_CIPHERS;
            this.cipherSuiteFilter = SecurityUtil.Defaults.DEFAULT_CIPHER_SUITE_FILTER;
        }

        @SuppressWarnings("unchecked")
        private Builder(Domain<? extends EndpointResolver<?>> domain) {
            this.httpAddress = domain.getHttpAddress();
            this.httpEndpointResolvers = new ArrayList<>((List<HttpEndpointResolver>) domain.getHttpEndpointResolvers());
            this.sslContext = domain.getSslContext();
            this.keyCertChain = domain.getCertificateChain();
        }

        public Builder setServerName(String serverName) {
            if (this.serverName == null) {
                this.serverName = serverName;
            }
            return this;
        }

        public Builder setSslContext(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
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

        public Builder setKeyCertChain(InputStream keyCertChainInputStream)
                throws CertificateException {
            Objects.requireNonNull(keyCertChainInputStream);
            this.keyCertChain = CertificateUtils.toCertificate(keyCertChainInputStream);
            return this;
        }

        public Builder setKey(InputStream keyInputStream, String keyPassword)
                throws NoSuchPaddingException, NoSuchAlgorithmException, IOException,
                KeyException, InvalidAlgorithmParameterException, InvalidKeySpecException {
            Objects.requireNonNull(keyInputStream);
            this.privateKey = PrivateKeyUtils.toPrivateKey(keyInputStream, keyPassword);
            return this;
        }

        public Builder setSelfCert() throws CertificateException, NoSuchPaddingException,
                NoSuchAlgorithmException, IOException, KeyException, InvalidAlgorithmParameterException,
                InvalidKeySpecException {
            ServiceLoader<ServerCertificateProvider> serverCertificateProviders =
                    ServiceLoader.load(ServerCertificateProvider.class);
            for (ServerCertificateProvider serverCertificateProvider : serverCertificateProviders) {
                if ("org.xbib.netty.http.bouncycastle.BouncyCastleSelfSignedCertificateProvider".equals(serverCertificateProvider.getClass().getName())) {
                    serverCertificateProvider.prepare(serverName);
                    setKeyCertChain(serverCertificateProvider.getCertificateChain());
                    setKey(serverCertificateProvider.getPrivateKey(), serverCertificateProvider.getKeyPassword());
                    logger.log(Level.INFO, "self signed certificate installed");
                }
            }
            if (keyCertChain == null) {
                throw new CertificateException("unable to set self certificate");
            }
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
            this.httpEndpointResolvers.clear();
            this.httpEndpointResolvers.add(HttpEndpointResolver.builder()
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

        public HttpServerDomain build() {
            if (httpEndpointResolvers.isEmpty()) {
                throw new IllegalArgumentException("domain must have at least one endpoint resolver");
            }
            if (httpAddress.isSecure() ) {
                try {
                    if (sslContext == null && privateKey != null && keyCertChain != null) {
                        trustManagerFactory.init(trustManagerKeyStore);
                        SslContextBuilder sslContextBuilder = SslContextBuilder
                                .forServer(privateKey, keyCertChain)
                                .trustManager(trustManagerFactory)
                                .sslProvider(sslProvider)
                                .ciphers(ciphers, cipherSuiteFilter);
                        if (sslContextProvider != null) {
                            sslContextBuilder.sslContextProvider(sslContextProvider);
                        }
                        if (httpAddress.getVersion().majorVersion() == 2) {
                            sslContextBuilder.applicationProtocolConfig(newApplicationProtocolConfig());
                        }
                        this.sslContext = sslContextBuilder.build();
                    }
                    return new HttpServerDomain(serverName,
                            httpAddress, httpEndpointResolvers,
                            sslContext, keyCertChain);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                return new HttpServerDomain(serverName,
                        httpAddress, httpEndpointResolvers,
                        null, null);
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
