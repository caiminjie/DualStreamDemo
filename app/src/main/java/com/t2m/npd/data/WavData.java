package com.t2m.npd.data;

import android.util.Log;

import com.t2m.npd.Node;
import com.t2m.npd.util.WavFileWriter;

import java.io.IOException;

public class WavData extends MediaData {
    private static final String TAG = WavData.class.getSimpleName();

    private WavFileWriter mFile;

    public WavData(int id) {
        super(id, TYPE_AUDIO);
    }

    public void bind(WavFileWriter file) {
        mFile = file;
    }

    @Override
    public int write(byte[] buff, int offset, int len) {
        if (mFile == null) {
            // drop buff
            return Node.RESULT_OK;
        }

        try {
            mFile.append(buff, offset, len);
            return Node.RESULT_OK;
        } catch (IOException e) {
            Log.e(TAG, "cannot write data with len: " + len);
            return Node.RESULT_ERROR;
        }
    }
}
