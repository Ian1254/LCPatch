package com.lcpatch;
import android.os.ParcelFileDescriptor;
import com.lcpatch.INativeProgressCallback;

interface INativeStorageService {
    String apply(in ParcelFileDescriptor archive, String language, INativeProgressCallback callback) = 1;
    void destroy() = 16777114;
}
