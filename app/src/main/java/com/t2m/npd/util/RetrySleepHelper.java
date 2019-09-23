package com.t2m.npd.util;

import android.util.Log;

public class RetrySleepHelper {
    private static final String TAG = RetrySleepHelper.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final int DEF_MIN_RETRY_THRESHOLD = 0;
    private static final int DEF_MAX_RETRY_THRESHOLD = 2;
    private static final int DEF_SLEEP_STEP = 1;

    private String mName; // initialized by constructor
    private long mSleepTime = 0;
    private long mSleepStep; // initialized by constructor
    private int mRetryCount = 0;
    private int mMinThreshold; // initialized by constructor
    private int mMaxThreshold; // initialized by constructor

    /**
     * Helper class to sleep duration retry
     * @param name name for debug
     */
    public RetrySleepHelper(String name) {
        this(name, DEF_MIN_RETRY_THRESHOLD, DEF_MAX_RETRY_THRESHOLD, DEF_SLEEP_STEP);
    }

    /**
     * Helper class to sleep duration retry
     * @param name name for debug
     * @param minThreshold min threshold that should decrease sleep time
     * @param maxThreshold max threshold that should increase sleep time
     */
    @SuppressWarnings("unused")
    public RetrySleepHelper(String name, int minThreshold, int maxThreshold) {
        this(name, minThreshold, maxThreshold, DEF_SLEEP_STEP);
    }

    /**
     * Helper class to sleep duration retry
     * @param name name for debug
     * @param minThreshold min threshold that should decrease sleep time
     * @param maxThreshold max threshold that should increase sleep time
     * @param step sleep time change step
     */
    @SuppressWarnings("WeakerAccess")
    public RetrySleepHelper(String name, int minThreshold, int maxThreshold, long step) {
        mName = name;
        mMinThreshold = minThreshold;
        mMaxThreshold = maxThreshold;
        mSleepStep = step;

        // min threshold should no less than 1
        if (mMinThreshold < 1) {
            mMinThreshold = 1;
        }

        // max threshold should no less than max threshold
        if (mMaxThreshold < mMinThreshold) {
            mMaxThreshold = mMinThreshold;
        }
    }

    public void begin() {
        mRetryCount = 0;
    }

    public void end() {
        if (mRetryCount >= mMaxThreshold) {
            mSleepTime += mSleepStep;
            if (DEBUG) {
                Log.d(TAG, "[" + mName + "] mSleepTime update# mRetryCount: " + mRetryCount + ", mSleepTime: " + mSleepTime);
            }
        } else if (mRetryCount <= mMinThreshold && mSleepTime > 0) {
            mSleepTime -= mSleepStep;
            if (DEBUG) {
                Log.d(TAG, "[" + mName + "] mSleepTime update# mRetryCount: " + mRetryCount + ", mSleepTime: " + mSleepTime);
            }
        }
    }

    public void sleep() {
        mRetryCount ++;

        if (DEBUG) {
            Log.d(TAG, "[" + mName + "] sleep()# mRetryCount: " + mRetryCount + ", mSleepTime: " + mSleepTime);
        }

        // do sleep
        if (mSleepTime <= 0) {
            Thread.yield();
        } else {
            try {
                Thread.sleep(mSleepTime);
            } catch (InterruptedException e) {
                // InterruptedException will consume interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unused")
    public void reset() {
        mSleepTime = 0;
    }
}
