package com.t2m.stream.node;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.t2m.stream.Node;
import com.t2m.stream.data.SurfaceData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraRecordNode extends ProcessNode<SurfaceData> {
    private static final String TAG = CameraRecordNode.class.getSimpleName();

    private static final int MSG_CAMERA_OPENED = 0;
    private static final int MSG_CAMERA_DISCONNECTED = 1;
    private static final int MSG_CAMERA_ERROR = 2;
    private static final int MSG_SESSION_CONFIGURED = 3;
    private static final int MSG_SESSION_CONFIGURE_FAILED = 4;
    private static final int MSG_QUIT = 5;

    private Context mContext;
    private CameraManager mCameraManager;
    private String mCameraId = null;
    private boolean mIsCameraOpened = false;

    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCurrentSession = null;
    private CaptureRequest mCurrentRequest = null;
    private int mCaptureTemplate = CameraDevice.TEMPLATE_PREVIEW;

    private boolean mPendingOpen = false;
    private final Object mBlockProcess = new Object();

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    private HandlerThread mEventThread;
    private Handler mEventHandler;
    private Handler.Callback mEventHandlerCallback = msg -> {
        switch (msg.what) {
            case MSG_CAMERA_OPENED:
                CameraRecordNode.this.onCameraOpened((CameraDevice) msg.obj);
                break;
            case MSG_CAMERA_DISCONNECTED:
                CameraRecordNode.this.onCameraDisconnected((CameraDevice) msg.obj);
                break;
            case MSG_CAMERA_ERROR:
                CameraRecordNode.this.onCameraError((CameraDevice) msg.obj, msg.arg1);
                break;
            case MSG_SESSION_CONFIGURED:
                CameraRecordNode.this.onSessionConfigured((CameraCaptureSession) msg.obj);
                break;
            case MSG_SESSION_CONFIGURE_FAILED:
                CameraRecordNode.this.onSessionConfigureFailed((CameraCaptureSession) msg.obj);
                break;
            case MSG_QUIT:
                CameraRecordNode.this.onQuit();
            default:
                Log.w(TAG, "Unknown message: " + msg.what);
        }
        return true;
    };

    private final Object mStreamCountLock = new Object();
    private int mStreamCount = 0;
    private final List<Surface> mSurfaces = new ArrayList<>();
    private OnCameraOpenedListener mOnCameraOpenedListener = null;

    public interface OnCameraOpenedListener {
        void onCameraOpened();
    }

    public CameraRecordNode(String name, Context context) {
        super(name);

        mContext = context;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }

    public void setCameraId(String id) {
        mCameraId = id;
    }

    public String[] getCameraIdList() {
        try {
            return mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e(TAG, "getCameraIdList() failed.", e);
            return new String[0];
        }
    }

    public Size[] getAvailableSize(Class<?> clazz) {
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                return map.getOutputSizes(clazz);
            } else {
                return new Size[0];
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "getAvailableSurfaceSize() failed.", e);
            return new Size[0];
        }
    }

    public Size[] getAvailableSurfaceSize() {
        return getAvailableSize(SurfaceTexture.class);
    }

    public Size[] getAvailableCodecSize() {
        return getAvailableSize(MediaCodec.class);
    }

    public void setStreamCount(int count) {
        if (isOpened()) {
            throw new RuntimeException("Cannot set stream count when node opened");
        }

        synchronized (mStreamCountLock) {
            Log.i(TAG, "[" + mName + "] setStreamCount()# " + count);
            mStreamCount = count;
        }
    }

    public int openCamera(OnCameraOpenedListener listener) {
        if (mIsCameraOpened) {
            return RESULT_OK;
        }
        mIsCameraOpened = true;

        mOnCameraOpenedListener = listener;

        mCameraThread = new HandlerThread("CameraWorkingThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mEventThread = new HandlerThread("CameraEventThread");
        mEventThread.start();
        mEventHandler = new Handler(mEventThread.getLooper(), mEventHandlerCallback);

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission to access camera");
            return Node.RESULT_ERROR;
        }

        try {
            // Choose the sizes for camera preview and video recording
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    Message msg = new Message();
                    msg.what = MSG_CAMERA_OPENED;
                    msg.obj = camera;
                    mEventHandler.sendMessage(msg);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    Message msg = new Message();
                    msg.what = MSG_CAMERA_DISCONNECTED;
                    msg.obj = camera;
                    mEventHandler.sendMessage(msg);
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Message msg = new Message();
                    msg.what = MSG_CAMERA_ERROR;
                    msg.obj = camera;
                    msg.arg1 = error;
                    mEventHandler.sendMessage(msg);
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "open camera failed.", e);
            mEventHandler.sendEmptyMessage(MSG_QUIT);
        } catch (NullPointerException e) {
            Log.e(TAG, "open camera failed.", e);
            mEventHandler.sendEmptyMessage(MSG_QUIT);
        }
        return RESULT_OK;
    }

    public void closeCamera() {
        if (!mIsCameraOpened) {
            return;
        }
        mIsCameraOpened = false;

        try {
            close();
        } catch (IOException e) {
            Log.e(TAG, "closeCamera()# close node failed.");
        }

        mCameraThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            Log.w(TAG, "[" + mName + "] stop working thread failed.");
        }

        mEventThread.quitSafely();
        try {
            mEventThread.join();
            mEventThread = null;
            mEventHandler = null;
        } catch (InterruptedException e) {
            Log.w(TAG, "[" + mName + "] stop event thread failed.");
        }
    }

    @Override
    public Node open() throws IOException {
        Log.d(TAG, "[" + mName + "] fake open");
        return this;
    }

    private Node realOpen() throws IOException {
        return super.open();
    }

    @Override
    protected void onOpen() throws IOException {
        mPendingOpen = false;

        try {
            mCameraDevice.createCaptureSession(mSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Message msg = new Message();
                    msg.what = MSG_SESSION_CONFIGURED;
                    msg.obj = session;
                    mEventHandler.sendMessage(msg);
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Message msg = new Message();
                    msg.what = MSG_SESSION_CONFIGURE_FAILED;
                    msg.obj = session;
                    mEventHandler.sendMessage(msg);
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "create session failed.", e);
            mEventHandler.sendEmptyMessage(MSG_QUIT);
        }
    }

    @Override
    protected void onClose() throws IOException {
        try {
            mCurrentSession.stopRepeating();
            mCurrentSession = null;
            mCurrentRequest = null;
        } catch (CameraAccessException e) {
            Log.e(TAG, "close failed", e);
        } catch (IllegalStateException e) {
            Log.w(TAG, "should already closed. ignore this error.", e);
        }

        mSurfaces.clear();
        mPendingOpen = false;
        mCaptureTemplate = CameraDevice.TEMPLATE_PREVIEW;

        synchronized (mBlockProcess) {
            mBlockProcess.notifyAll();
        }
    }

    @Override
    public int process(SurfaceData data) {
        synchronized (mSurfaces) {
            mPendingOpen = true;
            mSurfaces.add(data.surface);
            if (data.type == SurfaceData.TYPE_RECORD) {
                mCaptureTemplate = CameraDevice.TEMPLATE_RECORD;
            }

            synchronized (mStreamCountLock) {
                if (mSurfaces.size() == mStreamCount) {
                    try {
                        realOpen();
                    } catch (IOException e) {
                        Log.e(TAG, "[" + mName + "] open camera node failed.", e);
                    }
                }
            }
        }

        // only block this method when is opened.
        synchronized (mBlockProcess) {
            if (isOpened() || mPendingOpen) {
                try {
                    mBlockProcess.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "[" + mName + "] block pipeline interrupted.", e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        Log.d(TAG, "[" + mName + "] unblock pipeline");
        return RESULT_EOS;
        // TODO why sometimes thread is interrupted and unblock from wait, but thread is detected as non-interrupted at next pipeline loop.
    }

    private void onCameraOpened(CameraDevice device) {
        mCameraDevice = device;

        if (mOnCameraOpenedListener != null) {
            mOnCameraOpenedListener.onCameraOpened();
        }
    }

    private void onCameraDisconnected(CameraDevice device) {
        mEventHandler.sendEmptyMessage(MSG_QUIT);
    }

    private void onCameraError(CameraDevice device, int error) {
        mEventHandler.sendEmptyMessage(MSG_QUIT);
    }

    private void onSessionConfigured(CameraCaptureSession session) {
        mCurrentSession = session;

        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(mCaptureTemplate);
            synchronized (mSurfaces) {
                for (Surface surface : mSurfaces) {
                    builder.addTarget(surface);
                }
            }
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mCurrentRequest = builder.build();
            mCurrentSession.setRepeatingRequest(mCurrentRequest, null, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "create capture request failed.", e);
            mEventHandler.sendEmptyMessage(MSG_QUIT);
        }
    }

    private void onSessionConfigureFailed(CameraCaptureSession session) {
        mEventHandler.sendEmptyMessage(MSG_QUIT);
    }

    private void onQuit() {
        try {
            close();
        } catch (IOException e) {
            Log.e(TAG, "close failed.", e);
        }

        synchronized (mBlockProcess) {
            mBlockProcess.notifyAll();
        }
    }
}
