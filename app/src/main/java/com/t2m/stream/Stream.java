package com.t2m.stream;

import com.t2m.npd.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public abstract class Stream {
    protected String mName;

    public Stream(String name) {
        mName = name;
    }

    public String name() {
        return mName;
    }

    public boolean isAudio() {
        return this instanceof IAudioStream;
    }

    public boolean isVideo() {
        return this instanceof IVideoStream;
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

    public static int audioStreamCount(List<Stream> streams) {
        return (int) streams.stream().filter(Stream::isAudio).count();
    }

    public static int videoStreamCount(List<Stream> streams) {
        return (int) streams.stream().filter(Stream::isVideo).count();
    }

    public static int minFrameRate(List<Stream> streams) {
        return streams.stream().filter(Stream::isVideo) // only video stream
                .min(Comparator.comparingInt((s) -> ((IVideoStream)s).getFrameRate())) // get min frame rate set
                .map(stream -> ((IVideoStream) stream).getFrameRate()).orElse(0); // get min value
    }

    public static int maxFrameRate(List<Stream> streams) {
        return streams.stream().filter(Stream::isVideo) // only video stream
                .max(Comparator.comparingInt((s) -> ((IVideoStream)s).getFrameRate())) // get max frame rate set
                .map(stream -> ((IVideoStream) stream).getFrameRate()).orElse(0); // get max value
    }
}
