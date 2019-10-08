package com.t2m.stream.streams;

import com.t2m.pan.Task;
import com.t2m.stream.IAudioStream;
import com.t2m.stream.Stream;

public class AudioUploadStream extends Stream implements IAudioStream<AudioUploadStream> {
    public AudioUploadStream(String name) {
        super(name);
    }

    @Override
    public boolean build(Task task) {
        return false;
    }
}
