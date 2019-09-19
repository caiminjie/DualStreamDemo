package com.t2m.dualstream;

import android.app.Service;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;

import com.t2m.stream.Task;
import com.t2m.stream.data.SurfaceData;
import com.t2m.stream.node.AudioNode;
import com.t2m.stream.node.CameraRecordNode;
import com.t2m.stream.node.H264EncoderNode;
import com.t2m.stream.node.M4aEncoderNode;
import com.t2m.stream.node.MediaMuxerNode;
import com.t2m.stream.node.SurfaceNode;

public class DualStreamService extends Service {
    private static final String TAG = DualStreamService.class.getSimpleName();

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_PREVIEW = 1;
    public static final int STATUS_RECORDING = 2;
    private int mStatus = STATUS_IDLE;

    private Task mCurrentTask;
    private Size mPreviewSize;
    private Size[] mVideoSize = new Size[2];
    private CameraRecordNode mCameraNode;

    private ICameraOpenedCallback mOnOpenCallback = null;

    private Surface mPreviewSurface = null;

    private CameraRecordNode.OnCameraOpenedListener mCameraOpenedListener = () -> {
        if (mOnOpenCallback != null) {
            try {
                mOnOpenCallback.onCameraOpened();
            } catch (RemoteException e) {
                Log.e(TAG, "callback error", e);
            }
        }
    };

    public DualStreamService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCameraNode = new CameraRecordNode("Camera", this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void openCamera(String id) {
        closeCamera();

        mCameraNode.setCameraId(id);
        mCameraNode.openCamera(mCameraOpenedListener);
    }

    private void closeCamera() {
        if (mCameraNode != null) {
            mCameraNode.closeCamera();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (mStatus == STATUS_PREVIEW) {
            return;
        }

        if (mStatus == STATUS_RECORDING) {
            mCurrentTask.stop();
            mCurrentTask.waitForFinish();
            mStatus = STATUS_IDLE;
        }

        if (mPreviewSurface == null || !mPreviewSurface.isValid() || null == mPreviewSize) {
            return;
        }

//        SurfaceTexture texture = mTextureView.getSurfaceTexture();
//        assert texture != null;
//        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//        Surface previewSurface = new Surface(texture);

        // set stream count
        mCameraNode.setStreamCount(1);

        // create node
        SurfaceNode previewNode = new SurfaceNode("preview", SurfaceData.TYPE_PREVIEW, mPreviewSurface);

        // config pipeline
        previewNode.pipeline().addNode(mCameraNode);

        // create task
        mCurrentTask = new Task("Preview Task");
        mCurrentTask
                .addNode(mCameraNode)
                .addNode(previewNode);
        mCurrentTask.start();

        mStatus = STATUS_PREVIEW;
    }

    /**
     * Start video recording
     */
    private void startRecordingVideo() {
        if (mStatus == STATUS_RECORDING) {
            return;
        }

        if (mStatus == STATUS_PREVIEW) {
            mCurrentTask.stop();
            mCurrentTask.waitForFinish();
            mStatus = STATUS_IDLE;
        }

        if (mPreviewSurface == null || !mPreviewSurface.isValid() || null == mPreviewSize) {
            return;
        }

//        SurfaceTexture texture = mTextureView.getSurfaceTexture();
//        assert texture != null;
//        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//        Surface previewSurface = new Surface(texture);

        // init audio & camera
        AudioNode audioNode = new AudioNode("audio", MediaRecorder.AudioSource.MIC, 48000, 2, AudioFormat.ENCODING_PCM_16BIT);
        mCameraNode.setStreamCount(3);

        // stream preview
        SurfaceNode previewNode = new SurfaceNode("preview", SurfaceData.TYPE_PREVIEW, mPreviewSurface);
        previewNode.pipeline().addNode(mCameraNode);

        // stream 1
        H264EncoderNode videoEncoderNode1 = new H264EncoderNode("videoEncoder1", mVideoSize[0].getWidth(), mVideoSize[0].getHeight(), 10000000, 30);
        M4aEncoderNode audioEncoderNode1 = new M4aEncoderNode("audioEncoder1");
        MediaMuxerNode muxerNode1 = new MediaMuxerNode("MuxerNode1", "/sdcard/DCIM/a.mp4", mCameraNode.getSensorOrientation());
        videoEncoderNode1.inputPipelineSurface().addNode(mCameraNode);
        videoEncoderNode1.outputPipeline().addNode(muxerNode1);
        audioEncoderNode1.inputPipeline().addNode(audioNode);
        audioEncoderNode1.outputPipeline().addNode(muxerNode1);

        // stream 2
        H264EncoderNode videoEncoderNode2 = new H264EncoderNode("videoEncoder2", mVideoSize[1].getWidth(), mVideoSize[1].getHeight(), 5000000, 30);
        M4aEncoderNode audioEncoderNode2 = new M4aEncoderNode("audioEncoder2");
        MediaMuxerNode muxerNode2 = new MediaMuxerNode("MuxerNode2", "/sdcard/DCIM/b.mp4", mCameraNode.getSensorOrientation());
        videoEncoderNode2.inputPipelineSurface().addNode(mCameraNode);
        videoEncoderNode2.outputPipeline().addNode(muxerNode2);
        audioEncoderNode2.inputPipeline().addNode(audioNode);
        audioEncoderNode2.outputPipeline().addNode(muxerNode2);

        // create task
        mCurrentTask = new Task("Record Task");
        mCurrentTask
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
        mCurrentTask.start();

        mStatus = STATUS_RECORDING;
    }

    //////////////////////////
    // DataStore for object pass
    private static final SparseArray<Object> sDataStore = new SparseArray<>();

    public static int putData(Object data) {
        synchronized (sDataStore) {
            int key = data.hashCode();
            while (sDataStore.get(key) != null) {
                key ++;
            }
            sDataStore.put(key, data);
            return key;
        }
    }

    public static Object getData(int key, Object def) {
        synchronized (sDataStore) {
            Object value = sDataStore.get(key);
            if (value == null) {
                return def;
            } else {
                sDataStore.remove(key);
                return value;
            }
        }
    }

    ///////////////////////////////////
    // binder
    private IBinder mBinder = new IDualStreamService.Stub() {
        @Override
        public void openCamera(String id, ICameraOpenedCallback callback) throws RemoteException {
            mOnOpenCallback = callback;
            DualStreamService.this.openCamera(id);
        }

        @Override
        public void closeCamera() throws RemoteException {
            DualStreamService.this.closeCamera();
        }

        @Override
        public int getCameraOrientation() throws RemoteException {
            return mCameraNode.getSensorOrientation();
        }

        @Override
        public int getCameraIdList() throws RemoteException {
            return DualStreamService.putData(mCameraNode.getCameraIdList());
        }

        @Override
        public int getAvailableSurfaceSize() throws RemoteException {
            return DualStreamService.putData(mCameraNode.getAvailableSurfaceSize());
        }

        @Override
        public int getAvailableCodecSize() throws RemoteException {
            return DualStreamService.putData(mCameraNode.getAvailableCodecSize());
        }

        @Override
        public void setCameraStreamCount(int count) throws RemoteException {
            mCameraNode.setStreamCount(count);
        }

        @Override
        public int getAvailableFps() throws RemoteException {
            return DualStreamService.putData(mCameraNode.getAvailableFps());
        }

        @Override
        public void setFps(int key) throws RemoteException {
            mCameraNode.setFps((Range<Integer>)DualStreamService.getData(key, null));
        }

        @Override
        public void setPreviewSurface(int key) throws RemoteException {
            mPreviewSurface = (Surface) DualStreamService.getData(key, null);
        }

        @Override
        public int getStatus() throws RemoteException {
            return mStatus;
        }

        @Override
        public void startPreview() throws RemoteException {
            DualStreamService.this.startPreview();
        }

        @Override
        public void startVideoRecording() throws RemoteException {
            DualStreamService.this.startRecordingVideo();
        }

        @Override
        public void setPreviewSize(int key) throws RemoteException {
            mPreviewSize = (Size) DualStreamService.getData(key, null);
        }

        @Override
        public void setVideoSize(int index, int key) throws RemoteException {
            mVideoSize[index] = (Size) DualStreamService.getData(key, null);
        }
    };
}
