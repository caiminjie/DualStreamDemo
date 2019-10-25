package com.t2m.pan.node.head;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;

import com.t2m.pan.Data;
import com.t2m.pan.data.SurfaceData;
import com.t2m.pan.pan;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageCaptureNode extends HeadNode<SurfaceData> {
    private static final String TAG = ImageCaptureNode.class.getSimpleName();


    private int id = hashCode();
    private Size mSize;
    private ImageReader mImageReader = null;
    private HandlerThread mProcessThread;
    private String mPath;

    public ImageCaptureNode(String name) {
        super(name);
    }

    public ImageCaptureNode setSize(Size size) {
        mSize = size;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageCaptureNode setSize(int width, int height) {
        mSize = new Size(width, height);
        return this;
    }

    @SuppressWarnings("unused")
    public Size getSize() {
        return mSize;
    }

    @Override
    protected SurfaceData onCreateData() {
        return new SurfaceData(id, Data.TYPE_VIDEO);
    }

    @Override
    protected int onBindData(SurfaceData data) {
        data.surface = mImageReader.getSurface();
        data.template = CameraDevice.TEMPLATE_STILL_CAPTURE;
        data.width = mSize.getWidth();
        data.height = mSize.getHeight();
        return pan.RESULT_OK;
    }

    @Override
    protected int onReleaseData(SurfaceData data) {
        data.surface = null;
        return pan.RESULT_EOS;
    }

    @Override
    public boolean isOpened() {
        return mImageReader != null;
    }

    @Override
    protected void onOpen() throws IOException {
        mProcessThread = new HandlerThread(mName + "#ProcessThread");
        mProcessThread.start();
        Handler handler = new Handler(mProcessThread.getLooper());

        mImageReader = ImageReader.newInstance(mSize.getWidth(), mSize.getHeight(),
                ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(this::onImageAvailable, handler);
    }

    @Override
    protected void onClose() throws IOException {
        Log.d(TAG, "onClose: ");
        mProcessThread.quitSafely();
        mImageReader.close();
    }

    public ImageCaptureNode setPath(String path) {
        mPath = path;
        return this;
    }

    public String getPath() {
        return mPath;
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mPath);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            image.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
