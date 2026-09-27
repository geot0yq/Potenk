package com.example.aideveloper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** RECONSTRUCTED: small OpenAI-compatible client matching the original API fields. */
public final class ApiClient {
    public static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

    public String complete(String baseUrl, String apiKey, String model, String system, String user)
            throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new IllegalArgumentException("API Base URL is empty");
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("API Key is empty");
        if (model == null || model.trim().isEmpty()) throw new IllegalArgumentException("Model is empty");

        String endpoint = baseUrl.trim().replaceAll("/+$", "") + "/chat/completions";
        JSONObject payload = new JSONObject()
                .put("model", model.trim())
                .put("temperature", 0.2)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", system))
                        .put(new JSONObject().put("role", "user").put("content", user)));

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(45_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        String response = read(connection, code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("API error " + code + ": " + compact(response));
        }
        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IllegalStateException("API response has no choices");
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String content = message == null ? "" : message.optString("content", "").trim();
        if (content.isEmpty()) throw new IllegalStateException("API response content is empty");
        return content;
    }

    private static String read(HttpURLConnection connection, InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}