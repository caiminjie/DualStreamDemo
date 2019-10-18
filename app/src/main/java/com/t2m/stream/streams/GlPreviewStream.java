package com.t2m.stream.streams;

import android.hardware.camera2.CameraDevice;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.t2m.dualstream.Utils;
import com.t2m.pan.Task;
import com.t2m.pan.node.conn.GlVideoHubNode;
import com.t2m.pan.node.head.SurfaceNode;
import com.t2m.pan.node.tail.CameraNode;
import com.t2m.stream.IGlVideoStream;
import com.t2m.stream.IVideoStream;
import com.t2m.stream.Stream;

public class GlPreviewStream extends Stream implements IGlVideoStream<GlPreviewStream> {
    private static final String TAG = GlPreviewStream.class.getSimpleName();

    private CameraNode mCameraNode;
    private GlVideoHubNode mVideoHubNode;
    private Surface mPreviewSurface = null;
    private Size mPreferredPreviewSize = null;
    private Size mPreferredPreviewRatio = null;
    private Size mPreviewSize = null;
    private int mFrameRate = 30;

    public GlPreviewStream(String name, CameraNode cameraNode, GlVideoHubNode videoHubNode) {
        super(name);

        mCameraNode = cameraNode;
        mVideoHubNode = videoHubNode;
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

        // create node
        SurfaceNode previewNode = new SurfaceNode(subName("Preview"), CameraDevice.TEMPLATE_PREVIEW, mPreviewSurface);

        // config video hub input pipeline
        task.addPipeline("VideoHubInput")
                .addNode(mCameraNode)
                .addNode(mVideoHubNode.getInputNode());

        // config preview pipeline
        task.addPipeline("Preview")
                .addNode(mVideoHubNode.getOutputNode())
                .addNode(previewNode);

        // return
        return true;
    }

    public GlPreviewStream setPreviewSurface(Surface surface) {
        mPreviewSurface = surface;
        return this;
    }

    @SuppressWarnings("unused")
    public GlPreviewStream setPreferredPreviewSize(int width, int height, int ratioWidth, int ratioHeight) {
        mPreferredPreviewSize = new Size(width, height);
        mPreferredPreviewRatio = new Size(ratioWidth, ratioHeight);
        return this;
    }

    @SuppressWarnings("unused")
    public GlPreviewStream setPreviewSize(int width, int height) {
        mPreviewSize = new Size(width, height);
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public GlPreviewStream setPreviewSize(Size size) {
        mPreviewSize = size;
        return this;
    }

    @SuppressWarnings("unused")
    public Size getPreviewSize() {
        return mPreviewSize;
    }

    @Override
    public GlPreviewStream setFrameRate(int frameRate) {
        mFrameRate = frameRate;
        return this;
    }

    @Override
    public int getFrameRate() {
        return mFrameRate;
    }
}
