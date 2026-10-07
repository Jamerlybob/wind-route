package io.github.jamerlybob.windroute;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The two HTTP calls the app makes, on top of the JDK's own HttpURLConnection.
 *
 * <p>A library such as OkHttp or Retrofit would be the usual choice in a bigger
 * app. Two requests do not earn a dependency.
 *
 * <p>These block until the server answers, so they must never run on the main
 * thread. Android enforces that by throwing NetworkOnMainThreadException.
 */
public final class Http {

    private static final int TIMEOUT_MS = 15_000;

    private Http() {
    }

    public static String get(String url) throws IOException {
        HttpURLConnection connection = open(url);
        return read(connection);
    }

    public static String postJson(String url, Map<String, String> headers, String body)
            throws IOException {
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return read(connection);
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        return connection;
    }

    /**
     * Returns the response body whatever the status code. Both APIs explain
     * their errors in a JSON body, and the parsers turn that into a message
     * worth showing. A bare "HTTP 400" would tell the user nothing.
     */
    private static String read(HttpURLConnection connection) throws IOException {
        try {
            boolean ok = connection.getResponseCode() < 400;
            InputStream stream = ok ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream in = stream) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, count);
                }
                return buffer.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }
}
