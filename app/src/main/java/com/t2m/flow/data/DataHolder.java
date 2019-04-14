package com.t2m.flow.data;

import com.t2m.flow.util.Cache;

public class DataHolder {
    /**
     * Received data
     * {@link ByteBufferData}
     */
    public Data data;

    private static Cache<DataHolder> sCache = new Cache<>(
            "DataHolder",
            DataHolder::new,
            null,
            (holder) -> holder.data = null);

    public static DataHolder create() {
        return sCache.get();
    }

    public static DataHolder create(Data data) {
        DataHolder holder = sCache.get();
        holder.data = data;
        return holder;
    }

    private DataHolder() {
    }

    public void release() {
        sCache.put(this);
    }
}
