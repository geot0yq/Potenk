package com.example.aideveloper.host;

import android.content.Context;

import java.io.File;

/** NEW: stores only current request state and current install candidate metadata. */
public final class HostStore {
    private static final String PREFS = "host_state";
    private static final String REQUEST = "development_request";
    private static final String CANDIDATE = "candidate_path";
    private static final String EXPECTED_SHA = "candidate_sha";
    private static final String PREVIOUS_VERSION = "previous_version";

    private final Context context;

    public HostStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public void saveRequest(String request) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(REQUEST, request)
                .apply();
    }

    public String request() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(REQUEST, "");
    }

    public void saveCandidate(File file, String sha, long previousVersion) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CANDIDATE, file.getAbsolutePath())
                .putString(EXPECTED_SHA, sha)
                .putLong(PREVIOUS_VERSION, previousVersion)
                .apply();
    }

    public File candidate() {
        String path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CANDIDATE, "");
        return path.isEmpty() ? null : new File(path);
    }

    public String candidateSha() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EXPECTED_SHA, "");
    }

    public long previousVersion() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(PREVIOUS_VERSION, -1);
    }

    public void clearCandidate() {
        File file = candidate();
        if (file != null) file.delete();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(CANDIDATE)
                .remove(EXPECTED_SHA)
                .remove(PREVIOUS_VERSION)
                .apply();
    }
}