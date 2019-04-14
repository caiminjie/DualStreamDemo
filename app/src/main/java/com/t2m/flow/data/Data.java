package com.t2m.flow.data;

import com.t2m.flow.node.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Data {
    private Map<String, Object> mMap = new HashMap<>();

    public interface OnReleaseListener {
        int onRelease(Data data);
    }

    private List<OnReleaseListener> mReleaseListeners = new ArrayList<>();

    @SuppressWarnings({"unchecked", "deprecation"})
    public <T> T get(String key, T defValue) {
        Object value = mMap.get(key);
        return (value == null) ? defValue : (T) value;
    }

    public <T> T set(String key, T value) {
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

        return result;
    }

    public void addReleaseListener(OnReleaseListener listener) {
        if (!mReleaseListeners.contains(listener)) {
            mReleaseListeners.add(listener);
        }
    }

    public boolean hasKey(String key) {
        return mMap.containsKey(key);
    }
}
