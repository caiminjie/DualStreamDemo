package com.t2m.npd.pipeline;

import com.t2m.npd.Data;
import com.t2m.npd.Pipeline;
import com.t2m.npd.node.ProcessNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public abstract class InOutPipeline<T extends Data> extends Pipeline<T> {
    private final List<ProcessNode<T>> mOutgoingList = new ArrayList<>();
    private final List<ProcessNode<T>> mIncomingList = new ArrayList<>();
    private List<ProcessNode<T>> mList;
    private boolean mListDirty = true;

    @SuppressWarnings("unused")
    public InOutPipeline(String name, DataAdapter<T> adapter) {
        super(name, adapter);
    }

    @SuppressWarnings("unused")
    public InOutPipeline<T> addOutgoingNode(ProcessNode<T> node) {
        synchronized (mOutgoingList) {
            mOutgoingList.add(node);
            mListDirty = true;
            return this;
        }
    }

    @SuppressWarnings("WeakerAccess | UnusedReturnValue")
    public InOutPipeline<T> addIncomingNode(ProcessNode<T> node) {
        synchronized (mIncomingList) {
            mIncomingList.add(node);
            mListDirty = true;
            return this;
        }
    }

    @Override
    protected List<ProcessNode<T>> getNodeList() {
        if (mListDirty) {
            synchronized (mOutgoingList) {
                synchronized (mIncomingList) {
                    mList = Stream.of(mOutgoingList, mIncomingList)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                }
            }
        }
        return mList;
    }

    @Override
    public Pipeline<T> addNode(ProcessNode<T> node) {
        addIncomingNode(node);
        return this;
    }
}
