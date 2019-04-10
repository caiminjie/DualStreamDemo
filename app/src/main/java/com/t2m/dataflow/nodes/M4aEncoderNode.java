/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.nodes;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

/**
 * Encoder Node for M4A
 */
public class M4aEncoderNode extends CodecNode {
    public M4aEncoderNode() {
        super(true, createFormat());
    }

    private static MediaFormat createFormat() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 48000);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        return format;
    }
}
