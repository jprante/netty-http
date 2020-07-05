package org.xbib.netty.http.bouncycastle;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.logging.Logger;

class SelfSignedCertificateTest {

    private static final Logger logger = Logger.getLogger("test");

    @Test
    void testSelfSignedCertificate() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        selfSignedCertificate.generate("localhost", new SecureRandom(), 2048);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        selfSignedCertificate.exportPEM(outputStream);
        logger.info(new String(outputStream.toByteArray(), StandardCharsets.US_ASCII));
    }
}
