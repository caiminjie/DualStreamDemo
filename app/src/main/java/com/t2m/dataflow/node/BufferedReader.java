/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.node;

import com.t2m.dualstreamdemo.dataflow.data.Data;

/**
 * BufferedReader<br>
 * A ByteBuffer is provided for reading
 */
public abstract class BufferedReader {
    /**
     * read from node begin.<br>
     * For this method, buffer will be set by this method.<br>
     * The data should be read from the two value outside the method and call {@link #readEnd(Data)} after read finished.
     * @param data data
     * @return {@link DataNode#RESULT_OK}, {@link DataNode#RESULT_RETRY}, {@link DataNode#RESULT_NOT_OPEN}, {@link DataNode#RESULT_ERROR}
     * @see #readEnd(Data)
     */
    public abstract int readBegin(Data data);

    /**
     * read from node end.<br>
     * After the object fetch from {@link #readBegin(Data)} is read. This method should be called to release resources.
     * @param data data
     * @return {@link DataNode#RESULT_OK}, {@link DataNode#RESULT_RETRY}, {@link DataNode#RESULT_NOT_OPEN}, {@link DataNode#RESULT_ERROR}
     * @see #readBegin(Data)
     */
    public abstract int readEnd(Data data);
}
