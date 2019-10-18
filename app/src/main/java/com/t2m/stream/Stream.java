package com.t2m.stream;

import com.t2m.pan.Task;

public abstract class Stream {
    protected String mName;

    public Stream(String name) {
        mName = name;
    }

    public String name() {
        return mName;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isAudio() {
        return this instanceof IAudioStream;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isVideo() {
        return this instanceof IVideoStream;
    }

    public boolean isGlVideo() {
        return this instanceof IGlVideoStream;
    }

    protected String subName(String subName) {
        return mName + "#" + subName;
    }

    public abstract boolean build(Task task);
}
