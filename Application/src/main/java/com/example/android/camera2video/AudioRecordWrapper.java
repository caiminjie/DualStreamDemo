package com.example.android.camera2video;

import android.media.AudioRecord;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class AudioRecordWrapper extends AudioRecord {

    private static final String TAG = "AudioRecordWrapper";
    private static final int CACHED_BUFF_SIZE = 10;

    public static final int SAMPLES_PER_FRAME = 1024;	// AAC, bytes/frame/channel
    private int startNum = 0;
    private boolean mStarted =false;
    private AudioThread mAudioThread;
    private static AudioRecordWrapper mAudioRecordWrapper = null;

    private final Object[] mCachedBuffs = new Object[CACHED_BUFF_SIZE];


    public static AudioRecordWrapper getInstance(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) {
        if (mAudioRecordWrapper == null) {
            synchronized (AudioRecordWrapper.class) {
                if (mAudioRecordWrapper == null) {
                    mAudioRecordWrapper = new AudioRecordWrapper(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
                }
            }
        }
        return mAudioRecordWrapper;

    }

    private AudioRecordWrapper(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) throws IllegalArgumentException {
        super(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
    }

    @Override
    public void startRecording() throws IllegalStateException {
        ++startNum;
        if(mStarted) {
            Log.d(TAG, "audio record is started !");
            return;
        }
        super.startRecording();
        mStarted = true;
        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
            mAudioThread.start();
        }
    }

    @Override
    public void stop() throws IllegalStateException {
        super.stop();
        mStarted = false;
    }

    @Override
    public void release() {
        super.release();
    }

    private void cache(ByteBuffer buff, int position) {
        synchronized (mCachedBuffs) {
            for (int i = 0; i < mCachedBuffs.length; i++) {
                // try find null item
                if (mCachedBuffs[i] == null) {
                    WeakReference<ByteBuffer> ref1 = new WeakReference<>(buff);
                    ref1.get().position(position);
                    mCachedBuffs[i] = ref1;
                    Log.d(TAG, "cache: ----- buff : " + buff + ",   cache buff : " +ref1.get()  );
                    return;
                }

                // try find released WeakReference
                WeakReference<ByteBuffer> ref = (WeakReference<ByteBuffer>) mCachedBuffs[i];
                if (ref.get() == null) {
                    Log.d(TAG, "cache: ref.get is null");
                    mCachedBuffs[i] = new WeakReference<>(buff);
                    return;
                }
            }
        }
    }

    public int readBuf(ByteBuffer audioBuffer) {
        synchronized (mCachedBuffs) {
            for (Object obj : mCachedBuffs) {
                if (obj == null) {
                    continue;
                }

                WeakReference<ByteBuffer> ref = (WeakReference<ByteBuffer>) obj;
                if ((audioBuffer = ref.get()) == null) {
                    Log.d(TAG, "readBuf: audio buffer is null");
                    continue;
                }
                Log.d(TAG, "readBuf:  audiobuffer is : " +audioBuffer );

                return 1024;//audioBuffer.position();
            }

            return 0;
        }
    }

    private class AudioThread extends Thread {
        @Override
        public void run() {
            final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
            int readBytes;
            while (mStarted) {
                buf.clear();
                readBytes = read(buf, SAMPLES_PER_FRAME);
                Log.d(TAG, "run: readBytes = " +readBytes);
                if (readBytes > 0) {
                    //buf.position(readBytes);
                    cache(buf,readBytes);
                }
            }
        }
    }

}
