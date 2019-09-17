package com.t2m.stream.node;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.util.Log;
import android.util.SparseArray;

import com.t2m.stream.data.MediaData;
import com.t2m.stream.util.Cache;
import com.t2m.stream.util.Utils;

import java.io.IOException;
import java.util.LinkedList;

public class AudioNode extends ProcessNode<MediaData> {
    private final String TAG = com.t2m.stream.node.AudioNode.class.getSimpleName() + "#" + this.hashCode();

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

    private final SparseArray<Queue> mStreamQueues = new SparseArray<>();
    private final Object mStreamLock = new Object();

    private final Object mFetchLock = new Object();

    private class Queue {
        private final LinkedList<Buffer> mQueue = new LinkedList<>();

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
    }

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

                if (ref == 0) {
                    mCache.put(this);
                } else if (ref < 0) {
                    Log.w(TAG, "release buffer [" + this.hashCode() + "] when ref is 0");
                }
            }
        }
    }

    public AudioNode(String name, int audioSource, int sampleRate, int channelCount, int audioFormat) {
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
                (data) -> {
                    if (data == null) {
                        data = new Buffer(mBuffSize, mStreamQueues.size());
                    } else {
                        data.reset(mBuffSize, mStreamQueues.size());
                    }
                    return data;
                },
                null);
    }

    private Queue addStream(int id) {
        synchronized (mStreamLock) {
            Log.d(TAG, "addStream() begin");
            Queue queue = mStreamQueues.get(id);
            if (queue == null) {
                queue = new Queue();
                mStreamQueues.put(id, queue);
            }
            Log.d(TAG, "addStream() end");
            return queue;
        }
    }

    private void dispatchStreamData(Buffer buffer) {
        synchronized (mStreamLock) {
            buffer.ref = mStreamQueues.size();
            for (int i=0; i<mStreamQueues.size(); i++) {
                mStreamQueues.valueAt(i).enqueue(buffer);
            }
        }
    }

    @Override
    protected void onOpen() throws IOException {
        synchronized(mOpenLock){
            Log.d(TAG, "[" + mName + "] onOpen() begin");
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

            Log.d(TAG, "[" + mName + "] onOpen() end. " + mAudioRecord.getState());
        }
    }

    @Override
    protected void onClose() throws IOException {
        Log.d(TAG, "[" + mName + "] onClose() before lock");
        synchronized(mOpenLock) {
            Log.d(TAG, "[" + mName + "] onClose() begin");

            // clear all queues
            synchronized (mStreamLock) {
                for (int i = 0; i < mStreamQueues.size(); i++) {
                    Queue queue = mStreamQueues.valueAt(i);
                    queue.clearQueue();
                }
            }


            if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                mAudioRecord.stop();
            }
            mAudioRecord.release();
            mAudioRecord = null;

            Log.d(TAG, "[" + mName + "] onClose() end");
        }
    }

    @Override
    public int process(MediaData data) {
        if (mStatus == STATUS_NOT_OPENED) {
            return RESULT_RETRY;
        } else if (mStatus == STATUS_CLOSED || mStatus == STATUS_CLOSING) {
            return RESULT_EOS;
        }

        // get queue
        Queue queue = mStreamQueues.get(data.id);
        if (queue == null) {
            queue = addStream(data.id); // new id should add a new queue
        }

        // get buffer
        Buffer buffer;
        while ((buffer = queue.dequeue()) == null) {
            // fetch more
            int result = fetchMore(queue);
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

        return RESULT_OK;
    }

    private int fetchMore(Queue queue) {
        synchronized (mFetchLock) {
            if (!queue.shouldFetch()) {
                return RESULT_OK;  // already fetched by other node
            }

            // do fetch
            Buffer buffer = mCache.get();

            // read audio buff
            buffer.len = mAudioRecord.read(buffer.buffer, 0, buffer.buffer.length);
            if (buffer.len == 0) {
                mCache.put(buffer);
                return RESULT_RETRY;
            } else if (buffer.len < 0) {
                Log.e(TAG, "Fetch error: " + buffer.len);
                mCache.put(buffer);
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

                // dispatch buffer to all nodes
                dispatchStreamData(buffer);

                return RESULT_OK;
            }
        }
    }
}
