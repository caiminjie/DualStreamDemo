/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.nodes;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.t2m.dataflow.data.MediaData;
import com.t2m.dataflow.data.Data;
import com.t2m.dataflow.node.BufferedReader;
import com.t2m.dataflow.node.BufferedWriter;
import com.t2m.dataflow.node.DataNode;
import com.t2m.dataflow.node.DirectReader;
import com.t2m.dataflow.node.DirectWriter;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MediaMuxer Node for saving audio track
 */
public class SegmentMuxerNode extends DataNode {
    private static final String TAG = SegmentMuxerNode.class.getSimpleName();

    private String mPath;
    private String mDefPath;
    private Object mObject = new Object();
    private MediaMuxerNode mMediaMuxerNode;
    private MediaMuxerNode mNextMediaMuxerNode;
    private MediaFormat mAudioFormat;
    private MediaFormat mVideoFormat;
    private boolean mMuxStarted = false;
    private Timer mTimer = null;
    private int num = 0;
    private RecordTask mRecordTask;

    private DirectWriter mDirectWriter = new DirectWriter() {
        @Override
        public int write(Data data) {
            return SegmentMuxerNode.this.write(data);
        }
    };

    public SegmentMuxerNode(String path) {
        mDefPath = mPath = path;
    }

    @Override
    public DataNode open() throws IOException {
        if (isOpened()) {
            return this; // already opened
        }
        mMediaMuxerNode = (MediaMuxerNode) new MediaMuxerNode(mPath).open();
        return this;
    }

    @Override
    public boolean isOpened() {
        return mMediaMuxerNode != null;
    }

    @Override
    public void close() throws IOException {
        if(mTimer != null) {
            mTimer.cancel();
            mTimer= null;
        }
        mMuxStarted = false;

        if (mMediaMuxerNode != null) {
            mMediaMuxerNode.close();
            mMediaMuxerNode = null;
        }

        if (mNextMediaMuxerNode != null) {
            mNextMediaMuxerNode.close();
            mNextMediaMuxerNode = null;
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

    private int write(Data data) {
        synchronized(mObject){
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            if (MediaData.isConfig(data)) {
                if (MediaFormat.MIMETYPE_VIDEO_AVC.equals(MediaData.getConfigFormat(data).getString(MediaFormat.KEY_MIME))){
                    mVideoFormat = MediaData.getConfigFormat(data);
                } else if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(MediaData.getConfigFormat(data).getString(MediaFormat.KEY_MIME))) {
                    mAudioFormat = MediaData.getConfigFormat(data);
                }
                if (!mMuxStarted) {
                    mMuxStarted = true;
                    mTimer = new Timer();
                    mRecordTask = new RecordTask();
                    mTimer.schedule(mRecordTask, 5000, 5000);
                }
            }

            MediaCodec.BufferInfo info = null;
            info = MediaData.getBufferInfo(data);
            boolean keyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

            Log.d(TAG, "write: keyFrame = " + keyFrame + ", mNextMediaMuxerNode = " + mNextMediaMuxerNode);

            if (mNextMediaMuxerNode != null && keyFrame){
                try {
                    //close old muxer
                    mMediaMuxerNode.close();
                    mMediaMuxerNode = (MediaMuxerNode) mNextMediaMuxerNode.open();
                    //creat new config format data
                    Data audioData = new Data();
                    Data videoData = new Data();
                    MediaData.setBufferInfo(audioData, new MediaCodec.BufferInfo());
                    MediaData.setBufferInfo(videoData, new MediaCodec.BufferInfo());
                    MediaData.setConfigFormat(audioData,mAudioFormat);
                    MediaData.setConfigFormat(videoData,mVideoFormat);
                    MediaData.markConfig(audioData);
                    MediaData.markConfig(videoData);
                    //writer config format to new muxer
                    mMediaMuxerNode.getDirectWriter().write(audioData);
                    mMediaMuxerNode.getDirectWriter().write(videoData);
                    //writer key frame to new muxer
                    mMediaMuxerNode.getDirectWriter().write(data);
                    mNextMediaMuxerNode = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                mMediaMuxerNode.getDirectWriter().write(data);
            }
            return RESULT_OK;
        }
    }

    private class RecordTask extends TimerTask {

        @Override
        public void run() {
            try {
                num = ++num;
                String file = mDefPath;
                String perName = file.substring(0,file.lastIndexOf("."));
                mPath = perName + "_" + String.format("%04d", num) + ".mp4";
                Log.d(TAG, "current file name is :" + mPath);
                mNextMediaMuxerNode = (MediaMuxerNode) new MediaMuxerNode(mPath).open();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
