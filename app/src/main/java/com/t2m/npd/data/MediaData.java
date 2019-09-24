package com.t2m.npd.data;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.t2m.npd.Data;
import com.t2m.npd.Node;

import java.nio.ByteBuffer;

public class MediaData extends Data {
    //private final String TAG = AudioData.class.getSimpleName() + "#" + this.hashCode();
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    public int id;
    public int type;
    public int index;
    public ByteBuffer buffer;
    public MediaFormat format;

    public MediaData(int id, int type) {
        this.id = id;
        this.type = type;
        this.index = -1;
        this.buffer = null;
        this.format = null;
    }

    @SuppressWarnings("unused")
    public void markConfig() {
        info.flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
    }

    public boolean isConfig() {
        return (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
    }

    @SuppressWarnings("unused")
    public void markEos() {
        info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }

    @SuppressWarnings("unused")
    public boolean isEos() {
        return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    @SuppressWarnings("unused")
    public void clearFlags() {
        info.flags = 0;
    }

    public boolean isKeyFrame() {
        return (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
    }

    public int write(byte[] buff, int offset, int len) {
        buffer.clear();
        buffer.put(buff, offset, len);
        return Node.RESULT_OK;
    }

    @SuppressWarnings("unused")
    public int read(byte[] buff, int offset, int len) {
        buffer.get(buff, offset, len);
        return Node.RESULT_OK;
    }
}
