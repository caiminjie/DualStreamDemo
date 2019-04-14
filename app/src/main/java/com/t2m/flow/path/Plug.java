/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.path;

import com.t2m.flow.data.Data;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;

/**
 * Plug<br>
 * A data is provided for reading
 */
public abstract class Plug {
    /**
     * get data from plug
     * @param holder holder to receive the result and data
     * @return {@link Node#RESULT_OK}, {@link Node#RESULT_RETRY}, {@link Node#RESULT_NOT_OPEN}, {@link Node#RESULT_ERROR}
     */
    public abstract int getData(DataHolder holder);

    /**
     * release data
     * @param data data to release
     * @return {@link Node#RESULT_OK}, {@link Node#RESULT_RETRY}, {@link Node#RESULT_NOT_OPEN}, {@link Node#RESULT_ERROR}
     */
    public abstract int release(Data data);

    /**
     * Whether data should bypass this node when it it used as a writer.
     * @return true: bypass; false: otherwise
     */
    public boolean bypass() {
        return false;
    }
}
