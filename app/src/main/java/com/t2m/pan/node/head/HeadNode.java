package com.t2m.pan.node.head;

import com.t2m.pan.Data;
import com.t2m.pan.Node;
import com.t2m.pan.pan;
import com.t2m.pan.util.Cache;

import java.util.List;

public abstract class HeadNode<T extends Data> extends Node<Data, T> {
    private Cache<T> mCache;

    public HeadNode(String name) {
        super(name, TYPE_HEAD);

        mCache = new Cache<>(mName + "#cache",
                data -> data == null ? onCreateData() : data,
                null);
    }

    @Override
    protected int onDispatch(List<Data> inData, List<T> outData) {
        T d = mCache.get();
        int result = onBindData(d);
        outData.add(d);
        return result;
    }

    @Override
    protected int onProcess(List<T> inData, List<Data> outData) {
        int result = pan.RESULT_OK;
        for (T data : inData) {
            int r = onReleaseData(data);
            mCache.put(data);
            if (result == pan.RESULT_OK && r != pan.RESULT_OK) {
                result = r;
            }
        }
        return result;
    }

    protected abstract T onCreateData();
    protected abstract int onBindData(T data);
    protected abstract int onReleaseData(T data);
}
