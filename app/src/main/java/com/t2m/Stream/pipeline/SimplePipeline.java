package com.t2m.stream.pipeline;

import android.util.Log;

import com.t2m.stream.Data;
import com.t2m.stream.Node;
import com.t2m.stream.Pipeline;
import com.t2m.stream.node.ProcessNode;
import com.t2m.stream.util.Cache;
import com.t2m.stream.util.RetrySleepHelper;

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
