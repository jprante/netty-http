package org.xbib.netty.http.bouncycastle;

import org.bouncycastle.operator.OperatorCreationException;
import org.xbib.netty.http.server.api.ServerCertificateProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

public class BouncyCastleSelfSignedCertificateProvider implements ServerCertificateProvider {

    private final SelfSignedCertificate selfSignedCertificate;

    public BouncyCastleSelfSignedCertificateProvider() {
        this.selfSignedCertificate = new SelfSignedCertificate();
    }

    @Override
    public void prepare(String fqdn) {
        try {
            selfSignedCertificate.generate(fqdn,  new SecureRandom(), 2048);
        } catch (IOException | NoSuchProviderException | NoSuchAlgorithmException | OperatorCreationException e) {
            throw new UncheckedIOException(new IOException(e));
        }
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
