package com.example.aideveloper;

import org.junit.Assert;
import org.junit.Test;

public final class ErrorMonitorTest {
    @Test
    public void recordsOnlyTheFirstActiveErrorAndStopsAfterClear() {
        int[] repairable = {0};
        int[] cleared = {0};
        ErrorMonitor monitor = new ErrorMonitor(new ErrorMonitor.Listener() {
            @Override
            public void onRepairableError(String message) {
                repairable[0]++;
            }

            @Override
            public void onCleared() {
                cleared[0]++;
            }
        });

        monitor.recordActualError(new IllegalStateException("first"));
        monitor.recordActualError(new IllegalStateException("duplicate"));
        monitor.clearWhenResolved();
        monitor.clearWhenResolved();

        Assert.assertEquals(1, repairable[0]);
        Assert.assertEquals(1, cleared[0]);
    }
}