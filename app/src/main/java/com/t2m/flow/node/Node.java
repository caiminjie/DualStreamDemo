/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.node;

import com.t2m.flow.path.Plug;
import com.t2m.flow.path.Slot;

import java.io.Closeable;
import java.io.IOException;

/**
 * Data Node
 */
public abstract class Node implements Closeable {
    public static final int RESULT_OK = 0;
    public static final int RESULT_RETRY = 1;
    public static final int RESULT_NOT_OPEN = 2;
    public static final int RESULT_ERROR = 3;

    protected String mName;

    public Node(String name) {
        mName = name;
    }

    public abstract Node open() throws IOException;
    public abstract boolean isOpened();
    @Override
    public abstract void close() throws IOException;

    @SuppressWarnings("unused")
    public Plug plugReader() {
        return plugReader(0);
    }

    @SuppressWarnings("unused")
    public Plug plugWriter() {
        return plugWriter(0);
    }

    @SuppressWarnings("unused")
    public Slot slotReader() {
        return  slotReader(0);
    }

    @SuppressWarnings("unused")
    public Slot slotWriter() {
        return slotWriter(0);
    }

    public abstract Plug plugReader(int index);
    public abstract Plug plugWriter(int index);
    public abstract Slot slotReader(int index);
    public abstract Slot slotWriter(int index);
}
