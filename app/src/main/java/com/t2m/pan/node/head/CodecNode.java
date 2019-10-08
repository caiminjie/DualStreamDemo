package com.t2m.pan.node.head;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.t2m.pan.data.SurfaceData;
import com.t2m.pan.pan;
import com.t2m.pan.util.RetrySleepHelper;
import com.t2m.pan.Data;
import com.t2m.pan.Node;
import com.t2m.pan.data.ByteBufferData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

public class CodecNode extends HeadNode<Data> {
    private static final String TAG = CodecNode.class.getSimpleName();

    public static final int TYPE_BYTE_BUFFER = 0;
    public static final int TYPE_SURFACE = 1;

    private final Object mOpenLock = new Object();

    private int mMediaType;
    private MediaCodec mCodec;
    private boolean mIsEncoder;
    private MediaFormat mFormat;
    private int mCloseTimes = 0;

    private boolean mInputEos;
    private final Object mInputEosLock = new Object();

    private Surface mInputSurface;
    private Surface mOutputSurface;
    private int mInputType;
    private int mOutputType;
    private Node<? extends Data, ? extends Data> mInputNode;
    private Node<? extends Data, ? extends Data> mOutputNode;

    public CodecNode(String name, boolean isEncoder, MediaFormat format, int inputType, int outputType) {
        super(name);

        mIsEncoder = isEncoder;
        mFormat = format;

        if ((mInputType = inputType) == TYPE_BYTE_BUFFER) {
            mInputNode = new InputNode(mName + "#InputNode");
        } else {
            mInputNode = new InputSurfaceNode(mName + "InputSurfaceNode");
        }
        if ((mOutputType = outputType) == TYPE_BYTE_BUFFER) {
            mOutputNode = new OutputNode(mName + "#OutputNode");
        } else {
            throw new RuntimeException("not implemented yet");
        }

        // verify format
        if (mFormat == null) {
            throw new InvalidParameterException("[" + mName + "] null format");
        }
        if (mFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
            mMediaType = ByteBufferData.TYPE_VIDEO;
        } else {
            mMediaType = ByteBufferData.TYPE_AUDIO;
        }
    }

    @Override
    public boolean isOpened() {
        return mCodec != null;
    }

    @Override
    protected void onOpen() throws IOException {
        Log.i("==MyTest==", "[" + mName + "] CodecNode.onOpen()# begin");
        if (mIsEncoder) {
            mCodec = MediaCodec.createEncoderByType(mFormat.getString(MediaFormat.KEY_MIME));
            mCodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (mInputType == TYPE_SURFACE) {
                if (mInputSurface == null) {
                    mInputSurface = mCodec.createInputSurface();    // API >= 18
                } else {
                    mCodec.setInputSurface(mInputSurface);
                }
            }
        } else {
            mCodec = MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));
            mCodec.configure(mFormat, null, null, 0);
            if (mOutputType == TYPE_SURFACE) {
                if (mOutputSurface == null) {
                    throw new RuntimeException("Output type is surface, but output surface is not set.");
                } else {
                    mCodec.setOutputSurface(mOutputSurface);
                }
            }
        }
        mCodec.start();

        mCloseTimes = 2;
        mInputEos = false;
        Log.i("==MyTest==", "[" + mName + "] CodecNode.onOpen()# end");
    }

    @Override
    protected void onClose() throws IOException {
        Log.i("==MyTest==", "[" + mName + "] CodecNode.onClose()# begin");
        mCloseTimes --;
        if (mCloseTimes > 0) {
            Log.i(TAG, "[" + mName + "] CodecNode.onClose()# cannot do real close due to remaining close time: " + mCloseTimes);
            return;
        } else {
            Log.i(TAG, "[" + mName + "] CodecNode.onClose()# do real close");
        }

        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        Log.i("==MyTest==", "[" + mName + "] CodecNode.onClose()# end");

    }

    @Override
    protected Data onCreateData() {
        return null;
    }

    @Override
    protected int onBindData(Data data) {
        return pan.RESULT_OK;
    }

    @Override
    protected int onReleaseData(Data data) {
        return pan.RESULT_OK;
    }

    private void inputEos(boolean eos) {
        synchronized (mInputEosLock) {
            Log.i("==MyTest==", "[" + mName + "] inputEos##### " + eos, new Exception());
            mInputEos = eos;
        }
    }

    private boolean inputEos() {
        synchronized (mInputEosLock) {
            return mInputEos;
        }
    }

    public CodecNode setInputSurface(Surface surface) {
        mInputSurface = surface;
        return this;
    }

    public CodecNode setOutputSurface(Surface surface) {
        mOutputSurface = surface;
        return this;
    }

    public Node<? extends Data, ? extends Data> getInputNode() {
        return mInputNode;
    }

    public Node<? extends Data, ? extends Data> getOutputNode() {
        return mOutputNode;
    }

    private class InputNode extends HeadNode<ByteBufferData> {
        private int mId = hashCode();
        private RetrySleepHelper mRetryHelper;

        private InputNode(String name) {
            super(name);

            mRetryHelper = new RetrySleepHelper(mName + "#sleep");
        }

        @Override
        public boolean isOpened() {
            synchronized (mOpenLock) {
                return CodecNode.this.isOpened();
            }
        }

        @Override
        protected void onOpen() throws IOException {
            Log.i("==MyTest==", "[" + mName + "] InputNode.onOpen() begin");
            synchronized (mOpenLock) {
                CodecNode.this.open();
            }
            Log.i("==MyTest==", "[" + mName + "] InputNode.onOpen()# end");

        }

        @Override
        protected void onClose() throws IOException {
            synchronized (mOpenLock) {
                CodecNode.this.close();
            }
        }

        protected ByteBufferData onCreateData() {
            return new ByteBufferData(mId, mMediaType);
        }

        protected int onBindData(ByteBufferData data) {
            int index = MediaCodec.INFO_TRY_AGAIN_LATER;

            // get input buffer
            mRetryHelper.begin();
            while (!Thread.currentThread().isInterrupted() && (index = mCodec.dequeueInputBuffer(10000)) < 0) {
                mRetryHelper.sleep();
            }
            mRetryHelper.end();

            if (index < 0) {
                // interrupt
                data.markEos();
                return pan.RESULT_EOS;
            }

            // bind
            ByteBuffer buffer = mCodec.getInputBuffer(index);
            assert buffer != null;
            buffer.clear();
            data.buffer(buffer);
            data.index(index);
            return pan.RESULT_OK;
        }

        protected int onReleaseData(ByteBufferData data) {
            // queue buffer
            int index = data.index();
            if (index >= 0) {
                // get info
                MediaCodec.BufferInfo info = data.info();

                // Log.d(TAG, "[" + mName + "] releaseInputData: isConfig = " + data.isConfig() +", info :" + info);
                if (data.isConfig()) {
                    mCodec.queueInputBuffer(index, 0, 0, 0, 0); // we do not accept config
                } else {
                    mCodec.queueInputBuffer(index, info.offset, info.size, info.presentationTimeUs, info.flags);
                }

                data.index(-1);
            }

            // set input inputEos
            if (data.isEos()) {
                inputEos(true);
                return pan.RESULT_EOS;
            }

            return pan.RESULT_OK;
        }
    }

    private class OutputNode extends HeadNode<ByteBufferData> {
        private int mId = hashCode();
        private RetrySleepHelper mRetryHelper;

        private OutputNode(String name) {
            super(name);

            mRetryHelper = new RetrySleepHelper(mName + "#sleep");
        }

        @Override
        public boolean isOpened() {
            synchronized (mOpenLock) {
                return CodecNode.this.isOpened();
            }
        }

        @Override
        protected void onOpen() throws IOException {
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onOpen() begin");
            synchronized (mOpenLock) {
                CodecNode.this.open();
            }
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onOpen() end");
        }

        @Override
        protected void onClose() throws IOException {
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onClose() begin");
            synchronized (mOpenLock) {
                CodecNode.this.close();
            }
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onClose() end");

        }

        protected ByteBufferData onCreateData() {
            return new ByteBufferData(mId, mMediaType);
        }

        protected int onBindData(ByteBufferData data) {
            int index = MediaCodec.INFO_TRY_AGAIN_LATER;
            MediaCodec.BufferInfo info = data.info();
            mRetryHelper.begin();
            while (!Thread.currentThread().isInterrupted() && (index = mCodec.dequeueOutputBuffer(info, 10000)) < 0) {
                if (inputEos()) { // currently input is already eos. output should also be eos.
                    Log.i("==MyTest==", "[" + mName + "] output##### eos");
                    data.index(-1);
                    return pan.RESULT_EOS;
                }
                mRetryHelper.sleep();
            }
            mRetryHelper.end();

            if (index < 0) {
                // interrupt
                data.markEos();
                return pan.RESULT_EOS;
            }

            // bind data
            ByteBuffer buffer = mCodec.getOutputBuffer(index);
            assert buffer != null;
            buffer.position(info.offset);

            data.index(index);
            data.buffer(buffer);
            data.format(mCodec.getOutputFormat());
            return pan.RESULT_OK;
        }

        protected int onReleaseData(ByteBufferData data) {
            // release buffer
            int index = data.index();
            if (index >= 0) {
                mCodec.releaseOutputBuffer(index, false);
                data.index(-1);
            }

            return pan.RESULT_OK;
        }
    }

    private class InputSurfaceNode extends HeadNode<SurfaceData> {
        private int mId = hashCode();
        private RetrySleepHelper mRetryHelper;

        private InputSurfaceNode(String name) {
            super(name);

            mRetryHelper = new RetrySleepHelper(mName + "#sleep");
        }

        @Override
        public boolean isOpened() {
            synchronized (mOpenLock) {
                return CodecNode.this.isOpened();
            }
        }

        @Override
        protected void onOpen() throws IOException {
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onOpen() begin");
            synchronized (mOpenLock) {
                CodecNode.this.open();
            }
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onOpen() end");
        }

        @Override
        protected void onClose() throws IOException {
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onClose() begin");
            synchronized (mOpenLock) {
                CodecNode.this.close();
            }
            Log.i("==MyTest==", "[" + mName + "] OutputNode.onClose() end");
        }

        protected SurfaceData onCreateData() {
            return new SurfaceData(mId, mMediaType);
        }

        protected int onBindData(SurfaceData data) {
            // wait for surface ready
            mRetryHelper.begin();
            while (!Thread.currentThread().isInterrupted() && mInputSurface == null) {
                mRetryHelper.sleep();
            }
            mRetryHelper.end();

            if (mInputSurface == null) {
                // interrupt
                inputEos(true);
                return pan.RESULT_EOS;
            }

            // bind
            data.template = SurfaceData.TYPE_RECORD;
            data.surface = mInputSurface;
            return pan.RESULT_OK;
        }

        protected int onReleaseData(SurfaceData data) {
            Log.d(TAG, "[" + mName + "] releaseInputDataSurface() begin");
            data.surface.release();
            data.surface = null;
            Log.d(TAG, "[" + mName + "] releaseInputDataSurface() end");

            inputEos(true);

            return pan.RESULT_EOS;
        }
    }
}
