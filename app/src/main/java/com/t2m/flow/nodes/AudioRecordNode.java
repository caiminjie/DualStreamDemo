/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.nodes;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.util.Log;

import com.t2m.flow.data.ByteBufferData;
import com.t2m.flow.data.Data;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;
import com.t2m.flow.path.Plug;
import com.t2m.flow.path.Slot;
import com.t2m.flow.util.Cache;
import com.t2m.flow.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

/**
 * AudioRecord Node
 */
public class AudioRecordNode extends Node {
    private static final String TAG = AudioRecordNode.class.getSimpleName();

    private static final int BUFF_FRAME_COUNT = 100;
    private final Cache<ByteBuffer> mByteBufferCache;

    private AudioRecord mAudioRecord;
    private final Object mLock = new Object();
    private int mAudioSource;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;
    private int mChannelConfig;
    private int mBytesPerSample;
    private long mSampleCount;
    private boolean mStopped = false;

    private Plug mPlugReader = new Plug() {
        @Override
        public int getData(DataHolder holder) {
            return plugReaderGetData(holder);
        }

        @Override
        public int release(Data data) {
            return plugReaderRelease(data);
        }
    };

    public AudioRecordNode(String name, int audioSource, int sampleRate, int channelCount, int audioFormat) {
        super(name);

        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mAudioFormat = audioFormat;
        mBytesPerSample = Utils.getBytesPerSample(audioFormat);
        mChannelConfig = Utils.getChannelConfig(channelCount);

        // init cache
        int frameSize = Utils.getFrameSize(mChannelCount, mAudioFormat);
        int buffSize = frameSize * BUFF_FRAME_COUNT;
        mByteBufferCache = new Cache<>(
                "ByteBuffer",
                () -> ByteBuffer.wrap(new byte[buffSize]),
                null,
                ByteBuffer::clear);
    }

    @Override
    public Node open() throws IOException {
        synchronized(mLock){
            if (isOpened()) {
                Log.w(TAG, "[" + mName + "] Capture already started !");
                return this;
            }

            mSampleCount = 0;
            mStopped = false;

            int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelConfig, mAudioFormat);
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw new IOException("[" + mName + "] Invalid parameter !");
            }

            mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelConfig, mAudioFormat, minBufferSize * 4);
            if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                throw new IOException("[" + mName + "] AudioRecord initialize fail !");
            }

            mAudioRecord.startRecording();

            Log.i(TAG, "[" + mName + "] Start audio capture success !, audio status = " + mAudioRecord.getState());
            return this;
        }
    }

    @Override
    public boolean isOpened() {
        return mAudioRecord != null;
    }

    @Override
    public void close() throws IOException {
        if (!isOpened()) {
            return;
        }

        if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            mAudioRecord.stop();
        }
        mAudioRecord.release();

        // release cache
        mByteBufferCache.clear();

        Log.i(TAG, "[" + mName + "] Stop audio capture success !");
    }

    @Override
    public Plug plugReader(int index) {
        return mPlugReader;
    }

    @Deprecated
    @Override
    public Plug plugWriter(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    @Deprecated
    @Override
    public Slot slotReader(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    @Deprecated
    @Override
    public Slot slotWriter(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    private int plugReaderGetData(DataHolder holder) {
        ByteBuffer buffer = mByteBufferCache.get();

        int nRead = mAudioRecord.read(buffer.array(), 0, buffer.capacity());
        Log.d(TAG, "[" + mName + "] plugReaderGetData: buff = " + buffer.hashCode() + ", nRead = " + nRead);
        if (nRead == 0) {
            return RESULT_RETRY;
        } else if (nRead < 0) {
            Log.e(TAG, "[" + mName + "] Error: " + nRead);
            return RESULT_ERROR;
        } else {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            if (mStopped) {
                info.offset = 0;
                info.size = 0;
                info.presentationTimeUs = 0;
                info.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            } else {
                info.offset = 0;
                info.size = nRead;
                info.presentationTimeUs = mSampleCount * 1000000L / mSampleRate;
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;

                mSampleCount += nRead / mBytesPerSample / mChannelCount;
            }

            ByteBufferData data = ByteBufferData.create();
            data.setBufferInfo(info);
            data.setBuffer(buffer);
            holder.data = data;

            return RESULT_OK;
        }
    }

    private int plugReaderRelease(Data data) {
        ByteBuffer buffer = ((ByteBufferData)data).getBuffer();
        mByteBufferCache.put(buffer);
        return RESULT_OK;
    }

    public void stop() {
        mStopped = true;
    }
}
