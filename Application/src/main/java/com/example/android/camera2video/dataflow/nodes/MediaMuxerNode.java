/* Copyright (C) 2018 Tcl Corporation Limited */
package com.example.android.camera2video.dataflow.nodes;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

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
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MediaMuxer Node for saving audio track
 */
public class MediaMuxerNode extends DataNode {
    private static final String TAG = MediaMuxerNode.class.getSimpleName();

    private MediaMuxer mMuxer = null;
    private String mPath;
    private String mDefPath;


    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private boolean mIsAudioConfigured = false;
    private boolean mIsVideoConfigured = false;

    private MediaFormat mAudioFormat;
    private MediaFormat mVideoFormat;

    private DirectWriter mDirectWriter = new DirectWriter() {
        @Override
        public int write(Data data) {
            return MediaMuxerNode.this.write(data);
        }
    };

    public MediaMuxerNode(String path) {
        mDefPath = mPath = path;
    }


    @Override
    public DataNode open() throws IOException {
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
        if(mTimer != null) {
            mTimer.cancel();
            mTimer= null;
        }
        LowClose();
    }

    private void LowClose() throws IOException{
        mAudioTrackIndex = -1;
        mVideoTrackIndex = -1;
        mMuxStarted = false;
        mIsAudioConfigured = false;
        mIsVideoConfigured = false;

        if (mMuxer != null) {
            try {
                mMuxer.release();
            }catch (Exception e){
                throw new IOException("release muxer failed. the saved record should be broken", e); // MODIFIED by Fan.Hu, 2018-01-18,BUG-5709670
            }
            mMuxer = null;
        }
    }

    @Deprecated
    @Override
    public BufferedReader getBufferedReader() {
        throw new InvalidParameterException("method not supported");
    }

    @Deprecated
    @Override
    public BufferedWriter getBufferedWriter() {
        throw new InvalidParameterException("method not supported");
    }

    @Deprecated
    @Override
    public DirectReader getDirectReader() {
        throw new InvalidParameterException("method not supported");
    }

    @Override
    public DirectWriter getDirectWriter() {
        return mDirectWriter;
    }

    private boolean mMuxStarted = false;

    private int write(Data data) {
        // TODO sync later
        if (!isOpened() && !mChangingMuxer) {
            return RESULT_NOT_OPEN;
        }

        if (AudioData.isConfig(data)) {
            Log.d(TAG, "write: the muxer is " + mMuxer);
            if (MediaFormat.MIMETYPE_VIDEO_AVC.equals(AudioData.getConfigFormat(data).getString(MediaFormat.KEY_MIME))){
                mVideoFormat = AudioData.getConfigFormat(data);
                mVideoTrackIndex = mMuxer.addTrack(mVideoFormat);
                mIsVideoConfigured = true;
            } else if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(AudioData.getConfigFormat(data).getString(MediaFormat.KEY_MIME))) {
                mAudioFormat = AudioData.getConfigFormat(data);
                mAudioTrackIndex = mMuxer.addTrack(mAudioFormat);
                mIsAudioConfigured = true;
            }
            Log.d(TAG, "write: is config data : " + data);
            if (!mMuxStarted && mIsVideoConfigured && mIsAudioConfigured) {
                mMuxer.start();
                mMuxStarted = true;
                mTimer = new Timer();
                mRecordTask = new RecordTask();
                mTimer.schedule(mRecordTask, 5000, 5000);
            }
            return RESULT_OK;
        } else {
            if (mMuxStarted && mIsAudioConfigured && mIsVideoConfigured) {
                ByteBuffer buffer = null;
                MediaCodec.BufferInfo info = null;

                buffer = AudioData.getBuffer(data);
                assert buffer != null;
                info = AudioData.getBufferInfo(data);
                assert info != null;
                buffer.position(info.offset);

                // write encoded data to muxer(need to adjust presentationTimeUs.
                if (info != null && info.size > 0) {
                    info.presentationTimeUs = getPTSUs();
                    boolean keyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                    Log.d(TAG, "write: ----type  = " + AudioData.getConfigFormat(data).getString(MediaFormat.KEY_MIME) + ", key frame = " + keyFrame);
                    if (MediaFormat.MIMETYPE_VIDEO_AVC.equals(AudioData.getConfigFormat(data).getString(MediaFormat.KEY_MIME))){
                        mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                    } else {
                        mMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
                    }
                    prevOutputPTSUs = info.presentationTimeUs;
                }
                return RESULT_OK;
            } else {
                Log.w(TAG, "not configured, drop this sample");
                return RESULT_OK;
            }
        }
    }


    private long prevOutputPTSUs = 0;
    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

    private ArrayList<ByteBuffer> mBufferList;
    private Timer mTimer = null;
    private int num = 0;
    private RecordTask mRecordTask;
    private boolean mChangingMuxer = false;
    private class RecordTask extends TimerTask {

        @Override
        public void run() {
            try {
                if (mMuxStarted) {
                    Log.d(TAG, "muxer is started,we need stop it");
                    mChangingMuxer = true;
                    LowClose();
                }
                num = ++num;
                String file = mDefPath;
                String perName = file.substring(0,file.lastIndexOf("."));
                mPath = perName + "_" + String.format("%04d", num) + ".mp4";
                Log.d(TAG, "current file name is :" + mPath);

                mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                mMuxer.setOrientationHint(90);

                mAudioTrackIndex = mMuxer.addTrack(mAudioFormat);
                mIsAudioConfigured = true;

                mVideoTrackIndex = mMuxer.addTrack(mVideoFormat);
                mIsVideoConfigured = true;

                mMuxer.start();
                mChangingMuxer =false;
                mMuxStarted = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
