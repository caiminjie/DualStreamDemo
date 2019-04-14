/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.task;

import android.util.Log;

import com.t2m.flow.node.Node;
import com.t2m.flow.path.Path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data flow task
 */
public class FlowTask extends Thread {
    private static final String TAG = FlowTask.class.getSimpleName();

    private static final boolean DEBUG_PERFORMANCE = false;

    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = 1;
    public static final int RESULT_NOT_FINISHED = 2;

    private final List<Node> mNodeList = new ArrayList<>();
    private final List<Path> mPathList = new ArrayList<>();
    private int mResult = RESULT_NOT_FINISHED;

    public FlowTask(String name) {
        super(name);
    }

    public FlowTask addNode(Node node) {
        if (isAlive()) {
            throw new IllegalStateException("Should not add node duration task is running");
        }

        mNodeList.add(node);

        return this;
    }

    public FlowTask addPath(Path path) {
        if (isAlive()) {
            throw new IllegalStateException("Should not add node duration task is running");
        }

        mPathList.add(path);

        return this;
    }

    @Override
    public void run() {
        long startTime;
        if (DEBUG_PERFORMANCE) {
            startTime = System.currentTimeMillis();
        }

        // for child class to do something
        onCreate();

        // do run
        try {
            // open
            for (Node node : mNodeList) {
                node.open();
            }

            // process
            for (Path path : mPathList) {
                path.processAsync();
            }

            // wait
            for (Path path : mPathList) {
                path.waitForFinish();
            }

            // close
            for (Node node : mNodeList) {
                node.close();
            }

            mResult = RESULT_OK;
        } catch (IOException e) {
            Log.e(TAG, "FlowTask.run()# failed", e);
            mResult = RESULT_ERROR;
        } finally {
            // for child class to do something
            onDestroy();

            if (DEBUG_PERFORMANCE) {
                Log.i("==Performance==", "FlowTask.run()# " + (System.currentTimeMillis() - startTime));
            }
        }
    }

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-20,BUG-5709670*/
    public void cancel() {
        for (Path path : mPathList) {
            path.cancel();
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/
        }
    }

    public void waitForFinish() {
        try {
            join();
        } catch (InterruptedException e) {
            Log.w(TAG, "wait failed.", e);
        }
    }

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-18,BUG-5709670*/
    public int getResult() {
        return mResult;
    }
    /* MODIFIED-END by Fan.Hu,BUG-5709670*/

    protected void onCreate() {
        // do nothing
    }

    protected void onDestroy() {
        // do nothing
    }
}
