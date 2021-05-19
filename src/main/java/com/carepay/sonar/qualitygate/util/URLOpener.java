package com.carepay.sonar.qualitygate.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This interface allows us to open HTTPS connections during unit-tests. The default implementation
 * simply opens a real HTTPS connection
 */
@FunctionalInterface
public interface URLOpener {
    HttpURLConnection open(URL url) throws IOException;
    static URL create(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    class Default implements URLOpener {
        @Override
        public HttpURLConnection open(final URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }
    }
}
