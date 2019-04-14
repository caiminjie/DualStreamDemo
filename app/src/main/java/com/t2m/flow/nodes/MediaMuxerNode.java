/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.nodes;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.t2m.flow.data.ByteBufferData;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;
import com.t2m.flow.path.Plug;
import com.t2m.flow.path.Slot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

/**
 * MediaMuxer Node for saving audio track
 */
public class MediaMuxerNode extends Node {
    private static final String TAG = MediaMuxerNode.class.getSimpleName();

    public static final int INDEX_AUDIO_WRITER = 0;
    public static final int INDEX_VIDEO_WRITER = 1;

    private final Object mWriterLock = new Object();

    private MediaMuxer mMuxer = null;
    private String mPath;

    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private boolean mIsAudioConfigured = false;
    private boolean mIsVideoConfigured = false;
    private long prevOutputPTSUs = 0;
    private MediaFormat mAudioFormat;
    private MediaFormat mVideoFormat;

    private Slot mSlotAudioWriter = new Slot() {
        @Override
        public int setData(DataHolder holder) {
            return slotAudioWriterSetData(holder);
        }

        @Override
        public boolean bypass() {
            return false;
        }
    };

    private Slot mSlotVideoWriter = new Slot() {
        @Override
        public int setData(DataHolder holder) {
            return slotVideoWriterSetData(holder);
        }

        @Override
        public boolean bypass() {
            return false;
        }
    };

    public MediaMuxerNode(String name, String path) {
        super(name);

        mPath = path;
    }


    @Override
    public Node open() throws IOException {
        if (isOpened()) {
            return this; // already opened
        }
        mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mMuxer.setOrientationHint(90);
        return this;
    }

    @Override
    public boolean isOpened() {
        return mMuxer != null;
    }

    @Override
    public void close() throws IOException {
        mAudioTrackIndex = -1;
        mVideoTrackIndex = -1;
        mMuxStarted = false;
        mIsAudioConfigured = false;
        mIsVideoConfigured = false;

        if (mMuxer != null) {
            try {
                mMuxer.release();
            }catch (Exception e){
                throw new IOException("[" + mName + "] release muxer failed. the saved record should be broken", e); // MODIFIED by Fan.Hu, 2018-01-18,BUG-5709670
            }
            mMuxer = null;
        }
    }

    @Deprecated
    @Override
    public Plug plugReader(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    @Deprecated
    @Override
    public Plug plugWriter(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    @Deprecated
    @Override
    public Slot slotReader(int index) {
        throw new InvalidParameterException("[" + mName + "] method not supported");
    }

    @Override
    public Slot slotWriter(int index) {
        switch (index) {
            case INDEX_AUDIO_WRITER:
                return mSlotAudioWriter;
            case INDEX_VIDEO_WRITER:
                return mSlotVideoWriter;
            default:
                Log.e(TAG, "[" + mName + "] Invalid slot writer index: " + index);
                return null;
        }
    }

    public Slot slotAudioWriter() {
        return mSlotAudioWriter;
    }

    public Slot slotVideoWriter() {
        return mSlotVideoWriter;
    }

    private boolean mMuxStarted = false;

    private int slotAudioWriterSetData(DataHolder holder) {
        synchronized (mWriterLock) {
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            ByteBufferData data = (ByteBufferData) holder.data;
            if (data.isConfig()) {
                mAudioFormat = data.getConfigFormat();
                mAudioTrackIndex = mMuxer.addTrack(mAudioFormat);
                mIsAudioConfigured = true;

                Log.d(TAG, "[" + mName + "] received audio config data");
                if (!mMuxStarted && mIsVideoConfigured && mIsAudioConfigured) {
                    mMuxer.start();
                    mMuxStarted = true;
                }
                return RESULT_OK;
            } else {
                if (mMuxStarted && mIsAudioConfigured && mIsVideoConfigured) {
                    ByteBuffer buffer = data.getBuffer();
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.getBufferInfo();
                    assert info != null;
                    buffer.position(info.offset);

                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    if (info.size > 0) {
                        info.presentationTimeUs = getPTSUs();
                        mMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
                        prevOutputPTSUs = info.presentationTimeUs;
                    }
                    return RESULT_OK;
                } else {
                    Log.w(TAG, "not configured, drop this sample");
                    return RESULT_OK;
                }
            }
        }
    }

    private int slotVideoWriterSetData(DataHolder holder) {
        synchronized (mWriterLock) {
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            ByteBufferData data = (ByteBufferData) holder.data;
            if (data.isConfig()) {
                mVideoFormat = data.getConfigFormat();
                mVideoTrackIndex = mMuxer.addTrack(mVideoFormat);
                mIsVideoConfigured = true;

                Log.d(TAG, "[" + mName + "] received video config data");
                if (!mMuxStarted && mIsVideoConfigured && mIsAudioConfigured) {
                    mMuxer.start();
                    mMuxStarted = true;
                }
                return RESULT_OK;
            } else {
                if (mMuxStarted && mIsAudioConfigured && mIsVideoConfigured) {
                    ByteBuffer buffer = data.getBuffer();
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.getBufferInfo();
                    assert info != null;
                    buffer.position(info.offset);

                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    if (info.size > 0) {
                        info.presentationTimeUs = getPTSUs();
                        mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                        prevOutputPTSUs = info.presentationTimeUs;
                    }
                    return RESULT_OK;
                } else {
                    Log.w(TAG, "[" + mName + "] not configured, drop this sample");
                    return RESULT_OK;
                }
            }
        }
    }

    /**
     * get next encoding presentationTimeUs
     * @return time in ms
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }
}
