package com.example.aideveloper.host;

import org.junit.Assert;
import org.junit.Test;

public final class UpdateGateTest {
    @Test
    public void onlyDeveloperPackageIsAcceptedByContract() {
        Assert.assertEquals("com.example.aideveloper", UpdateGate.DEVELOPER_PACKAGE);
    }
}