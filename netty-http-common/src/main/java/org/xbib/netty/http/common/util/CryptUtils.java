package org.xbib.netty.http.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;
import java.util.Base64;

/**
 * A utility class for invoking encryption methods and returning password strings,
 * using {@link java.security.MessageDigest} and {@link javax.crypto.Mac}.
 */
public class CryptUtils {

    private static final Random random = new SecureRandom();

    public static String randomHex(int length) {
        byte[] b = new byte[length];
        random.nextBytes(b);
        return encodeHex(b);
    }

    public static String md5(String plainText) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), null, Algo.MD5.algo, Algo.MD5.prefix);
    }

    public static String sha(String plainText) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), null, Algo.SHA.algo, Algo.SHA.prefix);
    }

    public static String sha256(String plainText) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), null, Algo.SHA256.algo, Algo.SHA256.prefix);
    }

    public static String sha512(String plainText) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), null, Algo.SHA512.algo, Algo.SHA512.prefix);
    }

    public static String ssha(String plainText, byte[] salt) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), salt, Algo.SSHA.algo, Algo.SSHA.prefix);
    }

    public static String ssha256(String plainText, byte[] salt) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), salt, Algo.SSHA256.algo, Algo.SSHA256.prefix);
    }

    public static String ssha512(String plainText, byte[] salt) throws NoSuchAlgorithmException {
        return digest(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), salt, Algo.SSHA512.algo, Algo.SSHA512.prefix);
    }

    public static String hmacSHA1(String plainText, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        return hmac(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8), HMac.HMAC_SHA1);
    }

    public static String hmacSHA1(byte[] plainText, String secret) throws InvalidKeyException, NoSuchAlgorithmException {
        return hmac(Codec.BASE64, plainText, secret.getBytes(StandardCharsets.UTF_8), HMac.HMAC_SHA1);
    }

    public static String hmacSHA1(byte[] plainText, byte[] secret) throws InvalidKeyException, NoSuchAlgorithmException {
        return hmac(Codec.BASE64, plainText, secret, HMac.HMAC_SHA1);
    }

    public static String hmacSHA256(String plainText, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        return hmac(Codec.BASE64, plainText.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8), HMac.HMAC_SHA256);
    }

    public static String hmacSHA256(byte[] plainText, String secret) throws InvalidKeyException, NoSuchAlgorithmException {
        return hmac(Codec.BASE64, plainText, secret.getBytes(StandardCharsets.UTF_8), HMac.HMAC_SHA256);
    }

    public static String hmacSHA256(byte[] plainText, byte[] secret) throws InvalidKeyException, NoSuchAlgorithmException {
        return hmac(Codec.BASE64, plainText, secret, HMac.HMAC_SHA256);
    }

    public static String hmac(Codec codec, String plainText, String secret, HMac hmac) throws InvalidKeyException, NoSuchAlgorithmException {
        return hmac(codec, plainText.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8), hmac);
    }

    public static String digest(Codec codec, byte[] plainText, byte[] salt, String algo, String prefix) throws NoSuchAlgorithmException {
        Objects.requireNonNull(plainText);
        MessageDigest digest = MessageDigest.getInstance(algo);
        digest.update(plainText);
        byte[] bytes = digest.digest();
        if (salt != null) {
            digest.update(salt);
            byte[] hash = digest.digest();
            bytes = new byte[salt.length + hash.length];
            System.arraycopy(hash, 0, bytes, 0, hash.length);
            System.arraycopy(salt, 0, bytes, hash.length, salt.length);
        }
        return '{' + prefix + '}' +
                (codec == Codec.BASE64 ? Base64.getEncoder().encodeToString(bytes) :
                        codec == Codec.HEX ? encodeHex(bytes) : null);
    }

    public static String hmac(Codec codec, byte[] plainText, byte[] secret, HMac hmac) throws NoSuchAlgorithmException, InvalidKeyException {
        Objects.requireNonNull(plainText);
        Objects.requireNonNull(secret);
        Mac mac = Mac.getInstance(hmac.algo);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret, hmac.algo);
        mac.init(secretKeySpec);
        return codec == Codec.BASE64 ? Base64.getEncoder().encodeToString(mac.doFinal(plainText)) :
                codec == Codec.HEX ? encodeHex(mac.doFinal(plainText)) : null;
    }

    public static String encodeHex(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b: bytes) {
            stringBuilder.append(Integer.toHexString((int) b & 0xFF));
        }
        return stringBuilder.toString();
    }

    /**
     * Decodes the hex-encoded bytes and returns their value a byte string.
     * @param hex hexidecimal code
     * @return string
     */
    public static byte[] decodeHex(String hex) {
        Objects.requireNonNull(hex);
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("unexpected hex string " + hex);
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int d1 = decodeHexDigit(hex.charAt(i * 2)) << 4;
            int d2 = decodeHexDigit(hex.charAt(i * 2 + 1));
            result[i] = (byte) (d1 + d2);
        }
        return result;
    }

    private static int decodeHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("unexpected hex digit " + c);
    }
}
