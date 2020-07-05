package org.xbib.netty.http.server.security;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

public class CertificateUtils {

    @SuppressWarnings("unchecked")
    public static Collection<? extends X509Certificate> toCertificate(InputStream keyCertChainInputStream)
            throws CertificateException {
        return (Collection<? extends X509Certificate>) CertificateFactory.getInstance("X509")
                .generateCertificates(keyCertChainInputStream);
    }

    public static void processSubjectAlternativeNames(Collection<? extends X509Certificate> certificates,
                                                      SubjectAlternativeNamesProcessor processor) throws CertificateParsingException {
        if (certificates == null) {
            return;
        }
        for (X509Certificate certificate : certificates) {
            processor.setServerName(new DistinguishedNameParser(certificate.getSubjectX500Principal())
                    .findMostSpecific("CN"));
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            if (altNames != null) {
                for (List<?> altName : altNames) {
                    Integer type = (Integer) altName.get(0);
                    if (type == 2) { // Type DNS
                        String string = altName.get(1).toString();
                        processor.setSubjectAlternativeName(string);
                    }
                }
            }
        }
    }

    public interface SubjectAlternativeNamesProcessor {

        void setServerName(String serverName);

        void setSubjectAlternativeName(String subjectAlternativeName);
    }
}
