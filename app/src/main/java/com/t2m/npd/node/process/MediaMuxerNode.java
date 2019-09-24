/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.npd.node.process;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.t2m.npd.data.MediaData;
import com.t2m.npd.node.ProcessNode;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MediaMuxer Node for saving audio track
 */
public class MediaMuxerNode extends ProcessNode<MediaData> {
    private static final String TAG = MediaMuxerNode.class.getSimpleName();

    private final Object mWriterLock = new Object();

    private MediaMuxer mMuxer = null;
    private String mPath;
    private int mOrientation;

    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private boolean mIsAudioConfigured = false;
    private boolean mIsVideoConfigured = false;
    private long prevOutputPTSUs = 0;
    @SuppressWarnings("FieldCanBeLocal")
    private MediaFormat mAudioFormat;
    @SuppressWarnings("FieldCanBeLocal")
    private MediaFormat mVideoFormat;

    private boolean mMuxStarted = false;

    public MediaMuxerNode(String name, String path, int orientation) {
        super(name);

        mPath = path;
        mOrientation = orientation;
    }

    @Override
    protected void onOpen() throws IOException {
        File file = new File(mPath);
        if (file.exists()) {
            file.deleteOnExit();
        }

        mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mMuxer.setOrientationHint(mOrientation);
    }

    @Override
    protected void onClose() throws IOException {
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

    @Override
    public int process(MediaData data) {
        // do process
        switch (data.type) {
            case MediaData.TYPE_AUDIO:
                return processAudioData(data);
            case MediaData.TYPE_VIDEO:
                return processVideoData(data);
            default:
                Log.e(TAG, "[" + mName + "] Invalid data type: " + data.type);
                return RESULT_ERROR;
        }
    }

    private int processAudioData(MediaData data) {
        //Log.i(TAG, "processAudioData()");
        synchronized (mWriterLock) {
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            if (data.isConfig()) {
                mAudioFormat = data.format;
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
                    ByteBuffer buffer = data.buffer;
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.info;
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

    private int processVideoData(MediaData data) {
        //Log.i(TAG, "processVideoData()");
        synchronized (mWriterLock) {
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            if (data.isConfig()) {
                mVideoFormat = data.format;
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
                    ByteBuffer buffer = data.buffer;
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.info;
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
    @SuppressWarnings("WeakerAccess")
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }
}
