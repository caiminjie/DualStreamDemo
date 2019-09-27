package com.t2m.npd;

import android.util.Log;

import com.t2m.npd.node.ProcessNode;
import com.t2m.npd.util.Cache;

public class Pipeline <T extends Data> {
    private static final String TAG = Pipeline.class.getSimpleName();

    protected String mName;
    protected DataAdapter<T> mAdapter;
    protected ProcessNode<T> mTargetNode;

    private Thread mThread = null;
    private final Object mThreadLock = new Object();

    private final Cache<T> mCache = new Cache<>(
            "Pipeline.Cache",
            (data) -> {
                if (data == null) {
                    return mAdapter.onCreateData();
                } else {
                    return data;
                }
            },
            null);

    public interface DataAdapter<T extends Data> {
        T onCreateData();
        int onBindData(T data);
        void onReleaseData(T data);
    }

    public Pipeline(String name, DataAdapter<T> adapter) {
        mName = name;
        mAdapter = adapter;
    }

    public String name() {
        return mName;
    }

    public void setTargetNode(ProcessNode<T> node) {
        mTargetNode = node;
    }

    public int start() {
        if (isStarted()) {
            return Node.RESULT_OK;
        }
        return onStart();
    }

    public int stop() {
        if (!isStarted()) {
            return Node.RESULT_OK;
        }
        return onStop();
    }

    protected int onStart() {
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

                    try {
                        // bind & process
                        if ((result = mAdapter.onBindData(data)) == Node.RESULT_OK) {
                            result = mTargetNode.process(data);
                        }
                    } finally {
                        // release data
                        mAdapter.onReleaseData(data);

                        // recycle to cache
                        mCache.put(data);
                    }
                }

                Log.d(TAG, "[" + mName + "] pipeline end");
            });
            mThread.start();

            android.util.Log.d(TAG, "[" + mName + "] start()# end");
            return Node.RESULT_OK;
        }
    }

    protected int onStop() {
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
