// IStreamService.aidl
package com.t2m.dualstream;

// Declare any non-default types here with import statements
interface IStreamService {
    int getCameraNode();

    int getStatus(int channel);
    void startTask(int channel, int status, int task, boolean stopPreviewTask);

    void startPreview(int previewSurface);
    void startVideoRecording(int previewSurface, int videoSize1, int videoSize2);
}
