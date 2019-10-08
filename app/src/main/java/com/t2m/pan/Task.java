package com.t2m.pan;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Task {
    private static final String TAG = Task.class.getSimpleName();

    protected String mName;
    private final List<Pipeline> mPipelines = new ArrayList<>();
    private final List<Node> mSourceNodes = new ArrayList<>();
    private Thread mThread;
    private final Object mThreadLock = new Object();

    public Task(String name) {
        mName = name;
    }

    public Pipeline addPipeline(String name) {
        if (isStarted()) {
            throw new RuntimeException("Cannot add pipeline after task started.");
        }

        Pipeline pipeline = new Pipeline(name);
        mPipelines.add(pipeline);
        return pipeline;
    }

    public Task addSourceNode(Node node) {
        if (isStarted()) {
            throw new RuntimeException("Cannot add source node after task started.");
        }

        mSourceNodes.add(node);
        return this;
    }

    public void start() {
        synchronized (mThreadLock) {
            if (mThread != null) {
                Log.w(TAG, "[" + mName + "] task already started");
                return;
            }

            mThread = new Thread(()->{
                // start pipelines
                for (Pipeline pipeline : mPipelines) {
                    pipeline.start();
                }

                // wait for all pipeline finished
                for (Pipeline pipeline : mPipelines) {
                    pipeline.waitForFinish();
                }
            });
            mThread.start();
        }
    }

    public void waitForFinish() {
        Log.i("==MyTest==", "Task.waitForFinish()# begin");
        if (mThread == null) {
            Log.i("==MyTest==", "Task.waitForFinish()# end 2");
            return;
        }

        try {
            mThread.join();
        } catch (InterruptedException e) {
            Log.w(TAG, "wait for finish failed, due to interrupted.");
        }
        Log.i("==MyTest==", "Task.waitForFinish()# end");
    }

    public void forceStop() {
        synchronized (mThreadLock) {
            for (Pipeline pipeline : mPipelines) {
                pipeline.stop();
            }
        }
    }

    public void stop() {
        synchronized (mThreadLock) {
            for (Node node : mSourceNodes) {
                try {
                    node.close();
                } catch (IOException e) {
                    Log.e(TAG, "stop node failed. may cause leak!", e);
                }
            }
        }
    }

    public boolean isStarted() {
        synchronized (mThreadLock) {
            return mThread != null;
        }
    }
}
