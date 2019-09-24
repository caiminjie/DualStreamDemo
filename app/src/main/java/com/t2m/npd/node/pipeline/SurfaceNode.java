package com.t2m.npd.node.pipeline;

import android.util.Log;
import android.view.Surface;

import com.t2m.npd.Node;
import com.t2m.npd.Pipeline;
import com.t2m.npd.data.SurfaceData;
import com.t2m.npd.node.PipelineNode;
import com.t2m.npd.pipeline.SimplePipeline;

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
        mPipeline = new SimplePipeline<>(mName + "#pipeline",
                new Pipeline.DataAdapter<SurfaceData>() {
                    @Override
                    public SurfaceData onCreateData() {
                        return new SurfaceData();
                    }

                    @Override
                    public int onBindData(SurfaceData data) {
                        data.type = mType;
                        data.surface = mSurface;
                        return Node.RESULT_OK;
                    }

                    @Override
                    public void onReleaseData(SurfaceData data) {
                        mPipeline.stop();
                    }
                });
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
