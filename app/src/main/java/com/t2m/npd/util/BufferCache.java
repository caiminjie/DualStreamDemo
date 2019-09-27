package com.t2m.npd.util;

import android.util.Log;

import java.lang.ref.WeakReference;

public class BufferCache<T> {
    private static final String TAG = BufferCache.class.getSimpleName();

    private static final boolean DEBUG_LEAK = false;
    private int mLeakCount = 0;

    private static final int CACHED_BUFF_SIZE = 10;
    private final Object[] mCachedBuffs = new Object[CACHED_BUFF_SIZE];
    private String mName;
    private OnGet<T> mOnGet;
    private OnPut<T> mOnPut;
    private OnGetSize<T> mOnGetSize;

    private static class Item<T> {
        final int index;
        int power;
        WeakReference<T> ref;
        T hold;

        private Item(int index) {
            this.index = index;
            this.power = Integer.MAX_VALUE;
            this.ref = null;
            this.hold = null;
        }

        private void reset() {
            this.power = Integer.MAX_VALUE;
            this.ref = null;
            this.hold = null;
        }

        private T getAndLock() {
            return hold = ref.get();
        }

        private void unlock() {
            hold = null;
        }
    }

    public interface OnGet<T> {
        T onGet(T data, int size);
    }

    public interface OnPut<T> {
        void onPut(T data);
    }

    public interface OnGetSize<T> {
        int onGetSize(T data);
    }

    public BufferCache(String name, OnGet<T> onGet, OnPut<T> onPut, OnGetSize<T> onGetSize) {
        mName = name;
        mOnGet = onGet;
        mOnPut = onPut;
        mOnGetSize = onGetSize;

        for (int i=0; i<mCachedBuffs.length; i++) {
            mCachedBuffs[i] = new Item<T>(i);
        }
    }

    @SuppressWarnings("unchecked")
    public T get(int size) {
        synchronized (mCachedBuffs) {
            // find best item
            int bestSize = 0;
            Item<T> best = null;
            for (Object buff : mCachedBuffs) {
                Item<T> item = (Item<T>) buff;
                if (item.ref != null) {
                    item.power ++; // increase power. item with max power is the one least used.
                    if (item.getAndLock() == null) {
                        item.reset(); // item already recycled. unlock also done by reset.
                    } else {
                        int currSize = mOnGetSize.onGetSize(item.hold);
                        if (currSize >= size && (best == null || currSize < bestSize)) {
                            if (best != null) {
                                best.unlock();
                            }

                            best = item;
                            bestSize = currSize;
                        }
                    }
                }
            }

            // get data & reset best item
            T data = null;
            if (best != null) {
                data = best.hold;
                best.reset();
            }

            // on get
            if (mOnGet != null) {
                data = mOnGet.onGet(data, size);
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

            // on put
            if (mOnPut != null) {
                mOnPut.onPut(data);
            }

            // find least used item
            Item<T> least = null;
            for (Object buff : mCachedBuffs) {
                Item<T> item = (Item<T>) buff;

                if (least == null) {
                    least = item;
                } else if (item.power > least.power) {
                    least = item;
                }

                if (least.power == Integer.MAX_VALUE) {
                    break; // empty node found, unnecessary to search more
                }
            }

            // cache data
            if (least != null) {
                least.reset();
                least.ref = new WeakReference<>(data);
            }
        }
    }

    @SuppressWarnings("unchecked | unused")
    public void clear() {
        for (Object buff : mCachedBuffs) {
            Item<T> item = (Item<T>) buff;
            if (item != null) {
                item.reset();
            }
        }
    }
}
