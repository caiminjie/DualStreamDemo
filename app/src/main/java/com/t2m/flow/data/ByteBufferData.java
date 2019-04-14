package com.t2m.flow.data;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import com.t2m.flow.util.Cache;

import java.nio.ByteBuffer;

public class ByteBufferData extends Data {
    private static final String TAG = ByteBufferData.class.getSimpleName();

    private static final String KEY_DATA_BYTE_BUFFER = "key-data-byte-buffer";
    private static final String KEY_DATA_BUFFER_INFO = "key-data-buffer-info";
    private static final String KEY_CONFIG_MEDIA_FORMAT = "key-config-media-format";

    protected ByteBufferData mParent = null;

    private static Cache<ByteBufferData> sCache = new Cache<>(
            "ByteBufferData",
            ByteBufferData::new,
            null,
            null);

    private ByteBufferData() {
        super();
    }

    @Override
    public void setParent(Data data) {
        super.setParent(data);
        mParent = (ByteBufferData) super.mParent;
    }

    public static ByteBufferData create() {
        return sCache.get();  // get from cache to improve performance
    }

    @SuppressWarnings("unused")
    public void copyDataTo(ByteBufferData toData) {
        ByteBufferData.copyData(this, toData);
    }

    @SuppressWarnings("unused")
    public void copyDataFrom(ByteBufferData fromData) {
        ByteBufferData.copyData(fromData, this);
    }

    public static void copyData(ByteBufferData fromData, ByteBufferData toData) {
        ByteBuffer toBuffer = toData.getBuffer();
        assert toBuffer != null;
        MediaCodec.BufferInfo toInfo = toData.getBufferInfo();
        assert toInfo != null;
        ByteBuffer fromBuffer = fromData.getBuffer();
        assert fromBuffer != null;
        MediaCodec.BufferInfo fromInfo = fromData.getBufferInfo();
        assert fromInfo != null;

        if (fromData.hasConfigFormat()) {
            toData.setConfigFormat(fromData.getConfigFormat());
        } else {
            toBuffer.clear();
            toBuffer.put(fromBuffer);
        }
        toInfo.offset = 0;
        toInfo.size = fromInfo.size;
        toInfo.presentationTimeUs = fromInfo.presentationTimeUs;
        toInfo.flags = fromInfo.flags;
    }

    public ByteBuffer setBuffer(ByteBuffer buffer) {
        return set(KEY_DATA_BYTE_BUFFER, buffer);
    }

    public ByteBuffer getBuffer() {
        return get(KEY_DATA_BYTE_BUFFER, null);
    }

    public MediaCodec.BufferInfo setBufferInfo(MediaCodec.BufferInfo info) {
        return set(KEY_DATA_BUFFER_INFO, info);
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        return get(KEY_DATA_BUFFER_INFO, null);
    }

    public MediaFormat setConfigFormat(MediaFormat format) {
        return set(KEY_CONFIG_MEDIA_FORMAT, format);
    }

    public MediaFormat getConfigFormat() {
        return get(KEY_CONFIG_MEDIA_FORMAT, null);
    }

    public boolean hasConfigFormat() {
        return hasKey(KEY_CONFIG_MEDIA_FORMAT);
    }

    public void markConfig() {
        MediaCodec.BufferInfo info = get(KEY_DATA_BUFFER_INFO, null);
        if (info != null) {
            info.flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
        } else {
            Log.e(TAG, "markConfig()# failed due to info == null");
        }
    }

    public boolean isConfig() {
        MediaCodec.BufferInfo info = get(KEY_DATA_BUFFER_INFO, null);
        return info != null && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
    }

    @SuppressWarnings("unused")
    public void markEos() {
        MediaCodec.BufferInfo info = get(KEY_DATA_BUFFER_INFO, null);
        if (info != null) {
            info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        } else {
            Log.e(TAG, "markEof()# failed due to info == null");
        }
    }

    public boolean isEos() {
        MediaCodec.BufferInfo info = get(KEY_DATA_BUFFER_INFO, null);
        return info != null && (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    }

    @SuppressWarnings("unused")
    public void clearFlags() {
        MediaCodec.BufferInfo info = get(KEY_DATA_BUFFER_INFO, null);
        if (info != null) {
            info.flags = 0;
        }
    }

    @Override
    public int release() {
        int result = super.release();
        sCache.put(this);  // cache myself
        return result;
    }
}
