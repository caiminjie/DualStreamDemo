/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.task;

import android.util.Log;

import com.t2m.dualstreamdemo.dataflow.node.DataNode;
import com.t2m.dualstreamdemo.dataflow.path.DataPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data flow task
 */
public class DataFlowTask extends Thread {
    private static final String TAG = DataFlowTask.class.getSimpleName();

    private static final boolean DEBUG_PERFORMANCE = false; // MODIFIED by Fan.Hu, 2018-01-19,BUG-5709670

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-18,BUG-5709670*/
    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = 1;
    public static final int RESULT_NOT_FINISHED = 2;

    private final List<DataNode> mNodeList = new ArrayList<>();
    private final List<DataPath> mPathList = new ArrayList<>();
    private int mResult = RESULT_NOT_FINISHED;
    /* MODIFIED-END by Fan.Hu,BUG-5709670*/

    public DataFlowTask(String name) {
        super(name);
    }

    public DataFlowTask addNode(DataNode node) {
        if (isAlive()) {
            throw new IllegalStateException("Should not add node duration task is running");
        }

        mNodeList.add(node);

        return this;
    }

    public DataFlowTask addPath(DataPath path) {
        if (isAlive()) {
            throw new IllegalStateException("Should not add node duration task is running");
        }

        mPathList.add(path);

        return this;
    }

    @Override
    public void run() {
        /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
        long startTime;
        if (DEBUG_PERFORMANCE) {
            startTime = System.currentTimeMillis();
        }
        /* MODIFIED-END by Fan.Hu,BUG-5709670*/

        // for child class to do something
        onCreate();

        // do run
        try {
            // open
            for (DataNode node : mNodeList) {
                node.open();
            }

            // process
            for (DataPath path : mPathList) {
                path.processAsync();
            }

            // wait
            for (DataPath path : mPathList) {
                path.waitForFinish();
            }

            // close
            for (DataNode node : mNodeList) {
                node.close();
            }

            /* MODIFIED-BEGIN by Fan.Hu, 2018-01-18,BUG-5709670*/
            mResult = RESULT_OK;
        } catch (IOException e) {
            Log.e(TAG, "DataFlowTask.run()# failed", e);
            mResult = RESULT_ERROR;
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/
        } finally {
            // for child class to do something
            onDestroy();

            /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
            if (DEBUG_PERFORMANCE) {
                Log.i("==Performance==", "DataFlowTask.run()# " + (System.currentTimeMillis() - startTime));
            }
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/
        }
    }

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-20,BUG-5709670*/
    public void cancel() {
        for (DataPath path : mPathList) {
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
