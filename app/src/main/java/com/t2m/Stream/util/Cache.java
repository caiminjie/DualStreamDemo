package com.t2m.stream.util;

import android.util.Log;

import java.lang.ref.WeakReference;

public class Cache<T> {
    private static final String TAG = Cache.class.getSimpleName();

    private static final boolean DEBUG_LEAK = false;
    private int mLeakCount = 0;

    private static final int CACHED_BUFF_SIZE = 10;
    private final Object[] mCachedBuffs = new Object[CACHED_BUFF_SIZE];
    private String mName;
    private OnGet<T> mOnGet;
    private OnPut<T> mOnPut;

    public interface OnGet<T> {
        T onGet(T data);
    }

    public interface OnPut<T> {
        void onPut(T data);
    }

    public Cache(String name, OnGet<T> onGet, OnPut<T> onPut) {
        mName = name;
        mOnGet = onGet;
        mOnPut = onPut;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        synchronized (mCachedBuffs) {
            T data = null;
            for (int i=0; i<mCachedBuffs.length && data == null; i++) {
                if (mCachedBuffs[i] != null) {
                    WeakReference<T> ref = (WeakReference<T>) mCachedBuffs[i];
                    mCachedBuffs[i] = null;  // item should be removed
                    data = ref.get();
                }
            }

            // on get
            if (mOnGet != null) {
                data = mOnGet.onGet(data);
            }

            // debug leak
            if (DEBUG_LEAK) {
                mLeakCount ++;
                Log.w(TAG, "[" + mName + "] leak: " + mLeakCount);
            }

            return data;
        }
    }

    @SuppressWarnings("unchecked")
    public void put(T data) {
        synchronized (mCachedBuffs) {
            // debug leak
            if (DEBUG_LEAK) {
                mLeakCount--;
            }

            if (mOnPut != null) {
                mOnPut.onPut(data);
            }

            for (int i = 0; i < mCachedBuffs.length; i++) {
                // try find null item
                if (mCachedBuffs[i] == null) {
                    mCachedBuffs[i] = new WeakReference<>(data);
                    return;
                }

                // try find released WeakReference
                WeakReference<T> ref = (WeakReference<T>) mCachedBuffs[i];
                if (ref.get() == null) {
                    mCachedBuffs[i] = new WeakReference<>(data);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        for (int i = 0; i < mCachedBuffs.length; i++) {
            if (mCachedBuffs[i] == null) {
                continue;
            }

            WeakReference<T> ref = (WeakReference<T>) mCachedBuffs[i];
            ref.clear();
            mCachedBuffs[i] = null;
        }
    }
}
