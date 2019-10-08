package com.t2m.pan;

public class Data {
    public static final int RESULT_OK = 0;
    public static final int RESULT_EOS = 1;
    public static final int RESULT_ERROR = 2;

    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;

    public int result = RESULT_OK;
    public int id;
    public int type;

    public Data(int id, int type) {
        this.id = id;
        this.type = type;
    }
}
