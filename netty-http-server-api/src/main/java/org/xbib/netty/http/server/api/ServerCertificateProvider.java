package org.xbib.netty.http.server.api;

import java.io.InputStream;

public interface ServerCertificateProvider {

    /**
     * Prepare the server certificate, if appropriate.
     *
     * @param fqdn the full qualified domain name.
     */
    void prepare(String fqdn);

    /**
     * Returns the generated RSA private key file in PEM format.
     * @return input stream of private key
     */
    InputStream getPrivateKey();

    /**
     * Returns the generated X.509 certificate file in PEM format.
     * @return input stream of certificate
     */
    InputStream getCertificateChain();

    /**
     * A key password or null if key password is not required.
     * @return key password
     */
    String getKeyPassword();
}
