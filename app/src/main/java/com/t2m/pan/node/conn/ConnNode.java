package com.t2m.pan.node.conn;

import com.t2m.pan.Data;
import com.t2m.pan.Node;

public abstract class ConnNode<IN extends Data, OUT extends Data> extends Node<IN, OUT> {
    public ConnNode(String name) {
        super(name, TYPE_CONN);
    }
}
