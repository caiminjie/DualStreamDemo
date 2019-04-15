/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.path;

import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;

/**
 * Slot<br>
 * A ByteBuffer should be passed for writing
 */
public abstract class Slot {
    /**
     * write to node.<br>
     * For this method, buffer should be provided. method will get data from the given object.
     * @param holder to hold a data
     * @return {@link Node#RESULT_OK}, {@link Node#RESULT_RETRY}, {@link Node#RESULT_NOT_OPEN}, {@link Node#RESULT_ERROR}
     */
    public abstract int setData(DataHolder holder);

    /**
     * Whether data should bypass this node when it it used as a writer.
     * @return true: bypass; false: otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean bypass() {
        return false;
    }
}
