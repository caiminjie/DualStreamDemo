package com.t2m.npd.pipeline;

import com.t2m.npd.Data;
import com.t2m.npd.Pipeline;
import com.t2m.npd.node.ProcessNode;
import com.t2m.npd.node.process.GroupProcessNode;

public class SimplePipeline<T extends Data> extends Pipeline<T> {
    private static final String TAG = SimplePipeline.class.getSimpleName();
    private final GroupProcessNode<T> mChildrenNode;

    public SimplePipeline(String name, DataAdapter<T> adapter) {
        super(name, adapter);

        mChildrenNode = new GroupProcessNode<>(mName + "#children");
        super.setTargetNode(mChildrenNode);
    }

    @Override
    @Deprecated
    public void setTargetNode(ProcessNode<T> node) {
        throw new RuntimeException("Use addNode() instead");
    }

    public SimplePipeline<T> addNode(ProcessNode<T> node) {
        mChildrenNode.addChild(node);
        return this;
    }
}
