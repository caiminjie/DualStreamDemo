package com.t2m.pan.data;

public interface IWriteableData {
    int write(byte[] buff, int offset, int len);
}
