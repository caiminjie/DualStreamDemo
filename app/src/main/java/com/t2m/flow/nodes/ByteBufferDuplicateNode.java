package com.t2m.flow.nodes;

import android.util.Log;

import com.t2m.flow.data.ByteBufferData;
import com.t2m.flow.data.Data;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;
import com.t2m.flow.path.Plug;
import com.t2m.flow.path.Slot;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.LinkedList;

public class ByteBufferDuplicateNode extends Node {
    private static final String TAG = ByteBufferDuplicateNode.class.getSimpleName();

    final private int mRefCount;
    private Object[] mBuffers = null;
    final private Object[] mLocks;

    final private Slot mSlotWriter = new SlotWriter();
    private Object[] mPlugReaders;

    public ByteBufferDuplicateNode(String name, int count) {
        super(name);

        mRefCount = count;

        // locks
        mLocks = new Object[mRefCount];
        for (int i=0; i<mLocks.length; i++) {
            mLocks[i] = new Object();
        }
    }

    @Override
    public Node open() throws IOException {
        if (mBuffers != null) {
            Log.w(TAG, "[" + mName + "] Capture already started !");
            return this;
        }

        // lists & readers
        mBuffers =  new Object[mRefCount];
        mPlugReaders = new Object[mRefCount];
        for (int i=0; i<mRefCount; i++) {
            mBuffers[i] = new LinkedList<ByteBufferData>();
            mPlugReaders[i] = new PlugReader(i);
        }

        return this;
    }

    @Override
    public boolean isOpened() {
        return mBuffers != null;
    }

    @Override
    public void close() throws IOException {
        if (!isOpened()) {
            return;
        }

        for (int i=0; i<mRefCount; i++) {
            synchronized (mLocks[i]) {
                closeIndex(i);
                mLocks[i].notifyAll();
            }
        }

        mBuffers = null;
    }

    @Override
    public Plug plugReader(int index) {
        return (Plug) mPlugReaders[index];
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

    @Override
    public Slot slotWriter(int index) {
        return mSlotWriter;
    }

    @SuppressWarnings("unchecked")
    private void closeIndex(int index) {
        LinkedList<ByteBufferData> list = (LinkedList<ByteBufferData>)mBuffers[index];
        while (list != null && !list.isEmpty()) {
            list.pollFirst().release();
        }
        mBuffers[index] = null;
    }

    class PlugReader extends Plug {
        private int mIndex;

        PlugReader(int index) {
            mIndex = index;
        }

        @SuppressWarnings("unchecked")
        @Override
        public int getData(DataHolder holder) {
            synchronized (mLocks[mIndex]) {
                while (mBuffers[mIndex] != null && ((LinkedList<ByteBufferData>)mBuffers[mIndex]).isEmpty()) {
                    try {
                        mLocks[mIndex].wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "[" + mName + ": " + mIndex + "] interrupted.");
                        // thread is interrupted. so all data in buffer is useless. release them
                        closeIndex(mIndex);
                        mLocks[mIndex].notifyAll();
                        return Node.RESULT_ERROR; // TODO may be OK??
                    }
                }

                holder.data = ((LinkedList<ByteBufferData>)mBuffers[mIndex]).pollFirst();
            }

            return Node.RESULT_OK;
        }

        @Override
        public int release(Data data) {
            return Node.RESULT_OK;
        }
    }

    class SlotWriter extends Slot {
        @SuppressWarnings("unchecked")
        @Override
        public int setData(DataHolder holder) {
            for (int i=0; i<mRefCount; i++) {
                synchronized (mLocks[i]) {
                    if (mBuffers[i] != null) {
                        ByteBufferData duplicateBuffer = ByteBufferData.create();
                        duplicateBuffer.setParent(holder.data);
                        ((LinkedList<ByteBufferData>)mBuffers[i]).addLast(duplicateBuffer);
                        mLocks[i].notifyAll();
                    }
                }
            }
            return Node.RESULT_OK;
        }

        @Override
        public boolean bypass() {
            return true;
        }
    }
}
