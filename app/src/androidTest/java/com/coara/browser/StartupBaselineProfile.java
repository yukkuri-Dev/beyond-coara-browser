package com.coara.browser;

import androidx.benchmark.macro.BaselineProfileRule;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.junit4.BaselineProfileRuleKt;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class StartupBaselineProfile {

    @Rule
    public final BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void startup() throws IOException {
        baselineProfileRule.collect(
            "com.coara.browser",
            /* profileBlock = */ scope -> {
                try {
                    scope.startActivityAndWait();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Unit.INSTANCE;
            }
        );
    }
}
