/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.nodes;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.t2m.flow.data.ByteBufferData;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;
import com.t2m.flow.path.Plug;
import com.t2m.flow.path.Slot;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * MediaMuxer Node for saving audio track
 */
public class SegmentMuxerNode extends Node {
    private static final String TAG = SegmentMuxerNode.class.getSimpleName();

    private String mPath;
    private String mDefPath;
    private final Object mWriterLock = new Object();
    private MediaMuxerNode mMediaMuxerNode;
    private MediaMuxerNode mNextMediaMuxerNode;
    private MediaFormat mAudioFormat;
    private MediaFormat mVideoFormat;
    private boolean mMuxStarted = false;
    private Timer mTimer = null;
    private int num = 0;
    private RecordTask mRecordTask;

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

    public SegmentMuxerNode(String name, String path) {
        super(name);

        mDefPath = mPath = path;
    }

    @Override
    public Node open() throws IOException {
        if (isOpened()) {
            return this; // already opened
        }
        Log.d(TAG, "[" + mName + "] current file name is :" + mPath);
        mMediaMuxerNode = (MediaMuxerNode) new MediaMuxerNode(mName, mPath).open();
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
            case MediaMuxerNode.INDEX_AUDIO_WRITER:
                return mSlotAudioWriter;
            case MediaMuxerNode.INDEX_VIDEO_WRITER:
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

    private DataHolder createConfigData(MediaFormat format) {
        DataHolder holder = DataHolder.create(ByteBufferData.create());
        ByteBufferData data = (ByteBufferData) holder.data;
        data.setBufferInfo(new MediaCodec.BufferInfo());
        data.setConfigFormat(format);
        data.markConfig();
        return holder;
    }

    private void releaseConfigData(DataHolder holder) {
        holder.data.release();
        holder.release();
    }

    private int slotAudioWriterSetData(DataHolder holder) {
        synchronized(mWriterLock){
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            ByteBufferData data = (ByteBufferData) holder.data;
            if (data.isConfig()) {
                mAudioFormat = data.getConfigFormat();
                if (!mMuxStarted) {
                    mMuxStarted = true;
                    mTimer = new Timer();
                    mRecordTask = new RecordTask();
                    mTimer.schedule(mRecordTask, 5000, 5000);
                }
            }

            MediaCodec.BufferInfo info = data.getBufferInfo();
            boolean keyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

            Log.d(TAG, "[" + mName + "] slotAudioWriterSetData: keyFrame = " + keyFrame + ", mNextMediaMuxerNode = " + mNextMediaMuxerNode);

            if (mNextMediaMuxerNode != null && keyFrame){
                switchMediaMuxerNode();
            }
            mMediaMuxerNode.slotAudioWriter().setData(holder);

            return RESULT_OK;
        }
    }

    private int slotVideoWriterSetData(DataHolder holder) {
        synchronized(mWriterLock){
            if (!isOpened()) {
                return RESULT_NOT_OPEN;
            }

            ByteBufferData data = (ByteBufferData) holder.data;
            if (data.isConfig()) {
                mVideoFormat = data.getConfigFormat();
                if (!mMuxStarted) {
                    mMuxStarted = true;
                    mTimer = new Timer();
                    mRecordTask = new RecordTask();
                    mTimer.schedule(mRecordTask, 5000, 5000);
                }
            }

            MediaCodec.BufferInfo info = data.getBufferInfo();
            boolean keyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

            Log.d(TAG, "[" + mName + "] slotVideoWriterSetData: keyFrame = " + keyFrame + ", mNextMediaMuxerNode = " + mNextMediaMuxerNode);

            if (mNextMediaMuxerNode != null && keyFrame){
                switchMediaMuxerNode();
            }
            mMediaMuxerNode.slotVideoWriter().setData(holder);

            return RESULT_OK;
        }
    }

    private void switchMediaMuxerNode() {
        try {
            //close old muxer
            mMediaMuxerNode.close();
            mMediaMuxerNode = (MediaMuxerNode) mNextMediaMuxerNode.open();
            mNextMediaMuxerNode = null;

            //create new config format data
            DataHolder audioHolder = createConfigData(mAudioFormat);
            DataHolder videoHolder = createConfigData(mVideoFormat);

            //writer config format to new muxer
            mMediaMuxerNode.slotAudioWriter().setData(audioHolder);
            mMediaMuxerNode.slotVideoWriter().setData(videoHolder);

            // release temp data
            releaseConfigData(audioHolder);
            releaseConfigData(videoHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class RecordTask extends TimerTask {
        @Override
        public void run() {
            try {
                num = ++num;
                String file = mDefPath;
                String perName = file.substring(0,file.lastIndexOf("."));
                mPath = perName + "_" + String.format(Locale.ENGLISH, "%04d", num) + ".mp4";
                Log.d(TAG, "[" + mName + "] current file name is :" + mPath);
                mNextMediaMuxerNode = (MediaMuxerNode) new MediaMuxerNode(mName, mPath).open();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
