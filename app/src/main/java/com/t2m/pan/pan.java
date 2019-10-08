package com.t2m.pan;

public class pan {
    public static final int RESULT_OK = 0;
    public static final int RESULT_CONTINUE = 1;
    public static final int RESULT_EOS = 2;

    public static final int RESULT_ERROR = 1000;

    public static String resultString(int result) {
        switch (result) {
            case RESULT_OK:
                return "RESULT_OK";
            case RESULT_CONTINUE:
                return "RESULT_CONTINUE";
            case RESULT_EOS:
                return "RESULT_EOS";
            case RESULT_ERROR:
                return "RESULT_ERROR";
            default:
                return "RESULT_UNKNOWN";
        }
    }

    public static boolean stopPipeline(int result) {
        return result > RESULT_CONTINUE;
    }

    public static boolean isError(int result) {
        return result >= RESULT_ERROR;
    }

    public static boolean isOk(int result) {
        return result == RESULT_OK;
    }

    public static boolean isEos(int result) {
        return result == RESULT_EOS;
    }
}
