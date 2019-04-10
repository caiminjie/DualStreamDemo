package com.t2m.dataflow.data;

import java.util.HashMap;

public class Data extends HashMap<String, Object> {
    public <T> T get(String key, T defValue) {
        Object value = get(key);
        return (value == null) ? defValue : (T) value;
    }

    public <T> T set(String key, T value) {
        if (value == null) {
            if (containsKey(key)) {
                remove(key);
            }
        } else {
            super.put(key, value);
        }

        return value;
    }

    @Deprecated
    @Override
    public Object put(String key, Object value) {
        return super.put(key, value);
    }

    @Deprecated
    @Override
    public Object get(Object key) {
        return super.get(key);
    }
}
