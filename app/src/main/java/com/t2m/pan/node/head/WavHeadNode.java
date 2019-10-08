package com.t2m.pan.node.head;

import android.util.Log;

import com.t2m.pan.Data;
import com.t2m.pan.data.ByteBufferData;
import com.t2m.pan.pan;
import com.t2m.pan.util.Utils;
import com.t2m.pan.util.WavFileWriter;

import java.io.File;
import java.io.IOException;

public class WavHeadNode extends HeadNode<ByteBufferData> {
    private static final String TAG = WavHeadNode.class.getSimpleName();

    private int id = hashCode();
    private WavFileWriter mFile = null;
    private String mPath;
    private int mSampleRate;
    private int mChannelCount;
    private int mAudioFormat;
    private boolean mEnableSave = true;

    private final Object mFileAccessLock = new Object();

    private class WavData extends ByteBufferData {
        public WavData(int id) {
            super(id, Data.TYPE_AUDIO);
        }

        @Override
        public int write(byte[] buff, int offset, int len) {
            synchronized (mFileAccessLock) {
                try {
                    return mFile.append(buff, offset, len);
                } catch (IOException e) {
                    Log.e(TAG, "write()# write data failed.", e);
                    return 0;
                }
            }
        }
    }

    public WavHeadNode(String name, String path, int sampleRate, int channelCount, int audioFormat) {
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
            } else {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        Log.w(TAG, "Create dir [" + parent.getAbsolutePath() + "] failed.");
                    }
                }
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

    protected ByteBufferData onCreateData() {
        return new WavData(id);
    }

    protected int onBindData(ByteBufferData data) {
        return pan.RESULT_OK;
    }

    protected int onReleaseData(ByteBufferData data) {
        return pan.RESULT_OK;
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
