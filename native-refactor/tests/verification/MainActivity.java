package com.lcpatch.verification;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {
    private native String runChecks();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getFilesDir().mkdirs();
        System.loadLibrary("il2cpp");
        System.loadLibrary("lcpatch_core");
        new Thread(() -> {
            try { Log.i("LCPatchVerify", runChecks()); }
            catch (Throwable error) { Log.e("LCPatchVerify", "FAIL", error); }
            runOnUiThread(this::finish);
        }).start();
    }
}
