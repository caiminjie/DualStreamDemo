package com.t2m.npd.node;

import com.t2m.npd.Data;
import com.t2m.npd.Node;

public abstract class PipelineNode<T extends Data> extends Node {
    @SuppressWarnings("WeakerAccess")
    public PipelineNode(String name) {
        super(name);
    }

    public abstract void startPipeline();
    public abstract void stopPipeline();
    public abstract void waitPipelineFinish();
}
