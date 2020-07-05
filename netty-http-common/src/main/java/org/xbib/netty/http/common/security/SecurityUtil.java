package org.xbib.netty.http.common.security;

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.util.Arrays;
import java.util.List;

public class SecurityUtil {

    private static TrustManagerFactory TRUST_MANAGER_FACTORY;

    static {
        try {
            TRUST_MANAGER_FACTORY = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        } catch (Exception e) {
            TRUST_MANAGER_FACTORY = null;
        }
    }

    private SecurityUtil() {
    }

    public interface Defaults {

        List<String> OPENSSL_CIPHERS = Http2SecurityUtil.CIPHERS;

        List<String> JDK_CIPHERS =
                Arrays.asList(((SSLSocketFactory) SSLSocketFactory.getDefault()).getDefaultCipherSuites());


        TrustManagerFactory DEFAULT_TRUST_MANAGER_FACTORY = TRUST_MANAGER_FACTORY;
        /**
         * Default SSL provider.
         */
        SslProvider DEFAULT_SSL_PROVIDER = OpenSsl.isAvailable() ? SslProvider.OPENSSL : SslProvider.JDK;

        /**
         * Default ciphers.
         */
        Iterable<String> DEFAULT_CIPHERS = OpenSsl.isAvailable() ? OPENSSL_CIPHERS : JDK_CIPHERS;

        /**
         * Default cipher suite filter.
         */
        CipherSuiteFilter DEFAULT_CIPHER_SUITE_FILTER = SupportedCipherSuiteFilter.INSTANCE;

    }
}
