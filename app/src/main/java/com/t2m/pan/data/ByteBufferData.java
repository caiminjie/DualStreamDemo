package com.t2m.pan.data;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.t2m.pan.Data;

import java.nio.ByteBuffer;

public class ByteBufferData extends AbstractReadableWriteableData implements IByteBufferData {
    //private final String TAG = AudioData.class.getSimpleName() + "#" + this.hashCode();
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int index;
    private ByteBuffer buffer;
    private MediaFormat format;

    public ByteBufferData(int id, int type) {
        super(id, type);
        this.index = -1;
        this.buffer = null;
        this.format = null;
    }

    @Override
    public void markConfig() {
        info.flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
    }

    @Override
    public boolean isConfig() {
        return (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
    }

    @Override
    public void markEos() {
        info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }

    @Override
    public boolean isEos() {
        return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    @Override
    public boolean isKeyFrame() {
        return (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public int index(int index) {
        return this.index = index;
    }

    @Override
    public ByteBuffer buffer() {
        return buffer;
    }

    @Override
    public ByteBuffer buffer(ByteBuffer buffer) {
        return this.buffer = buffer;
    }

    @Override
    public MediaCodec.BufferInfo info() {
        return info;
    }

    @Override
    public MediaCodec.BufferInfo info(MediaCodec.BufferInfo info) {
        this.info.presentationTimeUs = info.presentationTimeUs;
        this.info.flags = info.flags;
        this.info.offset = info.offset;
        this.info.size = info.size;

        return this.info;
    }

    @Override
    public MediaFormat format() {
        return format;
    }

    @Override
    public MediaFormat format(MediaFormat format) {
        return this.format = format;
    }

    @Override
    public int write(byte[] buff, int offset, int len) {
        buffer.clear();
        buffer.put(buff, offset, len);
        return Data.RESULT_OK;
    }

    @Override
    public int read(byte[] buff, int offset, int len) {
        buffer.get(buff, offset, len);
        return Data.RESULT_OK;
    }
}
