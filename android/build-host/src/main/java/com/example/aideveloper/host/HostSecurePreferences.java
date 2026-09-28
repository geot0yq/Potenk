package com.example.aideveloper.host;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keystore-backed storage for the optional GitHub Actions token. */
public final class HostSecurePreferences {
    private static final String STORE = "host_secure_settings";
    private static final String KEY_ALIAS = "ai_anime_github_token";
    private static final String CIPHERTEXT = "github_token_ciphertext";
    private static final String IV = "github_token_iv";

    private final SharedPreferences preferences;

    public HostSecurePreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public void saveGithubToken(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            preferences.edit().remove(CIPHERTEXT).remove(IV).apply();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
        preferences.edit()
                .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    public String readGithubToken() throws Exception {
        String ciphertext = preferences.getString(CIPHERTEXT, null);
        String iv = preferences.getString(IV, null);
        if (ciphertext == null || iv == null) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        );
        return new String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
        );
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}