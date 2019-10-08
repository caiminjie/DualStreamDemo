package com.t2m.pan;

import android.util.Log;

import java.io.IOException;

public abstract class Node<IN extends Data, OUT extends Data> {
    private static final String TAG = Node.class.getSimpleName();

    public static final int TYPE_HEAD = 0;
    public static final int TYPE_TAIL = 1;
    public static final int TYPE_CONN = 2;

    protected String mName;
    protected int mType;

    public Node(String name, int type) {
        mName = name;
        mType = type;
    }

    public String name() {
        return mName;
    }

    int type() {
        return mType;
    }

    public void open() throws IOException {
        if (isOpened()) {
            return;
        }
        onOpen();
    }

    public void close() throws IOException {
        if (!isOpened()) {
            return;
        }
        onClose();
    }

    protected Data dispatch(Data data) {
        return onDispatch((IN)data);
    }

    protected Data process(Data data) {
        return onProcess((OUT) data);
    }

    Node<OUT, ? extends Data> next;
    Node<? extends Data, IN> prev;

    public abstract boolean isOpened();
    protected abstract void onOpen() throws IOException;
    protected abstract void onClose() throws IOException;
    protected abstract OUT onDispatch(IN data);
    protected abstract IN onProcess(OUT data);
}
