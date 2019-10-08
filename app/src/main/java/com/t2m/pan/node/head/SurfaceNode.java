package com.t2m.pan.node.head;

import android.view.Surface;

import com.t2m.pan.Data;
import com.t2m.pan.data.SurfaceData;

import java.io.IOException;

public class SurfaceNode extends HeadNode<SurfaceData> {
    private static final String TAG = SurfaceNode.class.getSimpleName();
    private boolean mIsOpened = false;
    private int mTemplate;
    private Surface mSurface;

    public SurfaceNode(String name, int template, Surface surface) {
        super(name);

        mTemplate = template;
        mSurface = surface;
    }

    @Override
    public boolean isOpened() {
        return mIsOpened;
    }

    @Override
    protected void onOpen() throws IOException {
        mIsOpened = true;
    }

    @Override
    protected void onClose() throws IOException {
        mIsOpened = false;
    }

    protected SurfaceData onCreateData() {
        return new SurfaceData(hashCode(), Data.TYPE_VIDEO);
    }

    protected int onBindData(SurfaceData data) {
        data.template = mTemplate;
        data.surface = mSurface;
        return Data.RESULT_OK;
    }

    protected int onReleaseData(SurfaceData data) {
        data.surface = null;
        return Data.RESULT_EOS;
    }
}
