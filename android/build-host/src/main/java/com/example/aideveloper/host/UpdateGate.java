package com.example.aideveloper.host;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/** NEW: fail-closed gate for package identity, version, hash, parseability and signer. */
public final class UpdateGate {
    public static final String DEVELOPER_PACKAGE = "com.example.aideveloper";

    public Result validate(Context context, File apk, String expectedSha256) {
        try {
            if (apk == null || !apk.isFile() || apk.length() == 0) {
                return Result.fail("ملف APK غير موجود أو فارغ");
            }
            String actual = sha256(apk);
            if (expectedSha256 == null || !actual.equalsIgnoreCase(expectedSha256.trim())) {
                return Result.fail("فشل SHA-256: الملف تغيّر أو المصدر غير موثوق");
            }

            PackageManager pm = context.getPackageManager();
            PackageInfo candidate = archivePackageInfo(pm, apk.getAbsolutePath());
            if (candidate == null || candidate.applicationInfo == null) {
                return Result.fail("Android لا يستطيع قراءة APK");
            }
            if (!DEVELOPER_PACKAGE.equals(candidate.packageName)) {
                return Result.fail("هوية الحزمة غير صحيحة: " + candidate.packageName);
            }

            PackageInfo installed;
            try {
                installed = installedPackageInfo(pm, DEVELOPER_PACKAGE);
            } catch (PackageManager.NameNotFoundException notInstalled) {
                return Result.fail("تطبيق Developer غير مثبت؛ لا يسمح Build Host بالتثبيت الأول");
            }
            long candidateVersion = versionCode(candidate);
            long installedVersion = versionCode(installed);
            if (candidateVersion <= installedVersion) {
                return Result.fail("الإصدار ليس ترقية: " + candidateVersion + " <= " + installedVersion);
            }
            if (!sameSigner(candidate, installed)) {
                return Result.fail("توقيع APK لا يطابق توقيع Developer المثبت");
            }
            return Result.ok(candidateVersion);
        } catch (Exception error) {
            return Result.fail("فشل التحقق: " + error.getMessage());
        }
    }

    private static PackageInfo archivePackageInfo(PackageManager pm, String path) {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        return pm.getPackageArchiveInfo(path, flags);
    }

    private static PackageInfo installedPackageInfo(PackageManager pm, String packageName)
            throws PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        return pm.getPackageInfo(packageName, flags);
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static boolean sameSigner(PackageInfo first, PackageInfo second) {
        if (Build.VERSION.SDK_INT >= 28) {
            if (first.signingInfo == null || second.signingInfo == null) return false;
            return Arrays.equals(
                    first.signingInfo.getApkContentsSigners(),
                    second.signingInfo.getApkContentsSigners()
            );
        }
        Signature[] firstSigners = first.signatures;
        Signature[] secondSigners = second.signatures;
        return firstSigners != null && secondSigners != null && Arrays.equals(firstSigners, secondSigners);
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

    public static final class Result {
        public final boolean accepted;
        public final String reason;
        public final long versionCode;

        private Result(boolean accepted, String reason, long versionCode) {
            this.accepted = accepted;
            this.reason = reason;
            this.versionCode = versionCode;
        }

        public static Result ok(long versionCode) {
            return new Result(true, "تم اجتياز فحوصات الحزمة والتوقيع والإصدار", versionCode);
        }

        public static Result fail(String reason) {
            return new Result(false, reason, -1);
        }
    }
}