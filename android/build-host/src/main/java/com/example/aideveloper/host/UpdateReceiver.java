package com.example.aideveloper.host;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** NEW: receives requests from Developer and validates update bytes before installer handoff. */
public final class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        HostStore store = new HostStore(context);
        if ("com.example.aideveloper.host.DEVELOPMENT_REQUEST".equals(intent.getAction())) {
            String request = intent.getStringExtra("requestJson");
            if (request != null && !request.trim().isEmpty()) store.saveRequest(request);
            return;
        }
        if (!"com.example.aideveloper.host.RECEIVE_UPDATE".equals(intent.getAction())) return;
        Uri source = intent.getData();
        String expectedSha = intent.getStringExtra("expectedSha256");
        if (source == null || expectedSha == null) {
            MainActivity.notifyStatus(context, "رفض التحديث: بيانات المرشح ناقصة");
            return;
        }

        PendingResult pending = goAsync();
        new Thread(() -> {
            File candidate = null;
            try {
                File folder = new File(context.getCacheDir(), "updates");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد المرشح");
                candidate = new File(folder, "Developer.apk");
                try (InputStream input = context.getContentResolver().openInputStream(source);
                     FileOutputStream output = new FileOutputStream(candidate)) {
                    if (input == null) throw new IllegalStateException("لا يمكن قراءة URI المرشح");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }

                UpdateGate gate = new UpdateGate();
                UpdateGate.Result result = gate.validate(context, candidate, expectedSha);
                if (!result.accepted) {
                    if (candidate != null) candidate.delete();
                    MainActivity.notifyStatus(context, "رفض التحديث: " + result.reason);
                    return;
                }
                long oldVersion = currentVersion(context);
                store.saveCandidate(candidate, expectedSha, oldVersion);
                MainActivity.notifyStatus(context, result.reason + "؛ يبدأ مسار التثبيت الرسمي");

                if (Build.VERSION.SDK_INT >= 26
                        && !context.getPackageManager().canRequestPackageInstalls()) {
                    Intent settings = new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.getPackageName())
                    );
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(settings);
                    MainActivity.notifyStatus(context, "Android يطلب إذن مصادر التثبيت لهذا التطبيق؛ لم يتم تجاوز حماية النظام.");
                    return;
                }
                Uri installUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        candidate
                );
                Intent installer = new Intent(Intent.ACTION_VIEW);
                installer.setDataAndType(installUri, "application/vnd.android.package-archive");
                installer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(installer);
            } catch (Exception error) {
                if (candidate != null) candidate.delete();
                MainActivity.notifyStatus(context, "فشل التحديث قبل التثبيت: " + error.getMessage());
            } finally {
                pending.finish();
            }
        }).start();
    }

    private static long currentVersion(Context context) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(UpdateGate.DEVELOPER_PACKAGE, 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) {
            return -1;
        }
    }
}