package com.t2m.pan.node.head;

import android.util.Size;
import android.view.Surface;

import com.t2m.pan.Data;
import com.t2m.pan.data.SurfaceData;
import com.t2m.pan.pan;

import java.io.IOException;

public class SurfaceNode extends HeadNode<SurfaceData> {
    private static final String TAG = SurfaceNode.class.getSimpleName();
    private boolean mIsOpened = false;
    private int mTemplate;
    private Surface mSurface;
    private Size mSize;

    public SurfaceNode(String name, int template, Surface surface, Size size) {
        super(name);

        mTemplate = template;
        mSurface = surface;
        mSize = size;
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
        data.width = mSize.getWidth();
        data.height = mSize.getHeight();
        return pan.RESULT_OK;
    }

    protected int onReleaseData(SurfaceData data) {
        data.surface = null;
        return pan.RESULT_EOS;
    }
}
