package com.t2m.npd.pipeline;

import android.util.Log;

import com.t2m.npd.Data;
import com.t2m.npd.Node;
import com.t2m.npd.Pipeline;
import com.t2m.npd.node.ProcessNode;
import com.t2m.npd.util.Cache;
import com.t2m.npd.util.RetrySleepHelper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class BufferedPipeline<T extends Data> extends Pipeline<T> {
    private static final String TAG = BufferedPipeline.class.getSimpleName();

    public static final int CB_ACCEPT = 0;
    public static final int CB_DROP = 1;

    private final List<ProcessNode<T>> mList = new ArrayList<>();

    private final Queue<T> mQueue = new Queue<>();

    private Thread mProducerThread = null;
    private Thread mConsumerThread = null;
    private final Object mProducerThreadLock = new Object();
    private final Object mConsumerThreadLock = new Object();

    private RetrySleepHelper mBindDataRetryHelper;
    private final Cache<T> mCache = new Cache<>(
            "Pipeline.Cache",
            (data) -> {
                if (data == null) {
                    return BufferedPipeline.this.mAdapter.onCreateData();
                } else {
                    return data;
                }
            },
            null);

    private OnReadyProcessCallback<T> mProcessCallback;
    private OnCachedCallback<T> mCachedCallback;
    public interface OnReadyProcessCallback<T extends Data> {
        int onReadyProcess(T data);
    }
    public interface OnCachedCallback<T extends Data> {
        void onDataCached(T data);
    }

    private static class Queue<T extends Data> {
        private final LinkedList<T> mQueue = new LinkedList<>();

        private void push(T data) {
            synchronized (mQueue) {
                mQueue.addLast(data); // add data to queue
            }
        }

        private T pop() {
            synchronized (mQueue) {
                return mQueue.pollFirst();
            }
        }

        private void clear() {
            synchronized (mQueue) {
                mQueue.clear();
            }
        }

        private int size() {
            return mQueue.size();
        }
    }

    public BufferedPipeline(String name, DataAdapter<T> adapter) {
        this(name, adapter, null, null);
    }

    public BufferedPipeline(String name, DataAdapter<T> adapter, OnCachedCallback<T> cachedCallback, OnReadyProcessCallback<T> processCallback) {
        super(name, adapter);

        mCachedCallback = cachedCallback;
        mProcessCallback = processCallback;
        mBindDataRetryHelper = new RetrySleepHelper(mName + "#bind");
    }

    @Override
    public int start() {
        int result;
        if ((result = startProducerThread()) != Node.RESULT_OK) {
            return result;
        }
        if ((result = startConsumerThread()) != Node.RESULT_OK) {
            return result;
        }
        return result;
    }

    private int startProducerThread() {
        synchronized (mProducerThreadLock) {
            Log.d(TAG, "[" + mName + "] startProducerThread()# begin");
            if (mProducerThread != null) {
                Log.d(TAG, "[" + mName + "] startProducerThread()# end. already started");
                return Node.RESULT_OK;
            }

            mProducerThread = new Thread(() -> {
                Log.d(TAG, "[" + mName + "] ProducerThread begin");
                int result = Node.RESULT_OK;

                while (!Thread.currentThread().isInterrupted() && result == Node.RESULT_OK) {
                    // get data
                    T data = mCache.get();
                    // bind data
                    mBindDataRetryHelper.begin();
                    while (!Thread.currentThread().isInterrupted() && (result = mAdapter.onBindData(data)) == Node.RESULT_RETRY) {
                        mBindDataRetryHelper.sleep();
                    }
                    mBindDataRetryHelper.end();

                    // callback
                    if (mCachedCallback != null) {
                        mCachedCallback.onDataCached(data);
                    }

                    // put data into queue
                    mQueue.push(data);

                    synchronized (mQueue) {
                        mQueue.notifyAll();
                    }
                }

                Log.d(TAG, "[" + mName + "] ProducerThread end");
            }, mName + "#Producer");
            mProducerThread.start();

            Log.d(TAG, "[" + mName + "] startProducerThread()# end");
            return Node.RESULT_OK;
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private int startConsumerThread() {
        synchronized (mConsumerThreadLock) {
            Log.d(TAG, "[" + mName + "] startConsumerThread()# begin");
            if (mConsumerThread != null) {
                Log.d(TAG, "[" + mName + "] startConsumerThread()# end. already started");
                return Node.RESULT_OK;
            }

            mConsumerThread = new Thread(() -> {
                Log.d(TAG, "[" + mName + "] ConsumerThread begin");
                int result = Node.RESULT_OK;

                while (!Thread.currentThread().isInterrupted() && result == Node.RESULT_OK) {
                    // get data
                    T data = null;

                    // fetch a data from queue
                    while (!Thread.currentThread().isInterrupted() && data == null) {
                        synchronized (mQueue) {
                            if ((data = mQueue.pop()) == null) {
                                try {
                                    mQueue.wait();
                                } catch (InterruptedException e) {
                                    Log.w(TAG, "wait queue failed due to interrupt.");
                                    return; // here thread is interrupted. just return.
                                }
                            }
                        }
                    }

                    // callback
                    int cb = CB_ACCEPT;
                    if (mProcessCallback != null) {
                        cb = mProcessCallback.onReadyProcess(data);
                    }

                    // process callback result
                    if (cb == CB_ACCEPT) {
                        // process data with process nodes
                        for (ProcessNode<T> node : getNodeList()) {
                            result = node.processWithRetry(data);
                            if (result != Node.RESULT_OK) {
                                Log.e(TAG, "get error code: [" + result + "] on Node: [" + node + "]");
                                break;
                            }
                        }
                    } else if (cb == CB_DROP) {
                        // drop data. do nothing here
                    } else {
                        // should not reach here
                        Log.e(TAG, "Should not reach here! Check your OnReadyProcessCallback, it returned an unexpected result.");
                        result = Node.RESULT_ERROR;
                    }

                    // release data
                    mAdapter.onReleaseData(data);

                    // recycle to cache
                    mCache.put(data);
                }

                Log.d(TAG, "[" + mName + "] ConsumerThread end");
            }, mName + "#Consumer");
            mConsumerThread.start();

            Log.d(TAG, "[" + mName + "] startConsumerThread()# end");
            return Node.RESULT_OK;
        }
    }

    @Override
    public int stop() {
        stopProducerThread();
        stopConsumerThread();
        mQueue.clear();
        return Node.RESULT_OK;
    }

    private void stopProducerThread() {
        synchronized (mProducerThreadLock) {
            Log.d(TAG, "[" + mName + "] stopProducerThread()# begin");
            if (mProducerThread == null) {  // TODO may stop twice, but no side effect at present.
                Log.d(TAG, "[" + mName + "] stopProducerThread()# begin. already stopped");
                return;
            }

            mProducerThread.interrupt();

            Log.d(TAG, "[" + mName + "] stopProducerThread()# end");
        }
    }

    private void stopConsumerThread() {
        synchronized (mConsumerThreadLock) {
            Log.d(TAG, "[" + mName + "] stopConsumerThread()# begin");
            if (mConsumerThread == null) {  // TODO may stop twice, but no side effect at present.
                Log.d(TAG, "[" + mName + "] stopConsumerThread()# begin. already stopped");
                return;
            }

            mConsumerThread.interrupt();

            Log.d(TAG, "[" + mName + "] stopConsumerThread()# end");
        }
    }

    @Override
    public boolean isStarted() {
        synchronized (mProducerThreadLock) {
            return mProducerThread != null;
        }
    }

    @Override
    public void waitForFinish() {
        try {
            mProducerThread.join();
            mConsumerThread.join();
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForFinish() interrupted.", e);
        }
    }

    @Override
    protected List<ProcessNode<T>> getNodeList() {
        return mList;
    }

    @Override
    public Pipeline<T> addNode(ProcessNode<T> node) {
        synchronized (mList) {
            mList.add(node);
        }
        return this;
    }
}
