package com.lcpatch;
import android.os.ParcelFileDescriptor;

interface INativeStorageService {
    String apply(in ParcelFileDescriptor archive, String language) = 1;
    String restore() = 2;
    boolean hasBackup() = 3;
    void destroy() = 16777114;
}
