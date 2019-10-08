package com.t2m.pan.node.conn;

import com.t2m.pan.Data;
import com.t2m.pan.Node;

public abstract class ConnNode<T extends Data> extends Node<T, T> {
    public ConnNode(String name) {
        super(name, TYPE_CONN);
    }

    @Override
    protected T onDispatch(T data) {
        data.result = onDispatchData(data);
        return data;
    }

    @Override
    protected T onProcess(T data) {
        data.result = onProcessData(data);
        return data;
    }

    protected abstract int onDispatchData(T data);
    protected abstract int onProcessData(T data);
}
