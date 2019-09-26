/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.npd.node.pipeline;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.t2m.npd.Data;
import com.t2m.npd.Pipeline;
import com.t2m.npd.data.MediaData;
import com.t2m.npd.data.SurfaceData;
import com.t2m.npd.node.PipelineNode;
import com.t2m.npd.pipeline.BufferedPipeline;
import com.t2m.npd.pipeline.SimplePipeline;

import java.io.IOException;
import java.security.InvalidParameterException;

/**
 * Codec Node for encode & decode
 */
public class CodecNode extends PipelineNode<Data> {
    private static final String TAG = CodecNode.class.getSimpleName();

    private int mType;
    private MediaCodec mCodec;
    private boolean mIsEncoder;
    private MediaFormat mFormat;
    private Surface mSurface;

    private SimplePipeline<SurfaceData> mInPipelineSurface;
    private BufferedPipeline<MediaData> mInPipeline;
    private BufferedPipeline<MediaData> mOutPipeline;

    private boolean mEnableOutput = true;
    private long mBlockDurationUs = 0;

    @SuppressWarnings("WeakerAccess")
    public CodecNode(String name, boolean isEncoder, MediaFormat format)  {
        super(name);

        mIsEncoder = isEncoder;
        mFormat = format;

        // verify format
        if (mFormat == null) {
            throw new InvalidParameterException("[" + mName + "] null format");
        }
        if (mFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
            mType = MediaData.TYPE_VIDEO;
        } else {
            mType = MediaData.TYPE_AUDIO;
        }

        // create input pipeline
        mInPipelineSurface = new SimplePipeline<>(mName + "#InPipeline.Surface",
                new Pipeline.DataAdapter<SurfaceData>() {
                    @Override
                    public SurfaceData onCreateData() {
                        return new SurfaceData();
                    }

                    @Override
                    public int onBindData(SurfaceData data) {
                        return CodecNode.this.bindInputDataSurface(data);
                    }

                    @Override
                    public void onReleaseData(SurfaceData data) {
                        CodecNode.this.releaseInputDataSurface(data);
                        mInPipelineSurface.stop();
                    }
                });
        mInPipeline = new BufferedPipeline<>(mName + "#InPipeline",
                new Pipeline.DataAdapter<MediaData>() {
                    private int id = hashCode();

                    @Override
                    public MediaData onCreateData() {
                        return new MediaData(id, mType);
                    }

                    @Override
                    public int onBindData(MediaData data) {
                        return CodecNode.this.bindInputData(data);
                    }

                    @Override
                    public void onReleaseData(MediaData data) {
                        CodecNode.this.releaseInputData(data);
                    }
                });

        // create output pipeline
        mOutPipeline = new BufferedPipeline<>(mName + "#OutPipeline",
                new Pipeline.DataAdapter<MediaData>() {
                    private int id = hashCode();

                    @Override
                    public MediaData onCreateData() {
                        return new MediaData(id, mType);
                    }

                    @Override
                    public int onBindData(MediaData data) {
                        return CodecNode.this.bindOutputData(data);
                    }

                    @Override
                    public void onReleaseData(MediaData data) {
                        CodecNode.this.releaseOutputData(data);
                    }
                },
                this::onOutDataCached,
                this::onOutDataReadyProcess);
    }

    @Override
    protected void onOpen() throws IOException {
        if (mIsEncoder) {
            mCodec = MediaCodec.createEncoderByType(mFormat.getString(MediaFormat.KEY_MIME));
            mCodec.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (mType == MediaData.TYPE_VIDEO) {
                mSurface = mCodec.createInputSurface();	// API >= 18
            }
        } else {
            mCodec = MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));
            mCodec.configure(mFormat, null, null, 0);
        }
        mCodec.start();
    }

    @Override
    protected void onClose() throws IOException {
        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    @Override
    public void startPipeline() {
        if (mType == MediaData.TYPE_VIDEO && mIsEncoder) {
            mInPipelineSurface.start();
        } else {
            mInPipeline.start();
        }
        mOutPipeline.start();
    }

    @Override
    public void stopPipeline() {
        if (mType == MediaData.TYPE_VIDEO && mIsEncoder) {
            mInPipelineSurface.stop();
        } else {
            mInPipeline.stop();
        }
        mOutPipeline.stop();
    }

    @Override
    public void waitPipelineFinish() {
        if (mType == MediaData.TYPE_VIDEO && mIsEncoder) {
            mInPipelineSurface.waitForFinish();
            Log.d(TAG, "[" + mName + "] pipeline [" + mInPipelineSurface.name() + "] finished");
        } else {
            mInPipeline.waitForFinish();
            Log.d(TAG, "[" + mName + "] pipeline [" + mInPipeline.name() + "] finished");
        }
        mOutPipeline.waitForFinish();
        Log.d(TAG, "[" + mName + "] pipeline [" + mOutPipeline.name() + "] finished");
    }

    public Pipeline<SurfaceData> inputPipelineSurface() {
        return mInPipelineSurface;
    }

    public Pipeline<MediaData> inputPipeline() {
        return mInPipeline;
    }

    public Pipeline<MediaData> outputPipeline() {
        return mOutPipeline;
    }

    private int bindInputData(MediaData data) {
        int index = mCodec.dequeueInputBuffer(10000);
        if (index < 0) {
            return RESULT_RETRY;
        } else {
            data.index = index;
            data.buffer = mCodec.getInputBuffer(data.index);
            assert data.buffer != null;

            data.buffer.clear();
            return RESULT_OK;
        }
    }

    private void releaseInputData(MediaData data) {
        if (data.index > 0) {
            // get info
            MediaCodec.BufferInfo info = data.info;

            // Log.d(TAG, "[" + mName + "] releaseInputData: isConfig = " + data.isConfig() +", info :" + info);
            if (data.isConfig()) {
                mCodec.queueInputBuffer(data.index, 0, 0, 0, 0); // we do not accept config
            } else {
                mCodec.queueInputBuffer(data.index, info.offset, info.size, info.presentationTimeUs, info.flags);
            }

            data.index = -1;
        }
    }

    private int bindInputDataSurface(SurfaceData data) {
        if (mSurface == null) {
            return RESULT_RETRY;
        } else {
            data.type = SurfaceData.TYPE_RECORD;
            data.surface = mSurface;
            return RESULT_OK;
        }
    }

    private void releaseInputDataSurface(SurfaceData data) {
        Log.d(TAG, "[" + mName + "] releaseInputDataSurface() begin");
        data.surface.release();
        data.surface = null;
        Log.d(TAG, "[" + mName + "] releaseInputDataSurface() end");
    }

    private int bindOutputData(MediaData data) {
        int index = mCodec.dequeueOutputBuffer(data.info, 10000);
        if (index < 0) {
            return RESULT_RETRY;
        } else {
            data.index = index;
            data.buffer = mCodec.getOutputBuffer(data.index);
            assert data.buffer != null;

            // set config data if necessary
            if (data.isConfig()) {
                data.format = mCodec.getOutputFormat();
            }

            data.buffer.position(data.info.offset);
            return RESULT_OK;
        }
    }

    private final Object mBlockLock = new Object();
    private long mLatestPresentationTimeUs = -1;
    private boolean mDropFrames = false;
    private void onOutDataCached(MediaData data) {
        mLatestPresentationTimeUs = data.info.presentationTimeUs;

        //Log.i("==MyTest==", "[" + mName + "] onOutDataCached()#");
        synchronized (mBlockLock) {
            mBlockLock.notifyAll();
        }
    }

    private int onOutDataReadyProcess(MediaData data) {
        if (mEnableOutput) {
            return BufferedPipeline.CB_ACCEPT;
        } else if (mBlockDurationUs > 0) {
            // drop non-key-frames
            if (mDropFrames) {
                if (!data.isKeyFrame()) {  // drop frames until we get a key frame.
                    return BufferedPipeline.CB_DROP;
                } else {
                    mDropFrames = false; // it's key frame, stop drop
                }
            }

            // block until buffered data duration is grater than max duration allowed
            while (!Thread.currentThread().isInterrupted() && (mLatestPresentationTimeUs - data.info.presentationTimeUs) <= mBlockDurationUs) {
                //Log.i("==MyTest==", "[" + mName + "] onOutDataReadyProcess()# " + (mLatestPresentationTimeUs - data.info.presentationTimeUs) + "/" + mBlockDurationUs);
                synchronized (mBlockLock) {
                    try {
                        mBlockLock.wait();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "onOutDataReadyProcess()# wait for block interrupted", e); // TODO remove exception log later
                    }
                }
            }

            // now there's too much data in cache, drop until not key frame
            mDropFrames = true;
            return BufferedPipeline.CB_DROP;
        } else {
            return BufferedPipeline.CB_DROP;
        }
    }

    private void releaseOutputData(MediaData data) {
        if (data.index > 0) {
            mCodec.releaseOutputBuffer(data.index, false);
            data.index = -1;
        }
    }

    public void enableOutput(boolean enable) {
        mEnableOutput = enable;
        synchronized (mBlockLock) {
            mBlockLock.notifyAll();
        }
    }

    public boolean enableOutput() {
        return mEnableOutput;
    }

    public void setBlockDurationUs(long durationUs) {
        mBlockDurationUs = durationUs;
        synchronized (mBlockLock) {
            mBlockLock.notifyAll();
        }
    }

    public long getBlockDurationUs() {
        return mBlockDurationUs;
    }
}
