package com.t2m.stream.streams;

import com.t2m.npd.Task;
import com.t2m.stream.Stream;

public class UploadStream extends Stream {
    public UploadStream(String name) {
        super(name, TYPE_UPLOAD, true, true);
    }

    @Override
    public boolean build(Task task) {
        return false;
    }
}
