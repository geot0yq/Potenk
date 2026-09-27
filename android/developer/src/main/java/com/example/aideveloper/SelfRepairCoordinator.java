package com.example.aideveloper;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** RECONSTRUCTED/NEW: error-triggered, bounded coordinator. It never runs generated code on-device. */
public final class SelfRepairCoordinator {
    public enum State { IDLE, ANALYZING, VALIDATING, HANDOFF, FAILED }

    public interface Callback {
        void onState(State state, String detail);
        void onComplete(String report);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile State state = State.IDLE;
    private int attempts;

    public State state() {
        return state;
    }

    public void develop(
            DevelopmentRequest request,
            ApiClient api,
            String baseUrl,
            String apiKey,
            String model,
            Callback callback
    ) {
        if (!running.compareAndSet(false, true)) {
            callback.onState(State.FAILED, "عملية تطوير أخرى قيد التنفيذ");
            return;
        }
        executor.execute(() -> {
            try {
                attempts++;
                publish(callback, State.ANALYZING, "تحليل الطلب والتحقق من النطاق...");
                String plan = api.complete(
                        baseUrl,
                        apiKey,
                        model,
                        "أنت محلل تغييرات آمن لمشروع Android. لا تكتب كودًا تنفيذيًا في الرد. أخرج خطة قصيرة تتضمن الملفات المتأثرة، المخاطر، الاختبارات، وهل يلزم موافقة بشرية.",
                        request.prompt
                );
                publish(callback, State.VALIDATING, "التحليل جاهز؛ لا يتم تنفيذ كود مولّد داخل الهاتف.");
                String report = "Request ID: " + request.id
                        + "\n\nتحليل المساعد:\n" + plan
                        + "\n\nالحد الآمن:\nسيُرسل الطلب كعقد تطوير إلى Build Host. "
                        + "التعديل والبناء الفعليان يتمان من مصدر GitHub عبر Gradle/Actions.";
                publish(callback, State.HANDOFF, "إرسال طلب التطوير إلى Build Host...");
                callback.onComplete(report);
                state = State.IDLE;
            } catch (Exception error) {
                state = State.FAILED;
                callback.onState(State.FAILED, "تعذر إكمال التحليل: " + error.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    public void stop() {
        executor.shutdownNow();
        running.set(false);
        state = State.IDLE;
    }

    private void publish(Callback callback, State next, String detail) {
        state = next;
        main.post(() -> callback.onState(next, detail));
    }
}