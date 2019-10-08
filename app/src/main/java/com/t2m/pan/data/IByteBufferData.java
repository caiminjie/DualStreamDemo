package com.t2m.pan.data;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public interface IByteBufferData {
    int index();
    int index(int index);
    ByteBuffer buffer();
    ByteBuffer buffer(ByteBuffer buffer);
    MediaCodec.BufferInfo info();
    MediaCodec.BufferInfo info(MediaCodec.BufferInfo info);
    MediaFormat format();
    MediaFormat format(MediaFormat format);

    boolean isConfig();
    boolean isEos();
    boolean isKeyFrame();

    void markConfig();
    void markEos();
}
