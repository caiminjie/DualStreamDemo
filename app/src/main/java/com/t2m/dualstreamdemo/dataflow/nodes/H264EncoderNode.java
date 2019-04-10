/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dualstreamdemo.dataflow.nodes;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

/**
 * Encoder Node for M4A
 */
public class H264EncoderNode extends CodecNode {
    public H264EncoderNode(String type, int width, int height, int bit, int frameRate) {
        super(true, createFormat(type, width, height, bit, frameRate));
    }

    private static MediaFormat createFormat(String type, int width, int height, int bit, int frameRate) {
        MediaFormat format = MediaFormat.createVideoFormat(type, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);	// API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, bit);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);// 1 seconds between I-frames

        return format;
    }
}
