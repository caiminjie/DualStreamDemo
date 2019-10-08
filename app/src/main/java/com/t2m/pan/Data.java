package com.t2m.pan;

public class Data {
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public int id;
    public int type;

    public Data(int id, int type) {
        this.id = id;
        this.type = type;
    }
}
