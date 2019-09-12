package com.t2m.stream.node;

import com.t2m.stream.Data;
import com.t2m.stream.Node;

public abstract class ProcessNode<T extends Data> extends Node {
    public ProcessNode(String name) {
        super(name);
    }

    public abstract int process(T data);
}
