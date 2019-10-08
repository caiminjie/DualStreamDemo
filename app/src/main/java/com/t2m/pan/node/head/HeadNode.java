package com.t2m.pan.node.head;

import com.t2m.pan.Data;
import com.t2m.pan.Node;
import com.t2m.pan.util.Cache;

public abstract class HeadNode<T extends Data> extends Node<Data, T> {
    private Data mResult;
    private Cache<T> mCache;

    public HeadNode(String name) {
        super(name, TYPE_HEAD);

        mCache = new Cache<>(mName + "#cache",
                data -> data == null ? onCreateData() : data,
                null);
    }

    @Override
    protected T onDispatch(Data data) {
        mResult = data;
        T d = mCache.get();
        d.result = onBindData(d);
        return d;
    }

    @Override
    protected Data onProcess(T data) {
        mResult.result = onReleaseData(data);
        if (data.result != Data.RESULT_OK) {
            // if previous node is not ok, pass the result to next.
            mResult.result = data.result;
        }
        mCache.put(data);
        return mResult;
    }

    protected abstract T onCreateData();
    protected abstract int onBindData(T data);
    protected abstract int onReleaseData(T data);
}
