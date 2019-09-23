package com.t2m.npd.node;

import com.t2m.npd.Data;
import com.t2m.npd.Node;

public abstract class ProcessNode<T extends Data> extends Node {
    @SuppressWarnings("WeakerAccess")
    public ProcessNode(String name) {
        super(name);
    }

    public abstract int process(T data);
}
