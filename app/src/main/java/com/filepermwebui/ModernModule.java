package com.lcpatch;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.io.File;

public final class ModernModule extends XposedModule {
    private static final String GAME = "com.ProjectMoon.LimbusCompany";
    private boolean initialized;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, "LCPatch", "LSPosed API " + getApiVersion() + "; open-source translation core");
    }

    @Override public void onPackageLoaded(PackageLoadedParam param) {
        loadOnce(param.getPackageName(), param.isFirstPackage());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        loadOnce(param.getPackageName(), param.isFirstPackage());
    }

    private synchronized void loadOnce(String packageName, boolean firstPackage) {
        if (!GAME.equals(packageName) || !firstPackage || initialized) return;
        try {
            String directory = getModuleApplicationInfo().nativeLibraryDir;
            System.load(new File(directory, "liblcpatch_core.so").getAbsolutePath());
            initialized = true;
            log(Log.INFO, "LCPatch", "Open-source translation core loaded");
        } catch (Throwable error) {
            log(Log.ERROR, "LCPatch", "Font core startup failed", error);
        }
    }

}
