package com.t2m.dualstream;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;
import android.util.Range;
import android.util.SparseArray;

import com.t2m.stream.Stream;
import com.t2m.npd.Task;
import com.t2m.npd.node.AudioNode;
import com.t2m.npd.node.CameraNode;

import java.util.LinkedList;
import java.util.List;

public class StreamService extends Service {
    private static final String TAG = StreamService.class.getSimpleName();

    private final Channel[] mChannels = new Channel[StreamManager.CHANNEL_COUNT];
    private CameraNode mCameraNode;
    private AudioNode mAudioNode;

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
        mAudioNode = new AudioNode("audio", MediaRecorder.AudioSource.MIC, 48000, 2, AudioFormat.ENCODING_PCM_16BIT);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void startStreams(String name, int channel, int status, boolean stopPreviewTask, List<Stream> streams) throws RemoteException {
        Channel ch = mChannels[channel];

        // close preview task if necessary
        if (stopPreviewTask) {
            ch.cancelAllTask(true);
        }

        // init for stream count
        int audioCount = Stream.audioStreamCount(streams);
        int videoCount = Stream.videoStreamCount(streams);
        mCameraNode.setStreamCount(videoCount);

        // config camera fps
        int minFps = Stream.minFrameRate(streams);
        int maxFps = Stream.maxFrameRate(streams);
        if (minFps > 0 && maxFps > 0) {
            Range<Integer> range = Utils.chooseFps(mCameraNode.getAvailableFps(), minFps, maxFps);
            mCameraNode.setFps(range);
        }

        // init task
        Task task = Stream.build(name, streams);
        if (task == null) {
            throw new RemoteException("build task from stream failed.");
        }

        // start
        ch.push(status, task);
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
        public int getStatus(int channel) throws RemoteException {
            return mChannels[channel].status();
        }

        @Override
        public void startStreams(String name, int channel, int status, boolean stopPreviewTask, int streams) throws RemoteException {
            StreamService.this.startStreams(name, channel, status, stopPreviewTask, getData(streams));
        }


        @Override
        public void startTask(int channel, int status, boolean stopPreviewTask, int task) throws RemoteException {
            StreamService.this.startTask(channel, status, stopPreviewTask, StreamService.getData(task));
        }
    };
}
