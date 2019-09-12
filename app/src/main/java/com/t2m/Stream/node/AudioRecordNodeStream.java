package com.t2m.stream.node;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.util.Log;

import com.t2m.stream.Node;
import com.t2m.stream.data.MediaData;
import com.t2m.stream.util.Cache;
import com.t2m.stream.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AudioRecordNodeStream extends Node {
    private final String TAG = AudioRecordNodeStream.class.getSimpleName() + "#" + this.hashCode();

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

    private final Cache<Buffer> mCache;

    private final List<AudioNode> mOpenedAudioNodes = new ArrayList<>();
    private final List<AudioNode> mAudioNodes = new ArrayList<>();
    private int mStreamCount = 0;
    private final Object mStreamCountLock = new Object();

    private final Object mFetchLock = new Object();

    private class Buffer {
        byte[] buffer = null;
        int len;
        int ref;
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        Buffer(int buffer_size, int ref) {
            reset(buffer_size, ref);
        }

        void reset(int buffer_size, int ref) {
            synchronized (this) {
                if (buffer == null || buffer.length != buffer_size) {
                    buffer = new byte[buffer_size];
                }
                len = 0;
                this.ref = ref;
            }
        }

        void release() {
            synchronized (this) {
                ref --;

                if (ref <= 0) {
                    mCache.put(this);
                }
            }
        }
    }

    public class AudioNode extends ProcessNode<MediaData> {
        private final LinkedList<Buffer> mQueue = new LinkedList<>();
        private int mStatus;

        public AudioNode(String name) {
            super(name);
            mStatus = Node.STATUS_NOT_OPENED;
        }

        private void enqueue(Buffer buffer) {
            synchronized (mQueue) {
                mQueue.addLast(buffer);
            }
        }

        private Buffer dequeue() {
            synchronized (mQueue) {
                return mQueue.pollFirst();
            }
        }

        private void clearQueue() {
            synchronized (mQueue) {
                Buffer buffer;
                while ((buffer = mQueue.pollFirst()) != null) {
                    buffer.release();
                }
            }
        }

        private boolean shouldFetch() {
            synchronized (mQueue) {
                return mQueue.size() <= 0;
            }
        }

        @Override
        public int process(MediaData data) {
            Log.d(TAG, "process() begin"); // TODO remove later
            if (mStatus == STATUS_NOT_OPENED) {
                Log.d(TAG, "process() end 2"); // TODO remove later
                return RESULT_RETRY;
            } else if (mStatus == STATUS_CLOSED) {
                Log.d(TAG, "process() end 3"); // TODO remove later
                return RESULT_EOS;
            }

            Buffer buffer;
            while ((buffer = dequeue()) == null) {
                // fetch more
                int result = fetchMore(this);
                if (result != RESULT_OK) {
                    return result;
                }
            }

            // copy to data
            data.buffer.clear();
            data.buffer.put(buffer.buffer, 0, buffer.len);
            data.info.flags = buffer.info.flags;
            data.info.offset = buffer.info.offset;
            data.info.size = buffer.info.size;
            data.info.presentationTimeUs = buffer.info.presentationTimeUs;

            // release buffer
            buffer.release();

            Log.d(TAG, "process() end"); // TODO remove later
            return RESULT_OK;
        }

        @Override
        protected void onOpen() throws IOException {
            synchronized (mOpenedAudioNodes) {
                mStatus = Node.STATUS_OPENING;
                if (mOpenedAudioNodes.size() <= 0) {
                    realOpen();
                }
                mOpenedAudioNodes.add(this);
                mStatus = Node.STATUS_OPENED;
            }
        }

        @Override
        protected void onClose() throws IOException {
            synchronized (mOpenedAudioNodes) {
                mStatus = Node.STATUS_CLOSING;
                clearQueue();
                mOpenedAudioNodes.remove(this);
                if (mOpenedAudioNodes.size() <= 0) {
                    realClose();
                }
                mStatus = Node.STATUS_CLOSED;
            }
        }
    }

    public AudioRecordNodeStream(String name, int audioSource, int sampleRate, int channelCount, int audioFormat) {
        this(name, audioSource, sampleRate, channelCount, audioFormat, 1);
    }

    public AudioRecordNodeStream(String name, int audioSource, int sampleRate, int channelCount, int audioFormat, int streamCount) {
        super(name);

        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mAudioFormat = audioFormat;
        mBytesPerSample = Utils.getBytesPerSample(audioFormat);
        mChannelConfig = Utils.getChannelConfig(channelCount);
        setStreamCount(streamCount);

        int mFrameSize = Utils.getFrameSize(mChannelCount, mAudioFormat);
        mBuffSize = mFrameSize * BUFF_FRAME_COUNT;

        mCache = new Cache<>(
                mName + "#cache",
                (data) -> {
                    if (data == null) {
                        data = new Buffer(mBuffSize, mStreamCount);
                    } else {
                        data.reset(mBuffSize, mStreamCount);
                    }
                    return data;
                },
                null);
    }

    public void setStreamCount(int count) {
        if (isOpened()) {
            throw new RuntimeException("Cannot set stream count when node opened");
        }

        synchronized (mStreamCountLock) {
            Log.i(TAG, "[" + mName + "] setStreamCount()# " + count);
            if (mStreamCount != count) {
                mStreamCount = count;

                mAudioNodes.clear();
                for (int i = 0; i < mStreamCount; i++) {
                    mAudioNodes.add(new AudioNode(mName + "#" + i));
                }
            }
        }
    }

    public AudioNode getStreamNode(int index) {
        synchronized (mStreamCountLock) {
            if (index < 0 || index >= mAudioNodes.size()) {
                throw new RuntimeException("Index out of bound. index: " + index + ", size: " + mAudioNodes.size());
            }

            return mAudioNodes.get(index);
        }
    }

    @Override
    public Node open() throws IOException {
        Log.d(TAG, "[" + mName + "] fake open");
        return this;
    }

    @Override
    public Node close() throws IOException {
        Log.d(TAG, "[" + mName + "] fake close");
        return this;
    }

    private Node realOpen() throws IOException {
        return super.open();
    }

    private Node realClose() throws IOException {
        return super.close();
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
        mAudioRecord = null;

        Log.i(TAG, "Stop audio capture success !");
    }

    private int fetchMore(AudioNode node) {
        synchronized (mFetchLock) {
            Log.d(TAG, "fetchMore() begin"); // TODO remove later
            if (!node.shouldFetch()) {
                return RESULT_OK;  // already fetched by other node
            }

            // do fetch
            Buffer buffer = mCache.get();

            // read audio buff
            buffer.len = mAudioRecord.read(buffer.buffer, 0, buffer.buffer.length);
            if (buffer.len == 0) {
                mCache.put(buffer);
                Log.d(TAG, "fetchMore() end 2"); // TODO remove later
                return RESULT_RETRY;
            } else if (buffer.len < 0) {
                Log.e(TAG, "Fetch error: " + buffer.len);
                mCache.put(buffer);
                Log.d(TAG, "fetchMore() end 3"); // TODO remove later
                return RESULT_ERROR;
            } else {
                // fill info
                MediaCodec.BufferInfo info = buffer.info;
                if (!isOpened()) {
                    info.offset = 0;
                    info.size = 0;
                    info.presentationTimeUs = 0;
                    info.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                } else {
                    info.offset = 0;
                    info.size = buffer.len;
                    info.presentationTimeUs = mSampleCount * 1000000L / mSampleRate;
                    info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;

                    mSampleCount += buffer.len / mBytesPerSample / mChannelCount;
                }

                // put buffer to all nodes
                for (AudioNode n : mAudioNodes) {
                    n.enqueue(buffer);
                }

                Log.d(TAG, "fetchMore() end"); // TODO remove later
                return RESULT_OK;
            }
        }
    }
}
