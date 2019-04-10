/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.nodes;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.util.Log;

import com.t2m.dualstreamdemo.dataflow.data.AudioData;
import com.t2m.dualstreamdemo.dataflow.data.Data;
import com.t2m.dualstreamdemo.dataflow.node.BufferedReader;
import com.t2m.dualstreamdemo.dataflow.node.BufferedWriter;
import com.t2m.dualstreamdemo.dataflow.node.DataNode;
import com.t2m.dualstreamdemo.dataflow.node.DirectReader;
import com.t2m.dualstreamdemo.dataflow.node.DirectWriter;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

/**
 * AudioRecord Node
 */
public class AudioRecordNode extends DataNode {
    private static final String TAG = AudioRecordNode.class.getSimpleName();

    private static final int CACHED_BUFF_SIZE = 10;
    private static final int BUFF_FRAME_COUNT = 100;

    private final Object[] mCachedBuffs = new Object[CACHED_BUFF_SIZE];

    private AudioRecord mAudioRecord;
    private Object mObject = new Object();
    private int mAudioSource;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;
    private int mChannelConfig;
    private int mBytesPerSample;
    private long mSampleCount;
    private int mBuffSize;
    private boolean mStopped = false;

    private BufferedReader mBufferedReader = new BufferedReader() {
        @Override
        public int readBegin(Data data) {
            return AudioRecordNode.this.readBegin(data);
        }

        @Override
        public int readEnd(Data data) {
            return AudioRecordNode.this.readEnd(data);
        }
    };

    public AudioRecordNode(int audioSource, int sampleRate, int channelCount, int audioFormat) {
        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mAudioFormat = audioFormat;
        mBytesPerSample = Utils.getBytesPerSample(audioFormat);
        mChannelConfig = Utils.getChannelConfig(channelCount);

        int mFrameSize = Utils.getFrameSize(mChannelCount, mAudioFormat);
        mBuffSize = mFrameSize * BUFF_FRAME_COUNT;
    }

    @Override
    public DataNode open() throws IOException {
        synchronized(mObject){
            if (isOpened()) {
                Log.w(TAG, "Capture already started !");
                return this;
            }

            mSampleCount = 0;
            mStopped = false;

            int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelConfig, mAudioFormat);
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw new IOException("Invalid parameter !");
            }

            mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelConfig, mAudioFormat, minBufferSize * 4);
            if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                throw new IOException("AudioRecord initialize fail !");
            }

            mAudioRecord.startRecording();

            Log.i(TAG, "Start audio capture success !, audio status = " + mAudioRecord.getState());
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
        clearCache();

        Log.i(TAG, "Stop audio capture success !");
    }

    @Override
    public BufferedReader getBufferedReader() {
        return mBufferedReader;
    }

    @Deprecated
    @Override
    public BufferedWriter getBufferedWriter() {
        throw new InvalidParameterException("method not supported");
    }

    @Deprecated
    @Override
    public DirectReader getDirectReader() {
        throw new InvalidParameterException("method not supported");
    }

    @Deprecated
    @Override
    public DirectWriter getDirectWriter() {
        throw new InvalidParameterException("method not supported");
    }

    private int readBegin(Data data) {
        ByteBuffer buffer = getCached(mBuffSize);
        buffer.clear();

        int nRead = mAudioRecord.read(buffer.array(), 0, buffer.capacity());
        Log.d(TAG, "readBegin: buff = " + buffer.array() + ", nRead = " + nRead);
        if (nRead == 0) {
            return RESULT_RETRY;
        } else if (nRead < 0) {
            Log.e(TAG, "Error: " + nRead);
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

            AudioData.setBufferInfo(data, info);
            AudioData.setBuffer(data, buffer);

            return RESULT_OK;
        }
    }

    private int readEnd(Data data) {
        ByteBuffer buffer = AudioData.getBuffer(data);
        cache(buffer);
        return RESULT_OK;
    }

    public void stop() {
        mStopped = true;
    }

    private ByteBuffer getCached(int newBuffSize) {
        synchronized (mCachedBuffs) {
            for (Object obj : mCachedBuffs) {
                if (obj == null) {
                    continue;
                }

                WeakReference<ByteBuffer> ref = (WeakReference<ByteBuffer>) obj;
                ByteBuffer buff;
                if ((buff = ref.get()) == null) {
                    continue;
                }

                return buff;
            }

            return ByteBuffer.wrap(new byte[newBuffSize]);
        }
    }

    private void cache(ByteBuffer buff) {
        synchronized (mCachedBuffs) {
            for (int i = 0; i < mCachedBuffs.length; i++) {
                // try find null item
                if (mCachedBuffs[i] == null) {
                    mCachedBuffs[i] = new WeakReference<>(buff);
                    return;
                }

                // try find released WeakReference
                WeakReference<ByteBuffer> ref = (WeakReference<ByteBuffer>) mCachedBuffs[i];
                if (ref.get() == null) {
                    mCachedBuffs[i] = new WeakReference<>(buff);
                    return;
                }
            }
        }
    }

    private void clearCache() {
        for (int i = 0; i < mCachedBuffs.length; i++) {
            if (mCachedBuffs[i] == null) {
                continue;
            }

            WeakReference<ByteBuffer> ref = (WeakReference<ByteBuffer>) mCachedBuffs[i];
            ref.clear();
            mCachedBuffs[i] = null;
        }
    }
}
