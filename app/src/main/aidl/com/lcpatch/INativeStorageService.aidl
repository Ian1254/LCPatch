package com.lcpatch;
import android.os.ParcelFileDescriptor;
import com.lcpatch.INativeProgressCallback;

interface INativeStorageService {
    String apply(in ParcelFileDescriptor archive, String language, INativeProgressCallback callback) = 1;
    String restore() = 2;
    boolean hasBackup() = 3;
    void destroy() = 16777114;
}
