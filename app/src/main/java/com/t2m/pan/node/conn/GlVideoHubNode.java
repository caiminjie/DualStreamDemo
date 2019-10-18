package com.t2m.pan.node.conn;

import android.content.Context;
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
import com.t2m.pan.util.gles.Texture2dProgram;
import com.t2m.pan.util.gles.WindowSurface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GlVideoHubNode extends ConnNode<SurfaceData, SurfaceData> {
    private static final String TAG = GlVideoHubNode.class.getSimpleName();

    private Size mInputSize = new Size(1080, 1920);

    private InputNode mInputNode;
    private OutputNode mOutputNode;
    private StreamProcessor mStreamProcessor;

    public GlVideoHubNode(String name) {
        super(name);

        mStreamProcessor = new StreamProcessor();
        mInputNode = new InputNode(mName + "#Input", mStreamProcessor);
        mOutputNode = new OutputNode(mName + "#Output", mStreamProcessor);
    }

    @Override
    public boolean isOpened() {
        return mStreamProcessor.isStreamProcessReady();
    }

    @Override
    protected void onOpen() throws IOException {
        mStreamProcessor.initStreamProcess();
    }

    @Override
    protected void onClose() throws IOException {
        mStreamProcessor.destroyStreamProcess();
        mOutputNode.unblock();
    }

    @Override
    protected int onDispatch(List<SurfaceData> inData, List<SurfaceData> outData) {
        return 0;
    }

    @Override
    protected int onProcess(List<SurfaceData> inData, List<SurfaceData> outData) {
        return 0;
    }

    public Size inputSize() {
        return mInputSize;
    }

    public void inputSize(Size size) {
        mInputSize = size;
    }

    public void inputSize(int width, int height) {
        mInputSize = new Size(width, height);
    }

    public HeadNode<SurfaceData> getInputNode() {
        return mInputNode;
    }

    public TailNode<SurfaceData> getOutputNode() {
        return mOutputNode;
    }

    public static Size bestSurfaceSize(Size[] availableSizes) {
        Size bestSize = null;
        int maxSize = 0;
        for (Size size : availableSizes) {
            int s = size.getWidth() * size.getHeight();
            if (s > maxSize) {
                bestSize = size;
                maxSize = s;
            }
        }
        return bestSize;
    }

    //////////////////////////////
    // stream processor
    private class StreamProcessor {
        private final Object mStreamProcessLock = new Object();
        private int mPendingFrames = 0;
        private List<Stream> mStreams = new ArrayList<>();
        private HandlerThread mStreamThread = null;
        private Handler mStreamHandler = null;
        private int mInputTexture;
        private SurfaceTexture mInputSurfaceTexture;
        private Surface mInputSurface = null;
        private final Object mSendInputSurfaceLock = new Object();
        private float[] mInMatrix = new float[16];
        private final Object mInputDirtyLock = new Object();
        private boolean mInputDirty = false;
        private int mDrawingCount = 0;
        private static final int MSG_FRAME_AVAILABLE = 0;
        private static final int MSG_INIT = 1;
        private static final int MSG_EXIT = 2;
        private static final int MSG_ADD_STREAM = 3;
        private static final int MSG_CLEAR_STREAM = 4;

        private int createTexture() {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            return textures[0];
        }

        private SurfaceTexture createSurfaceTexture(int texture, Size size, SurfaceTexture.OnFrameAvailableListener listener) {
            SurfaceTexture surfaceTexture = new SurfaceTexture(texture);
            surfaceTexture.detachFromGLContext();
            surfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            surfaceTexture.setOnFrameAvailableListener(listener);
            return surfaceTexture;
        }

        private boolean handleStreamMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT: {
                    Log.i("==MyTest==", "process MSG_INIT " + hashCode());
                    // create input surface
                    mInputTexture = createTexture();
                    mInputSurfaceTexture = createSurfaceTexture(
                            mInputTexture, mInputSize,
                            tex -> {
                                synchronized (mStreamProcessLock) {
                                    mPendingFrames++;
                                    //mStreamProcessLock.notifyAll();
                                    mStreamHandler.sendEmptyMessage(MSG_FRAME_AVAILABLE);
                                }
                            });

                    // save surface for input
                    synchronized (mSendInputSurfaceLock) {
                        mInputSurface = new Surface(mInputSurfaceTexture);
                        mSendInputSurfaceLock.notifyAll();
                    }

                }
                return true;
                case MSG_EXIT: {
                    Log.i("==MyTest==", "process MSG_EXIT " + hashCode());
                    // clear streams
                    for (Stream stream : mStreams) {
                        stream.release();
                    }
                    mStreams.clear();

                    // release surface texture
                    mInputSurfaceTexture.release();

                    synchronized (mSendInputSurfaceLock) {
                        mInputSurface = null;
                    }
                }
                return true;
                case MSG_FRAME_AVAILABLE: {
                    synchronized (mStreamProcessLock) {
                        if (mPendingFrames > 0) {
                            if (mPendingFrames > 1) {
                                Log.w(TAG, "" + (mPendingFrames - 1) + " frames dropped");
                            }
                            mPendingFrames = 0;

                            synchronized (mInputDirtyLock) {
                                mInputDirty = true;
                            }

                            // fill stream data
                            mInputSurfaceTexture.getTransformMatrix(mInMatrix);
                            for (Stream stream : mStreams) {
                                stream.draw(mInputTexture, mInMatrix);
                            }
                        }
                    }
                }
                return true;
                case MSG_ADD_STREAM: {
                    Log.i("==MyTest==", "process MSG_ADD_STREAM " + hashCode());
                    synchronized (mStreamProcessLock) {
                        mStreams.add(new Stream(this, (Surface) msg.obj));
                    }
                }
                return true;
                case MSG_CLEAR_STREAM: {
                    Log.i("==MyTest==", "process MSG_CLEAR_STREAM " + hashCode());
                    synchronized (mStreamProcessLock) {
                        for (Stream stream : mStreams) {
                            stream.release();
                        }
                        mStreams.clear();
                    }
                }
                return true;
                default:
                    return false;
            }
        }

        private boolean isStreamProcessReady() {
            synchronized (mStreamProcessLock) {
                return mStreamHandler != null;
            }
        }

        private void initStreamProcess() {
            synchronized (mStreamProcessLock) {
                if (mStreamHandler == null) {
                    mStreamThread = new HandlerThread("StreamThread");
                    mStreamThread.start();

                    mStreamHandler = new Handler(
                            mStreamThread.getLooper(),
                            this::handleStreamMessage);
                    mStreamHandler.sendEmptyMessage(MSG_INIT);
                }
            }
        }

        private void destroyStreamProcess() {
            synchronized (mStreamProcessLock) {
                if (mStreamHandler != null) {
                    mStreamHandler.sendEmptyMessage(MSG_EXIT);
                    mStreamThread.quitSafely();

                    mStreamThread = null;
                    mStreamHandler = null;
                }
            }
        }

        private void addStream(final SurfaceData surfaceData) {
            synchronized (mStreamProcessLock) {
                if (mStreamHandler != null) {
                    mStreamHandler.obtainMessage(MSG_ADD_STREAM, surfaceData.surface).sendToTarget();
                }
            }
        }

        private void clearStream() {
            synchronized (mStreamProcessLock) {
                if (mStreamHandler != null) {
                    mStreamHandler.sendEmptyMessage(MSG_CLEAR_STREAM);
                }
            }
        }

        private Surface getInputSurfaceAwait() {
            synchronized (mSendInputSurfaceLock) {
                while (mInputSurface == null) {
                    try {
                        mSendInputSurfaceLock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "getInputSurfaceLocked()# wait interrupted");
                    }
                }
                return mInputSurface;
            }
        }

        // called on stream draw thread
        private void beginDraw() {
            synchronized (mInputDirtyLock) {
                if (mInputDirty) {
                    while (mDrawingCount > 0) {
                        try {
                            mInputDirtyLock.wait();
                        } catch (InterruptedException e) {
                            //
                        }
                    }

                    if (mInputDirty) {
                        mInputDirty = false;
                        mInputSurfaceTexture.attachToGLContext(mInputTexture);
                        mInputSurfaceTexture.updateTexImage();
                        mInputSurfaceTexture.detachFromGLContext();
                    }
                }

                mDrawingCount ++;
            }
        }

        private void endDraw() {
            synchronized (mInputDirtyLock) {
                mDrawingCount --;
                mInputDirtyLock.notifyAll();
            }
        }
    }

    private static class Stream {
        private StreamProcessor mProcessor;
        EglCore core;
        WindowSurface windowSurface;
        FullFrameRect frame;
        private HandlerThread mThread;
        private Handler mHandler;

        private static final int MSG_INIT = 0;
        private static final int MSG_EXIT = 1;
        private static final int MSG_DRAW = 2;

        Stream(StreamProcessor processor, Surface surface) {
            mProcessor = processor;

            mThread = new HandlerThread("StreamThread#" + surface.hashCode());
            mThread.start();
            mHandler = new Handler(mThread.getLooper(), this::handleMessage);

            mHandler.obtainMessage(MSG_INIT, surface).sendToTarget();
        }

        void release() {
            mHandler.sendEmptyMessage(MSG_EXIT);
            mThread.quitSafely();
        }

        void draw(int texture, float[] inMatrix) {
            mHandler.obtainMessage(MSG_DRAW, texture, 0, inMatrix).sendToTarget();
        }

        private boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT: {
                    Log.i("==MyTest==", "MSG_INIT " + hashCode());
                    core = new EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_RECORDABLE);
                    windowSurface = new WindowSurface(core, (Surface)msg.obj, false);
                    windowSurface.makeCurrent();
                    frame = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
                } return true;
                case MSG_EXIT: {
                    Log.i("==MyTest==", "MSG_EXIT " + hashCode());
                    frame.release(true);
                    windowSurface.release();
                    core.release();
                } return true;
                case MSG_DRAW: {
                    mProcessor.beginDraw();

                    frame.drawFrame(msg.arg1, (float[])msg.obj);
                    windowSurface.swapBuffers();

                    mProcessor.endDraw();
                } return true;
                default:
                    return false;
            }
        }
    }

    private class InputNode extends HeadNode<SurfaceData> {
        private int id = hashCode();
        private StreamProcessor mProcessor;

        private InputNode(String name, StreamProcessor processor) {
            super(name);

            mProcessor = processor;
        }

        @Override
        protected SurfaceData onCreateData() {
            return new SurfaceData(id, Data.TYPE_VIDEO);
        }

        @Override
        protected int onBindData(SurfaceData data) {
            data.surface = mProcessor.getInputSurfaceAwait();
            data.template = SurfaceData.TYPE_RECORD;
            return pan.RESULT_OK;
        }

        @Override
        protected int onReleaseData(SurfaceData data) {
            data.surface.release();
            data.surface = null;
            return pan.RESULT_EOS;
        }

        @Override
        public boolean isOpened() {
            return GlVideoHubNode.this.isOpened();
        }

        @Override
        protected void onOpen() throws IOException {
            GlVideoHubNode.this.onOpen();
        }

        @Override
        protected void onClose() throws IOException {
            GlVideoHubNode.this.onClose();
        }
    }

    private class OutputNode extends TailNode<SurfaceData> {
        private final Object mBlockProcess = new Object();

        private StreamProcessor mProcessor;

        private OutputNode(String name, StreamProcessor processor) {
            super(name);

            mProcessor = processor;
        }

        @Override
        protected int onProcessData(SurfaceData data) {
            mProcessor.addStream(data);

            // only block this method when is opened.
            synchronized (mBlockProcess) {
                if (isOpened()) {
                    try {
                        mBlockProcess.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "[" + mName + "] block pipeline interrupted.", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }

            Log.d(TAG, "[" + mName + "] unblock pipeline");
            return pan.RESULT_EOS; // only run once
        }

        @Override
        public boolean isOpened() {
            return GlVideoHubNode.this.isOpened();
        }

        @Override
        protected void onOpen() throws IOException {
            GlVideoHubNode.this.onOpen();
        }

        @Override
        protected void onClose() throws IOException {
            GlVideoHubNode.this.onClose();
        }

        private void unblock() {
            synchronized (mBlockProcess) {
                mBlockProcess.notifyAll();
            }
        }
    }
}
