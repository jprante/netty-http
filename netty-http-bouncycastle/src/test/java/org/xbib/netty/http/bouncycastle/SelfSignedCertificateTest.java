package org.xbib.netty.http.bouncycastle;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.security.Security;
import java.util.logging.Logger;

class SelfSignedCertificateTest {

    @Test
    void testSelfSignedCertificate() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        selfSignedCertificate.generate("localhost", new SecureRandom(), 2048);
        selfSignedCertificate.exportPEM(Logger.getLogger("test"));
    }
}
