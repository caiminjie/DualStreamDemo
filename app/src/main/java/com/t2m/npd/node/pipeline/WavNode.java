package com.t2m.npd.node.pipeline;

import android.util.Log;

import com.t2m.npd.Pipeline;
import com.t2m.npd.data.MediaData;
import com.t2m.npd.data.WavData;
import com.t2m.npd.node.PipelineNode;
import com.t2m.npd.pipeline.SimplePipeline;
import com.t2m.npd.util.Utils;
import com.t2m.npd.util.WavFileWriter;

import java.io.File;
import java.io.IOException;

public class WavNode extends PipelineNode<MediaData> {
    private static final String TAG = WavNode.class.getSimpleName();

    private WavFileWriter mFile = null;
    private String mPath;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;
    private boolean mEnableSave = true;

    private SimplePipeline<MediaData> mPipeline;

    private final Object mFileAccessLock = new Object();

    public WavNode(String name, String path, int sampleRate, int channelCount, int audioFormat) {
        super(name);
        mPath = path;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mAudioFormat = audioFormat;

        // If you want use BufferedPipeline, you should block data bind until previous data is released. Or it will cause memory leak.
        mPipeline = new SimplePipeline<>(
                mName + "#Pipeline",
                new Pipeline.DataAdapter<MediaData>() {
                    private int id = this.hashCode();

                    @Override
                    public WavData onCreateData() {
                        return new WavData(id);
                    }

                    @Override
                    public int onBindData(MediaData data) {
                        synchronized (mFileAccessLock) {
                            ((WavData) data).bind(mEnableSave ? mFile : null);
                        }
                        return RESULT_OK;
                    }

                    @Override
                    public void onReleaseData(MediaData data) {
                        data.buffer = null;
                    }
                });
    }

    @Override
    protected void onOpen() throws IOException {
        synchronized (mFileAccessLock) {
            if (mPath == null) {
                Log.e(TAG, "Cannot open node while path is null");
                return;
            }
            File file = new File(mPath);
            if (file.exists()) {
                file.deleteOnExit();
            }

            mFile = new WavFileWriter(mPath, mSampleRate, mChannelCount, Utils.getBitsPerSample(mAudioFormat));
            mFile.open();
        }
    }

    @Override
    protected void onClose() throws IOException {
        synchronized (mFileAccessLock) {
            mFile.close();
            mFile = null;
        }
    }

    @Override
    public void startPipeline() {
        mPipeline.start();
    }

    @Override
    public void stopPipeline() {
        mPipeline.stop();
    }

    @Override
    public void waitPipelineFinish() {
        mPipeline.waitForFinish();
    }

    public SimplePipeline<MediaData> getPipeline() {
        return mPipeline;
    }

    /**
     * set save enabled/disabled
     * @param enable true: enabled (default); false: disabled
     * @param path path for file to be saved. Only available when enabled is true
     */
    public void enableSave(boolean enable, String path) {
        synchronized (mFileAccessLock) {
            mEnableSave = enable;
            if (mEnableSave) {
                mPath = path;
            }
        }
    }

    public void newSegment(String path) {
        synchronized (mFileAccessLock) {
            try {
                // close file
                onClose();

                // update path and reopen
                mPath = path;
                onOpen();
            } catch (IOException e) {
                Log.e(TAG, "newSegment() failed.");
            }
        }
    }
}
