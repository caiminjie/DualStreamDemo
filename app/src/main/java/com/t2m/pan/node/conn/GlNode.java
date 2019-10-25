package com.t2m.pan.node.conn;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.t2m.pan.Data;
import com.t2m.pan.data.SurfaceData;
import com.t2m.pan.node.head.HeadNode;
import com.t2m.pan.node.tail.TailNode;
import com.t2m.pan.pan;
import com.t2m.pan.util.gles.EglCore;
import com.t2m.pan.util.gles.FullFrameRect;
import com.t2m.pan.util.gles.GlUtil;
import com.t2m.pan.util.gles.Texture2dProgram;
import com.t2m.pan.util.gles.WindowSurface;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class GlNode extends ConnNode<SurfaceData, SurfaceData> {
    private static final String TAG = GlNode.class.getSimpleName();

    private SurfaceData mOutputData = null;
    private final Object mOutputSurfaceLock = new Object();
    private SurfaceData mCaptureOutputData = null;
    private final Object mCaptureOutputSurfaceLock = new Object();
    private GlProcessor mProcessor;

    private InputNode mInputNode;
    private OutputNode mOutputNode;

    private final Object mOutputBlock = new Object();

    private int mPendingFrame = 0;
    private final Object mDrawingLock = new Object();

    public GlNode(String name) {
        super(name);

        mInputNode = new InputNode(mName + "#input");
        mOutputNode = new OutputNode(mName + "#output");

        mProcessor = new GlProcessor();
    }

    @Override
    public boolean isOpened() {
        return true;
    }

    @Override
    protected void onOpen() throws IOException {

    }

    @Override
    protected void onClose() throws IOException {
        synchronized (mOutputBlock) {
            mOutputBlock.notifyAll();
        }
    }

    @Override
    protected int onDispatch(List inData, List outData) {
        return 0;
    }

    @Override
    protected int onProcess(List inData, List outData) {
        return 0;
    }

    public InputNode getInputNode() {
        return mInputNode;
    }

    public OutputNode getOutputNode() {
        return mOutputNode;
    }

    public int addWaterMark(String text, int x, int y, int textColor, int textSize) {
        return mProcessor.addWaterMark(text, x, y, textColor, textSize);
    }

    public GlNode updateWaterMarkText(int id, String text) {
        mProcessor.updateWaterMarkText(id, text);
        return this;
    }

    public GlNode updateWaterMarkPosition(int id, int x, int y) {
        mProcessor.updateWaterMarkPosition(id, x, y);
        return this;
    }

    public GlNode updateWaterMarkTextColor(int id, int color) {
        mProcessor.updateWaterMarkTextColor(id, color);
        return this;
    }

    public GlNode updateWaterMarkTextSize(int id, int size) {
        mProcessor.updateWaterMarkTextSize(id, size);
        return this;
    }

    public GlNode capture(String path) {
        return this;
    }

    private class CaptureOutputNode extends  TailNode<SurfaceData> {
        public CaptureOutputNode(String name) {
            super(name);
        }

        @Override
        protected int onProcessData(SurfaceData data) {
            synchronized (mCaptureOutputSurfaceLock) {
                mCaptureOutputData = data;
                mCaptureOutputSurfaceLock.notifyAll();
            }

            // only block this method when is opened.
            synchronized (mOutputBlock) {
                if (isOpened()) {
                    try {
                        mOutputBlock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "[" + mName + "] block pipeline interrupted.", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return pan.RESULT_EOS;
        }

        @Override
        public boolean isOpened() {
            return GlNode.this.isOpened();
        }

        @Override
        protected void onOpen() throws IOException {
            GlNode.this.onOpen();
        }

        @Override
        protected void onClose() throws IOException {
            GlNode.this.onClose();
        }
    }

    private class InputNode extends HeadNode<SurfaceData> {
        private int id = hashCode();

        private InputNode(String name) {
            super(name);
        }

        @Override
        protected SurfaceData onCreateData() {
            return new SurfaceData(id, Data.TYPE_VIDEO);
        }

        @Override
        protected int onBindData(SurfaceData data) {
            synchronized (mOutputSurfaceLock) {
                while (mOutputData == null) {
                    try {
                        mOutputSurfaceLock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "onBindData wait interrupted.");
                    }
                }

                // start processor
                mProcessor.init(mOutputData.surface, mOutputData.width, mOutputData.height);

                // bind data
                data.surface = mProcessor.getInputSurfaceAwait();
                data.width = mOutputData.width;
                data.height = mOutputData.height;
                data.template = mOutputData.template;
            }
            return pan.RESULT_OK;
        }

        @Override
        protected int onReleaseData(SurfaceData data) {
            mProcessor.release();
            return pan.RESULT_EOS;
        }

        @Override
        public boolean isOpened() {
            return GlNode.this.isOpened();
        }

        @Override
        protected void onOpen() throws IOException {
            GlNode.this.onOpen();
        }

        @Override
        protected void onClose() throws IOException {
            GlNode.this.onClose();
        }
    }

    private class OutputNode extends TailNode<SurfaceData> {
        private OutputNode(String name) {
            super(name);
        }

        @Override
        protected int onProcessData(SurfaceData data) {
            synchronized (mOutputSurfaceLock) {
                mOutputData = data;
                mOutputSurfaceLock.notifyAll();
            }

            // only block this method when is opened.
            synchronized (mOutputBlock) {
                if (isOpened()) {
                    try {
                        mOutputBlock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "[" + mName + "] block pipeline interrupted.", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return pan.RESULT_EOS;
        }

        @Override
        public boolean isOpened() {
            return GlNode.this.isOpened();
        }

        @Override
        protected void onOpen() throws IOException {
            GlNode.this.onOpen();
        }

        @Override
        protected void onClose() throws IOException {
            GlNode.this.onClose();
        }
    }

    private static class WaterMarkItem {
        String text;
        int x;
        int y;
        int color;
        int textSize;
        Rect frameRect;
        boolean dirty;

        Rect srcRect = new Rect();
        Rect dstRect = new Rect();
        float[] matrix = new float[16];
        int textureId;
        FloatBuffer vertexArray;

        private WaterMarkItem(String text, int x, int y, int color, int textSize) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.textSize = textSize;
            this.frameRect = null;
            this.dirty = true;
        }

        private void updateVertexArray(Rect frameRect) {
            int frameWidth = frameRect.height();
            int frameHeight = frameRect.width();
            float left = 2.0f * dstRect.left / frameWidth - 1.0f;
            float right = 2.0f * dstRect.right / frameWidth - 1.0f;
            float top = 2.0f * (frameHeight - dstRect.top) / frameHeight - 1.0f;
            float bottom = 2.0f * (frameHeight - dstRect.bottom) / frameHeight - 1.0f;

            vertexArray = GlUtil.createFloatBuffer(new float[]{
                    left, bottom,   // 0 bottom left
                    right, bottom,   // 1 bottom right
                    left, top,   // 2 top left
                    right, top,   // 3 top right
            });
        }

        private void update() {
            // TODO update all items is too heavy
            if (dirty) {
                Paint paint = new Paint();
                paint.setColor(color);
                paint.setTextSize(textSize);

                // save rect
                Paint.FontMetrics metrics = paint.getFontMetrics();
                srcRect.set(
                        0,
                        0,
                        (int) Math.nextUp(paint.measureText(text)),
                        (int) Math.nextUp(metrics.bottom - metrics.top)
                );
                dstRect.set(srcRect);
                dstRect.offset(x, y);

                // update vertex array to target area
                updateVertexArray(frameRect);

                SurfaceTexture surfaceTexture = GlUtil.createSurfaceTexture(
                        textureId,
                        new Size(srcRect.width(), srcRect.height()),
                        null
                );

                // draw text to texture
                Surface surface = new Surface(surfaceTexture);
                Canvas canvas = surface.lockCanvas(srcRect);
                canvas.drawText(text, 0, srcRect.height(), paint);
                surface.unlockCanvasAndPost(canvas);
                surfaceTexture.updateTexImage();
                surface.release(); // surface is useless now

                // get matrix
                surfaceTexture.getTransformMatrix(matrix);
                surfaceTexture.release();
            }
        }

        private void setText(String text) {
            this.text = text;
            this.dirty = true;
        }

        private void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
            this.dirty = true;
        }

        private void setTextSize(int size) {
            this.textSize = size;
            this.dirty = true;
        }

        private void setTextColor(int color) {
            this.color = color;
            this.dirty = true;
        }

        private void setFrameRect(Rect rect) {
            this.frameRect = rect;
            this.dirty = true;
        }

        private void init(int texId, Rect frameRect) {
            // create texture
            textureId = texId;
            setFrameRect(frameRect);
            update();
        }

        private void release() {
            GlUtil.releaseTexture(textureId);
        }
    }

    private class GlProcessor {
        EglCore core;
        WindowSurface windowSurface;
        FullFrameRect frame;
        private HandlerThread mThread;
        private Handler mHandler;
        private Rect mFrameRect = new Rect();

        private float[] mInMatrix = new float[16];
        private int mInTexture = 0;
        private SurfaceTexture mInSurfaceTexture = null;
        private Surface mInSurface = null;
        private final Object mInputSurfaceLock = new Object();
        private final List<WaterMarkItem> mWaterMarkItems = new ArrayList<>();

        private static final int MSG_INIT = 0;
        private static final int MSG_EXIT = 1;
        private static final int MSG_DRAW = 2;

        GlProcessor() {
            mThread = new HandlerThread("StreamThread#" + hashCode());
            mThread.start();
            mHandler = new Handler(mThread.getLooper(), this::handleMessage);
        }

        void init(Surface surface, int width, int height) {
            mHandler.obtainMessage(MSG_INIT, width, height, surface).sendToTarget();
        }

        void release() {
            mHandler.sendEmptyMessage(MSG_EXIT);
            mThread.quitSafely();
        }

        void draw(int texture, float[] inMatrix) {
            mHandler.obtainMessage(MSG_DRAW, texture, 0, inMatrix).sendToTarget();
        }

        int addWaterMark(String text, int x, int y, int textColor, int textSize) {
            synchronized (mWaterMarkItems) {
                mWaterMarkItems.add(new WaterMarkItem(text, x, y, textColor, textSize));
                return mWaterMarkItems.size() - 1;
            }
        }

        void updateWaterMarkText(int id, String text) {
            synchronized (mWaterMarkItems) {
                mWaterMarkItems.get(id).setText(text);
            }
        }

        void updateWaterMarkPosition(int id, int x, int y) {
            synchronized (mWaterMarkItems) {
                mWaterMarkItems.get(id).setPosition(x, y);
            }
        }

        void updateWaterMarkTextColor(int id, int color) {
            synchronized (mWaterMarkItems) {
                mWaterMarkItems.get(id).setTextColor(color);
            }
        }

        void updateWaterMarkTextSize(int id, int size) {
            synchronized (mWaterMarkItems) {
                mWaterMarkItems.get(id).setTextSize(size);
            }
        }

        Surface getInputSurfaceAwait() {
            synchronized (mInputSurfaceLock) {
                while (mInSurface == null) {
                    try {
                        mInputSurfaceLock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "wait input surface interrupted.");
                    }
                }
            }

            return mInSurface;
        }

        private boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT: {
                    mFrameRect.set(0, 0, msg.arg1, msg.arg2);

                    Log.i("==MyTest==", "MSG_INIT " + hashCode() + ", size: [" + msg.arg1 + ", " + msg.arg2 + "]");
                    synchronized (mWaterMarkItems) {
                        // create openGl context
                        core = new EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_RECORDABLE);
                        windowSurface = new WindowSurface(core, (Surface) msg.obj, false);
                        windowSurface.makeCurrent();
                        frame = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

                        // create input texture & input surface
                        mInTexture = frame.createTextureObject();
                        mInSurfaceTexture = GlUtil.createSurfaceTexture(
                                mInTexture,
                                new Size(mFrameRect.width(), mFrameRect.height()),
                                surfaceTexture -> {
                                    synchronized (mDrawingLock) {
                                        mPendingFrame++;

                                        mInSurfaceTexture.getTransformMatrix(mInMatrix);
                                        mProcessor.draw(mInTexture, mInMatrix);
                                    }
                                });
                        mInSurface = new Surface(mInSurfaceTexture);
                        synchronized (mInputSurfaceLock) {
                            mInputSurfaceLock.notifyAll();
                        }

                        // init water marks
                        for (WaterMarkItem item : mWaterMarkItems) {
                            item.init(frame.createTextureObject(), mFrameRect);
                        }

                        // enable alpha
                        GLES20.glEnable(GLES20.GL_BLEND);
                        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    }
                } return true;
                case MSG_EXIT: {
                    Log.i("==MyTest==", "MSG_EXIT " + hashCode());
                    frame.release(true);
                    windowSurface.release();
                    core.release();

                    // release input surface
                    mInSurfaceTexture.release();
                    mInSurface.release();
                    GlUtil.releaseTexture(mInTexture);

                    // release water marks
                    synchronized (mWaterMarkItems) {
                        for (WaterMarkItem item : mWaterMarkItems) {
                            item.release();
                        }
                    }
                } return true;
                case MSG_DRAW: {
                    synchronized (mDrawingLock) {
                        if (mPendingFrame > 0) {
                            mPendingFrame = 0;

                            // load input image from input surface to texture
                            mInSurfaceTexture.updateTexImage();

                            // draw source image
                            Log.i("==MyTest==", "main id: " + msg.arg1);
                            frame.drawFrame(msg.arg1, (float[]) msg.obj);

                            // draw water mark
                            synchronized (mWaterMarkItems) {
                                for (WaterMarkItem item : mWaterMarkItems) {
                                    Log.i("==MyTest==", "water mark id: " + item.textureId);
                                    item.update();
                                    frame.drawFrame(item.textureId, item.matrix, item.vertexArray);
                                }
                            }

                            windowSurface.swapBuffers();
                        } else {
                            Log.w(TAG, "skip frame");
                        }
                    }
                } return true;
                default:
                    return false;
            }
        }
    }
}
