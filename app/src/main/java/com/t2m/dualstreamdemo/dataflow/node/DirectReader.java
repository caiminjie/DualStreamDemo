/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dualstreamdemo.dataflow.node;


import com.t2m.dualstreamdemo.dataflow.data.Data;

/**
 * DirectReader<br>
 * A ByteBuffer should be passed for reading
 */
public abstract class DirectReader {
    /**
     * read from node.<br>
     * For this method. buffer should be provided. method will fill data into the given object.
     * @param data data
     * @return {@link DataNode#RESULT_OK}, {@link DataNode#RESULT_RETRY}, {@link DataNode#RESULT_NOT_OPEN}, {@link DataNode#RESULT_ERROR}
     */
    public abstract int read(Data data);
}
