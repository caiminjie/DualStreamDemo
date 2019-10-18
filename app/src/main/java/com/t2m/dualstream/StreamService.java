package com.t2m.dualstream;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;
import android.util.SparseArray;

import com.t2m.pan.Task;
import com.t2m.pan.node.conn.GlVideoHubNode;
import com.t2m.pan.node.tail.AudioNode;
import com.t2m.pan.node.tail.CameraNode;
import com.t2m.stream.StreamTask;

import java.util.LinkedList;

public class StreamService extends Service {
    @SuppressWarnings("unused")
    private static final String TAG = StreamService.class.getSimpleName();

    private final Channel[] mChannels = new Channel[StreamManager.CHANNEL_COUNT];
    private CameraNode mCameraNode;
    private AudioNode mAudioNode;
    private GlVideoHubNode mVideoHubNode;

    private static class Channel {
        private final LinkedList<Pair<Integer, Task>> mQueue = new LinkedList<>();
        private Thread mThread = null;
        private Pair<Integer, Task> mCurrentItem = null;

        private int status() {
            return mCurrentItem == null ? StreamManager.STATUS_IDLE : mCurrentItem.first;
        }

        private void cancelCurrentTask(boolean wait) {
            if (mCurrentItem != null) {
                mCurrentItem.second.stop();

                if (wait) {
                    mCurrentItem.second.waitForFinish();
                }
            }
        }

        @SuppressWarnings("SameParameterValue")
        private void cancelAllTask(boolean wait) {
            while (pop() != null); // remove all queued tasks
            cancelCurrentTask(wait); // cancel current task
        }

        private void push(int status, Task task) {
            synchronized (mQueue) {
                mQueue.addLast(new Pair<>(status, task));

                if (mThread == null) {
                    mThread = new Thread(() -> {
                        while (true) {
                            synchronized (mQueue) {
                                if ((mCurrentItem = pop()) == null) {
                                    mThread = null;
                                    break;
                                }
                            }

                            mCurrentItem.second.start();
                            mCurrentItem.second.waitForFinish();
                        }
                    });
                    mThread.start();
                }
            }
        }

        private Pair<Integer, Task> pop() {
            synchronized (mQueue) {
                return mQueue.pollFirst();
            }
        }
    }

    public StreamService() {
        for (int i=0; i<mChannels.length; i++) {
            mChannels[i] = new Channel();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCameraNode = new CameraNode("Camera", this);
        mAudioNode = new AudioNode("Audio", MediaRecorder.AudioSource.MIC, 48000, 2, AudioFormat.ENCODING_PCM_16BIT);
        mVideoHubNode = new GlVideoHubNode("VideoHub");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void startTask(int channel, int status, boolean stopPreviewTask, Task task) {
        Channel ch = mChannels[channel];

        if (stopPreviewTask) {
            ch.cancelAllTask(true);
        }

        ch.push(status, task);
    }

    //////////////////////////
    // DataStore for object pass
    private static final SparseArray<Object> sDataStore = new SparseArray<>();

    public static int putData(Object data) {
        synchronized (sDataStore) {
            if (data == null) {
                return 0;
            } else {
                int key = data.hashCode();
                while (key == 0 || sDataStore.get(key) != null) {
                    key++;
                }
                sDataStore.put(key, data);
                return key;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(int key) {
        synchronized (sDataStore) {
            Object value = sDataStore.get(key);
            if (value != null) {
                sDataStore.remove(key);
                return (T) value;
            } else {
                return null;
            }
        }
    }

    ///////////////////////////////////
    // binder
    private IBinder mBinder = new IStreamService.Stub() {
        @Override
        public int getCameraNode() throws RemoteException {
            return StreamService.putData(mCameraNode);
        }

        @Override
        public int getAudioNode() throws RemoteException {
            return StreamService.putData(mAudioNode);
        }

        @Override
        public int getVideoHubNode() throws RemoteException {
            return StreamService.putData(mVideoHubNode);
        }

        @Override
        public int getStatus(int channel) throws RemoteException {
            return mChannels[channel].status();
        }

        @Override
        public int createStreamTask(String name) throws RemoteException {
            return putData(new StreamTask(name, mCameraNode, mAudioNode, mVideoHubNode));
        }


        @Override
        public void startTask(int channel, int status, boolean stopPreviewTask, int task) throws RemoteException {
            StreamService.this.startTask(channel, status, stopPreviewTask, StreamService.getData(task));
        }
    };
}
