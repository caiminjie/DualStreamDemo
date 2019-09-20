package com.t2m.stream;

public interface IVideoStream<T> {
    T setFrameRate(int frameRate);
    int getFrameRate();
}
