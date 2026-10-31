package com.lcpatch.guard;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {
    private native int runChecks(String directory);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("verification_guard_test");
        int result = runChecks(getFilesDir().getAbsolutePath());
        Log.i("LCPatchGuardVerify", result == 0 ? "PASS: ARM64 real-unwind verification/text isolation, 13 checks" : "FAIL: " + result);
        finish();
    }
}
