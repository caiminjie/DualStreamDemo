package com.t2m.dualstream;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;

import com.t2m.stream.Task;
import com.t2m.stream.data.SurfaceData;
import com.t2m.stream.node.AudioNode;
import com.t2m.stream.node.CameraNode;
import com.t2m.stream.node.H264EncoderNode;
import com.t2m.stream.node.M4aEncoderNode;
import com.t2m.stream.node.MediaMuxerNode;
import com.t2m.stream.node.SurfaceNode;

import java.util.LinkedList;

public class StreamService extends Service {
    private static final String TAG = StreamService.class.getSimpleName();

    private final Channel[] mChannels = new Channel[StreamManager.CHANNEL_COUNT];
    private CameraNode mCameraNode;

    private static class Channel {
        private final LinkedList<Pair<Integer, Task>> mQueue = new LinkedList<>();
        private Thread mThread = null;
        private Pair<Integer, Task> mCurrentItem = null;

        public int status() {
            return mCurrentItem == null ? StreamManager.STATUS_IDLE : mCurrentItem.first;
        }

        public void cancelCurrentTask(boolean wait) {
            if (mCurrentItem != null) {
                mCurrentItem.second.stop();

                if (wait) {
                    mCurrentItem.second.waitForFinish();
                }
            }
        }

        public void cancelAllTask(boolean wait) {
            while (pop() != null); // remove all queued tasks
            cancelCurrentTask(wait); // cancel current task
        }

        public void push(int status, Task task) {
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

        public Pair<Integer, Task> pop() {
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void startTask(int channel, int status, Task task, boolean stopPreviewTask) {
        Channel ch = mChannels[channel];

        if (stopPreviewTask) {
            ch.cancelAllTask(true);
        }

        ch.push(status, task);
    }

    /**
     * Start the camera preview.
     */
    private void startPreview(Surface previewSurface) {
        Channel ch = mChannels[StreamManager.CHANNEL_BACKGROUND];
        if (ch.status() == StreamManager.STATUS_PREVIEW) {
            return;
        }

        if (previewSurface == null || !previewSurface.isValid()) {
            return;
        }

        ch.cancelAllTask(true);

        // set stream count
        mCameraNode.setStreamCount(1);

        // create node
        SurfaceNode previewNode = new SurfaceNode("preview", SurfaceData.TYPE_PREVIEW, previewSurface);

        // config pipeline
        previewNode.pipeline().addNode(mCameraNode);

        // create task
        Task task = new Task("Preview Task");
        task
                .addNode(mCameraNode)
                .addNode(previewNode);
        startTask(StreamManager.CHANNEL_BACKGROUND, StreamManager.STATUS_PREVIEW, task, true);
    }

    /**
     * Start video recording
     */
    private void startVideoRecording(Surface previewSurface, Size videoSize1, Size videoSize2) {
        Channel ch = mChannels[StreamManager.CHANNEL_BACKGROUND];
        if (ch.status() == StreamManager.STATUS_RECORDING) {
            return;
        }

        if (previewSurface == null || !previewSurface.isValid()) {
            return;
        }

        ch.cancelAllTask(true);

        // init audio & camera
        AudioNode audioNode = new AudioNode("audio", MediaRecorder.AudioSource.MIC, 48000, 2, AudioFormat.ENCODING_PCM_16BIT);
        mCameraNode.setStreamCount(3);

        // stream preview
        SurfaceNode previewNode = new SurfaceNode("preview", SurfaceData.TYPE_PREVIEW, previewSurface);
        previewNode.pipeline().addNode(mCameraNode);

        // stream 1
        H264EncoderNode videoEncoderNode1 = new H264EncoderNode("videoEncoder1", videoSize1.getWidth(), videoSize1.getHeight(), 10000000, 30);
        M4aEncoderNode audioEncoderNode1 = new M4aEncoderNode("audioEncoder1");
        MediaMuxerNode muxerNode1 = new MediaMuxerNode("MuxerNode1", "/sdcard/DCIM/a.mp4", mCameraNode.getSensorOrientation());
        videoEncoderNode1.inputPipelineSurface().addNode(mCameraNode);
        videoEncoderNode1.outputPipeline().addNode(muxerNode1);
        audioEncoderNode1.inputPipeline().addNode(audioNode);
        audioEncoderNode1.outputPipeline().addNode(muxerNode1);

        // stream 2
        H264EncoderNode videoEncoderNode2 = new H264EncoderNode("videoEncoder2", videoSize2.getWidth(), videoSize2.getHeight(), 5000000, 30);
        M4aEncoderNode audioEncoderNode2 = new M4aEncoderNode("audioEncoder2");
        MediaMuxerNode muxerNode2 = new MediaMuxerNode("MuxerNode2", "/sdcard/DCIM/b.mp4", mCameraNode.getSensorOrientation());
        videoEncoderNode2.inputPipelineSurface().addNode(mCameraNode);
        videoEncoderNode2.outputPipeline().addNode(muxerNode2);
        audioEncoderNode2.inputPipeline().addNode(audioNode);
        audioEncoderNode2.outputPipeline().addNode(muxerNode2);

        // create task
        Task task = new Task("Record Task");
        task
                .addNode(mCameraNode)
                .addNode(audioNode)
                .addNode(previewNode)
                .addNode(videoEncoderNode1)
                .addNode(audioEncoderNode1)
                .addNode(muxerNode1)
                .addNode(videoEncoderNode2)
                .addNode(audioEncoderNode2)
                .addNode(muxerNode2)
        ;

        startTask(StreamManager.CHANNEL_BACKGROUND, StreamManager.STATUS_RECORDING, task, true);
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
        public int getStatus(int channel) throws RemoteException {
            return mChannels[channel].status();
        }

        @Override
        public void startTask(int channel, int status, int task, boolean stopPreviewTask) throws RemoteException {
            StreamService.this.startTask(channel, status, StreamService.getData(task), stopPreviewTask);
        }

        @Override
        public void startPreview(int previewSurface) throws RemoteException {
            StreamService.this.startPreview(getData(previewSurface));
        }

        @Override
        public void startVideoRecording(int previewSurface, int videoSize1, int videoSize2) throws RemoteException {
            StreamService.this.startVideoRecording(
                    getData(previewSurface),
                    getData(videoSize1),
                    getData(videoSize2)
            );
        }
    };
}
