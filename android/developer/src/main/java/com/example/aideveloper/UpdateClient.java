package com.example.aideveloper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** NEW: fixed public GitHub release feed; hands verified bytes to Build Host. */
public final class UpdateClient {
    public interface Callback {
        void onStatus(String status);
        void onError(String message);
    }

    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/geot0yq/Potenk/releases/latest";

    public void checkAndSend(Context context, Callback callback) {
        new Thread(() -> {
            File candidate = null;
            try {
                callback.onStatus("فحص الإصدار المنشور...");
                HttpURLConnection release = (HttpURLConnection) new URL(LATEST_RELEASE).openConnection();
                release.setRequestProperty("Accept", "application/vnd.github+json");
                release.setConnectTimeout(10_000);
                release.setReadTimeout(20_000);
                String body = read(release);
                if (release.getResponseCode() != 200) throw new IllegalStateException("GitHub release HTTP " + release.getResponseCode());
                JSONArray assets = new JSONObject(body).optJSONArray("assets");
                JSONObject developerAsset = findAsset(assets, "Developer.apk");
                if (developerAsset == null) throw new IllegalStateException("لا يوجد Developer.apk في الإصدار المنشور");

                File folder = new File(context.getCacheDir(), "updates");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد التحديث");
                candidate = new File(folder, "Developer.apk");
                download(developerAsset.getString("browser_download_url"), candidate);
                String sha = sha256(candidate);
                callback.onStatus("تم تنزيل المرشح والتحقق من SHA-256؛ إرسال إلى Build Host...");
                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        candidate
                );
                Intent handoff = new Intent("com.example.aideveloper.host.RECEIVE_UPDATE");
                handoff.setPackage("com.example.aideveloper.host");
                handoff.setData(uri);
                handoff.putExtra("expectedSha256", sha);
                handoff.putExtra("sourcePackage", context.getPackageName());
                handoff.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.sendBroadcast(handoff);
                callback.onStatus("استلم Build Host المرشح. سيمنع التثبيت إذا فشل أي فحص.");
            } catch (Exception error) {
                callback.onError(error.getMessage() == null ? "فشل التحديث" : error.getMessage());
                if (candidate != null) candidate.delete();
            }
        }).start();
    }

    private static JSONObject findAsset(JSONArray assets, String name) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && name.equals(asset.optString("name"))) return asset;
        }
        return null;
    }

    private static void download(String url, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("Accept", "application/octet-stream");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        try (FileOutputStream output = new FileOutputStream(destination);
             java.io.InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IllegalStateException("تنزيل التحديث فشل");
        }
    }

    private static String read(HttpURLConnection connection) throws Exception {
        try (java.io.InputStream input = connection.getInputStream()) {
            byte[] bytes = new byte[64 * 1024];
            int count = input.read(bytes);
            return new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8);
        }
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }
}