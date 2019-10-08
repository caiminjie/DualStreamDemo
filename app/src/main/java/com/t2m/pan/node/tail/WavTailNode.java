package com.t2m.pan.node.tail;

import android.util.Log;

import com.t2m.pan.Data;
import com.t2m.pan.data.ByteBufferData;
import com.t2m.pan.pan;
import com.t2m.pan.util.Utils;
import com.t2m.pan.util.WavFileWriter;

import java.io.File;
import java.io.IOException;

public class WavTailNode extends TailNode<ByteBufferData> {
    private static final String TAG = WavTailNode.class.getSimpleName();

    private WavFileWriter mFile = null;
    private String mPath;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;
    private boolean mEnableSave = true;

    private final Object mFileAccessLock = new Object();

    public WavTailNode(String name, String path, int sampleRate, int channelCount, int audioFormat) {
        super(name);

        mPath = path;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mAudioFormat = audioFormat;
    }

    @Override
    public boolean isOpened() {
        synchronized (mFileAccessLock) {
            return mFile != null;
        }
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

    protected int onProcessData(ByteBufferData data) {
        synchronized (mFileAccessLock) {
            try {
                data.read(mFile.getAppendBuffer(data.info().size));
            } catch (IOException e) {
                Log.e(TAG, "encode()# write data error", e);
                return pan.RESULT_ERROR;
            }
            return pan.RESULT_OK;
        }
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
