package com.t2m.dualstream;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;

public class DualStreamServiceManager {
    private static final String TAG = DualStreamServiceManager.class.getSimpleName();
    private IDualStreamService mService;

    public DualStreamServiceManager(IBinder binder) {
        mService = IDualStreamService.Stub.asInterface(binder);
    }

    public DualStreamServiceManager(IDualStreamService service) {
        mService = service;
    }

    public void openCamera(String id, ICameraOpenedCallback callback) {
        try {
            mService.openCamera(id, callback);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public void closeCamera() {
        try {
            mService.closeCamera();
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public int getCameraOrientation() {
        try {
            return mService.getCameraOrientation();
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return 90;
        }
    }

    public String[] getCameraIdList() {
        try {
            int key = mService.getCameraIdList();
            return (String[]) DualStreamService.getData(key, new String[0]);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return new String[0];
        }
    }

    public Size[] getAvailableSurfaceSize() {
        try {
            int key = mService.getAvailableSurfaceSize();
            return (Size[]) DualStreamService.getData(key, new Size[0]);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return new Size[0];
        }
    }

    public Size[] getAvailableCodecSize() {
        try {
            int key = mService.getAvailableCodecSize();
            return (Size[]) DualStreamService.getData(key, new Size[0]);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return new Size[0];
        }
    }

    public void setCameraStreamCount(int count) {
        try {
            mService.setCameraStreamCount(count);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public Range<Integer>[] getAvailableFps() {
        try {
            int key = mService.getAvailableFps();
            return (Range<Integer>[]) DualStreamService.getData(key, (new ArrayList<Range<Integer>>()).toArray());
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return (Range<Integer>[]) (new ArrayList<Range<Integer>>()).toArray();
        }
    }

    public void setFps(Range<Integer> fps) {
        try {
            mService.setFps(DualStreamService.putData(fps));
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public void setPreviewSurface(Surface surface) {
        try {
            mService.setPreviewSurface(DualStreamService.putData(surface));
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public int getStatus() {
        try {
            return mService.getStatus();
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return DualStreamService.STATUS_IDLE;
        }
    }

    public void startPreview() {
        try {
            mService.startPreview();
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public void startVideoRecording() {
        try {
            mService.startVideoRecording();
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public void setPreviewSize(Size size) {
        try {
            mService.setPreviewSize(DualStreamService.putData(size));
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public void setVideoSize(int index, Size size) {
        try {
            mService.setVideoSize(index, DualStreamService.putData(size));
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }
}
