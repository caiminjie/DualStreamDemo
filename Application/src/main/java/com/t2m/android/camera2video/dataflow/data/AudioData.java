/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.android.camera2video.dataflow.data;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * AudioData
 */
public class AudioData {
    private static final String TAG = AudioData.class.getSimpleName();

    public static final String KEY_DATA_BYTE_BUFFER = "key-data-byte-buffer";
    public static final String KEY_DATA_BUFFER_INFO = "key-data-buffer-info";
    public static final String KEY_CONFIG_MEDIA_FORMAT = "key-config-media-format";

    public static void createBuff(Data data) {
        byte[] buff = new byte[512*1024];
        setBuffer(data, ByteBuffer.wrap(buff, 0, buff.length));
        setBufferInfo(data, new MediaCodec.BufferInfo());
    }

    public static void copyData(Data fromData, Data toData) {
        ByteBuffer writeBuffer = AudioData.getBuffer(toData);
        assert writeBuffer != null;
        MediaCodec.BufferInfo writeInfo = AudioData.getBufferInfo(toData);
        assert writeInfo != null;
        ByteBuffer readBuffer = AudioData.getBuffer(fromData);
        assert readBuffer != null;
        MediaCodec.BufferInfo readInfo = AudioData.getBufferInfo(fromData);
        assert readInfo != null;

        if (AudioData.hasConfigFormat(fromData)) {
            AudioData.setConfigFormat(toData, AudioData.getConfigFormat(fromData));
        } else {
            writeBuffer.clear();
            writeBuffer.put(readBuffer);
        }
        writeInfo.offset = 0;
        writeInfo.size = readInfo.size;
        writeInfo.presentationTimeUs = readInfo.presentationTimeUs;
        writeInfo.flags = readInfo.flags;
    }

    public static ByteBuffer setBuffer(Data data, ByteBuffer buffer) {
        return data.set(KEY_DATA_BYTE_BUFFER, buffer);
    }

    public static ByteBuffer getBuffer(Data data) {
        return data.get(KEY_DATA_BYTE_BUFFER, null);
    }

    public static MediaCodec.BufferInfo setBufferInfo(Data data, MediaCodec.BufferInfo info) {
        return data.set(KEY_DATA_BUFFER_INFO, info);
    }

    public static MediaCodec.BufferInfo getBufferInfo(Data data) {
        return data.get(KEY_DATA_BUFFER_INFO, null);
    }

    public static MediaFormat setConfigFormat(Data data, MediaFormat format) {
        return data.set(KEY_CONFIG_MEDIA_FORMAT, format);
    }

    public static MediaFormat getConfigFormat(Data data) {
        return data.get(KEY_CONFIG_MEDIA_FORMAT, null);
    }

    public static boolean hasConfigFormat(Data data) {
        return data.containsKey(KEY_CONFIG_MEDIA_FORMAT);
    }

    public static void markConfig(Data data) {
        MediaCodec.BufferInfo info = data.get(KEY_DATA_BUFFER_INFO, null);
        if (info != null) {
            info.flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        } else {
            Log.e(TAG, "setConfig()# failed due to info == null");
        }
    }

    public static boolean isConfig(Data data) {
        MediaCodec.BufferInfo info = data.get(KEY_DATA_BUFFER_INFO, null);
        return info != null && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
    }

    public static void markEof(Data data) {
        MediaCodec.BufferInfo info = data.get(KEY_DATA_BUFFER_INFO, null);
        if (info != null) {
            info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        } else {
            Log.e(TAG, "markEof()# failed due to info == null");
        }
    }

    public static boolean isEof(Data data) {
        MediaCodec.BufferInfo info = data.get(KEY_DATA_BUFFER_INFO, null);
        return info != null && (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    }

    public static void clearFlags(Data data) {
        MediaCodec.BufferInfo info = data.get(KEY_DATA_BUFFER_INFO, null);
        if (info != null) {
            info.flags = 0;
        }
    }
}
