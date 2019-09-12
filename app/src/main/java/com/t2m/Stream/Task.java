package com.t2m.stream;

import android.util.Log;

import com.t2m.stream.node.PipelineNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Task {
    private final static String TAG = Task.class.getSimpleName();

    private final List<Node> mNodes = new ArrayList<>();
    private final List<PipelineNode<? extends Data>> mPipelineNodes = new ArrayList<>();

    private String mName;
    private Thread mThread = null;
    private final Object mThreadLock = new Object();

    public Task(String name) {
        mName = name;
    }

    public Task addNode(Node node) {
        mNodes.add(node);
        if (node instanceof PipelineNode) {
            mPipelineNodes.add((PipelineNode<? extends Data>) node);
        }
        return this;
    }

    public Task start() {
        synchronized (mThreadLock) {
            Log.d(TAG, "[" + mName + "] start()# begin");
            if (mThread != null) {
                Log.w(TAG, "[" + mName + "] Cannot start task due to it is already started. If you want to reuse a used task please call reset first");
                return this;
            }

            mThread = new Thread(() -> {
                Log.d(TAG, "[" + mName + "] task begin");
                try {
                    // open nodes
                    for (Node node : mNodes) {
                        node.open();
                    }

                    // start pipeline
                    for (PipelineNode<? extends Data> node : mPipelineNodes) {
                        node.startPipeline();
                    }

                    // wait
                    for (PipelineNode<? extends Data> node : mPipelineNodes) {
                        node.waitPipelineFinish();
                    }

                    Log.d(TAG, "[" + mName + "] all pipeline finished");

                    // close nodes
                    for (Node node : mNodes) {
                        node.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "start task failed.", e);
                }
                Log.d(TAG, "[" + mName + "] task end");
            });
            mThread.start();

            Log.d(TAG, "[" + mName + "] start()# end");
            return this;
        }
    }

    public Task stop() {
        synchronized (mThreadLock) {
            Log.d(TAG, "[" + mName + "] stop()# begin");
            if (mThread == null) {
                Log.d(TAG, "[" + mName + "] stop()# end. not started.");
                return this;
            }

            // stop pipeline
            for (PipelineNode<? extends Data> node : mPipelineNodes) {
                node.stopPipeline();
            }

            Log.d(TAG, "[" + mName + "] stop()# end");
            return this;
        }
    }

    public Task reset() {
        synchronized (mThreadLock) {
            mThread = null;
            mNodes.clear();
            mPipelineNodes.clear();
            return this;
        }
    }

    public Task waitForFinish() {
        Log.i("==MyTest==", "[" + mName + "] waitForFinish()# begin.");
        if (mThread == null) {
            Log.i("==MyTest==", "[" + mName + "] waitForFinish()# end 2.");
            return this;
        }

        try {
            mThread.join();
        } catch (InterruptedException e) {
            Log.w(TAG, "[" + mName + "] waitForFinish()# interrupted.", e);
        }

        Log.i("==MyTest==", "[" + mName + "] waitForFinish()# end.");

        return this;
    }
}
