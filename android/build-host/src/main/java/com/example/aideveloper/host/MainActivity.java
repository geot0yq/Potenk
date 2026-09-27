package com.example.aideveloper.host;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;

/** RECONSTRUCTED/NEW: Build Host status screen and post-install verification. */
public final class MainActivity extends Activity {
    private static volatile String lastStatus = "";
    private TextView status;
    private TextView request;
    private HostStore store;

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
        findViewById(R.id.install).setOnClickListener(view -> launchPending());
        findViewById(R.id.openDeveloper).setOnClickListener(view -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(UpdateGate.DEVELOPER_PACKAGE);
            if (launch != null) startActivity(launch);
        });
        render();
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
            lastStatus = "لا يوجد APK مرشح اجتاز التحقق.";
            render();
            return;
        }
        UpdateGate.Result result = new UpdateGate().validate(this, candidate, store.candidateSha());
        if (!result.accepted) {
            store.clearCandidate();
            lastStatus = "مُنع التثبيت: " + result.reason;
            render();
            return;
        }
        UpdateReceiver receiver = new UpdateReceiver();
        Intent intent = new Intent(this, MainActivity.class);
        // The receiver already starts the official installer when an update arrives.
        lastStatus = "المرشح صالح؛ أعد إرساله من Developer لبدء Android Package Installer.";
        render();
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