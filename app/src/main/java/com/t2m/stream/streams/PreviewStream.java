package com.t2m.stream.streams;

import android.graphics.Color;
import android.hardware.camera2.CameraDevice;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.t2m.dualstream.Utils;
import com.t2m.pan.Task;
import com.t2m.pan.node.conn.GlNode;
import com.t2m.pan.node.tail.CameraNode;
import com.t2m.pan.node.head.SurfaceNode;
import com.t2m.stream.IVideoStream;
import com.t2m.stream.Stream;

public class PreviewStream extends Stream implements IVideoStream<PreviewStream> {
    private static final String TAG = PreviewStream.class.getSimpleName();

    private CameraNode mCameraNode;
    private Surface mPreviewSurface = null;
    private Size mPreferredPreviewSize = null;
    private Size mPreferredPreviewRatio = null;
    private Size mPreviewSize = null;
    private int mFrameRate = 30;

    public PreviewStream(String name, CameraNode cameraNode) {
        super(name);

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

        // create node
        SurfaceNode previewNode = new SurfaceNode(subName("Preview"), CameraDevice.TEMPLATE_PREVIEW, mPreviewSurface, mPreviewSize);
        GlNode glNode = new GlNode("WaterMark");

        glNode.addWaterMark("TEXT", 100, 100, Color.RED, 100);
        glNode.addWaterMark("TEXT2", 200, 400, Color.GREEN, 100);

        // config preview pipeline
        task.addPipeline("PreviewGlInput")
                .addNode(mCameraNode)
                .addNode(glNode.getInputNode());
        task.addPipeline("PreviewGlOutput")
                .addNode(glNode.getOutputNode())
                .addNode(previewNode);

        // return
        return true;
    }

    public PreviewStream setPreviewSurface(Surface surface) {
        mPreviewSurface = surface;
        return this;
    }

    @SuppressWarnings("unused")
    public PreviewStream setPreferredPreviewSize(int width, int height, int ratioWidth, int ratioHeight) {
        mPreferredPreviewSize = new Size(width, height);
        mPreferredPreviewRatio = new Size(ratioWidth, ratioHeight);
        return this;
    }

    @SuppressWarnings("unused")
    public PreviewStream setPreviewSize(int width, int height) {
        mPreviewSize = new Size(width, height);
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public PreviewStream setPreviewSize(Size size) {
        mPreviewSize = size;
        return this;
    }

    @SuppressWarnings("unused")
    public Size getPreviewSize() {
        return mPreviewSize;
    }

    @Override
    public PreviewStream setFrameRate(int frameRate) {
        mFrameRate = frameRate;
        return this;
    }

    @Override
    public int getFrameRate() {
        return mFrameRate;
    }
}
