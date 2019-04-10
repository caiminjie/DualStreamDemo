/* Copyright (C) 2018 Tcl Corporation Limited */
package com.t2m.dataflow.path;

import com.t2m.dataflow.data.MediaData;
import com.t2m.dataflow.data.Data;
import com.t2m.dataflow.node.BufferedReader;
import com.t2m.dataflow.node.BufferedWriter;
import com.t2m.dataflow.node.DirectReader;
import com.t2m.dataflow.node.DirectWriter;

/**
 * MediaDataPath to link two data node
 */
public class MediaDataPath extends DataPath {
    public MediaDataPath(String name, BufferedReader reader, BufferedWriter writer) {
        super(name, reader, writer);
    }
    public MediaDataPath(String name, BufferedReader reader, DirectWriter writer) {
        super(name, reader, writer);
    }

    public MediaDataPath(String name, DirectReader reader, BufferedWriter writer) {
        super(name, reader, writer);
    }

    public MediaDataPath(String name, DirectReader reader, DirectWriter writer) {
        super(name, reader, writer);
    }

    @Override
    protected void copyData(Data fromData, Data toData) {
        MediaData.copyData(fromData, toData);
    }

    @Override
    protected boolean isEof(Data data) {
        return MediaData.isEof(data);
    }

    @Override
    protected void createBuff(Data data) {
        MediaData.createBuff(data);
    }
}
