package com.t2m.stream;

import com.t2m.npd.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Stream {
    public static final int TYPE_PREVIEW = 0;
    public static final int TYPE_LOCAL_VIDEO = 1;
    public static final int TYPE_UPLOAD = 2;

    protected String mName;
    protected int mType;
    protected boolean mHasAudio;
    protected boolean mHasVideo;

    public Stream(String name, int type, boolean hasAudio, boolean hasVideo) {
        mName = name;
        mType = type;
        mHasAudio = hasAudio;
        mHasVideo = hasVideo;
    }

    public String name() {
        return mName;
    }

    public int type() {
        return mType;
    }

    public boolean hasAudio() {
        return mHasVideo;
    }

    public boolean hasVideo() {
        return mHasVideo;
    }

    protected String subName(String subName) {
        return mName + "#" + subName;
    }

    public abstract boolean build(Task task);

    public static Task build(String name, Stream... streams) {
        ArrayList<Stream> s = new ArrayList<>();
        Collections.addAll(s, streams);
        return build(name, s);
    }

    public static Task build(String name, List<Stream> streams) {
        Task task = new Task(name);
        for (Stream stream : streams) {
            if (!stream.build(task)) {
                return null;
            }
        }
        return task;
    }
}
