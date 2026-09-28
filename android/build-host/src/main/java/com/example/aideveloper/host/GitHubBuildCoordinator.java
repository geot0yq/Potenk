package com.example.aideveloper.host;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Starts the repository's Android Actions workflow and hands its verified
 * Developer artifact to the normal update receiver. No source or generated
 * code is executed on the phone.
 */
public final class GitHubBuildCoordinator {
    private static final String API = "https://api.github.com";
    private static final String WORKFLOW = "android.yml";
    private static final String ARTIFACT = "ai-anime-validation-apks";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private GitHubBuildCoordinator() {
    }

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) {
            MainActivity.notifyStatus(app, "عملية بناء وتحديث أخرى قيد التنفيذ.");
            return;
        }
        new Thread(() -> {
            try {
                run(app);
            } catch (Exception error) {
                MainActivity.notifyStatus(app, "فشل البناء والتحديث: " + safeMessage(error));
            } finally {
                RUNNING.set(false);
            }
        }, "github-build-coordinator").start();
    }

    private static void run(Context context) throws Exception {
        HostStore store = new HostStore(context);
        String requestText = store.request();
        if (requestText.trim().isEmpty()) {
            throw new IllegalStateException("لا يوجد طلب تطوير مستلم");
        }

        String token = new HostSecurePreferences(context).readGithubToken().trim();
        if (token.isEmpty()) {
            throw new IllegalStateException(
                    "أدخل GitHub Actions Token في حقل الإعداد ثم اضغط بناء وتحديث"
            );
        }

        JSONObject request = new JSONObject(requestText);
        Repository repository = Repository.parse(request.optString(
                "repositoryUrl", "https://github.com/geot0yq/Potenk"
        ));
        String ref = request.optString("branch", "main").trim();
        if (ref.isEmpty()) ref = "main";

        long requestedAt = System.currentTimeMillis();
        MainActivity.notifyStatus(
                context,
                "تم استلام الطلب. بدأ بناء المشروع من GitHub دون تعديل معلوماته."
        );
        String endpoint = API + "/repos/" + repository.owner + "/" + repository.name;
        Response dispatch = call(
                "POST",
                endpoint + "/actions/workflows/" + WORKFLOW + "/dispatches",
                token,
                new JSONObject().put("ref", ref).toString()
        );
        if (dispatch.code != 204) {
            throw new IllegalStateException("تعذر تشغيل GitHub Actions (HTTP " + dispatch.code + ")");
        }

        long runId = waitForRun(endpoint, token, requestedAt, ref);
        MainActivity.notifyStatus(context, "اكتمل البناء بنجاح؛ جارٍ تنزيل APK والتحقق منه.");
        long artifactId = findArtifact(endpoint, token, runId);
        File archive = new File(context.getCacheDir(), "build-host-artifacts.zip");
        File apk = new File(new File(context.getCacheDir(), "updates"), "Developer.apk");
        downloadBinary(
                endpoint + "/actions/artifacts/" + artifactId + "/zip",
                token,
                archive
        );
        extractDeveloperApk(archive, apk);
        archive.delete();

        String sha = UpdateGate.sha256(apk);
        // The APK is already in Build Host's private cache. Do not send it
        // through RECEIVE_UPDATE: that receiver copies a content URI and
        // would otherwise copy the file onto itself.
        UpdateReceiver.submitCandidate(context, apk, sha);
        MainActivity.notifyStatus(
                context,
                "تم بناء Developer.apk والتحقق من SHA-256؛ يبدأ التحديث الرسمي الآن."
        );
    }

    private static long waitForRun(
            String endpoint,
            String token,
            long requestedAt,
            String ref
    ) throws Exception {
        for (int attempt = 0; attempt < 120; attempt++) {
            Response response = call(
                    "GET",
                    endpoint + "/actions/runs?event=workflow_dispatch&branch="
                            + Uri.encode(ref) + "&per_page=10",
                    token,
                    null
            );
            if (response.code != 200) {
                throw new IllegalStateException("تعذر قراءة حالة البناء (HTTP " + response.code + ")");
            }
            JSONArray runs = new JSONObject(response.body).optJSONArray("workflow_runs");
            if (runs != null) {
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject run = runs.optJSONObject(i);
                    if (run == null || parseDate(run.optString("created_at")) < requestedAt - 120_000L) {
                        continue;
                    }
                    String status = run.optString("status");
                    if (!"completed".equals(status)) {
                        break;
                    }
                    if (!"success".equals(run.optString("conclusion"))) {
                        throw new IllegalStateException(
                                "فشل GitHub Actions: " + run.optString("conclusion", "unknown")
                        );
                    }
                    return run.optLong("id", -1L);
                }
            }
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("تم إيقاف متابعة البناء");
            }
        }
        throw new IllegalStateException("انتهت مهلة انتظار GitHub Actions");
    }

    private static long findArtifact(String endpoint, String token, long runId) throws Exception {
        Response response = call(
                "GET",
                endpoint + "/actions/runs/" + runId + "/artifacts?per_page=20",
                token,
                null
        );
        if (response.code != 200) {
            throw new IllegalStateException("تعذر قراءة مخرجات البناء (HTTP " + response.code + ")");
        }
        JSONArray artifacts = new JSONObject(response.body).optJSONArray("artifacts");
        if (artifacts != null) {
            for (int i = 0; i < artifacts.length(); i++) {
                JSONObject artifact = artifacts.optJSONObject(i);
                if (artifact != null
                        && ARTIFACT.equals(artifact.optString("name"))
                        && !artifact.optBoolean("expired", true)) {
                    return artifact.optLong("id", -1L);
                }
            }
        }
        throw new IllegalStateException("لم يعثر على APK الناتج من البناء");
    }

    private static void downloadBinary(String url, String token, File destination) throws Exception {
        HttpURLConnection connection = open("GET", url, token);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("تعذر تنزيل مخرجات البناء (HTTP "
                        + connection.getResponseCode() + ")");
            }
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("تعذر إنشاء مجلد APK");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination)) {
                copy(input, output);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void extractDeveloperApk(File archive, File destination) throws Exception {
        boolean extracted = false;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.US);
                if (entry.isDirectory() || !name.endsWith("developer-release.apk")) continue;
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("تعذر إنشاء مجلد التحديث");
                }
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    copy(input, output);
                }
                extracted = true;
                break;
            }
        }
        if (!extracted || !destination.isFile() || destination.length() == 0) {
            throw new IllegalStateException("لم يوجد Developer.apk داخل مخرجات البناء");
        }
    }

    private static Response call(String method, String url, String token, String body) throws Exception {
        HttpURLConnection connection = open(method, url, token);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            String response = stream == null ? "" : read(stream);
            return new Response(code, response);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String method, String url, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        if ("POST".equals(method)) connection.setRequestProperty("Content-Type", "application/json");
        return connection;
    }

    private static String read(InputStream stream) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        copy(stream, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws Exception {
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private static long parseDate(String value) {
        ParsePosition position = new ParsePosition(0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = format.parse(value, position);
        return date == null ? 0L : date.getTime();
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private static final class Repository {
        final String owner;
        final String name;

        Repository(String owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        static Repository parse(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (value.endsWith(".git")) value = value.substring(0, value.length() - 4);
            String prefix = "https://github.com/";
            if (!value.startsWith(prefix)) {
                throw new IllegalArgumentException("رابط المستودع يجب أن يكون من GitHub");
            }
            String path = value.substring(prefix.length());
            String[] parts = path.split("/");
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()
                    || parts[0].contains("?") || parts[1].contains("?")) {
                throw new IllegalArgumentException("رابط مستودع GitHub غير صالح");
            }
            return new Repository(parts[0], parts[1]);
        }
    }
}