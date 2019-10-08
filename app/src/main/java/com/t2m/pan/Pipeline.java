package com.t2m.pan;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Pipeline {
    private static final String TAG = Pipeline.class.getSimpleName();

    protected String mName;

    private Thread mThread = null;
    private final Object mThreadLock = new Object();

    private ItemNode mHeadNodeItem = null;
    private ItemNode mTailNodeItem = null;
    private ItemNode mCurrNodeItem = null;

    private static class ItemNode {
        Node<? extends Data, ? extends Data> node;
        ItemNode prev = null;
        ItemNode next = null;

        ItemNode(Node node) {
            this.node = node;
        }

        ItemNode addNext(Node node) {
            this.next = new ItemNode(node);
            this.next.prev = this;
            return this.next;
        }

        ItemNode addPrev(Node node) {
            this.prev = new ItemNode(node);
            this.prev.next = this;
            return this.prev;
        }
    }

    public Pipeline(String name) {
        mName = name;
    }

    public String name() {
        return mName;
    }

    public Pipeline addNode(Node<? extends Data, ? extends Data> node) {
        switch (node.type()) {
            case Node.TYPE_CONN:
                if (mHeadNodeItem == null && mTailNodeItem == null) {
                    throw new RuntimeException("conn node cannot be the first node.");
                }
                if (mHeadNodeItem != null && mTailNodeItem != null) {
                    throw new RuntimeException("both head & tail are added. no more node can be added.");
                }
                if (mTailNodeItem == null) {
                    mCurrNodeItem = mCurrNodeItem.addNext(node); // add node from head
                } else {
                    mCurrNodeItem = mCurrNodeItem.addPrev(node); // add node from tail
                }
                break;
            case Node.TYPE_HEAD:
                if (mHeadNodeItem != null) {
                    throw new RuntimeException("head node already added, no more head node can be added.");
                }
                if (mCurrNodeItem == null) {
                    mHeadNodeItem = mCurrNodeItem = new ItemNode(node);
                } else {
                    mHeadNodeItem = mCurrNodeItem = mCurrNodeItem.addPrev(node);
                }
                break;
            case Node.TYPE_TAIL:
                if (mTailNodeItem != null) {
                    throw new RuntimeException("tail node already added, no more head node can be added.");
                }
                if (mCurrNodeItem == null) {
                    mTailNodeItem = mCurrNodeItem = new ItemNode(node);
                } else {
                    mTailNodeItem = mCurrNodeItem = mCurrNodeItem.addNext(node);
                }
                break;
            default:
                throw new RuntimeException("invalid node type.");
        }
        return this;
    }

    public void start() {
        synchronized (mThreadLock) {
            Log.d(TAG, "[" + mName + "#" + hashCode() + "] start()# begin");
            if (mThread != null) {
                Log.d(TAG, "[" + mName + "#" + hashCode() + "] start()# end. already started");
                return;
            }

            // prepare node list
            if (mHeadNodeItem == null || mTailNodeItem == null) {
                throw new RuntimeException("Node are not complete");
            }

            mThread = new Thread(() -> {
                Log.d(TAG, "[" + mName + "#" + hashCode() + "] pipeline begin");
                ItemNode item;

                // open nodes
                try {
                    item = mHeadNodeItem;
                    while(item != null) {
                        item.node.open();
                        item = item.next;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "[" + mName + "#" + hashCode() + "] open node failed.", e);
                    return;
                }

                // process
                List<Data> inData = new ArrayList<>();
                List<Data> outData = new ArrayList<>();
                List<Data> tmpData;
                int result = pan.RESULT_OK;
                while (!Thread.currentThread().isInterrupted() && !pan.stopPipeline(result)) {
                    // dispatch
                    item = mHeadNodeItem;
                    while (item != null) {
                        result = item.node.dispatch(inData, outData);
                        if (result != pan.RESULT_OK)  break; // loop until get error or eos
                        item = item.next;

                        // prepare in/out data for next loop
                        tmpData = inData;
                        inData = outData;
                        outData = tmpData;
                        outData.clear();
                    }

                    // process
                    int tmpResult;
                    if (item == null)   item = mTailNodeItem; // if reach end, start from tail
                    while (item != null) {
                        // do process
                        tmpResult = item.node.process(inData, outData);

                        // get result
                        if (result < tmpResult) {
                            result = tmpResult;
                        }

                        // next item
                        item = item.prev;

                        // prepare in/out data for next loop
                        tmpData = inData;
                        inData = outData;
                        outData = tmpData;
                        outData.clear();
                    }
                }

                // close nodes
                item = mHeadNodeItem;
                while (item != null) {
                    try {
                        item.node.close();
                    } catch (IOException e) {
                        Log.e(TAG, "[" + mName + "#" + hashCode() + "] close node failed. may cause leak!!", e);
                    }
                    item = item.next;
                }

                Log.d(TAG, "[" + mName + "#" + hashCode() + "] pipeline end with result code: " + result + " (" + pan.resultString(result) + ")");
            });
            mThread.start();

            Log.d(TAG, "[" + mName + "#" + hashCode() + "] start()# end");
        }
    }

    public void stop() {
        synchronized (mThreadLock) {
            Log.d(TAG, "[" + mName + "#" + hashCode() + "] stop()# begin");
            if (mThread == null) {
                Log.d(TAG, "[" + mName + "#" + hashCode() + "] stop()# begin. already stopped");
                return;
            }

            mThread.interrupt();

            Log.d(TAG, "[" + mName + "#" + hashCode() + "] stop()# end");
        }
    }

    public boolean isStarted() {
        synchronized (mThreadLock) {
            return mThread != null;
        }
    }

    public void waitForFinish() {
        try {
            mThread.join();
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForFinish() interrupted.", e);
        }
    }
}
