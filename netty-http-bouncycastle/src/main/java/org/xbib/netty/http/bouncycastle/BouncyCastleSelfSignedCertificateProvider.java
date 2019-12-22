package org.xbib.netty.http.bouncycastle;

import org.xbib.netty.http.common.ServerCertificateProvider;
import java.io.InputStream;
import java.security.SecureRandom;

public class BouncyCastleSelfSignedCertificateProvider implements ServerCertificateProvider {

    private final SelfSignedCertificate selfSignedCertificate;

    public BouncyCastleSelfSignedCertificateProvider() {
        this.selfSignedCertificate = new SelfSignedCertificate();
    }

    @Override
    public void prepare(String fqdn) throws Exception {
        selfSignedCertificate.generate(fqdn,  new SecureRandom(), 2048);
    }

    @Override
    public InputStream getPrivateKey() {
        return selfSignedCertificate.privateKey();
    }

    @Override
    public InputStream getCertificateChain() {
        return selfSignedCertificate.certificate();
    }

    @Override
    public String getKeyPassword() {
        return null;
    }
}
