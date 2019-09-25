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

            // get muxer. new muxer will be received if path changed (new segment)
            MediaMuxer muxer = getMuxer();
            if (muxer == null) {
                return RESULT_ERROR;
            }

            // get format
            if (data.isConfig()) {
                mAudioFormat = data.format;
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
                    ByteBuffer buffer = data.buffer;
                    assert buffer != null;
                    MediaCodec.BufferInfo info = data.info;
                    buffer.position(info.offset);

                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    if (info.size > 0) {
                        info.presentationTimeUs = getPTSUs();
                        muxer.writeSampleData(mAudioTrackIndex, buffer, info);
                        prevOutputPTSUs = info.presentationTimeUs;
                    }
                }
                return RESULT_OK;
            } else {
                Log.w(TAG, "not configured, drop this audio sample");
                return RESULT_OK;
            }
        }
    }

    private int processVideoData(MediaData data) {
        //Log.i(TAG, "processVideoData()");
        synchronized (mWriterLock) {
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            // get muxer. new muxer will be received if path changed (new segment)
            MediaMuxer muxer = getMuxer();
            if (muxer == null) {
                return RESULT_ERROR;
            }

            // get format
            if (data.isConfig()) {
                mVideoFormat = data.format;
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
                }
                return RESULT_OK;
            } else {
                Log.w(TAG, "[" + mName + "] not configured, drop this video sample");
                return RESULT_OK;
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
