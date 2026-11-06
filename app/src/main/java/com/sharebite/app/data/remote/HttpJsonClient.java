package com.sharebite.app.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal blocking HTTP GET + Gson client built on {@link HttpURLConnection}.
 * Must only be used from background threads (see {@code BackgroundTaskRunner}).
 */
public class HttpJsonClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_CHARS = 512 * 1024;

    private final Gson gson;
    private final String userAgent;

    public HttpJsonClient(@NonNull Gson gson, @NonNull String userAgent) {
        this.gson = gson;
        this.userAgent = userAgent;
    }

    /** Performs a GET request and parses the JSON body as {@code type}. */
    @WorkerThread
    @NonNull
    public <T> T get(@NonNull String url, @NonNull Type type) throws ApiException {
        String body = getString(url);
        try {
            T parsed = gson.fromJson(body, type);
            if (parsed == null) {
                throw new ApiException(ApiException.Kind.INVALID_RESPONSE, "Empty response from server.");
            }
            return parsed;
        } catch (JsonParseException e) {
            throw new ApiException(ApiException.Kind.INVALID_RESPONSE, "Unexpected response format.", -1, e);
        }
    }

    @WorkerThread
    @NonNull
    String getString(@NonNull String url) throws ApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", userAgent);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new ApiException(ApiException.Kind.HTTP, "Server responded with HTTP " + status + ".", status, null);
            }
            return readBody(connection.getInputStream());
        } catch (ApiException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new ApiException(ApiException.Kind.TIMEOUT, "The request timed out.", -1, e);
        } catch (IOException e) {
            throw new ApiException(ApiException.Kind.NETWORK, "Could not reach the server. Check your connection.", -1, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
                if (sb.length() > MAX_BODY_CHARS) {
                    throw new IOException("Response too large");
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Request cancelled");
                }
            }
            return sb.toString();
        }
    }
}
