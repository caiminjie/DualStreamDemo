package com.t2m.stream.streams;

import com.t2m.pan.Task;
import com.t2m.stream.Stream;
import com.t2m.stream.IVideoStream;

public class VideoUploadStream extends Stream implements IVideoStream<VideoUploadStream> {
    private int mFrameRate = 30;

    public VideoUploadStream(String name) {
        super(name);
    }

    @Override
    public boolean build(Task task) {
        return false;
    }

    @Override
    public VideoUploadStream setFrameRate(int frameRate) {
        mFrameRate = frameRate;
        return this;
    }

    @Override
    public int getFrameRate() {
        return mFrameRate;
    }
}
