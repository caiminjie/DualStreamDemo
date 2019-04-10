package com.t2m.dualstreamdemo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public abstract class MediaEncoder implements Runnable {

    private static String TAG = "MediaEncoder";
    protected static final int TIMEOUT_USEC = 10000;
    private String mCodecName;
    protected final Object mSync = new Object();
    protected volatile boolean mIsCapturing;
    private int mRequestDrain;
    protected volatile boolean mRequestStop;
    protected boolean mIsEOS;
    protected boolean mMuxerStarted;
    protected int mTrackIndex;
    protected MediaCodec mMediaCodec;
    protected final WeakReference<MediaMuxerWrapper> mWeakMuxer;
    private MediaCodec.BufferInfo mBufferInfo;

    public MediaEncoder(final MediaMuxerWrapper muxer, String tag) {
        if (muxer == null) throw new NullPointerException("MediaMuxerWrapper is null");
        mCodecName = tag + " : ";
        mWeakMuxer = new WeakReference<MediaMuxerWrapper>(muxer);
        muxer.addEncoder(this);
        Log.d(TAG, mCodecName + "MediaEncoder : before");
        synchronized (mSync) {
            Log.d(TAG, mCodecName + "MediaEncoder : get");
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = new MediaCodec.BufferInfo();
            // wait for starting thread
            new Thread(this, getClass().getSimpleName()).start();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
        Log.d(TAG, mCodecName +"MediaEncoder : end");
    }

    @Override
    public void run() {
        Log.d(TAG, mCodecName +"MediaEncoder run: before");
        synchronized (mSync) {
            Log.d(TAG, mCodecName +"MediaEncoder run: get");
            mRequestStop = false;
            mRequestDrain = 0;
            mSync.notify();
        }
        Log.d(TAG, mCodecName +"MediaEncoder run: end");
        final boolean isRunning = true;
        boolean localRequestStop;
        boolean localRequestDrain;
        while (true) {
            Log.d(TAG, mCodecName +"isRunning:  before");
            synchronized (mSync) {
                Log.d(TAG, mCodecName +"isRunning:  get, mRequestStop = " + mRequestStop);
                localRequestStop = mRequestStop;
                localRequestDrain = (mRequestDrain > 0);
                if (localRequestDrain)
                    mRequestDrain--;
            }
            Log.d(TAG, mCodecName +"isRunning: end");
            if (localRequestStop) {
                Log.d(TAG, mCodecName +"localRequestStop : " + localRequestStop);
                drain();
                // request stop recording
                signalEndOfInputStream();
                // process output data again for EOS signale
                drain();
                // release all related objects
                release();
                break;
            }
            if (localRequestDrain) {
                Log.d(TAG, mCodecName +"localRequestDrain : " + localRequestDrain);
                drain();
            } else {
                Log.d(TAG, mCodecName +"1111 run: before");
                synchronized (mSync) {
                    Log.d(TAG, mCodecName +"1111 run: get");
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
                Log.d(TAG, mCodecName +"1111 run: end");
            }
        } // end of while
        Log.d(TAG, mCodecName +"Encoder thread exiting before");
        synchronized (mSync) {
            Log.d(TAG, mCodecName +"Encoder thread exiting get");
            mRequestStop = true;
            mIsCapturing = false;
        }
        Log.d(TAG, mCodecName +"Encoder thread exiting end");
    }

    abstract void prepare() throws IOException;

    void startRecording() {
        Log.v(TAG, mCodecName +"startRecording before");
        synchronized (mSync) {
            Log.v(TAG, mCodecName +"startRecording get");
            mIsCapturing = true;
            mRequestStop = false;
            mSync.notifyAll();
        }
        Log.v(TAG, mCodecName +"startRecording end");
    }

    void stopRecording() {
        Log.v(TAG, mCodecName +"stopRecording before");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mIsCapturing = false;
            mRequestStop = true;	// for rejecting newer frame
            mSync.notifyAll();
            // We can not know when the encoding and writing finish.
            // so we return immediately after request to avoid delay of caller thread
        }
        Log.v(TAG, mCodecName +"stopRecording end");
    }


    protected void release() {
        Log.d(TAG, mCodecName +"release:");
        mIsCapturing = false;
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (final Exception e) {
                Log.e(TAG, mCodecName +"failed releasing MediaCodec", e);
            }
        }
        if (mMuxerStarted) {
            final MediaMuxerWrapper muxer = mWeakMuxer != null ? mWeakMuxer.get() : null;
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (final Exception e) {
                    Log.e(TAG, mCodecName +"failed stopping muxer", e);
                }
            }
        }
        mBufferInfo = null;
    }

    public boolean frameAvailableSoon() {
        Log.v(TAG, mCodecName +"frameAvailableSoon before");
        synchronized (mSync) {
            Log.v(TAG, mCodecName +"frameAvailableSoon get");
            if (!mIsCapturing || mRequestStop) {
                return false;
            }
            mRequestDrain++;
            mSync.notifyAll();
        }
        Log.v(TAG, mCodecName +"frameAvailableSoon end");
        return true;
    }

    protected void signalEndOfInputStream() {
        Log.d(TAG, mCodecName +"sending EOS to encoder");
        // signalEndOfInputStream is only avairable for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
//		mMediaCodec.signalEndOfInputStream();	// API >= 18
        encode(null, 0, getPTSUs());
    }


    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        Log.d(TAG, mCodecName +"encode: ");
        if (!mIsCapturing) return;
        while (mIsCapturing) {
            final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                if (buffer != null) {
                    inputBuffer.put(buffer);
                }
//	            if (DEBUG) Log.v(TAG, "encode:queueInputBuffer");
                if (length <= 0) {
                    // send EOS
                    mIsEOS = true;
                    Log.i(TAG, mCodecName +"send BUFFER_FLAG_END_OF_STREAM");
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0);
                }
                break;
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }


    protected void drain() {
        Log.e(TAG, mCodecName +"drain: ");
        if (mMediaCodec == null) return;
        int encoderStatus, count = 0;
        final MediaMuxerWrapper muxer = mWeakMuxer.get();
        if (muxer == null) {
//        	throw new NullPointerException("muxer is unexpectedly null");
            Log.w(TAG, mCodecName +"muxer is unexpectedly null");
            return;
        }
        LOOP:	while (mIsCapturing) {
            // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.e(TAG , mCodecName +" time out");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.v(TAG, mCodecName +"INFO_OUTPUT_FORMAT_CHANGED");
                // this status indicate the output format of codec is changed
                // this should come only once before actual encoded data
                // but this status never come on Android4.3 or less
                // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                if (mMuxerStarted) {	// second time request is error
                    throw new RuntimeException("format changed twice");
                }
                // get output format from codec and pass them to muxer
                // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
                mTrackIndex = muxer.addTrack(format);
                mMuxerStarted = true;
                if (!muxer.start()) {
                    // we should wait until muxer is ready
                    synchronized (muxer) {
                        while (!muxer.isStarted())
                            try {
                                muxer.wait(100);
                            } catch (final InterruptedException e) {
                                break LOOP;
                            }
                    }
                }
            } else if (encoderStatus < 0) {
                // unexpected status
                Log.w(TAG, mCodecName +"drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
            } else {
                Log.v(TAG, mCodecName +"useful buffer");
                ByteBuffer encodedData = mMediaCodec.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    // this never should come...may be a MediaCodec internal error
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // You shoud set output format to muxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                    // therefor we should expand and prepare output format from buffer data.
                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                    Log.d(TAG, mCodecName +"drain:BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    // encoded data is ready, clear waiting counter
                    count = 0;
                    if (!mMuxerStarted) {
                        // muxer is not ready...this will prrograming failure.
                        throw new RuntimeException("drain:muxer hasn't started");
                    }
                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    mBufferInfo.presentationTimeUs = getPTSUs();
                    muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }
                // return buffer to encoder
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // when EOS come.
                    mIsCapturing = false;
                    break;      // out of while
                }
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

}
