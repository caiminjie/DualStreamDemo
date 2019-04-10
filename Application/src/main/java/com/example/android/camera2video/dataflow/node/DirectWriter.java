/* Copyright (C) 2018 Tcl Corporation Limited */
package com.example.android.camera2video.dataflow.node;

import com.example.android.camera2video.dataflow.data.Data;

/**
 * DirectWriter<br>
 * A ByteBuffer should be passed for writing
 */
public abstract class DirectWriter {
    /**
     * write to node.<br>
     * For this method, buffer should be provided. method will get data from the given object.
     * @param data data
     * @return {@link DataNode#RESULT_OK}, {@link DataNode#RESULT_RETRY}, {@link DataNode#RESULT_NOT_OPEN}, {@link DataNode#RESULT_ERROR}
     */
    public abstract int write(Data data);
}
