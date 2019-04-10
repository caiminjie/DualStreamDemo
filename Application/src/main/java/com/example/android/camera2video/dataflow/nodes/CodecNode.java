/* Copyright (C) 2018 Tcl Corporation Limited */
package com.example.android.camera2video.dataflow.nodes;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.example.android.camera2video.dataflow.data.AudioData;
import com.example.android.camera2video.dataflow.data.Data;
import com.example.android.camera2video.dataflow.node.BufferedReader;
import com.example.android.camera2video.dataflow.node.BufferedWriter;
import com.example.android.camera2video.dataflow.node.DataNode;
import com.example.android.camera2video.dataflow.node.DirectReader;
import com.example.android.camera2video.dataflow.node.DirectWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

/**
 * Codec Node for encode & decode
 */
public class CodecNode extends DataNode {
    private static final String TAG = CodecNode.class.getSimpleName();

    private static final String KEY_CODEC_OUTPUT_INDEX = "key-codec-output-index";
    private static final String KEY_CODEC_INPUT_INDEX = "key-codec-input-index";

    private MediaCodec mCodec;
    private boolean mIsEncoder;
    private MediaFormat mFormat;
    private Surface mSurface;

    private BufferedReader mBufferedReader = new BufferedReader() {
        @Override
        public int readBegin(Data data) {
            return CodecNode.this.readBegin(data);
        }

        @Override
        public int readEnd(Data data) {
            return CodecNode.this.readEnd(data);
        }
    };
    private BufferedWriter mBufferedWriter = new BufferedWriter() {
        @Override
        public int writeBegin(Data data) {
            return CodecNode.this.writeBegin(data);
        }

        @Override
        public int writeEnd(Data data) {
            return CodecNode.this.writeEnd(data);
        }
    };

    public CodecNode(boolean isEncoder, MediaFormat format)  {
        mIsEncoder = isEncoder;
        mFormat = format;

        // verify format
        if (mFormat == null) {
            throw new InvalidParameterException("null format");
        }
    }

    @Override
    public DataNode open() throws IOException {
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
    public BufferedReader getBufferedReader() {
        return mBufferedReader;
    }

    @Override
    public BufferedWriter getBufferedWriter() {
        return mBufferedWriter;
    }

    @Deprecated
    @Override
    public DirectReader getDirectReader() {
        throw new InvalidParameterException("method not supported");
    }

    @Deprecated
    @Override
    public DirectWriter getDirectWriter() {
        throw new InvalidParameterException("method not supported");
    }

    private int readBegin(Data data) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        // create buffer info & get output index & clear config format
        MediaCodec.BufferInfo info = AudioData.setBufferInfo(data, new MediaCodec.BufferInfo());
        int encoderStatus = 0;
        encoderStatus = mCodec.dequeueOutputBuffer(info, 10000);
        Log.d(TAG, "readBegin: encoderStatus = " + encoderStatus);
        int outputIndex = setOutputIndex(data, encoderStatus);

        if (outputIndex < 0) {
            return RESULT_RETRY;
        } else {
            // get buffer
            ByteBuffer buffer = AudioData.setBuffer(data, mCodec.getOutputBuffer(outputIndex));
            assert buffer != null;

            // set config data if necessary
            if (AudioData.isConfig(data)) {
                AudioData.setConfigFormat(data, mCodec.getOutputFormat());
            }

            // prepare buffer
            buffer.position(info.offset);
            return RESULT_OK;
        }
    }

    private int readEnd(Data data) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        int index = getOutputIndex(data);
        if (index > 0) {
            mCodec.releaseOutputBuffer(index, false);
        }
        return RESULT_OK;
    }

    private int writeBegin(Data data) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }


        int inputIndex = setInputIndex(data, mCodec.dequeueInputBuffer(10000));
        Log.d(TAG, "writeBegin: inputIndex = " + inputIndex + ", data = " + data);
        if (inputIndex < 0) {
            return RESULT_RETRY;
        } else {
            AudioData.setBufferInfo(data, new MediaCodec.BufferInfo());
            ByteBuffer buffer = AudioData.setBuffer(data, mCodec.getInputBuffer(inputIndex));
            assert buffer != null;

            buffer.clear();
            return RESULT_OK;
        }
    }

    private int writeEnd(Data data) {
        if (!isOpened()) {
            return RESULT_NOT_OPEN;
        }

        // get index
        int index = getInputIndex(data);
        if (index > 0) {
            // get info
            MediaCodec.BufferInfo info = AudioData.getBufferInfo(data);
            assert info != null;

            Log.d(TAG, "writeEnd: isConfig = " + AudioData.isConfig(data) +", info :" + info);
            if (AudioData.isConfig(data)) {
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
