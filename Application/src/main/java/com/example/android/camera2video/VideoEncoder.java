package com.example.android.camera2video;

import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

public class VideoEncoder {
    private static final String TAG = Camera2VideoFragment.TAG;

    private VideoEncoderCore mVideoEncoder;

    public static class EncoderConfig {
        final File mOutputFile;
        final int mWidth;
        final int mHeight;
        final int mBitRate;
        final int mFrameRate;
        final String mMimeType;

        public EncoderConfig(File outputFile, int width, int height, int bitRate, String type, int frameRate) {
            mOutputFile = outputFile;
            mWidth = width;
            mHeight = height;
            mBitRate = bitRate;
            mMimeType = type;
            mFrameRate = frameRate;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mWidth + "x" + mHeight + " @" + mBitRate +
                    " to '" + mOutputFile.toString();
        }
    }

    public void prepareRecording(EncoderConfig config) {
        Log.d(TAG, "prepareRecording: ");
        try {
            mVideoEncoder = new VideoEncoderCore(config.mWidth, config.mHeight, config.mBitRate, config.mOutputFile, config.mMimeType, config.mFrameRate);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public void startRecording() {
        Log.d(TAG, "Encoder: startRecording()");
        //mVideoEncoder.drainEncoder(false);
        mVideoEncoder.startMediaCodecRecording();
    }

    public void stopRecording() {
        //mVideoEncoder.drainEncoder(true);
        mVideoEncoder.StopMediaCodecRecording();
        //releaseEncoder();
    }

    public Surface getInputSurface() {
        Log.d(TAG, "getInputSurface, mVideoEncoder = " + mVideoEncoder);
        return mVideoEncoder.getInputSurface();
    }

    private void releaseEncoder() {
        mVideoEncoder.release();
    }

}
