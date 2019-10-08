/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.pan.node.tail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.t2m.pan.Data;
import com.t2m.pan.data.ByteBufferData;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MediaMuxer Node for saving audio track
 */
public class MediaMuxerNode extends TailNode<ByteBufferData> {
    private static final String TAG = MediaMuxerNode.class.getSimpleName();

    private final Object mWriterLock = new Object();

    private MediaMuxer mMuxer = null;
    private final Object mMuxerLock = new Object();
    private String mPath;
    private int mOrientation;

    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private long prevOutputPTSUs = 0;
    @SuppressWarnings("FieldCanBeLocal")
    private MediaFormat mAudioFormat = null;
    @SuppressWarnings("FieldCanBeLocal")
    private MediaFormat mVideoFormat = null;

    private boolean mMuxStarted = false;

    public MediaMuxerNode(String name, String path, int orientation) {
        super(name);

        mPath = path;
        mOrientation = orientation;
    }

    @Override
    public boolean isOpened() {
        return true;
    }

    @Override
    protected void onOpen() throws IOException {
        // do nothing
    }

    @Override
    protected void onClose() throws IOException {
        mAudioTrackIndex = -1;
        mVideoTrackIndex = -1;
        mMuxStarted = false;

        if (mMuxer != null) {
            try {
                mMuxer.release();
            }catch (Exception e){
                throw new IOException("[" + mName + "] release muxer failed. the saved record should be broken", e); // MODIFIED by Fan.Hu, 2018-01-18,BUG-5709670
            }
            mMuxer = null;
        }
    }

    @Override
    protected int onProcessData(ByteBufferData data) {
        // do process
        switch (data.type) {
            case ByteBufferData.TYPE_AUDIO:
                return onProcessAudioData(data);
            case ByteBufferData.TYPE_VIDEO:
                return onProcessVideoData(data);
            default:
                Log.e(TAG, "[" + mName + "] Invalid data type: " + data.type);
                return Data.RESULT_ERROR;
        }
    }

    private MediaMuxer getMuxer() {
        synchronized (mMuxerLock) {
            if (mPath != null) {
                // release old if necessary
                if (mMuxer != null) {
                    mMuxer.stop();
                    mMuxer.release();
                }

                // delete fle if already exists
                File file = new File(mPath);
                if (file.exists()) {
                    file.deleteOnExit();
                }

                // create new
                try {
                    mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    mMuxer.setOrientationHint(mOrientation);
                    mVideoTrackIndex = -1;
                    mAudioTrackIndex = -1;
                    mMuxStarted = false;
                } catch (IOException e) {
                    Log.e(TAG, "create muxer failed.", e);
                    mMuxer = null;
                }

                mPath = null;
            }

            return mMuxer;
        }
    }

    private int onProcessAudioData(ByteBufferData data) {
        //Log.i(TAG, "processAudioData()");
        synchronized (mWriterLock) {
            if (!isOpened()) {
                Log.e(TAG, "onProcessAudioData()# not opened.");
                return Data.RESULT_ERROR;
            }

            // get muxer. new muxer will be received if path changed (new segment)
            MediaMuxer muxer = getMuxer();
            if (muxer == null) {
                Log.e(TAG, "onProcessAudioData()# get muxer failed.");
                return Data.RESULT_ERROR;
            }

            // get format
            if (data.isConfig()) {
                mAudioFormat = data.format();
            }

            // config track
            if (mAudioFormat != null && mAudioTrackIndex < 0) {
                mAudioTrackIndex = muxer.addTrack(mAudioFormat);
            }

            // start muxer if necessary
            if (!mMuxStarted && mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                muxer.start();
                mMuxStarted = true;
            }

            // write sample if configured
            if (mMuxStarted) {
                if (!data.isConfig()) {
                    ByteBuffer buffer = data.buffer();
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.info();
                    buffer.position(info.offset);

                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    if (info.size > 0) {
                        info.presentationTimeUs = getPTSUs();
                        muxer.writeSampleData(mAudioTrackIndex, buffer, info);
                        prevOutputPTSUs = info.presentationTimeUs;
                    }
                }
                return Data.RESULT_OK;
            } else {
                Log.w(TAG, "not configured, drop this audio sample");
                return Data.RESULT_OK;
            }
        }
    }

    private int onProcessVideoData(ByteBufferData data) {
        //Log.i(TAG, "processVideoData()");
        synchronized (mWriterLock) {
            if (!isOpened()) {
                Log.e(TAG, "onProcessVideoData()# not opened.");
                return Data.RESULT_ERROR;
            }

            // get muxer. new muxer will be received if path changed (new segment)
            MediaMuxer muxer = getMuxer();
            if (muxer == null) {
                Log.e(TAG, "onProcessVideoData()# get muxer failed.");
                return Data.RESULT_ERROR;
            }

            // get format
            if (data.isConfig()) {
                mVideoFormat = data.format();
            }

            // config track
            if (mVideoFormat != null && mVideoTrackIndex < 0) {
                mVideoTrackIndex = muxer.addTrack(mVideoFormat);
            }

            // start muxer if necessary
            if (!mMuxStarted && mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                muxer.start();
                mMuxStarted = true;
            }

            // write sample if configured
            if (mMuxStarted) {
                if (!data.isConfig()) {
                    ByteBuffer buffer = data.buffer();
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.info();
                    buffer.position(info.offset);

                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    if (info.size > 0) {
                        info.presentationTimeUs = getPTSUs();
                        mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                        prevOutputPTSUs = info.presentationTimeUs;
                    }
                }
                return Data.RESULT_OK;
            } else {
                Log.w(TAG, "[" + mName + "] not configured, drop this video sample");
                return Data.RESULT_OK;
            }
        }
    }

    /**
     * get next encoding presentationTimeUs
     * @return time in ms
     */
    @SuppressWarnings("WeakerAccess")
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    public void newSegment(String path) {
        synchronized (mMuxerLock) {
            mPath = path;
        }
    }
}
