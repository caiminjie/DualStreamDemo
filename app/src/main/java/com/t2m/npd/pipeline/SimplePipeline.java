package com.t2m.npd.pipeline;

import com.t2m.npd.Data;
import com.t2m.npd.Pipeline;
import com.t2m.npd.node.ProcessNode;

import java.util.ArrayList;
import java.util.List;

public abstract class SimplePipeline<T extends Data> extends Pipeline<T> {
    private static final String TAG = SimplePipeline.class.getSimpleName();

    private final List<ProcessNode<T>> mList = new ArrayList<>();


    public SimplePipeline(String name) {
        super(name);
    }

    @Override
    protected List<ProcessNode<T>> getNodeList() {
        return mList;
    }

    @Override
    protected void onAddNode(ProcessNode<T> node) {
        synchronized (mList) {
            mList.add(node);
        }
    }
}
