package com.t2m.npd;

import com.t2m.npd.node.ProcessNode;

import java.util.List;

public abstract class Pipeline <T extends Data> {
    protected String mName;
    protected DataAdapter<T> mAdapter;

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

    public abstract int start();
    public abstract int stop();
    @SuppressWarnings("unused")
    public abstract boolean isStarted();
    public abstract void waitForFinish();
    protected abstract List<ProcessNode<T>> getNodeList();
    @SuppressWarnings("UnusedReturnValue")
    public abstract Pipeline<T> addNode(ProcessNode<T> node);
}
