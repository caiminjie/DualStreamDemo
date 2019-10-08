package com.t2m.pan.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class WavFileWriter {
    private static final int HEADER_SIZE = 44;
    private static final short WAVE_FORMAT_PCM = 0x0001;

    private RandomAccessFile mFile;
    private FileChannel mFileChannel;

    private String mPath;
    private int mSampleRate;
    private int mChannelCount;
    private int mBitsPerSample;
    private int mSize;

    public WavFileWriter(String path, int sampleRate, int channelCount, int bitsPerSample) {
        mPath = path;
        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mBitsPerSample = bitsPerSample;
        mSize = 0;
    }

    @SuppressWarnings("unused")
    public boolean isOpened() {
        return mFileChannel != null && mFileChannel.isOpen();
    }

    public void open () throws IOException {
        mFile = new RandomAccessFile(mPath, "rw");
        mFileChannel = mFile.getChannel();
    }

    public void close() throws IOException {
        writeHeaders(mSize, mChannelCount, mSampleRate, mBitsPerSample);
        mFileChannel.close();
        mFile.close();
    }

    @SuppressWarnings("UnusedReturnValue")
    public int append(byte[] buff, int offset, int len) throws IOException {
        ByteBuffer buffer = mFileChannel.map(FileChannel.MapMode.READ_WRITE, HEADER_SIZE + mSize, len);
        mSize += len;
        buffer.put(buff, offset, len);
        return len;
    }

    public int append(ByteBuffer buffer, int offset, int len) throws IOException {
        return append(buffer.array(), offset, len);
    }

    @SuppressWarnings("unused")
    public ByteBuffer getAppendBuffer(int len) throws IOException {
        ByteBuffer buffer = mFileChannel.map(FileChannel.MapMode.READ_WRITE, HEADER_SIZE + mSize, len);
        mSize += len;
        return buffer;
    }

    private void writeHeaders(int dataSize, int numChannels, int sampleRate, int bitsPerSample) throws IOException {
        // get channel
        boolean shouldClose;
        RandomAccessFile file;
        if (mFileChannel.isOpen()) {
            file = mFile;
            shouldClose = false;
        } else {
            file = new RandomAccessFile(mPath, "rw"); // TODO don't know why file is closed here. Reopen for walk around.
            shouldClose = true;
        }

        // write header
        try (FileChannel channel = file.getChannel()) {
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_SIZE);
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
        }

        // close file if necessary
        if (shouldClose) {
            file.close();
        }
    }
}
