package com.t2m.npd.data;

import android.view.Surface;

import com.t2m.npd.Data;

public class SurfaceData extends Data {
    public static final int TYPE_PREVIEW = 0;
    public static final int TYPE_RECORD = 1;

    public int type;
    public Surface surface;
}
