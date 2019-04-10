package com.t2m.dualstreamdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

public class MediaVideoEncoder extends MediaEncoder {
    private static final String TAG = "MediaVideoEncoder";
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 5;

    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mFrameRate;
    private String mType;
    private Surface mSurface;

    public MediaVideoEncoder(final MediaMuxerWrapper muxer, final int width, final int height, int bitRate, String type, int frameRate) {
        super(muxer, TAG);
        Log.i(TAG, "MediaVideoEncoder: ");
        mWidth = width;
        mHeight = height;
        mBitRate = bitRate;
        mFrameRate = frameRate;
        mType = type;
    }

    @Override
    void startRecording() {
        super.startRecording();
        frameAvailableSoon();
    }


    @Override
    protected void prepare() throws IOException {
        Log.i(TAG, "prepare: ");
        mTrackIndex = -1;
        mMuxerStarted = mIsEOS = false;

        final MediaFormat format = MediaFormat.createVideoFormat(mType, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);	// API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        Log.i(TAG, "format: " + format);

        mMediaCodec = MediaCodec.createEncoderByType(mType);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // get Surface for encoder input
        // this method only can call between #configure and #start
        mSurface = mMediaCodec.createInputSurface();	// API >= 18
        mMediaCodec.start();
        Log.i(TAG, "prepare finishing");
    }

    public Surface getInputSurface() {
        return mSurface;
    }

    @Override
    protected void release() {
        Log.i(TAG, "release:");
//        if (mSurface != null) {
//            mSurface.release();
//            mSurface = null;
//        }
        super.release();
    }

    @Override
    protected void signalEndOfInputStream() {
        Log.d(TAG, "sending EOS to encoder");
        mMediaCodec.signalEndOfInputStream();	// API >= 18
        mIsEOS = true;
    }
}
