package com.t2m.stream.node;

import com.t2m.stream.Data;
import com.t2m.stream.Node;
import com.t2m.stream.Pipeline;

import java.util.List;

public abstract class PipelineNode<T extends Data> extends Node {
    public PipelineNode(String name) {
        super(name);
    }

    public abstract void startPipeline();
    public abstract void stopPipeline();
    public abstract void waitPipelineFinish();
}
