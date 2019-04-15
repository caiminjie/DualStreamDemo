/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.flow.path;

import android.util.Log;

import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;


/**
 * Data path from Node and Node
 */
public abstract class Path {
    private static final String TAG = Path.class.getSimpleName();

    private static final boolean DEBUG_RETRY = true;

    private static final int PROCESS_PLUG_2_PLUG = 0;
    private static final int PROCESS_PLUG_2_SLOT = 1;
    private static final int PROCESS_SLOT_2_PLUG = 2;
    private static final int PROCESS_SLOT_2_SLOT = 3;

    String mName;
    Plug mPlugReader;
    Plug mPlugWriter;
    Slot mSlotReader;
    Slot mSlotWriter;
    private int mProcessType;
    private Path.ProcessThread mProcessThread = null;

    // retry helper to improve retry performance
    RetrySleepHelper mReaderRetryHelper;
    RetrySleepHelper mWriterRetryHelper;

    private boolean mCanceled = false;

    Path(String name, Plug reader, Plug writer) {
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mPlugReader = reader;
        mPlugWriter = writer;
        mProcessType = PROCESS_PLUG_2_PLUG;

        initRetryHelper();
    }

    Path(String name, Plug reader, Slot writer) {
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mPlugReader = reader;
        mSlotWriter = writer;
        mProcessType = PROCESS_PLUG_2_SLOT;

        initRetryHelper();
    }

    Path(String name, Slot reader, Plug writer) {
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mSlotReader = reader;
        mPlugWriter = writer;
        mProcessType = PROCESS_SLOT_2_PLUG;

        initRetryHelper();
    }

    Path(String name, Slot reader, Slot writer) {
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mSlotReader = reader;
        mSlotWriter = writer;
        mProcessType = PROCESS_SLOT_2_SLOT;

        initRetryHelper();
    }

    private void initRetryHelper() {
        mReaderRetryHelper = new RetrySleepHelper(mName + "#Reader");
        mWriterRetryHelper = new RetrySleepHelper(mName + "#Writer");
    }

    public String getName() {
        return mName;
    }

    public synchronized boolean processAsync() {
        if (!mCanceled && mProcessThread == null) {
        /* MODIFIED-END by Fan.Hu,BUG-5709670*/
            mProcessThread = new Path.ProcessThread(mName);
            mProcessThread.start();
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("unused")
    public boolean processSync() {
        if (processAsync()) {
            waitForFinish();
            return true;
        } else {
            return false;
        }
    }

    public void waitForFinish() {
        if (mProcessThread != null) {
            try {
                mProcessThread.join();
            } catch (InterruptedException e) {
                Log.w(TAG, "[" + mName + "] wait failed.", e);

                // interrupt also cancel join. wait until thread finished.
                while (!mProcessThread.isFinished()) {
                    Thread.yield();
                }
            }
        }
    }

    private void process() {
        try {
            switch (mProcessType) {
                case PROCESS_PLUG_2_PLUG:
                    processPlug2Plug();
                    break;
                case PROCESS_PLUG_2_SLOT:
                    processPlug2Slot();
                    break;
                case PROCESS_SLOT_2_PLUG:
                    processSlot2Plug();
                    break;
                case PROCESS_SLOT_2_SLOT:
                    processSlot2Slot();
                    break;
                default:
                    Log.e(TAG, "[" + mName + "] invalid process type: " + mProcessType);
            }
        } catch (Exception e) {
            /* FIXME The issue should be fixed by the modification in method waitForFinish().
             * However, no time for further debug du to scheduler. So add "try ... catch ..."
             * to monitor whether issues if fully fixed, and this code should be removed in the future.
             */
            Log.e(TAG, "[" + mName + "] process()# exception detected.", e);
        }
    }

    protected abstract void processPlug2Plug();
    protected abstract void processPlug2Slot();
    protected abstract void processSlot2Plug();
    protected abstract void processSlot2Slot();

    public synchronized void cancel() {
        mCanceled = true;

        if (mProcessThread != null) {
            mProcessThread.interrupt();
        }
    }

    @SuppressWarnings("unused")
    public boolean isCanceled() {
        return mCanceled;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isThreadInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    static void interruptThread() {
        Thread.currentThread().interrupt();
    }

    int getPlugData(Plug plug, DataHolder holder, RetrySleepHelper retryHelper) {
        int result = Node.RESULT_ERROR;

        // receive data
        retryHelper.begin();
        while (!isThreadInterrupted() && (result = plug.getData(holder)) == Node.RESULT_RETRY) {
            // loop in case retry
            if (DEBUG_RETRY) {
                Log.w(TAG, "[" + mName + "] getPlugData()# RESULT_RETRY"); // TODO plug info later
            }
            retryHelper.sleep();
        }
        retryHelper.end();

        // add release
        if (result == Node.RESULT_OK) {
            holder.data.addReleaseListener(plug::release);
        }
        return result;
    }

    int setSlotData(Slot slot, DataHolder holder, RetrySleepHelper retryHelper) {
        int result = Node.RESULT_ERROR;

        retryHelper.begin();
        while (!isThreadInterrupted() && (result = slot.setData(holder)) == Node.RESULT_RETRY) {
            // loop in case retry
            if (DEBUG_RETRY) {
                Log.w(TAG, "[" + mName + "] setSlotData()# RESULT_RETRY");
            }
            retryHelper.sleep();
        }
        retryHelper.end();

        return result;
    }

    private class ProcessThread extends Thread {
        private boolean mFinished = false;

        ProcessThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            process();

            mFinished = true;
        }

        @Override
        public synchronized void start() {
            mFinished = false;
            super.start();
        }

        public boolean isFinished() {
            return mFinished;
        }
    }

    private static class RetrySleepHelper {
        private static final boolean DEBUG = false;

        private static final int DEF_MIN_RETRY_THRESHOLD = 1;
        private static final int DEF_MAX_RETRY_THRESHOLD = 3;
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
        RetrySleepHelper(String name) {
            this(name, DEF_MIN_RETRY_THRESHOLD, DEF_MAX_RETRY_THRESHOLD, DEF_SLEEP_STEP);
        }

        /**
         * Helper class to sleep duration retry
         * @param name name for debug
         * @param minThreshold min threshold that should decrease sleep time
         * @param maxThreshold max threshold that should increase sleep time
         */
        @SuppressWarnings("unused")
        RetrySleepHelper(String name, int minThreshold, int maxThreshold) {
            this(name, minThreshold, maxThreshold, DEF_SLEEP_STEP);
        }

        /**
         * Helper class to sleep duration retry
         * @param name name for debug
         * @param minThreshold min threshold that should decrease sleep time
         * @param maxThreshold max threshold that should increase sleep time
         * @param step sleep time change step
         */
        RetrySleepHelper(String name, int minThreshold, int maxThreshold, long step) {
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
            if (mRetryCount > mMaxThreshold) {
                mSleepTime += mSleepStep;
                if (DEBUG) {
                    Log.d(TAG, "[" + mName + "] mSleepTime update# mRetryCount: " + mRetryCount + ", mSleepTime: " + mSleepTime);
                }
            } else if (mRetryCount < mMinThreshold && mSleepTime > 0) {
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

        public void reset() {
            mSleepTime = 0;
        }
    }
}
