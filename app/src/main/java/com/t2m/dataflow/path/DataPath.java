/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.path;

import android.util.Log;

import com.t2m.dualstreamdemo.dataflow.data.Data;
import com.t2m.dualstreamdemo.dataflow.node.BufferedReader;
import com.t2m.dualstreamdemo.dataflow.node.BufferedWriter;
import com.t2m.dualstreamdemo.dataflow.node.DataNode;
import com.t2m.dualstreamdemo.dataflow.node.DirectReader;
import com.t2m.dualstreamdemo.dataflow.node.DirectWriter;

/**
 * Data path from Node and Node
 */
public abstract class DataPath {
    private static final String TAG = DataPath.class.getSimpleName(); // MODIFIED by Fan.Hu, 2018-02-09,BUG-5727229

    private static final boolean DEBUG_RETRY = true; // MODIFIED by Fan.Hu, 2018-01-19,BUG-5709670

    private static final int PROCESS_BUFFERED_2_BUFFERED = 0;
    private static final int PROCESS_BUFFERED_2_DIRECT = 1;
    private static final int PROCESS_DIRECT_2_BUFFERED = 2;
    private static final int PROCESS_DIRECT_2_DIRECT = 3;

    private String mName;
    private BufferedReader mBufferedReader;
    private BufferedWriter mBufferedWriter;
    private DirectReader mDirectReader;
    private DirectWriter mDirectWriter;
    private int mProcessType;
    private DataPath.ProcessThread mProcessThread = null;

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
    private RetrySleepHelper mReaderRetryHelper;
    private RetrySleepHelper mWriterRetryHelper;
    /* MODIFIED-END by Fan.Hu,BUG-5709670*/

    private boolean mCanceled = false; // MODIFIED by Fan.Hu, 2018-01-20,BUG-5709670

    DataPath(String name, BufferedReader reader, BufferedWriter writer) {
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mBufferedReader = reader;
        mBufferedWriter = writer;
        mProcessType = PROCESS_BUFFERED_2_BUFFERED;

        initRetryHelper(); // MODIFIED by Fan.Hu, 2018-01-19,BUG-5709670
    }

    DataPath(String name, BufferedReader reader, DirectWriter writer) { // MODIFIED by Fan.Hu, 2018-01-09,BUG-5709670
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mBufferedReader = reader;
        mDirectWriter = writer;
        mProcessType = PROCESS_BUFFERED_2_DIRECT;

        initRetryHelper(); // MODIFIED by Fan.Hu, 2018-01-19,BUG-5709670
    }

    DataPath(String name, DirectReader reader, BufferedWriter writer) { // MODIFIED by Fan.Hu, 2018-01-09,BUG-5709670
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mDirectReader = reader;
        mBufferedWriter = writer;
        mProcessType = PROCESS_DIRECT_2_BUFFERED;

        initRetryHelper(); // MODIFIED by Fan.Hu, 2018-01-19,BUG-5709670
    }

    DataPath(String name, DirectReader reader, DirectWriter writer) { // MODIFIED by Fan.Hu, 2018-01-09,BUG-5709670
        if (reader == null || writer == null) {
            throw new IllegalArgumentException("null read or writer! reader: " + reader + ", writer: " + writer);
        }

        mName = name;
        mDirectReader = reader;
        mDirectWriter = writer;
        mProcessType = PROCESS_DIRECT_2_DIRECT;

        /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
        initRetryHelper();
    }

    private void initRetryHelper() {
        mReaderRetryHelper = new RetrySleepHelper(mName + "#Reader");
        mWriterRetryHelper = new RetrySleepHelper(mName + "#Writer");
        /* MODIFIED-END by Fan.Hu,BUG-5709670*/
    }

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-09,BUG-5709670*/
    public String getName() {
        return mName;
    }
    /* MODIFIED-END by Fan.Hu,BUG-5709670*/

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-20,BUG-5709670*/
    public synchronized boolean processAsync() {
        if (!mCanceled && mProcessThread == null) {
        /* MODIFIED-END by Fan.Hu,BUG-5709670*/
            mProcessThread = new DataPath.ProcessThread(mName);
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

                /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
                // interrupt also cancel join. wait until thread finished.
                while (!mProcessThread.isFinished()) { // MODIFIED by Fan.Hu, 2018-02-09,BUG-5727229
                    Thread.yield();
                }
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
            }
        }
    }

    private void process() {
        /* MODIFIED-BEGIN by Fan.Hu, 2018-02-09,BUG-5727229*/
        try {
            switch (mProcessType) {
                case PROCESS_BUFFERED_2_BUFFERED:
                    processBuffered2Buffered();
                    break;
                case PROCESS_BUFFERED_2_DIRECT:
                    processBuffered2Direct();
                    break;
                case PROCESS_DIRECT_2_BUFFERED:
                    processDirect2Buffered();
                    break;
                case PROCESS_DIRECT_2_DIRECT:
                    processDirect2Direct();
                    break;
                default:
                    Log.e(TAG, "[" + mName + "] invalid process type: " + mProcessType);
            }
        } catch (Exception e) {
            /* FIXME The issue should be fixed by the modification in method waitForFinish().
             * However, no time for further debug du to scheduler. So add "try ... catch ..."
             * to monitor whether issues if fully fixed, and this code should be removed in the future.
             */
            Log.e(TAG, "process()# exception detected.", e);
            /* MODIFIED-END by Fan.Hu,BUG-5727229*/
        }
    }

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-20,BUG-5709670*/
    public synchronized void cancel() {
        mCanceled = true;
        /* MODIFIED-END by Fan.Hu,BUG-5709670*/

        if (mProcessThread != null) {
            mProcessThread.interrupt();
        }
    }

    @SuppressWarnings("unused")
    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-20,BUG-5709670*/
    public boolean isCanceled() {
        return mCanceled;
        /* MODIFIED-END by Fan.Hu,BUG-5709670*/
    }

    private static boolean isThreadInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    private static void interruptThread() {
        Thread.currentThread().interrupt();
    }

    private void processBuffered2Buffered() {
        int resultRead = DataNode.RESULT_ERROR;
        int resultWrite = DataNode.RESULT_ERROR;
        Data writeData = new Data();
        Data readData = new Data();

        while (!isThreadInterrupted()) {
            // read begin
            /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
            mReaderRetryHelper.begin();
            while (!isThreadInterrupted() && (resultRead = mBufferedReader.readBegin(readData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                /* MODIFIED-BEGIN by Fan.Hu, 2018-01-09,BUG-5709670*/
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processBuffered2Buffered()# read RESULT_RETRY");
                }
                mReaderRetryHelper.sleep();
            }
            mReaderRetryHelper.end();
            if (resultRead != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processBuffered2Buffered()# read error. ignore this sample. >>" + resultRead);
                return; // error detected.
            }

            // write begin
            mWriterRetryHelper.begin();
            while (!isThreadInterrupted() && (resultWrite = mBufferedWriter.writeBegin(writeData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processBuffered2Buffered()# write begin RESULT_RETRY");
                }
                mWriterRetryHelper.sleep();
            }
            mWriterRetryHelper.end();
            if (resultWrite != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processBuffered2Buffered()# write begin error. ignore this sample. >>" + resultWrite);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // copy data
            copyData(readData, writeData);
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/

            // read end
            mBufferedReader.readEnd(readData);

            // write end
            mBufferedWriter.writeEnd(writeData);

            // check eof
            if (isEof(writeData)) {
                Log.i(TAG, "[" + mName + "] processBuffered2Buffered()# eof");
                interruptThread();
            }
        }
    }

    private void processBuffered2Direct() {
        int resultRead = DataNode.RESULT_ERROR;
        int resultWrite = DataNode.RESULT_ERROR;
        Data readData = new Data();

        while (!isThreadInterrupted()) {
            // read begin
            /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
            mReaderRetryHelper.begin();
            while (!isThreadInterrupted() && (resultRead = mBufferedReader.readBegin(readData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                /* MODIFIED-BEGIN by Fan.Hu, 2018-01-09,BUG-5709670*/
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processBuffered2Direct()# read RESULT_RETRY");
                }
                mReaderRetryHelper.sleep();
            }
            mReaderRetryHelper.end();
            if (resultRead != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processBuffered2Direct()# read error. ignore this sample. >>" + resultRead);
                return; // error detected.
            }

            // write
            mWriterRetryHelper.begin();
            while (!isThreadInterrupted() && (resultWrite = mDirectWriter.write(readData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processBuffered2Direct()# write begin RESULT_RETRY");
                }
                mWriterRetryHelper.sleep();
            }
            mWriterRetryHelper.end();
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/
            if (resultWrite != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processBuffered2Direct()# write begin error. ignore this sample. >>" + resultWrite);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // read end
            mBufferedReader.readEnd(readData);

            // check eof
            if (isEof(readData)) {
                Log.i(TAG, "[" + mName + "] processBuffered2Direct()# eof");
                interruptThread();
            }
        }
    }

    private void processDirect2Buffered() {
        int resultRead = DataNode.RESULT_ERROR;
        int resultWrite = DataNode.RESULT_ERROR;
        Data writeData = new Data();

        while (!isThreadInterrupted()) {
            // write begin
            /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
            mWriterRetryHelper.begin();
            while (!isThreadInterrupted() && (resultWrite = mBufferedWriter.writeBegin(writeData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                /* MODIFIED-BEGIN by Fan.Hu, 2018-01-09,BUG-5709670*/
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processDirect2Buffered()# write begin RESULT_RETRY");
                }
                mWriterRetryHelper.sleep();
            }
            mWriterRetryHelper.end();
            if (resultWrite != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processDirect2Buffered()# write begin error. ignore this sample. >>" + resultWrite);
                return; // error detected.
            }

            // read
            mReaderRetryHelper.begin();
            while (!isThreadInterrupted() && (resultRead = mDirectReader.read(writeData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processDirect2Buffered()# read RESULT_RETRY");
                }
                mReaderRetryHelper.sleep();
            }
            mReaderRetryHelper.end();
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/
            if (resultRead != DataNode.RESULT_OK) {
                Log.w(TAG, "[" + mName + "] processDirect2Buffered()# read error. ignore this sample. >>" + resultRead);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // write end
            mBufferedWriter.writeEnd(writeData);

            // check eof
            if (isEof(writeData)) {
                Log.i(TAG, "[" + mName + "] processDirect2Buffered()# eof");
                interruptThread();
            }
        }
    }

    private void processDirect2Direct() {
        int resultRead = DataNode.RESULT_ERROR;
        int resultWrite = DataNode.RESULT_ERROR;
        Data readData = new Data();
        createBuff(readData);

        while (!isThreadInterrupted()) {
            // read
            /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
            mReaderRetryHelper.begin();
            while (!isThreadInterrupted() && (resultRead = mDirectReader.read(readData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                /* MODIFIED-BEGIN by Fan.Hu, 2018-01-09,BUG-5709670*/
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processDirect2Direct()# read RESULT_RETRY");
                }
                mReaderRetryHelper.sleep();
            }
            mReaderRetryHelper.end();
            if (resultRead != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processDirect2Direct()# read error. ignore this sample. >>" + resultRead);
                return; // error detected.
            }

            // write
            mWriterRetryHelper.begin();
            while (!isThreadInterrupted() && (resultWrite = mDirectWriter.write(readData)) == DataNode.RESULT_RETRY) {
                // loop in case retry
                if (DEBUG_RETRY) {
                    Log.w(TAG, "[" + mName + "] processDirect2Direct()# write begin RESULT_RETRY");
                }
                mWriterRetryHelper.sleep();
            }
            mWriterRetryHelper.end();
            /* MODIFIED-END by Fan.Hu,BUG-5709670*/
            if (resultWrite != DataNode.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processDirect2Direct()# write begin error. ignore this sample. >>" + resultWrite);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // check eof
            if (isEof(readData)) {
                Log.i(TAG, "[" + mName + "] processDirect2Direct()# eof");
                interruptThread();
            }
        }
    }

    protected abstract void copyData(Data fromData, Data toData);
    protected abstract boolean isEof(Data data);
    protected abstract void createBuff(Data data);

    private class ProcessThread extends Thread {
        private boolean mFinished = false; // MODIFIED by Fan.Hu, 2018-02-09,BUG-5727229

        ProcessThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            process();

            /* MODIFIED-BEGIN by Fan.Hu, 2018-02-09,BUG-5727229*/
            mFinished = true;
        }

        @Override
        public synchronized void start() {
            mFinished = false;
            super.start();
        }

        public boolean isFinished() {
            return mFinished;
            /* MODIFIED-END by Fan.Hu,BUG-5727229*/
        }
    }

    /* MODIFIED-BEGIN by Fan.Hu, 2018-01-19,BUG-5709670*/
    private static class RetrySleepHelper {
        private static final boolean DEBUG = false;

        private static final int DEF_MIN_RETRY_THRESHOLD = 1;
        private static final int DEF_MAX_RETRY_THRESHOLD = 3;
        private static final int DEF_SLEEP_STEP = 1;

        private String mName;
        private long mSleepTime = 0;
        private long mSleepStep = 1;
        private int mRetryCount = 0;
        private int mMinThreshold = DEF_MIN_RETRY_THRESHOLD;
        private int mMaxThreshold = DEF_MAX_RETRY_THRESHOLD;

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
    /* MODIFIED-END by Fan.Hu,BUG-5709670*/
}
