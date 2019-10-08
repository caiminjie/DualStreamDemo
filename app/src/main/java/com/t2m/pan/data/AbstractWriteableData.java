package com.t2m.pan.data;

import com.t2m.pan.Data;

import java.nio.ByteBuffer;

public abstract class AbstractWriteableData extends Data implements IWriteableData {
    public AbstractWriteableData(int id, int type) {
        super(id, type);
    }

    public int write(ByteBuffer buffer) {
        return write(buffer.array(), buffer.position(), buffer.remaining());
    }

    public int write(ByteBuffer buffer, int offset, int len) {
        return write(buffer.array(), offset, len);
    }
}
