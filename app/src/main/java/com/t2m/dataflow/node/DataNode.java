/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dualstreamdemo.dataflow.node;

import java.io.Closeable;
import java.io.IOException;

/**
 * Data Node
 */
public abstract class DataNode implements Closeable {
    public static final int RESULT_OK = 0;
    public static final int RESULT_RETRY = 1;
    public static final int RESULT_NOT_OPEN = 2;
    public static final int RESULT_ERROR = 3;

    public abstract DataNode open() throws IOException;
    public abstract boolean isOpened();
    @Override
    public abstract void close() throws IOException;

    public abstract BufferedReader getBufferedReader();
    public abstract BufferedWriter getBufferedWriter();
    public abstract DirectReader getDirectReader();
    public abstract DirectWriter getDirectWriter();
}
