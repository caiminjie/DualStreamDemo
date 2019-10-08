package com.t2m.stream.streams;

import android.util.Log;

import com.t2m.pan.Task;
import com.t2m.pan.node.tail.AudioNode;
import com.t2m.pan.node.head.WavHeadNode;
import com.t2m.stream.Stream;

public class AudioRecordStream extends Stream {
    private static final String TAG = AudioRecordStream.class.getSimpleName();

    private AudioNode mAudioNode;

    private WavHeadNode mWavNode;

    private String mPath = null;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;

    public AudioRecordStream(String name, AudioNode audioNode) {
        super(name);

        mAudioNode = audioNode;
    }

    @Override
    public boolean build(Task task) {
        // check
        if (mPath == null) {
            Log.e(TAG, "Cannot build stream due to path not set. see: setPath().");
            return false;
        }

        // create node
        mWavNode = new WavHeadNode(
                "WavHeadWriter", mPath,
                mSampleRate, mChannelCount, mAudioFormat);

        // config audio pipeline
        task.addPipeline("Audio")
                .addNode(mAudioNode)
                .addNode(mWavNode);

        return true;
    }

    public AudioRecordStream setPath(String path) {
        mPath = path;
        return this;
    }

    public AudioRecordStream setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;
        return this;
    }

    public AudioRecordStream setChannelCount(int channelCount) {
        mChannelCount = channelCount;
        return this;
    }

    public AudioRecordStream setAudioFormat(int audioFormat) {
        mAudioFormat = audioFormat;
        return this;
    }

    public void enableSave(boolean enable, String path) {
        mWavNode.enableSave(enable, path);
    }

    public void newSegment(String path) {
        mWavNode.newSegment(path);
    }
}
