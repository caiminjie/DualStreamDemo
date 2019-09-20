package com.t2m.dualstream;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.t2m.stream.streams.LocalVideoStream;
import com.t2m.stream.streams.PreviewStream;
import com.t2m.stream.Stream;
import com.t2m.stream.streams.AudioUploadStream;
import com.t2m.npd.node.AudioNode;
import com.t2m.npd.node.CameraNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StreamManager {
    private static final String TAG = StreamManager.class.getSimpleName();

    public static final int CHANNEL_BACKGROUND = 0;
    public static final int CHANNEL_TEMP = 1;
    public static final int CHANNEL_COUNT = 2;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_PREVIEW = 1;
    public static final int STATUS_RECORDING = 2;

    private IStreamService mService;

    private static StreamManager sManager = null;

    private static Handler sHandler = new Handler();
    private static ServiceConnection sConnection = null;

    public interface OnServiceReadyListener {
        void onServiceReady(StreamManager manager);
    }

    public interface OnServiceDiedListener {
        void onServiceDied();
    }

    public static void getService(Context context, final OnServiceReadyListener readyListener, final OnServiceDiedListener diedListener) {
        if (sManager != null) {
            sHandler.post(() -> {
                if (readyListener != null) {
                    readyListener.onServiceReady(sManager);
                }
            });
            return;
        }

        Intent intent = new Intent(context, StreamService.class);
        context.bindService(intent, sConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                sManager = new StreamManager(service);
                if (readyListener != null) {
                    readyListener.onServiceReady(sManager);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                sManager = null;
                sConnection = null;
                if (diedListener != null) {
                    diedListener.onServiceDied();
                }
            }
        }, Service.BIND_AUTO_CREATE);
    }

    public void release(Context context) {
        if (sConnection != null) {
            context.unbindService(sConnection);
        }
    }

    private StreamManager(IBinder binder) {
        mService = IStreamService.Stub.asInterface(binder);
    }

    public CameraNode getCameraNode() {
        try {
            return StreamService.getData(mService.getCameraNode());
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return null;
        }
    }

    public AudioNode getAudioNode() {
        try {
            return StreamService.getData(mService.getAudioNode());
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return null;
        }
    }

    public int getStatus(int channel) {
        try {
            return mService.getStatus(channel);
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
            return STATUS_IDLE;
        }
    }

    public PreviewStream createPreviewStream(String name) {
        return new PreviewStream(name, getCameraNode());
    }

    public LocalVideoStream createLocalVideoStream(String name) {
        return new LocalVideoStream(name, getCameraNode(), getAudioNode());
    }

    public AudioUploadStream createUploadStream(String name) {
        return new AudioUploadStream(name);
    }

    public void startStreams(String name, int channel, int status, boolean stopPreviewTask, List<Stream> streams) {
        try {
            mService.startStreams(name, channel, status, stopPreviewTask, StreamService.putData(streams));
        } catch (RemoteException e) {
            Log.e(TAG, "remote exception", e);
        }
    }

    public void startStreams(String name, int channel, int status, boolean stopPreviewTask, Stream... streams) {
        ArrayList<Stream> s = new ArrayList<>();
        Collections.addAll(s, streams);
        startStreams(name, channel, status, stopPreviewTask, s);
    }
}
