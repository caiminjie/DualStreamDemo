package com.t2m.npd.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TODO check whether need this class. It's a version with buffer, maybe FileChannel is better.
 */
public class WavFileWriterBk {
    private static final int HEADER_SIZE = 44;
    private static final short WAVE_FORMAT_PCM = 0x0001;

    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB

    private RandomAccessFile mFile = null;
    private ByteBuffer mBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    private String mPath;
    private int mSampleRate;
    private int mChannelCount;
    private int mBitsPerSample;
    private int mSize;

    public WavFileWriterBk(String path, int sampleRate, int channelCount, int bitsPerSample) {
        mPath = path;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mBitsPerSample = bitsPerSample;
        mSize = 0;
    }

    @SuppressWarnings("unused")
    public boolean isOpened() {
        return mFile != null;
    }

    public void open () throws IOException {
        if (mFile != null) {
            return;
        }

        mFile = new RandomAccessFile(mPath, "rw");
        mFile.setLength(HEADER_SIZE);
        mFile.seek(HEADER_SIZE);
    }

    public void close() throws IOException {
        if (mFile == null) {
            return;
        }

        flush();

        writeHeaders(mSize, mChannelCount, mSampleRate, mBitsPerSample);
        mFile.close();
        mFile = null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public int append(byte[] buff, int offset, int len) throws IOException {
        int buffRemaining = mBuffer.remaining();
        if (len < buffRemaining) {
            mBuffer.put(buff, offset, len);
        } else {
            mBuffer.put(buff, offset, buffRemaining);
            flush();

            len -= buffRemaining;
            if (len > 0) {
                mBuffer.put(buff, buffRemaining, len);
            }
        }

        mSize += len;
        return len;
    }

    private void flush() throws IOException {
        mFile.write(mBuffer.array(), 0, mBuffer.position());
        mBuffer.clear();
    }

    private void writeHeaders(int dataSize, int numChannels, int sampleRate, int bitsPerSample) throws IOException {
        // write header
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        /* write RIFF header */
        buffer.put("RIFF".getBytes(), 0, 4);
        //buffer.putInt(60 + dataSize);
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(), 0, 4);

        /* write fmt header */
        buffer.put("fmt ".getBytes(), 0, 4);
        buffer.putInt(16);
        buffer.putShort(WAVE_FORMAT_PCM);
        buffer.putShort((short) numChannels);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * numChannels * (bitsPerSample / 8));
        buffer.putShort((short) (numChannels * (bitsPerSample / 8)));
        buffer.putShort((short) bitsPerSample);

        /* write data header */
        buffer.put("data".getBytes(), 0, 4);
        buffer.putInt(dataSize);

        // save header
        long pos = mFile.getFilePointer();
        mFile.seek(0);
        mFile.write(buffer.array(), 0, buffer.position());
        mFile.seek(pos);
    }
}
