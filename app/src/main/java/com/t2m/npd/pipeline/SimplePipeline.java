package com.t2m.npd.pipeline;

import android.util.Log;

import com.t2m.npd.Data;
import com.t2m.npd.Node;
import com.t2m.npd.Pipeline;
import com.t2m.npd.node.ProcessNode;
import com.t2m.npd.util.Cache;
import com.t2m.npd.util.RetrySleepHelper;

import java.util.ArrayList;
import java.util.List;

public class SimplePipeline<T extends Data> extends Pipeline<T> {
    private static final String TAG = SimplePipeline.class.getSimpleName();

    private final List<ProcessNode<T>> mList = new ArrayList<>();

    private Thread mThread = null;
    private final Object mThreadLock = new Object();

    private RetrySleepHelper mBindDataRetryHelper;
    private final Cache<T> mCache = new Cache<>(
            "Pipeline.Cache",
            (data) -> {
                if (data == null) {
                    return SimplePipeline.this.mAdapter.onCreateData();
                } else {
                    return data;
                }
            },
            null);

    @SuppressWarnings("WeakerAccess")
    public SimplePipeline(String name, DataAdapter<T> adapter) {
        super(name, adapter);

        mBindDataRetryHelper = new RetrySleepHelper(mName + "#bind");
    }

    @Override
    public int start() {
        synchronized (mThreadLock) {
            android.util.Log.d(TAG, "[" + mName + "] start()# begin");
            if (mThread != null) {
                android.util.Log.d(TAG, "[" + mName + "] start()# end. already started");
                return Node.RESULT_OK;
            }

            mThread = new Thread(() -> {
                Log.d(TAG, "[" + mName + "] pipeline begin");
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

                    if (result == Node.RESULT_OK) {
                        // process data with outgoing node
                        for (ProcessNode<T> node : getNodeList()) {
                            result = node.processWithRetry(data);
                            if (result != Node.RESULT_OK) {
                                Log.e(TAG, "get error code: [" + result + "] on Node: [" + node + "]");
                                break;
                            }
                        }
                    }

                    // release data
                    mAdapter.onReleaseData(data);

                    // recycle to cache
                    mCache.put(data);
                }

                Log.d(TAG, "[" + mName + "] pipeline end");
            });
            mThread.start();

            android.util.Log.d(TAG, "[" + mName + "] start()# end");
            return Node.RESULT_OK;
        }
    }

    @Override
    public int stop() {
        synchronized (mThreadLock) {
            android.util.Log.d(TAG, "[" + mName + "] stop()# begin");
            if (mThread == null) {  // TODO may stop twice, but no side effect at present.
                android.util.Log.d(TAG, "[" + mName + "] stop()# begin. already stopped");
                return Node.RESULT_OK;
            }

            mThread.interrupt();

            android.util.Log.d(TAG, "[" + mName + "] stop()# end");
            return Node.RESULT_OK;
        }
    }

    @Override
    public boolean isStarted() {
        synchronized (mThreadLock) {
            return mThread != null;
        }
    }

    @Override
    public void waitForFinish() {
        try {
            mThread.join();
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
