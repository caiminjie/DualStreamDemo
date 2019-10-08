package com.t2m.pan.node.tail;

import com.t2m.pan.Data;
import com.t2m.pan.Node;

public abstract class TailNode<T extends Data> extends Node<T, T> {
    public TailNode(String name) {
        super(name, TYPE_TAIL);
    }

    @Override
    protected T onDispatch(T data) {
        return data;
    }

    protected T onProcess(T data) {
        data.result = onProcessData(data);
        return data;
    }

    protected abstract int onProcessData(T data);
}
