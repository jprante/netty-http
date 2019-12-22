package org.xbib.netty.http.bouncycastle;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.util.encoders.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates a temporary self-signed certificate for testing purposes.
 */
public final class SelfSignedCertificate {

    /** Current time minus 1 year, just in case software clock goes back due to time synchronization */
    private static final Date DEFAULT_NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);

    /** The maximum possible value in X.509 specification: 9999-12-31 23:59:59 */
    private static final Date DEFAULT_NOT_AFTER = new Date(253402300799000L);

    private static final String BEGIN_KEY = "-----BEGIN PRIVATE KEY-----";

    private static final String END_KEY = "-----END PRIVATE KEY-----";

    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";

    private static final String END_CERT = "-----END CERTIFICATE-----";

    private byte[] keyBytes;

    private byte[] certBytes;

    private Certificate cert;

    private PrivateKey key;

    /**
     * Creates a new instance.
     *
     * @param fqdn a fully qualified domain name
     * @param random the {@link SecureRandom} to use
     * @param bits the number of bits of the generated private key
     * @throws NoSuchAlgorithmException if algorithm does not exist
     * @throws NoSuchProviderException if provider does not exist
     * @throws OperatorCreationException if provider does not exist
     * @throws IOException if generation fails
     */
    public void generate(String fqdn, SecureRandom random, int bits)
            throws IOException, NoSuchProviderException, NoSuchAlgorithmException, OperatorCreationException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(bits, random);
        KeyPair keypair = keyGen.generateKeyPair();
        this.key = keypair.getPrivate();
        X500Name name = new X500Name("CN=" + fqdn);
        SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(keypair.getPublic().getEncoded());
        X509v3CertificateBuilder certificateBuilder =
                new X509v3CertificateBuilder(name, BigInteger.valueOf(System.currentTimeMillis()),
                        DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER, name, subjectPublicKeyInfo);
        AlgorithmIdentifier sigAlgId =
                new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSAEncryption");
        AlgorithmIdentifier digestAlgId =
                new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        AsymmetricKeyParameter caPrivateKeyParameters = PrivateKeyFactory.createKey(key.getEncoded());
        ContentSigner contentSigner = new BcRSAContentSignerBuilder(sigAlgId, digestAlgId)
                .build(caPrivateKeyParameters);
        this.cert = certificateBuilder.build(contentSigner).toASN1Structure();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(BEGIN_KEY.getBytes(StandardCharsets.US_ASCII));
        outputStream.write('\n');
        writeEncoded(key.getEncoded(), outputStream);
        outputStream.write(END_KEY.getBytes(StandardCharsets.US_ASCII));
        outputStream.write('\n');
        this.keyBytes = outputStream.toByteArray();
        outputStream = new ByteArrayOutputStream();
        outputStream.write(BEGIN_CERT.getBytes(StandardCharsets.US_ASCII));
        outputStream.write('\n');
        writeEncoded(cert.getEncoded(), outputStream);
        outputStream.write(END_CERT.getBytes(StandardCharsets.US_ASCII));
        outputStream.write('\n');
        this.certBytes = outputStream.toByteArray();
    }

    /**
     * Returns the generated X.509 certificate file in PEM format.
     * @return input stream of certificate
     */
    public InputStream certificate() {
        return new ByteArrayInputStream(certBytes);
    }

    /**
     * Returns the generated RSA private key file in PEM format.
     * @return input stream of private key
     */
    public InputStream privateKey() {
        return new ByteArrayInputStream(keyBytes);
    }

    /**
     * Returns the generated RSA private key.
     * @return private key
     */
    public PrivateKey key() {
        return key;
    }

    public void exportPEM(OutputStream outputStream) throws IOException {
        outputStream.write(keyBytes);
        outputStream.write(certBytes);
    }

    public void exportPEM(Logger logger) {
        logger.log(Level.INFO, new String(keyBytes, StandardCharsets.US_ASCII) +
                new String(certBytes, StandardCharsets.US_ASCII));
    }

    private void writeEncoded(byte[] bytes, OutputStream outputStream) throws IOException {
        byte[] buf = new byte[64];
        byte[] base64 = Base64.encode(bytes);
        for (int i = 0; i < base64.length; i += buf.length) {
            int index = 0;
            while (index != buf.length) {
                if ((i + index) >= base64.length) {
                    break;
                }
                buf[index] = base64[i + index];
                index++;
            }
            outputStream.write(buf, 0, index);
            outputStream.write('\n');
        }
    }
}
