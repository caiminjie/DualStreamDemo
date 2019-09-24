package com.t2m.stream.streams;

import android.util.Log;

import com.t2m.npd.Task;
import com.t2m.npd.node.process.AudioNode;
import com.t2m.npd.node.pipeline.WavNode;
import com.t2m.stream.Stream;

public class AudioRecordStream extends Stream {
    private static final String TAG = AudioRecordStream.class.getSimpleName();

    private AudioNode mAudioNode;

    private WavNode mWavNode;

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
        mWavNode = new WavNode(
                "WavWriter", mPath,
                mSampleRate, mChannelCount, mAudioFormat);

        // config pipeline
        mWavNode.getPipeline().addNode(mAudioNode);

        // add node
        task
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
