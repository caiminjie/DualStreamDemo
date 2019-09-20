/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.t2m.dualstreamdemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.t2m.dualstream.StreamManager;
import com.t2m.dualstream.Utils;
import com.t2m.stream.streams.LocalVideoStream;
import com.t2m.stream.Stream;
import com.t2m.npd.node.CameraNode;

import java.io.File;

public class Camera2VideoFragment extends Fragment
        implements FragmentCompat.OnRequestPermissionsResultCallback {

    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    public static final String TAG = "Camera2VideoFragment";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    // TODO
    private String mCameraId = null;
    private Size mPreviewSize;
    private StreamManager mStreamManager;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            onOpenCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        StreamManager.getService(
                getContext(),
                (manager) -> mStreamManager = manager,
                () -> mStreamManager = null);
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.texture);
        view.findViewById(R.id.video).setOnClickListener(v -> {
            if (mStreamManager == null) {
                Log.w(TAG, "service not ready");
                return;
            }

            if (mStreamManager.getStatus(StreamManager.CHANNEL_BACKGROUND) == StreamManager.STATUS_RECORDING) {
                ((TextView) v).setText(R.string.record);
                startPreview();
            } else {
                ((TextView) v).setText(R.string.stop);
                startDualVideoRecord();
            }
        });
        view.findViewById(R.id.info).setOnClickListener(v -> {
            Activity activity = getActivity();
            if (null != activity) {
                new AlertDialog.Builder(activity)
                        .setMessage(R.string.intro_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTextureView.isAvailable()) {
            onOpenCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        if (mStreamManager != null) {
            mStreamManager.release(getContext());
        }
        onCloseCamera();
        super.onPause();
    }

    private Surface createPreviewSurface() {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        return new Surface(texture);
    }

    private void startPreview() {
        Stream previewStream = mStreamManager.createPreviewStream("Preview")
                .setPreviewSurface(createPreviewSurface())
                .setPreviewSize(mPreviewSize);

        mStreamManager.startStreams(
                "Preview",
                StreamManager.CHANNEL_BACKGROUND,
                StreamManager.STATUS_PREVIEW,
                true,
                previewStream);
    }

    private void startDualVideoRecord() {
        Stream previewStream = mStreamManager.createPreviewStream("Preview")
                .setPreviewSurface(createPreviewSurface())
                .setPreviewSize(mPreviewSize);
        Stream videoStream1 = mStreamManager.createLocalVideoStream("Video1")
                .setPreferredVideoSize(1080, 16, 9)
                .setVideoCodecType(LocalVideoStream.CODEC_H264)
                .setBitRate(10000000)
                .setFrameRate(30)
                .setPath("/sdcard/DCIM/a.mp4");
        Stream videoStream2 = mStreamManager.createLocalVideoStream("Video2")
                .setPreferredVideoSize(720, 16, 9)
                .setVideoCodecType(LocalVideoStream.CODEC_H264)
                .setBitRate(10000000)
                .setFrameRate(30)
                .setPath("/sdcard/DCIM/b.mp4");

        mStreamManager.startStreams(
                "DualVideo",
                StreamManager.CHANNEL_BACKGROUND,
                StreamManager.STATUS_RECORDING,
                true,
                previewStream, videoStream1, videoStream2);
    }

    private void onOpenCamera() {
        final CameraNode cameraNode = mStreamManager.getCameraNode();
        if (mCameraId == null) {
            mCameraId = cameraNode.getCameraIdList()[0];
        }
        cameraNode.setCameraId(mCameraId);
        cameraNode.openCamera(() -> {
            mPreviewSize = Utils.chooseOptimalSize(
                    cameraNode.getAvailableSurfaceSize(),
                    mTextureView.getWidth(), mTextureView.getHeight(),
                    16, 9);

            cameraNode.setFps(Utils.chooseFps(cameraNode.getAvailableFps(), 30, 30));

            Log.i(TAG, "preview: [" + mPreviewSize + "]");

            mTextureView.post(() -> {
                int orientation = getResources().getConfiguration().orientation;
                int sensorOrientation = cameraNode.getSensorOrientation();
                boolean sensorLandscape = sensorOrientation == 0 || sensorOrientation == 180;
                boolean deviceLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

                if (deviceLandscape ^ sensorLandscape) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            });

            startPreview();
        });
    }

    private void onCloseCamera() {
        mStreamManager.getCameraNode().closeCamera();
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private File getVideoFile(Context context) {
        File outputFile = new File(context.getExternalFilesDir(null), "/"+System.currentTimeMillis() + ".mp4");
        Log.d(TAG, "getVideoFile: " + outputFile.toString());
        return outputFile;
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                            REQUEST_VIDEO_PERMISSIONS))
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> parent.getActivity().finish())
                    .create();
        }

    }

}
