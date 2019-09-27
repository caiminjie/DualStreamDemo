package com.t2m.npd.node;

import com.t2m.npd.Data;
import com.t2m.npd.Node;
import com.t2m.npd.util.RetrySleepHelper;

public abstract class ProcessNode<T extends Data> extends Node {
    private RetrySleepHelper mRetryHelper;
    private boolean mEnabled;

    public ProcessNode(String name) {
        this(name, true);
    }

    public ProcessNode(String name, boolean enabled) {
        super(name);
        mEnabled = enabled;
        mRetryHelper = new RetrySleepHelper(mName + "#retry");
    }

    /**
     * process the data
     * @param data data to process
     * @return result {@link Node#RESULT_OK}, {@link Node#RESULT_RETRY}, {@link Node#RESULT_NOT_OPEN}, {@link Node#RESULT_ERROR}, {@link Node#RESULT_EOS}
     */
    public final int process(T data) {
        if (mEnabled) {
            return onProcess(data);
        } else {
            return Node.RESULT_OK; // disabled node, just drop data.
        }
    }

    /**
     * process the data with auto retry
     * @param data data to process
     * @return result {@link Node#RESULT_OK}, {@link Node#RESULT_NOT_OPEN}, {@link Node#RESULT_ERROR}, {@link Node#RESULT_EOS}
     */
    public int processWithRetry(T data) {
        int result = RESULT_OK;
        mRetryHelper.begin();
        while (!Thread.currentThread().isInterrupted() && (result = process(data)) == Node.RESULT_RETRY) {
            mRetryHelper.sleep();
        }
        mRetryHelper.end();
        return result;
    }

    protected abstract int onProcess(T data);
}
