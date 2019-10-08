package com.t2m.pan.data;

import android.view.Surface;

import com.t2m.pan.Data;

public class SurfaceData extends Data {
    public static final int TYPE_PREVIEW = 0;
    public static final int TYPE_RECORD = 1;

    public int template;
    public Surface surface;

    public SurfaceData(int id, int type) {
        super(id, type);
    }
}
