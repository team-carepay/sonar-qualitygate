package com.carepay.sonar.qualitygate.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provides access to REST endpoints that return JSON
 */
public class ResourceFetcher {

    private final URLOpener opener;

    public ResourceFetcher() {
        this(new URLOpener.Default());
    }

    public ResourceFetcher(URLOpener opener) {
        this.opener = opener;
    }

    public JsonObject query(final URL url, final Map<String, String> headers) throws IOException {
        final HttpURLConnection uc = opener.open(url);
        uc.setConnectTimeout(1000);
        uc.setReadTimeout(1000);
        if (headers != null) {
            headers.forEach(uc::setRequestProperty);
        }
        try (final InputStream is = uc.getInputStream();
             final InputStreamReader reader = is != null ? new InputStreamReader(is, UTF_8) : null) {
            return reader != null ? (JsonObject) Jsoner.deserialize(reader) : null;
        } catch (JsonException e) {
            throw new IllegalStateException(e);
        } finally {
            uc.disconnect();
        }
    }

    public JsonObject queryJson(final URL url) {
        return queryJson(url, null);
    }

    public JsonObject queryJson(final URL url, final Map<String, String> headers) {
        try {
            return query(url, headers);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public JsonObject queryJsonBasicAuth(final URL url, final String username, final String password) {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(UTF_8)));
        return queryJson(url, headers);
    }
}
