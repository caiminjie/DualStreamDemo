package com.t2m.npd;

import android.util.Log;

import com.t2m.npd.node.ProcessNode;
import com.t2m.npd.util.Cache;
import com.t2m.npd.util.RetrySleepHelper;

import java.util.List;

public abstract class Pipeline <T extends Data> {
    private static final String TAG = Pipeline.class.getSimpleName();

    private String mName;
    private Thread mThread = null;
    private final Object mThreadLock = new Object();

    private RetrySleepHelper mBindDataRetryHelper;

    private final Cache<T> mCache = new Cache<>(
            "Pipeline.Cache",
            (data) -> {
                if (data == null) {
                    return Pipeline.this.onCreateData();
                } else {
                    return data;
                }
            },
            null);


    public Pipeline(String name) {
        mName = name;

        mBindDataRetryHelper = new RetrySleepHelper(mName + "#bind");
    }

    public String name() {
        return mName;
    }

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
                    while (!Thread.currentThread().isInterrupted() && (result = onBindData(data)) == Node.RESULT_RETRY) {
                        mBindDataRetryHelper.sleep();
                    }
                    mBindDataRetryHelper.end();

                    if (result == Node.RESULT_OK) {
                        // process data with outgoing node
                        for (ProcessNode<T> node : getNodeList()) {
                            node.retryBegin();
                            while ((result = node.process(data)) == Node.RESULT_RETRY) {
                                node.retrySleep();
                            }
                            node.retryEnd();
                        }
                    }

                    // release data
                    onReleaseData(data);

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

    @SuppressWarnings("unused")
    public boolean isStarted() {
        synchronized (mThreadLock) {
            return mThread != null;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public Pipeline<T> addNode(ProcessNode<T> node) {
        onAddNode(node);
        return this;
    }

    public void waitForFinish() {
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace(); // TODO log here
        }
    }

    protected abstract List<ProcessNode<T>> getNodeList();
    protected abstract void onAddNode(ProcessNode<T> node);
    protected abstract T onCreateData();
    protected abstract int onBindData(T data);
    protected abstract void onReleaseData(T data);
}
