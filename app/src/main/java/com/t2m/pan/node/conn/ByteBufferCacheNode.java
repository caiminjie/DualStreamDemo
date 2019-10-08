package com.t2m.pan.node.conn;

import android.util.Log;

import com.t2m.pan.data.ByteBufferData;
import com.t2m.pan.pan;
import com.t2m.pan.util.BufferCache;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class ByteBufferCacheNode extends ConnNode<ByteBufferData, ByteBufferData> {
    private static final String TAG = ByteBufferCacheNode.class.getSimpleName();
    private static final int MAGIC_INDEX = -10086;

    private final Object mLock = new Object();
    private long mDurationUs = 0;
    private boolean mEnableOutput = true;

    private final LinkedList<ByteBufferData> mQueue = new LinkedList<>();
    private final BufferCache<ByteBuffer> mCache;

    private ByteBufferData mConfigData;

    public ByteBufferCacheNode(String name) {
        super(name);

        mCache = new BufferCache<>(
                mName + "#cache",
                (data, size) -> data == null ? ByteBuffer.allocate(size) : data,
                null,
                Buffer::capacity);
    }

    public void cacheDurationUs(long durationUs) {
        synchronized (mLock) {
            mDurationUs = durationUs;
        }
    }

    public long cacheDurationUs() {
        synchronized (mLock) {
            return mDurationUs;
        }
    }

    public void enableOutput(boolean enable) {
        synchronized (mLock) {
            mEnableOutput = enable;
        }
    }

    public boolean enanbleOutput() {
        synchronized (mLock) {
            return mEnableOutput;
        }
    }

    @Override
    public boolean isOpened() {
        return false;
    }

    @Override
    protected void onOpen() throws IOException {

    }

    @Override
    protected void onClose() throws IOException {

    }

    @Override
    protected int onDispatch(List<ByteBufferData> inData, List<ByteBufferData> outData) {
        synchronized (mLock) {
            if (mEnableOutput) {
                // add cached data into out data
                if (!mQueue.isEmpty()) {
                    if (mConfigData != null) {
                        outData.add(mConfigData);
                    } else {
                        Log.w(TAG, "No config data received.");
                    }
                    outData.addAll(mQueue);
                    mQueue.clear();
                }

                // add in data into out data
                outData.addAll(inData);
                return pan.RESULT_OK;
            } else {
                for (ByteBufferData in : inData) {
                    // save config data
                    if (mConfigData != null && in.isConfig()) {
                        mConfigData = new ByteBufferData(in.id, in.type);
                        mConfigData.format(in.format());
                        //mConfigData.buffer(in.buffer()); // TODO add buffer?
                        mConfigData.info(in.info());
                    } else if (mDurationUs > 0) {
                        // get buffer
                        ByteBuffer buffer = mCache.get(in.info().size);
                        buffer.clear();
                        buffer.put(in.buffer());

                        // create out data
                        ByteBufferData out = new ByteBufferData(in.id, in.type);
                        out.format(in.format());
                        out.info(in.info());
                        out.buffer(buffer);
                        out.index(MAGIC_INDEX);

                        // queue out data
                        mQueue.addLast(out);
                    }

                    // return in data for previous node to release it
                    outData.add(in);
                }

                // check whether should remove previous data in queue
                while (!mQueue.isEmpty()
                        && (mQueue.peekLast().info().presentationTimeUs - mQueue.peekFirst().info().presentationTimeUs) > mDurationUs) {
                    // remove the first key from from queue, and put it into outData for recycle.
                    ByteBufferData data = mQueue.pollFirst();
                    outData.add(data);

                    // remove data from queue, until find a key frame
                    while (!mQueue.isEmpty() && (data = mQueue.peekFirst()) != null && !data.isKeyFrame()) {
                        outData.add(mQueue.pollFirst());
                    }
                }
                return pan.RESULT_CONTINUE; // don't dispatch to next node
            }
        }
    }

    @Override
    protected int onProcess(List<ByteBufferData> inData, List<ByteBufferData> outData) {
        for (ByteBufferData in : inData) {
            if (in.id == MAGIC_INDEX) {
                mCache.put(in.buffer());
            } else {
                // this data is from previous node, directly bypass
                outData.add(in);
            }
        }
        return pan.RESULT_OK;
    }
}
