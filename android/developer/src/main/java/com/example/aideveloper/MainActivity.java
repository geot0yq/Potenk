package com.example.aideveloper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public final class MainActivity extends android.app.Activity {
    private EditText apiBaseUrl;
    private EditText apiKey;
    private EditText model;
    private EditText request;
    private TextView status;
    private TextView report;
    private SecurePreferences securePreferences;
    private final ApiClient api = new ApiClient();
    private SelfRepairCoordinator coordinator;
    private ErrorMonitor errorMonitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        securePreferences = new SecurePreferences(this);
        coordinator = new SelfRepairCoordinator();
        apiBaseUrl = findViewById(R.id.apiBaseUrl);
        apiKey = findViewById(R.id.apiKey);
        model = findViewById(R.id.model);
        request = findViewById(R.id.request);
        status = findViewById(R.id.status);
        report = findViewById(R.id.report);
        TextView bot = findViewById(R.id.floatingBot);
        loadSettings();

        findViewById(R.id.testApi).setOnClickListener(view -> testApi());
        findViewById(R.id.develop).setOnClickListener(view -> develop());
        bot.setOnClickListener(view -> showChat());
        bot.setOnTouchListener(new DragListener(bot));

        errorMonitor = new ErrorMonitor(new ErrorMonitor.Listener() {
            @Override
            public void onRepairableError(String message) {
                status.setText("خطأ فعلي: " + message + "\nالمراقب متوقف بعد تسجيل الحالة؛ اضغط تطوير لطلب إصلاح آمن.");
            }

            @Override
            public void onCleared() {
                status.setText("تم حل الخطأ؛ المراقب متوقف.");
            }
        });
    }

    private void loadSettings() {
        apiBaseUrl.setText(getPreferences(MODE_PRIVATE).getString("baseUrl", ApiClient.DEFAULT_BASE_URL));
        model.setText(getPreferences(MODE_PRIVATE).getString("model", "llama-3.1-8b-instant"));
        try {
            apiKey.setText(securePreferences.readApiKey());
        } catch (Exception error) {
            errorMonitorSafe(error);
        }
    }

    private void saveSettings() throws Exception {
        getPreferences(MODE_PRIVATE).edit()
                .putString("baseUrl", apiBaseUrl.getText().toString().trim())
                .putString("model", model.getText().toString().trim())
                .apply();
        securePreferences.saveApiKey(apiKey.getText().toString().trim());
    }

    private void testApi() {
        try {
            saveSettings();
        } catch (Exception error) {
            errorMonitorSafe(error);
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        status.setText("جارٍ اختبار الاتصال...");
        new Thread(() -> {
            try {
                String response = api.complete(
                        apiBaseUrl.getText().toString(),
                        apiKey.getText().toString(),
                        model.getText().toString(),
                        "أجب بالعربية بجملة واحدة فقط: API connection successful.",
                        "اختبار اتصال"
                );
                runOnUiThread(() -> {
                    status.setText("الاتصال ناجح: " + response);
                    errorMonitor.clearWhenResolved();
                });
            } catch (Exception error) {
                errorMonitorSafe(error);
                runOnUiThread(() -> status.setText("فشل الاتصال: " + error.getMessage()));
            }
        }).start();
    }

    private void develop() {
        String prompt = request.getText().toString().trim();
        if (prompt.isEmpty()) {
            request.setError("اكتب طلب التطوير أولًا");
            return;
        }
        try {
            saveSettings();
        } catch (Exception error) {
            errorMonitorSafe(error);
            return;
        }
        status.setText("بدء التحليل الآمن...");
        coordinator.develop(
                new DevelopmentRequest(prompt),
                api,
                apiBaseUrl.getText().toString(),
                apiKey.getText().toString(),
                model.getText().toString(),
                new SelfRepairCoordinator.Callback() {
                    @Override
                    public void onState(SelfRepairCoordinator.State state, String detail) {
                        status.setText(state.name() + "\n" + detail);
                    }

                    @Override
                    public void onComplete(String result) {
                        report.setVisibility(View.VISIBLE);
                        report.setText(result);
                        sendRequestToHost(prompt, result);
                    }
                }
        );
    }

    private void sendRequestToHost(String prompt, String reportText) {
        Intent intent = new Intent("com.example.aideveloper.host.DEVELOPMENT_REQUEST");
        intent.setPackage("com.example.aideveloper.host");
        try {
            intent.putExtra("requestJson", new JSONObject()
                    .put("protocolVersion", 1)
                    .put("developerPackage", getPackageName())
                    .put("repositoryUrl", "https://github.com/geot0yq/Potenk")
                    .put("prompt", prompt)
                    .put("analysis", reportText)
                    .put("createdAt", System.currentTimeMillis())
                    .toString());
        } catch (Exception error) {
            errorMonitorSafe(error);
            status.setText("تعذر تجهيز عقد طلب التطوير: " + error.getMessage());
            return;
        }
        sendBroadcast(intent);
        status.setText("تم إرسال طلب التطوير إلى Build Host. لا يتم تنفيذ كود مولّد داخل الهاتف.");
    }

    private void showChat() {
        View content = getLayoutInflater().inflate(R.layout.dialog_chat, null);
        TextView history = content.findViewById(R.id.chatHistory);
        EditText input = content.findViewById(R.id.chatInput);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مساعد الأنمي")
                .setView(content)
                .setNegativeButton("إغلاق", null)
                .setPositiveButton("إرسال", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            history.append("\nأنت: " + message + "\n");
            input.setText("");
            history.append("المساعد: جارٍ التفكير...\n");
            new Thread(() -> {
                try {
                    String answer = api.complete(
                            apiBaseUrl.getText().toString(),
                            apiKey.getText().toString(),
                            model.getText().toString(),
                            "أنت شخصية أنمي ودودة ومساعد تطوير آمن. لا تدّعي تنفيذ عمليات لم تنفذها.",
                            message
                    );
                    runOnUiThread(() -> history.append("المساعد: " + answer + "\n"));
                } catch (Exception error) {
                    errorMonitorSafe(error);
                    runOnUiThread(() -> history.append("المساعد: تعذر الاتصال: " + error.getMessage() + "\n"));
                }
            }).start();
        }));
        dialog.show();
    }

    private void errorMonitorSafe(Exception error) {
        if (errorMonitor != null) errorMonitor.recordActualError(error);
    }

    private static final class DragListener implements View.OnTouchListener {
        private final View view;
        private float downX;
        private float downY;
        private float originalX;
        private float originalY;

        DragListener(View view) {
            this.view = view;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    originalX = view.getX();
                    originalY = view.getY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    view.setX(originalX + event.getRawX() - downX);
                    view.setY(originalY + event.getRawY() - downY);
                    return true;
                default:
                    return false;
            }
        }
    }
}