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
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraDevice;
import android.media.AudioFormat;
import android.media.MediaRecorder;
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
import com.t2m.pan.Pipeline;
import com.t2m.pan.Task;
import com.t2m.pan.node.head.CodecNode;
import com.t2m.pan.node.head.H264EncoderNode;
import com.t2m.pan.node.head.M4aEncoderNode;
import com.t2m.pan.node.head.SurfaceNode;
import com.t2m.pan.node.head.WavHeadNode;
import com.t2m.pan.node.tail.AudioNode;
import com.t2m.pan.node.tail.CameraNode;
import com.t2m.pan.node.tail.MediaMuxerNode;
import com.t2m.pan.node.tail.SmartCameraNode;
import com.t2m.stream.StreamTask;
import com.t2m.stream.streams.AudioRecordStream;
import com.t2m.stream.streams.VideoRecordStream;

import java.io.File;

public class SmartCamera2VideoFragment extends Fragment
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

    private CameraNode mCameraNode;
    private AudioNode mAudioNode;
    private boolean mIsRecording = false;
    private Task mTask;

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

    public static SmartCamera2VideoFragment newInstance() {
        return new SmartCamera2VideoFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mCameraNode = new CameraNode("Camera", getContext());
        mAudioNode = new AudioNode("audio", MediaRecorder.AudioSource.MIC, 48000, 2, AudioFormat.ENCODING_PCM_16BIT);
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.texture);
        view.findViewById(R.id.video).setOnClickListener(v -> {
            if (mIsRecording) {
                ((TextView) v).setText(R.string.record);
                startPreview();
            } else {
                ((TextView) v).setText(R.string.stop);
                //startDualVideoRecord();
                startVideoAudioRecord(false);
            }
        });
        view.findViewById(R.id.segment).setOnClickListener(v -> {
        });
        view.findViewById(R.id.prerecord).setOnClickListener(v -> {
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
        if (mTask != null) {
            mTask.stop();
            mTask.waitForFinish();
        }

        SurfaceNode previewNode = new SurfaceNode("previewSurface", CameraDevice.TEMPLATE_PREVIEW, createPreviewSurface());

        mTask = new Task("preview");
        mTask.addPipeline("preview")
                .addNode(mCameraNode)
                .addNode(previewNode);

        mTask.start();
    }

    private void startDualVideoRecord() {
    }

    private void startVideoAudioRecord(boolean isPreRecord) {
        if (mTask != null) {
            mTask.stop();
            mTask.waitForFinish();
        }

        Log.i("==MyTest==", "startVideoAudioRecord()");
        Size videoSize = Utils.chooseVideoSize(mCameraNode.getAvailableCodecSize(), 1080, new Size(16, 9));

        SurfaceNode previewNode = new SurfaceNode("previewSurface", CameraDevice.TEMPLATE_PREVIEW, createPreviewSurface());
        H264EncoderNode videoEncoder = new H264EncoderNode("VE264", videoSize.getWidth(), videoSize.getHeight(), 10000000, 30, CodecNode.TYPE_SURFACE, CodecNode.TYPE_BYTE_BUFFER);
        M4aEncoderNode audioEncoder = new M4aEncoderNode("AE", CodecNode.TYPE_BYTE_BUFFER, CodecNode.TYPE_BYTE_BUFFER);
        MediaMuxerNode muxerNode = new MediaMuxerNode("MX", "/sdcard/DCIM/ds/a.mp4", mCameraNode.getSensorOrientation());
        WavHeadNode wavNode = new WavHeadNode("WavHeadWriter", "/sdcard/DCIM/ds/b.wav", mAudioNode.getSampleRate(), mAudioNode.getChannelCount(), mAudioNode.getAudioFormat());

        mTask = new Task("dual stream");
        mTask.addPipeline("preview")
                .addNode(mCameraNode)
                .addNode(previewNode);
        mTask.addPipeline("VideoInput")
                .addNode(mCameraNode)
                .addNode(videoEncoder.getInputNode());
        mTask.addPipeline("VideoOutput")
                .addNode(videoEncoder.getOutputNode())
                .addNode(muxerNode);
        mTask.addPipeline("AudioInput")
                .addNode(mAudioNode)
                .addNode(audioEncoder.getInputNode());
        mTask.addPipeline("AudioOutput")
                .addNode(audioEncoder.getOutputNode())
                .addNode(muxerNode);
        mTask.addPipeline("AudioWav")
                .addNode(mAudioNode)
                .addNode(wavNode);

        mTask.start();
    }

    private void onOpenCamera() {
        final CameraNode cameraNode = mCameraNode;
        if (mCameraId == null) {
            mCameraId = cameraNode.getCameraIdList()[0];
        }
        cameraNode.setCameraId(mCameraId);
        cameraNode.openCamera(() -> {
            mPreviewSize = Utils.chooseOptimalSize(
                    cameraNode.getAvailableSurfaceSize(),
                    mTextureView.getWidth(), mTextureView.getHeight(),
                    16, 9);
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
        mCameraNode.closeCamera();
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

    private String getOutputPath(String ext) {
        File file;
        while ((file = new File("/sdcard/DCIM/ds/" + System.currentTimeMillis() + "." + ext)).exists());
        return file.getAbsolutePath();
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
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
