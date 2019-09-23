package com.t2m.stream.streams;

import com.t2m.npd.Task;
import com.t2m.npd.node.AudioNode;
import com.t2m.stream.Stream;

public class AudioRecordStream extends Stream {
    private AudioNode mAudioNode;

    public AudioRecordStream(String name, AudioNode audioNode) {
        super(name);

        mAudioNode = audioNode;
    }

    @Override
    public boolean build(Task task) {
        return false;
    }
}
