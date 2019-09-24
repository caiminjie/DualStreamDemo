/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.npd.node.pipeline;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

/**
 * Encoder Node for h264
 */
public class H264EncoderNode extends CodecNode {
    public H264EncoderNode(String name, int width, int height, int bit, int frameRate) {
        super(name, true, createFormat(width, height, bit, frameRate));
    }

    private static MediaFormat createFormat(int width, int height, int bit, int frameRate) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);	// API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, bit);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);// 1 seconds between I-frames

        return format;
    }
}
