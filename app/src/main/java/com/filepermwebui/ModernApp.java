package com.lcpatch;

import android.app.Application;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModernApp extends Application {
    public static volatile XposedService service;
    public static volatile boolean scopeKnown;
    public static volatile boolean scopeGranted;

    public static void refreshScope() {
        XposedService value = service;
        if (value == null) { scopeKnown = false; scopeGranted = false; return; }
        new Thread(() -> {
            try {
                boolean granted = value.getScope().contains(LogProvider.GAME);
                if (service == value) { scopeGranted = granted; scopeKnown = true; }
            } catch (Throwable ignored) { scopeKnown = false; }
        }, "LCPatch-scope").start();
    }

    @Override public void onCreate() {
        super.onCreate();
        new Thread(() -> {
            try { CrashDiagnostics.INSTANCE.capture(this); }
            catch (Exception error) { android.util.Log.e("LCPatch", "Background diagnostic collection failed", error); }
            new TranslationRepository(this).migrateRuntime();
        }, "LCPatch-diagnostics").start();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService value) { service = value; refreshScope(); }
            @Override public void onServiceDied(XposedService value) {
                if (service == value) { service = null; scopeKnown = false; scopeGranted = false; }
            }
        });
    }
}
