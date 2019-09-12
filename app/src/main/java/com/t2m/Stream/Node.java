package com.t2m.stream;

import com.t2m.stream.util.RetrySleepHelper;

import java.io.IOException;

public abstract class Node {
    private static final String TAG = Node.class.getSimpleName();

    public static final int RESULT_OK = 0;
    public static final int RESULT_RETRY = 1;
    public static final int RESULT_NOT_OPEN = 2;
    public static final int RESULT_ERROR = 3;
    public static final int RESULT_EOS = 4;

    public static final int STATUS_NOT_OPENED = 0;
    public static final int STATUS_OPENING = 1;
    public static final int STATUS_OPENED = 2;
    public static final int STATUS_CLOSING = 3;
    public static final int STATUS_CLOSED = 4;

    protected String mName;
    private boolean mIsOpened = false;
    private final Object mOpenLock = new Object();

    protected int mStatus = STATUS_NOT_OPENED; // new for status control. old is mIsOpened

    private RetrySleepHelper mRetryHelper;

    public Node(String name) {
        mName = name;
        mRetryHelper = new RetrySleepHelper(mName + "#process");
    }

    public Node open() throws IOException {
        synchronized (mOpenLock) {
            android.util.Log.d(TAG, "[" + mName + "] open()# begin");
            if (mIsOpened) {
                android.util.Log.d(TAG, "[" + mName + "] open()# end. already opened");
                return this;
            }

            mStatus = STATUS_OPENING;
            mIsOpened = true;
            onOpen();
            android.util.Log.d(TAG, "[" + mName + "] open()# end");
            mStatus = STATUS_OPENED;
            return this;
        }
    }

    public Node close() throws IOException {
        synchronized (mOpenLock) {
            android.util.Log.d(TAG, "[" + mName + "] close()# begin");
            if (!mIsOpened) {
                android.util.Log.d(TAG, "[" + mName + "] close()# end. already closed.");
                return this;
            }

            mStatus = STATUS_CLOSING;
            mIsOpened = false;
            onClose();
            android.util.Log.d(TAG, "[" + mName + "] close()# end");
            mStatus = STATUS_CLOSED;
            return this;
        }
    }

    public boolean isOpened() {
        synchronized (mOpenLock) {
            return mIsOpened;
            // return mStatus == STATUS_OPENED || mStatus == STATUS_OPENING; // TODO switch later
        }
    }

    public void retryBegin() {
        mRetryHelper.begin();
    }

    public void retryEnd() {
        mRetryHelper.end();
    }

    public void retrySleep() {
        mRetryHelper.sleep();
    }

    protected abstract void onOpen() throws IOException;
    protected abstract void onClose() throws IOException;
}
