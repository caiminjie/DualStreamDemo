package com.t2m.pan.node.tail;

import com.t2m.pan.Data;
import com.t2m.pan.Node;
import com.t2m.pan.pan;

import java.util.List;

public abstract class TailNode<T extends Data> extends Node<T, T> {
    public TailNode(String name) {
        super(name, TYPE_TAIL);
    }

    @Override
    protected int onDispatch(List<T> inData, List<T> outData) {
        outData.addAll(inData);
        return pan.RESULT_OK;
    }

    @Override
    protected int onProcess(List<T> inData, List<T> outData) {
        int result = pan.RESULT_OK;
        for (T data : inData) {
            if (! pan.stopPipeline(result)) {
                result = onProcessData(data);
            }
            outData.add(data);
        }
        return result;
    }

    protected abstract int onProcessData(T data);
}
