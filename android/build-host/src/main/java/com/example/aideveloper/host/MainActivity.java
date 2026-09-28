package com.example.aideveloper.host;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;

/** RECONSTRUCTED/NEW: Build Host status screen and post-install verification. */
public final class MainActivity extends Activity {
    private static volatile String lastStatus = "";
    private TextView status;
    private TextView request;
    private HostStore store;
    private EditText githubToken;

    public static void notifyStatus(Context context, String message) {
        lastStatus = message;
        Intent intent = new Intent(context, MainActivity.class)
                .setAction("com.example.aideveloper.host.STATUS")
                .putExtra("status", message);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new HostStore(this);
        status = findViewById(R.id.status);
        request = findViewById(R.id.request);
        githubToken = findViewById(R.id.githubToken);
        findViewById(R.id.install).setOnClickListener(view -> launchPending());
        findViewById(R.id.buildAndUpdate).setOnClickListener(view -> startBuild());
        findViewById(R.id.openDeveloper).setOnClickListener(view -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(UpdateGate.DEVELOPER_PACKAGE);
            if (launch != null) startActivity(launch);
        });
        loadToken();
        render();
    }

    private void loadToken() {
        try {
            githubToken.setText(new HostSecurePreferences(this).readGithubToken());
        } catch (Exception error) {
            lastStatus = "تعذر قراءة رمز GitHub المحفوظ بأمان.";
        }
    }

    private void startBuild() {
        try {
            new HostSecurePreferences(this).saveGithubToken(
                    githubToken.getText().toString().trim()
            );
            GitHubBuildCoordinator.start(this);
        } catch (Exception error) {
            lastStatus = "تعذر حفظ إعداد البناء: " + error.getMessage();
            render();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && "com.example.aideveloper.host.STATUS".equals(intent.getAction())) {
            lastStatus = intent.getStringExtra("status");
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        verifyAfterInstall();
    }

    private void render() {
        if (status == null) return;
        String text = lastStatus.isEmpty() ? "جاهز لاستقبال طلبات التطبيق الأصلي" : lastStatus;
        status.setText(text);
        String saved = store == null ? "" : store.request();
        if (saved.isEmpty()) {
            request.setVisibility(View.GONE);
        } else {
            request.setVisibility(View.VISIBLE);
            try {
                JSONObject json = new JSONObject(saved);
                request.setText("طلب تطوير مستلم:\n" + json.optString("prompt")
                        + "\n\nالمستودع: " + json.optString("repositoryUrl"));
            } catch (Exception error) {
                request.setText(saved);
            }
        }
    }

    private void launchPending() {
        File candidate = store.candidate();
        if (candidate == null || !candidate.isFile()) {
            lastStatus = store.request().isEmpty()
                    ? "لا يوجد APK مرشح اجتاز التحقق."
                    : "تم استلام الطلب. اضغط «بناء وتحديث» بعد حفظ رمز GitHub.";
            render();
            return;
        }
        try {
            UpdateReceiver.submitCandidate(this, candidate, store.candidateSha());
        } catch (Exception error) {
            store.clearCandidate();
            lastStatus = "فشل بدء التثبيت: " + error.getMessage();
            render();
        }
    }

    private void verifyAfterInstall() {
        File candidate = store.candidate();
        if (candidate == null) return;
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(UpdateGate.DEVELOPER_PACKAGE, 0);
            long installed = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            if (installed > store.previousVersion()) {
                lastStatus = "نجح التثبيت والتحقق من الإصدار " + installed;
                sendResultToDeveloper(true, installed, lastStatus);
                store.clearCandidate();
            }
        } catch (Exception ignored) {
            // Keep the candidate until the official installer result is observable.
        }
        render();
    }

    private void sendResultToDeveloper(boolean success, long version, String message) {
        Intent result = new Intent("com.example.aideveloper.UPDATE_RESULT");
        result.setPackage(UpdateGate.DEVELOPER_PACKAGE);
        result.putExtra("success", success);
        result.putExtra("versionCode", version);
        result.putExtra("message", message);
        sendBroadcast(result);
    }
}