package com.t2m.npd.data;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.t2m.npd.Data;

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

    public void markConfig() {
        info.flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
    }

    public boolean isConfig() {
        return (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
    }

    public void markEos() {
        info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }

    public boolean isEos() {
        return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    public void clearFlags() {
        info.flags = 0;
    }
}
