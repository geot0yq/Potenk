package com.example.aideveloper;

import android.os.Build;

import org.json.JSONObject;

import java.util.UUID;

/** NEW: stable request contract sent from Developer to Build Host. */
public final class DevelopmentRequest {
    public final String id;
    public final String prompt;
    public final long createdAt;
    public final int sdk;
    public final String packageName;
    public final String repositoryUrl;

    public DevelopmentRequest(String prompt) {
        this.id = UUID.randomUUID().toString();
        this.prompt = prompt;
        this.createdAt = System.currentTimeMillis();
        this.sdk = Build.VERSION.SDK_INT;
        this.packageName = "com.example.aideveloper";
        this.repositoryUrl = "https://github.com/geot0yq/Potenk";
    }

    public JSONObject toJson() {
        try {
            return new JSONObject()
                    .put("protocolVersion", 1)
                    .put("requestId", id)
                    .put("prompt", prompt)
                    .put("createdAt", createdAt)
                    .put("sdk", sdk)
                    .put("developerPackage", packageName)
                    .put("repositoryUrl", repositoryUrl);
        } catch (Exception error) {
            throw new IllegalStateException("تعذر إنشاء عقد التطوير", error);
        }
    }
}