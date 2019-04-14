/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.nodes;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.t2m.flow.data.ByteBufferData;
import com.t2m.flow.data.Data;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;
import com.t2m.flow.path.Plug;
import com.t2m.flow.path.Slot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

/**
 * Codec Node for encode & decode
 */
public class CodecNode extends Node {
    private static final String TAG = CodecNode.class.getSimpleName();

    private static final String KEY_CODEC_OUTPUT_INDEX = "key-codec-output-index";
    private static final String KEY_CODEC_INPUT_INDEX = "key-codec-input-index";

    private MediaCodec mCodec;
    private boolean mIsEncoder;
    private MediaFormat mFormat;
    private Surface mSurface;

    private Plug mPlugReader = new Plug() {
        @Override
        public int getData(DataHolder holder) {
            return plugReaderGetData(holder);
        }

        @Override
        public int release(Data data) {
            return plugReaderRelease(data);
        }
    };
    private Plug mPlugWriter = new Plug() {
        @Override
        public int getData(DataHolder holder) {
            return plugWriterGetData(holder);
        }

        @Override
        public int release(Data data) {
            return plugWriterRelease(data);
        }

        @Override
        public boolean bypass() {
            return false;
        }
    };

    public CodecNode(String name, boolean isEncoder, MediaFormat format)  {
        super(name);

        mIsEncoder = isEncoder;
        mFormat = format;

        // verify format
        if (mFormat == null) {
            throw new InvalidParameterException("[" + mName + "] null format");
        }
    }

    @Override
    public Node open() throws IOException {
        if (isOpened()) {
            return this; // already opened
        }
        if (mIsEncoder) {
            mCodec = MediaCodec.createEncoderByType(mFormat.getString(MediaFormat.KEY_MIME));
            mCodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (MediaFormat.MIMETYPE_VIDEO_AVC.equals(mFormat.getString(MediaFormat.KEY_MIME))){
                mSurface = mCodec.createInputSurface();	// API >= 18
            }
        } else {
            mCodec = MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));
            mCodec.configure(mFormat, null, null, 0);
        }
        mCodec.start();
        return this;
    }

    public Surface getInputSurface() {
        return mSurface;
    }

    @Override
    public boolean isOpened() {
        return mCodec != null;
    }

    @Override
    public void close() throws IOException {
        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    @Override
    public Plug plugReader(int index) {
        return mPlugReader;
    }

    @Override
    public Plug plugWriter(int index) {
        return mPlugWriter;
    }

    @Deprecated
    @Override
    public Slot slotReader(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    @Deprecated
    @Override
    public Slot slotWriter(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    private int plugReaderGetData(DataHolder holder) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        // onCreate buffer info & get output index & clear config format
        ByteBufferData data = ByteBufferData.create();
        MediaCodec.BufferInfo info = data.setBufferInfo(new MediaCodec.BufferInfo());
        int encoderStatus = mCodec.dequeueOutputBuffer(info, 10000);
        Log.d(TAG, "[" + mName + "] plugReaderGetData: encoderStatus = " + encoderStatus);
        int outputIndex = setOutputIndex(data, encoderStatus);

        if (outputIndex < 0) {
            data.release();
            return RESULT_RETRY;
        } else {
            // get buffer
            ByteBuffer buffer = data.setBuffer(mCodec.getOutputBuffer(outputIndex));
            assert buffer != null;

            // set config data if necessary
            if (data.isConfig()) {
                data.setConfigFormat(mCodec.getOutputFormat());
            }

            // prepare buffer
            buffer.position(info.offset);
            holder.data = data;
            return RESULT_OK;
        }
    }

    private int plugReaderRelease(Data data) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        int index = getOutputIndex(data);
        if (index > 0) {
            mCodec.releaseOutputBuffer(index, false);
        }

        return RESULT_OK;
    }

    private int plugWriterGetData(DataHolder holder) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        ByteBufferData data = ByteBufferData.create();
        int inputIndex = setInputIndex(data, mCodec.dequeueInputBuffer(10000));
        Log.d(TAG, "[" + mName + "] plugWriterGetData: inputIndex = " + inputIndex + ", data = " + data);
        if (inputIndex < 0) {
            data.release();
            return RESULT_RETRY;
        } else {
            data.setBufferInfo(new MediaCodec.BufferInfo());
            ByteBuffer buffer = data.setBuffer(mCodec.getInputBuffer(inputIndex));
            assert buffer != null;

            buffer.clear();
            holder.data = data;
            return RESULT_OK;
        }
    }

    private int plugWriterRelease(Data data) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        ByteBufferData d = (ByteBufferData) data;

        // get index
        int index = getInputIndex(d);
        if (index > 0) {
            // get info
            MediaCodec.BufferInfo info = d.getBufferInfo();
            assert info != null;

            Log.d(TAG, "[" + mName + "] plugWriterRelease: isConfig = " + d.isConfig() +", info :" + info);
            if (d.isConfig()) {
                mCodec.queueInputBuffer(index, 0, 0, 0, 0); // we do not accept config
            } else {
                mCodec.queueInputBuffer(index, info.offset, info.size, info.presentationTimeUs, info.flags);
            }
        }

        return RESULT_OK;
    }

    private static int setOutputIndex(Data data, int index) {
        return data.set(KEY_CODEC_OUTPUT_INDEX, index);
    }

    private static int getOutputIndex(Data data) {
        return data.get(KEY_CODEC_OUTPUT_INDEX, -1000);
    }

    private static int setInputIndex(Data data, int index) {
        return data.set(KEY_CODEC_INPUT_INDEX, index);
    }

    private static int getInputIndex(Data data) {
        return data.get(KEY_CODEC_INPUT_INDEX, -1000);
    }
}
