package com.t2m.flow.data;

import com.t2m.flow.node.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Data {
    private Map<String, Object> mMap = new HashMap<>();
    protected int mRef = 0;
    protected Data mParent = null;

    public interface OnReleaseListener {
        int onRelease(Data data);
    }

    private List<OnReleaseListener> mReleaseListeners = new ArrayList<>();

    public void setParent(Data data) {
        if (data != null) {
            mParent = data;
            synchronized (mParent) {
                mParent.mRef++;
            }
        } else {
            mParent = null;
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public <T> T get(String key, T defValue) {
        // get keys from local first
        Object value = mMap.get(key);

        if (value == null) {
            // get from parent
            if (mParent != null) {
                return mParent.get(key, defValue);
            } else {
                return defValue;
            }
        } else {
            return (T) value;
        }
    }

    public <T> T set(String key, T value) {
        // only allowed to set local. parent is read only.
        if (value == null) {
            mMap.remove(key);
        } else {
            mMap.put(key, value);
        }

        return value;
    }

    public int release() {
        int result = Node.RESULT_OK;

        // callback listener from last added.
        for (int i=mReleaseListeners.size()-1; i>=0; i--) {
            int r = mReleaseListeners.get(i).onRelease(this);
            if (r != Node.RESULT_OK) {
                result = r;
            }
        }

        mReleaseListeners.clear(); // clear listener
        mMap.clear(); // clear contents

        // check parent
        if (mParent != null) {
            synchronized (mParent) {
                mParent.mRef --;

                // release parent if necessary
                if (mParent.mRef <= 0) {
                    int r = mParent.release();
                    if (r != Node.RESULT_OK) {
                        result = r;
                    }
                }
            }
        }

        return result;
    }

    public void addReleaseListener(OnReleaseListener listener) {
        if (!mReleaseListeners.contains(listener)) {
            mReleaseListeners.add(listener);
        }
    }

    public boolean hasKey(String key) {
        return mMap.containsKey(key) || (mParent != null && mParent.hasKey(key));
    }
}
