package com.t2m.npd.node.process;

import com.t2m.npd.Data;
import com.t2m.npd.Node;
import com.t2m.npd.node.ProcessNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GroupProcessNode<T extends Data> extends ProcessNode<T> {
    private final List<ProcessNode<T>> mChildren = new ArrayList<>();

    public GroupProcessNode(String name) {
        super(name);
    }

    public GroupProcessNode<T> addChild(ProcessNode<T> node) {
        synchronized (mChildren) {
            mChildren.add(node);
        }
        return this;
    }

    @Override
    public int onProcess(T data) {
        synchronized (mChildren) {
            int result = Node.RESULT_OK;
            for (ProcessNode<T> child : mChildren) {
                result = child.processWithRetry(data);
                if (result != Node.RESULT_OK) {
                    break;
                }
            }
            return result;
        }
    }

    @Override
    protected void onOpen() throws IOException {
        synchronized (mChildren) {
            for (ProcessNode<T> child : mChildren) {
                child.open();
            }
        }
    }

    @Override
    protected void onClose() throws IOException {
        synchronized (mChildren) {
            for (ProcessNode<T> child : mChildren) {
                child.close();
            }
        }
    }
}
