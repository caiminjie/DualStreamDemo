/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.android.camera2video.dataflow.node;


import com.t2m.android.camera2video.dataflow.data.Data;

/**
 * BufferedWriter<br>
 * A ByteBuffer is provided for writing
 */
public abstract class BufferedWriter {
    /**
     * write to node begin.<br>
     * For this method, buffer will be set by this method.<br>
     * The data should be filled to the two value outside the method and call {@link #writeEnd(Data)} for commit.
     * @param data data
     * @return {@link DataNode#RESULT_OK}, {@link DataNode#RESULT_RETRY}, {@link DataNode#RESULT_NOT_OPEN}, {@link DataNode#RESULT_ERROR}
     * @see #writeEnd(Data)
     */
    public abstract int writeBegin(Data data);

    /**
     * write to node end.<br>
     * After the object fetch from {@link #writeBegin(Data)} is filled. This method should be called to commit and release resources.
     * @param data data
     * @return {@link DataNode#RESULT_OK}, {@link DataNode#RESULT_RETRY}, {@link DataNode#RESULT_NOT_OPEN}, {@link DataNode#RESULT_ERROR}
     * @see #writeBegin(Data)
     */
    public abstract int writeEnd(Data data);
}
