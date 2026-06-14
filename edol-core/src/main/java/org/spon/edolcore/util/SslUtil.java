package org.spon.edolcore.util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class SslUtil {

    private SslUtil() {
        /* This utility class should not be instantiated */
    }

    public static SSLSocketFactory createTrustAllSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // No op
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // No op
                    }
                }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());

        return ctx.getSocketFactory();
    }
}
