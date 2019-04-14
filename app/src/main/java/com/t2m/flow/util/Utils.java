/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.util;

import android.media.AudioFormat;

/**
 * Utils
 */
public class Utils {
    public static int getChannelConfig(int channelCount) {
        if (channelCount == 1) {
            return AudioFormat.CHANNEL_OUT_MONO;
        } else if (channelCount == 2) {
            return AudioFormat.CHANNEL_OUT_STEREO;
        } else {
            return AudioFormat.CHANNEL_OUT_DEFAULT;
        }
    }

    public static int getBytesPerSample(int audioFormat) {
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:
                return 1;
            case AudioFormat.ENCODING_PCM_16BIT:
            case AudioFormat.ENCODING_IEC61937:
            case AudioFormat.ENCODING_DEFAULT:
                return 2;
            case AudioFormat.ENCODING_PCM_FLOAT:
                return 4;
            case AudioFormat.ENCODING_INVALID:
            default:
                throw new IllegalArgumentException("Bad audio format " + audioFormat);
        }
    }

    public static int getFrameSize(int channelCount, int audioFormat) {
        return channelCount * (getBytesPerSample(audioFormat));
    }
}
