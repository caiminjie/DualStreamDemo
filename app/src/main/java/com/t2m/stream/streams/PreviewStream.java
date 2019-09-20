package com.t2m.stream.streams;

import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.t2m.dualstream.Utils;
import com.t2m.npd.Task;
import com.t2m.npd.data.SurfaceData;
import com.t2m.npd.node.CameraNode;
import com.t2m.npd.node.SurfaceNode;
import com.t2m.stream.Stream;

public class PreviewStream extends Stream {
    private static final String TAG = PreviewStream.class.getSimpleName();

    private CameraNode mCameraNode;
    private Surface mPreviewSurface = null;
    private Size mPreferredPreviewSize = null;
    private Size mPreferredPreviewRatio = null;
    private Size mPreviewSize = null;

    public PreviewStream(String name, CameraNode cameraNode) {
        super(name, TYPE_PREVIEW, false, true);

        mCameraNode = cameraNode;
    }

    @Override
    public boolean build(Task task) {
        if (!mCameraNode.isCameraOpened()) {
            Log.e(TAG, "Cannot build template due to camera node not opened.");
            return false;
        }

        // prepare preview size
        if (mPreviewSize == null) {
            if (mPreferredPreviewSize == null || mPreferredPreviewRatio == null) {
                Log.e(TAG, "Cannot build template due to preview size not set. see: setPreviewSize() or setPreferredPreviewSize()");
                return false;
            }
            mPreviewSize = Utils.chooseOptimalSize(
                    mCameraNode.getAvailableSurfaceSize(),
                    mPreferredPreviewSize, mPreferredPreviewRatio);
            if (mPreviewSize == null) {
                Log.e(TAG, "Cannot build template due to preferred preview size not valid.");
                return false;
            }
        }

        assert mPreviewSurface != null;
        assert mPreferredPreviewSize != null;
        assert mPreferredPreviewRatio != null;

        // compute preview size
        mPreviewSize = Utils.chooseOptimalSize(
                mCameraNode.getAvailableSurfaceSize(),
                mPreferredPreviewSize, mPreferredPreviewRatio);

        // create node
        SurfaceNode previewNode = new SurfaceNode(subName("Preview"), SurfaceData.TYPE_PREVIEW, mPreviewSurface);

        // config pipeline
        previewNode.pipeline().addNode(mCameraNode);

        // create task
        task
                .addNode(mCameraNode)
                .addNode(previewNode);

        // return
        return true;
    }

    public PreviewStream setPreviewSurface(Surface surface) {
        mPreviewSurface = surface;
        return this;
    }

    public PreviewStream setPreferredPreviewSize(int width, int height, int ratioWidth, int ratioHeight) {
        mPreferredPreviewSize = new Size(width, height);
        mPreferredPreviewRatio = new Size(ratioWidth, ratioHeight);
        return this;
    }

    public void setPreviewSize(int width, int height) {
        mPreviewSize = new Size(width, height);
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }
}
