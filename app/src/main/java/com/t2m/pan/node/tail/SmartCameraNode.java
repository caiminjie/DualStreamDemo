package com.t2m.pan.node.tail;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
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
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import com.t2m.pan.ScriptC_StreamProcess;
import com.t2m.pan.data.SurfaceData;
import com.t2m.pan.pan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SmartCameraNode extends TailNode<SurfaceData> {
    private static final String TAG = SmartCameraNode.class.getSimpleName();

    private static final int PREFERRED_FPS = 30;

    private Context mContext;
    private CameraManager mCameraManager;
    private String mCameraId = null;
    private boolean mIsCameraOpened = false;

    private Range<Integer> mFps;

    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCurrentSession = null;
    private CaptureRequest mCurrentRequest = null;

    private boolean mPendingOpen = false;
    private final Object mBlockProcess = new Object();

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    private OnCameraOpenedListener mOnCameraOpenedListener = null;

    private Size mSourceSize;
    private RenderScript mRs;

    public interface OnCameraOpenedListener {
        void onCameraOpened();
    }

    public SmartCameraNode(String name, Context context) {
        super(name);

        mContext = context;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        mRs = RenderScript.create(mContext);
    }

    @Override
    public boolean isOpened() {
        return mCurrentSession != null;
    }

    @Override
    protected void onOpen() throws IOException {
        mPendingOpen = true;
    }

    @Override
    protected void onClose() throws IOException {
        Log.i("==MyTest==", "CameraNode.onClose()# begin");

        try {
            if (mCurrentSession != null) {
                mCurrentSession.stopRepeating();
                mCurrentSession = null;
                mCurrentRequest = null;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "close failed", e);
        } catch (IllegalStateException e) {
            Log.w(TAG, "should already closed. ignore this error.", e);
        }

        mPendingOpen = false;

        synchronized (mBlockProcess) {
            mBlockProcess.notifyAll();
        }
        Log.i("==MyTest==", "CameraNode.onClose()# end");
    }

    @Override
    protected int onProcessData(SurfaceData data) {
        addStream(data);

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
        return pan.RESULT_EOS; // only run once
    }

    public void setCameraId(String id) {
        mCameraId = id;

        mSourceSize = bestSurfaceSize();

        // get fps range
        Range<Integer>[] ranges = getAvailableFps();
        mFps = ranges[0];
        for (Range<Integer> range : ranges) {
            if (range.getUpper() >= PREFERRED_FPS &&
                    ((range.getUpper() - range.getLower()) < (mFps.getUpper() - mFps.getLower()))) {
                mFps = range;
            }
        }
    }

    public int getSensorOrientation() {
        try {
            Integer i = mCameraManager.getCameraCharacteristics(mCameraId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION);
            return i == null ? 90 : i;
        } catch (CameraAccessException e) {
            Log.e(TAG, "getSensorOrientation() failed.", e);
            return 90;
        }
    }

    public String[] getCameraIdList() {
        try {
            return mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e(TAG, "getCameraIdList() failed.", e);
            return new String[0];
        }
    }

    @SuppressWarnings("WeakerAccess")
    public Size[] getAvailableSize(Class<?> clazz) {
        try {
            StreamConfigurationMap map = mCameraManager.getCameraCharacteristics(mCameraId)
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

    public Size bestSurfaceSize() {
        Size bestSize = null;
        int maxSize = 0;
        Size[] sizes = getAvailableSurfaceSize();
        for (Size size : sizes) {
            int s = size.getWidth() * size.getHeight();
            if (s > maxSize) {
                bestSize = size;
                maxSize = s;
            }
        }
        return bestSize;
    }

    public Range<Integer>[] getAvailableFps() {
        try {
            return mCameraManager.getCameraCharacteristics(mCameraId)
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        } catch (CameraAccessException e) {
            Log.e(TAG, "getAvailableFps() failed.", e);
            return null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isCameraOpened() {
        return mIsCameraOpened;
    }

    @SuppressWarnings("UnusedReturnValue")
    public int openCamera(OnCameraOpenedListener listener) {
        if (mIsCameraOpened) {
            return pan.RESULT_OK;
        }
        mIsCameraOpened = true;

        mOnCameraOpenedListener = listener;

        mCameraThread = new HandlerThread("CameraWorkingThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission to access camera");
            return pan.RESULT_ERROR;
        }

        try {
            // Choose the sizes for camera preview and video recording
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    onCameraOpened(camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    onQuit();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    onQuit();
                }
            }, mCameraHandler);
        } catch (CameraAccessException | NullPointerException e) {
            Log.e(TAG, "open camera failed.", e);
            onQuit();
        }
        return pan.RESULT_OK;
    }

    public void closeCamera() {
        if (!mIsCameraOpened) {
            return;
        }
        mIsCameraOpened = false;

        onQuit();

        mCameraThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            Log.w(TAG, "[" + mName + "] stop working thread failed.");
        }
    }

    private void onCameraOpened(CameraDevice device) {
        mCameraDevice = device;

        if (mOnCameraOpenedListener != null) {
            mOnCameraOpenedListener.onCameraOpened();
        }
    }

    private void onQuit() {
        try {
            close();
        } catch (IOException e) {
            Log.e(TAG, "close failed.", e);
        }
    }

    /////////////////////////////////////////
    private Allocation mSourceAllocation = null;
    private Thread mStreamProcessThread;
    private final Object mStreamProcessLock = new Object();
    private List<Allocation> mOutAllocations = new ArrayList<>();
    private List<ScriptC_StreamProcess> mScripts = new ArrayList<>();
    private int mPendingFrames = 0;

    private void addStream(SurfaceData surfaceData) {
        synchronized (mStreamProcessLock) {
            // start capture if necessary
            if (mSourceAllocation == null) {
                mOutAllocations.clear();

                // create source allocation
                Type.Builder yuvTypeBuilder = new Type.Builder(mRs, Element.YUV(mRs));
                yuvTypeBuilder.setX(mSourceSize.getWidth());
                yuvTypeBuilder.setY(mSourceSize.getHeight());
                yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
                mSourceAllocation = Allocation.createTyped(mRs, yuvTypeBuilder.create(),
                        Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

                mSourceAllocation.setOnBufferAvailableListener(new Allocation.OnBufferAvailableListener() {
                    @Override
                    public void onBufferAvailable(Allocation a) {
                        synchronized (mStreamProcessLock) {
                            mPendingFrames ++;
                            mStreamProcessLock.notifyAll();
                        }
                    }
                });

                // start capture session
                try {
                    mCameraDevice.createCaptureSession(
                            Collections.singletonList(mSourceAllocation.getSurface()), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    // request capture
                                    mCurrentSession = session;
                                    mPendingOpen = false;

                                    try {
                                        CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                        builder.addTarget(mSourceAllocation.getSurface());
                                        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mFps);
                                        mCurrentRequest = builder.build();
                                        mCurrentSession.setRepeatingRequest(mCurrentRequest, null, mCameraHandler);
                                    } catch (CameraAccessException e) {
                                        Log.e(TAG, "create capture request failed.", e);
                                        onQuit();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {
                                    onQuit();
                                }
                            }, mCameraHandler);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "create session failed.", e);
                    onQuit();
                }
            }

            // create output allocation
            Type.Builder yuvTypeBuilder = new Type.Builder(mRs, Element.YUV(mRs));
            yuvTypeBuilder.setX(surfaceData.width);
            yuvTypeBuilder.setY(surfaceData.height);
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            Allocation alloc = Allocation.createTyped(mRs, yuvTypeBuilder.create(),
                    Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
            alloc.setSurface(surfaceData.surface);

            // create script
            ScriptC_StreamProcess rs = new ScriptC_StreamProcess(mRs);
            rs.set_src(mSourceAllocation);
            rs.set_dst(alloc);
            rs.invoke_initSize();
            mScripts.add(rs);
        }
    }

    private void startStreamProcess() {
        synchronized (mStreamProcessLock) {
            if (mStreamProcessThread == null) {
                mStreamProcessThread = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        synchronized (mStreamProcessLock) {
                            // wait for frame available
                            while (!Thread.currentThread().isInterrupted() && mPendingFrames <= 0) {
                                try {
                                    mStreamProcessLock.wait();
                                } catch (InterruptedException e) {
                                    Log.w(TAG, "wait interrupted.");
                                }
                            }
                            mPendingFrames = 0;

                            // fill stream data
                            for (ScriptC_StreamProcess script : mScripts) {
                                script.invoke_initSize();
                            }
                        }
                    }
                });
            }
        }
    }

    private void stopStreamProcess() {
        synchronized (mStreamProcessLock) {
            if (mStreamProcessThread != null) {
                mStreamProcessThread.interrupt();
            }
        }
    }
}
