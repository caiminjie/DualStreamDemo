// IDualStreamService.aidl
package com.t2m.dualstream;

// Declare any non-default types here with import statements
import com.t2m.dualstream.ICameraOpenedCallback;

interface IDualStreamService {
    void openCamera(String id, ICameraOpenedCallback callback);
    void closeCamera();
    int getCameraOrientation();
    int getCameraIdList();
    int getAvailableSurfaceSize();
    int getAvailableCodecSize();
    void setCameraStreamCount(int count);
    int getAvailableFps();
    void setFps(int key);
    void setPreviewSurface(int key);
    int getStatus();

    void startPreview();
    void startVideoRecording();

    void setPreviewSize(int key);
    void setVideoSize(int index, int key);
}
