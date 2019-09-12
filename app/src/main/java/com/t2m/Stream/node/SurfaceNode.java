package com.t2m.stream.node;

import android.util.Log;
import android.view.Surface;

import com.t2m.stream.Node;
import com.t2m.stream.Pipeline;
import com.t2m.stream.data.SurfaceData;

import java.io.IOException;

public class SurfaceNode extends PipelineNode<SurfaceData> {
    private static final String TAG = SurfaceNode.class.getSimpleName();

    private int mType;
    private Surface mSurface;

    private Pipeline<SurfaceData> mPipeline;

    public SurfaceNode(String name, int type, Surface surface) {
        super(name);

        mType = type;
        mSurface = surface;
        mPipeline = new Pipeline<SurfaceData>(mName + "#pipeline") {
            @Override
            protected SurfaceData onCreateData() {
                return new SurfaceData();
            }

            @Override
            protected int onBindData(SurfaceData data) {
                data.type = mType;
                data.surface = mSurface;
                return Node.RESULT_OK;
            }

            @Override
            protected void onReleaseData(SurfaceData data) {
                stop();
            }
        };
    }

    @Override
    protected void onOpen() throws IOException {

    }

    @Override
    protected void onClose() throws IOException {

    }

    @Override
    public void startPipeline() {
        mPipeline.start();
    }

    @Override
    public void stopPipeline() {
        mPipeline.stop();
    }

    @Override
    public void waitPipelineFinish() {
        mPipeline.waitForFinish();
        Log.d(TAG, "[" + mName + "] pipeline [" + mPipeline.name() + "] finished");
    }

    public Pipeline<SurfaceData> pipeline() {
        return mPipeline;
    }
}
