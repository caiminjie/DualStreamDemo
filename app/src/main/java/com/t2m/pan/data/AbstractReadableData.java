package com.t2m.pan.data;

import com.t2m.pan.Data;

import java.nio.ByteBuffer;

public abstract class AbstractReadableData extends Data implements IReadableData {
    public AbstractReadableData(int id, int type) {
        super(id, type);
    }

    public int read(ByteBuffer buffer) {
        int nRead = read(buffer.array(), buffer.position(), buffer.remaining());
        buffer.limit(buffer.position() + nRead);
        return nRead;
    }

    public int read(ByteBuffer buffer, int offset, int len) {
        int nRead =  read(buffer.array(), offset, len);
        buffer.position(offset);
        buffer.limit(offset + len);
        return nRead;
    }
}
