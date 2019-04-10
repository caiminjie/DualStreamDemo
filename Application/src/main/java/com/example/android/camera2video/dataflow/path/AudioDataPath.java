/* Copyright (C) 2018 Tcl Corporation Limited */
package com.example.android.camera2video.dataflow.path;

import com.example.android.camera2video.dataflow.data.AudioData;
import com.example.android.camera2video.dataflow.data.Data;
import com.example.android.camera2video.dataflow.node.BufferedReader;
import com.example.android.camera2video.dataflow.node.BufferedWriter;
import com.example.android.camera2video.dataflow.node.DirectReader;
import com.example.android.camera2video.dataflow.node.DirectWriter;

/**
 * AudioDataPath to link two data node
 */
public class AudioDataPath extends DataPath {
    public AudioDataPath(String name, BufferedReader reader, BufferedWriter writer) {
        super(name, reader, writer);
    }
    public AudioDataPath(String name, BufferedReader reader, DirectWriter writer) {
        super(name, reader, writer);
    }

    public AudioDataPath(String name, DirectReader reader, BufferedWriter writer) {
        super(name, reader, writer);
    }

    public AudioDataPath(String name, DirectReader reader, DirectWriter writer) {
        super(name, reader, writer);
    }

    @Override
    protected void copyData(Data fromData, Data toData) {
        AudioData.copyData(fromData, toData);
    }

    @Override
    protected boolean isEof(Data data) {
        return AudioData.isEof(data);
    }

    @Override
    protected void createBuff(Data data) {
        AudioData.createBuff(data);
    }
}
