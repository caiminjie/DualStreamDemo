// IStreamService.aidl
package com.t2m.dualstream;

// Declare any non-default types here with import statements
interface IStreamService {
    int getCameraNode();
    int getAudioNode();

    int getStatus(int channel);
    void startStreams(String name, int channel, int status, boolean stopPreviewTask, int streams);
    void startTask(int channel, int status, boolean stopPreviewTask, int task);
}
