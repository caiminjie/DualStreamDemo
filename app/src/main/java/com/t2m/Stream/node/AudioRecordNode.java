package com.t2m.stream.node;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.util.Log;

import com.t2m.stream.data.MediaData;
import com.t2m.stream.util.Cache;
import com.t2m.stream.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioRecordNode extends ProcessNode<MediaData> {
    private final String TAG = AudioRecordNode.class.getSimpleName() + "#" + this.hashCode();

    private static final int BUFF_FRAME_COUNT = 100;

    private AudioRecord mAudioRecord;
    private final Object mOpenLock = new Object();
    private int mAudioSource;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;
    private int mChannelConfig;
    private int mBytesPerSample;
    private long mSampleCount;
    private int mBuffSize;

    private final Cache<ByteBuffer> mCache;

    public AudioRecordNode(String name, int audioSource, int sampleRate, int channelCount, int audioFormat) {
        super(name);

        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mAudioFormat = audioFormat;
        mBytesPerSample = Utils.getBytesPerSample(audioFormat);
        mChannelConfig = Utils.getChannelConfig(channelCount);

        int mFrameSize = Utils.getFrameSize(mChannelCount, mAudioFormat);
        mBuffSize = mFrameSize * BUFF_FRAME_COUNT;

        mCache = new Cache<>(
                mName + "#cache",
                (data) -> ByteBuffer.wrap(new byte[mBuffSize]),
                null);
    }

    @Override
    protected void onOpen() throws IOException {
        synchronized(mOpenLock){
            mSampleCount = 0;

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
        }
    }

    @Override
    protected void onClose() throws IOException {
        if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            mAudioRecord.stop();
        }
        mAudioRecord.release();

        Log.i(TAG, "Stop audio capture success !");
    }

    @Override
    public int process(MediaData data) {
        int result;

        // get buffer
        ByteBuffer buffer = mCache.get();
        buffer.clear();

        // read audio buff
        int nRead = mAudioRecord.read(buffer.array(), 0, buffer.capacity());
        //Log.d(TAG, "readBegin: buff = " + buffer.array() + ", nRead = " + nRead);
        if (nRead == 0) {
            result = RESULT_RETRY;
        } else if (nRead < 0) {
            Log.e(TAG, "Error: " + nRead);
            result = RESULT_ERROR;
        } else {
            // copy data
            data.buffer.clear();
            data.buffer.put(buffer);  // FIXME maybe local buffer larger than data buffer

            // fill info
            MediaCodec.BufferInfo info = data.info;
            if (!isOpened()) {
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

            result = RESULT_OK;
        }
        mCache.put(buffer);
        return result;
    }
}
