/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.t2m.dualstreamdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore {
    private static final String TAG = Camera2VideoFragment.TAG;
    private static final boolean VERBOSE = true;

    // TODO: these ought to be configurable as well
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 5;           // 5 seconds between I-frames

    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private File mOutputFile;
    private MediaFormat mMediaFormat;

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore(int width, int height, int bitRate, File outputFile, String type, int frameRate)
            throws IOException {
        mOutputFile = outputFile;
        mBufferList = new ArrayList<>();
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat videoFormat = MediaFormat.createVideoFormat(type != null ? type : MIME_TYPE, width, height);
        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate != 0 ? frameRate : FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,48000, 1);
        if (VERBOSE) Log.d(TAG, "format: " + videoFormat);



        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(type);
        mEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        mMuxer = new MediaMuxer(outputFile.toString(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mMuxer != null) {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
        if(mTimer != null) {
            mTimer.cancel();
            mTimer= null;
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
//    public void drainEncoder(boolean endOfStream) {
//
//        if(mEncoder == null) return;
//        mEncoder.start();
//
//        final int TIMEOUT_USEC = 10000;
//        if (VERBOSE) Log.d(TAG, "drainEncoder(" + endOfStream + ")");
//
//        if (endOfStream) {
//            if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
//            mEncoder.signalEndOfInputStream();
//        }
//
//        while (true) {
//            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
//
//            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                // no output available yet
//                if (!endOfStream) {
//                    break;      // out of while
//                } else {
//                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
//                }
//            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                // should happen before receiving buffers, and should only happen once
//                if (mMuxerStarted) {
//                    throw new RuntimeException("format changed twice");
//                }
//                MediaFormat newFormat = mEncoder.getOutputFormat();
//                Log.d(TAG, "encoder output format changed: " + newFormat);
//
//                // now that we have the Magic Goodies, start the muxer
//                mTrackIndex = mMuxer.addTrack(newFormat);
//                mMuxer.start();
//                mMuxerStarted = true;
//            } else if (encoderStatus < 0) {
//                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
//                        encoderStatus);
//                // let's ignore it
//            } else {
//                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
//
//                if (encodedData == null) {
//                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
//                            " was null");
//                }
//
//                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                    // The codec config data was pulled out and fed to the muxer when we got
//                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
//                    mBufferInfo.size = 0;
//                }
//
//                if (mBufferInfo.size != 0) {
//                    if (!mMuxerStarted) {
//                        throw new RuntimeException("muxer hasn't started");
//                    }
//
//                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
//                    encodedData.position(mBufferInfo.offset);
//                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
//
//                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
//                    if (VERBOSE) {
//                        Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
//                                mBufferInfo.presentationTimeUs);
//                    }
//                }
//
//                mEncoder.releaseOutputBuffer(encoderStatus, false);
//
//                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    if (!endOfStream) {
//                        Log.w(TAG, "reached end of stream unexpectedly");
//                    } else {
//                        if (VERBOSE) Log.d(TAG, "end of stream reached");
//                    }
//                    break;      // out of while
//                }
//            }
//        }
//    }

    public void StopMediaCodecRecording(){
            mEncoder.signalEndOfInputStream();

    }

    public void startMediaCodecRecording()
    {
        Thread recordThread = new Thread(){
            @Override
            public void run() {
                super.run();
                if (mEncoder == null)
                {
                    return ;
                }
                Log.d(TAG, "开始录制###################" );
                //mEncoder.start();
                while (true)
                {
                    int status = mEncoder.dequeueOutputBuffer(mBufferInfo, 10000);
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER)
                    {
                        Log.e(TAG , " time out");
                    }else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
                    {
                        Log.e(TAG, "format changed");
                        // should happen before receiving buffers, and should only happen once
                        if (mMuxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }
                        mMediaFormat = mEncoder.getOutputFormat();
                        Log.d(TAG, "encoder output format changed: " + mMediaFormat);

                        // now that we have the Magic Goodies, start the muxer
                        mTrackIndex = mMuxer.addTrack(mMediaFormat);
                        mMuxer.start();
                        mMuxerStarted = true;
                    }else if (status >= 0)
                    {
                        ByteBuffer data = mEncoder.getOutputBuffer(status);
                        if (mBufferInfo.size != 0 ) {
                            // adjust the ByteBuffer values to match BufferInfo (not needed?)
                            data.position(mBufferInfo.offset);
                            data.limit(mBufferInfo.offset + mBufferInfo.size);

                            if (mMuxerStarted) {
                                for (int i=0; i< mBufferList.size(); i++) {
                                    Log.d(TAG, "write saved buffer first, i = " + i);
                                    mMuxer.writeSampleData(mTrackIndex, mBufferList.get(i), mBufferInfo);
                                    Log.d(TAG, "old ----- sent " + mBufferInfo.size + " bytes to muxer, ts=" + mBufferInfo.presentationTimeUs);
                                }
                                mBufferList.clear();
                                mMuxer.writeSampleData(mTrackIndex, data, mBufferInfo);
                                Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" + mBufferInfo.presentationTimeUs);
                            } else {
                                Log.d(TAG, " muxer is not started, we will save the buffer");
                                mBufferList.add(data);
                            }
                        }
                        // releasing buffer is important
                        mEncoder.releaseOutputBuffer(status, false);
                        final int endOfStream = mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        if (endOfStream == MediaCodec.BUFFER_FLAG_END_OF_STREAM) break;
                    }
                }
                release();
            }
        };
        /*开始录制*/
        recordThread.start();
        mTimer = new Timer();
        mRecordTask = new RecordTask();
        mTimer.schedule(mRecordTask, 10000, 10000);
    }

    private ArrayList<ByteBuffer> mBufferList;
    private Timer mTimer = null;
    private int num = 0;
    private RecordTask mRecordTask;
    private class RecordTask extends TimerTask {

        @Override
        public void run() {
            if (mMuxerStarted) {
                Log.d(TAG, "muxer is started,we need stop it");
                mMuxer.stop();
                mMuxer.release();
                mMuxer = null;
                mMuxerStarted = false;
            }
            num = ++num;
            String file = mOutputFile.toString();
            String perName = file.substring(0,file.lastIndexOf("."));
            String currentName = perName + "_" + String.format("%04d", num) + ".mp4";
            Log.d(TAG, "current file name is :" + currentName);

            try {
                mMuxer = new MediaMuxer(currentName,
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mTrackIndex = -1;
            //mMediaFormat = mEncoder.getOutputFormat();
            mTrackIndex = mMuxer.addTrack(mMediaFormat);
            mMuxer.start();
            mMuxerStarted = true;
        }
    }
}
