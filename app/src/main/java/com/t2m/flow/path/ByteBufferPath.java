package com.t2m.flow.path;

import android.media.MediaCodec;
import android.util.Log;

import com.t2m.flow.data.ByteBufferData;
import com.t2m.flow.data.DataHolder;
import com.t2m.flow.node.Node;

import java.nio.ByteBuffer;

public class ByteBufferPath extends Path{
    private static final String TAG = Path.class.getSimpleName();

    @SuppressWarnings("unused")
    public ByteBufferPath(String name, Plug reader, Plug writer) {
        super(name, reader, writer);
    }

    @SuppressWarnings("unused")
    public ByteBufferPath(String name, Plug reader, Slot writer) {
        super(name, reader, writer);
    }

    @SuppressWarnings("unused")
    public ByteBufferPath(String name, Slot reader, Plug writer) {
        super(name, reader, writer);
    }

    @SuppressWarnings("unused")
    public ByteBufferPath(String name, Slot reader, Slot writer) {
        super(name, reader, writer);
    }

    @Override
    protected void processPlug2Plug() {
        while (!isThreadInterrupted()) {
            DataHolder readerDataHolder = DataHolder.create();
            DataHolder writerDataHolder = DataHolder.create();

            // get plug reader data
            int resultReader = getPlugData(mPlugReader, readerDataHolder, mReaderRetryHelper);
            if (resultReader != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processPlug2Plug()# read error. ignore this sample. >> " + resultReader);
                return; // error detected.
            }

            // get plug writer data
            int resultWriter = getPlugData(mPlugWriter, writerDataHolder, mWriterRetryHelper);
            if (resultWriter != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processPlug2Plug()# write error. ignore this sample. >> " + resultWriter);
                return; // error detected.
            }

            // copy data
            copyData(readerDataHolder, writerDataHolder);

            // check eos
            if (((ByteBufferData) writerDataHolder.data).isEos()) {
                Log.i(TAG, "[" + mName + "] processPlug2Plug()# eos");
                interruptThread();
            }

            // release
            readerDataHolder.data.release(); // always release reader data for plug to plug
            if (!mPlugWriter.bypass()) {
                // release reader data, as it will not bypass to next node.
                writerDataHolder.data.release();
            }
            readerDataHolder.release();
            writerDataHolder.release();
        }
    }

    @Override
    protected void processPlug2Slot() {
        while (!isThreadInterrupted()) {
            DataHolder readerDataHolder = DataHolder.create();

            // get plug reader data
            int resultReader = getPlugData(mPlugReader, readerDataHolder, mReaderRetryHelper);
            if (resultReader != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processPlug2Slot()# read error. ignore this sample. >> " + resultReader);
                return; // error detected.
            }

            // write
            int resultWriter = setSlotData(mSlotWriter, readerDataHolder, mWriterRetryHelper);
            if (resultWriter != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processPlug2Slot()# write error. ignore this sample. >> " + resultWriter);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // check eof
            if (((ByteBufferData) readerDataHolder.data).isEos()) {
                Log.i(TAG, "[" + mName + "] processPlug2Slot()# eos");
                interruptThread();
            }

            // release
            if (!mSlotWriter.bypass()) {
                readerDataHolder.data.release();
            }
            readerDataHolder.release();
        }
    }

    @Override
    protected void processSlot2Plug() {
        while (!isThreadInterrupted()) {
            DataHolder writerDataHolder = DataHolder.create();

            // get plug writer data
            int resultWriter = getPlugData(mPlugWriter, writerDataHolder, mWriterRetryHelper);
            if (resultWriter != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processSlot2Plug()# write error. ignore this sample. >> " + resultWriter);
                return; // error detected.
            }

            // read
            int resultReader = setSlotData(mSlotReader, writerDataHolder, mReaderRetryHelper);
            if (resultReader != Node.RESULT_OK) {
                Log.w(TAG, "[" + mName + "] processSlot2Plug()# read error. ignore this sample. >> " + resultReader);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // check eof
            if (((ByteBufferData)writerDataHolder.data).isEos()) {
                Log.i(TAG, "[" + mName + "] processSlot2Plug()# eos");
                interruptThread();
            }

            // release
            if (!mPlugWriter.bypass()) {
                writerDataHolder.data.release();
            }
            writerDataHolder.release();
        }
    }

    @Override
    protected void processSlot2Slot() {
        byte[] buff = new byte[512*1024];
        ByteBuffer buffer = ByteBuffer.wrap(buff, 0, buff.length);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (!isThreadInterrupted()) {
            buffer.clear();
            DataHolder readerDataHolder = DataHolder.create();
            readerDataHolder.data = ByteBufferData.create();
            ((ByteBufferData) readerDataHolder.data).setBuffer(buffer);
            ((ByteBufferData) readerDataHolder.data).setBufferInfo(info);

            // read
            int resultReader = setSlotData(mSlotReader, readerDataHolder, mReaderRetryHelper);
            if (resultReader != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processSlot2Slot()# read error. ignore this sample. >> " + resultReader);
                return; // error detected.
            }

            // write
            int resultWriter = setSlotData(mSlotWriter, readerDataHolder, mWriterRetryHelper);
            if (resultWriter != Node.RESULT_OK) { // check for error
                Log.w(TAG, "[" + mName + "] processDirect2Direct()# write error. ignore this sample. >> " + resultWriter);
                /* MODIFIED-END by Fan.Hu,BUG-5709670*/
                return; // error detected.
            }

            // check eof
            if (((ByteBufferData)readerDataHolder.data).isEos()) {
                Log.i(TAG, "[" + mName + "] processDirect2Direct()# eof");
                interruptThread();
            }

            // release
            if (!mSlotWriter.bypass()) {
                readerDataHolder.data.release();
            }
            readerDataHolder.release();
        }
    }

    private void copyData(DataHolder fromHolder, DataHolder toHolder) {
        ByteBufferData fromData = (ByteBufferData) fromHolder.data;
        ByteBufferData toData = (ByteBufferData) toHolder.data;
        ByteBufferData.copyData(fromData, toData);
    }
}
