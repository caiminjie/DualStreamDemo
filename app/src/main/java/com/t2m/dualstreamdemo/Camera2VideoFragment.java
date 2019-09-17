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
import android.widget.Button;
import android.widget.TextView;

import com.t2m.stream.Task;
import com.t2m.stream.data.SurfaceData;
import com.t2m.stream.node.AudioNode;
import com.t2m.stream.node.CameraRecordNode;
import com.t2m.stream.node.H264EncoderNode;
import com.t2m.stream.node.H265EncoderNode;
import com.t2m.stream.node.M4aEncoderNode;
import com.t2m.stream.node.MediaMuxerNode;
import com.t2m.stream.node.SurfaceNode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_PREVIEW = 1;
    private static final int STATUS_RECORDING = 2;
    private int mStatus = STATUS_IDLE;

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

    private Task mCurrentTask;
    private Size mPreviewSize;
    private Size mVideoSize1;
    private Size mVideoSize2;
    private CameraRecordNode mCameraNode;

    private CameraRecordNode.OnCameraOpenedListener mCameraOpenedListener = () -> {
        mVideoSize1 = chooseVideoSize(mCameraNode.getAvailableCodecSize(), 16, 9, 1080);
        //mVideoSize1 = chooseVideoSize(mCameraNode.getAvailableCodecSize(), 16, 9, 720);
        mVideoSize2 = chooseVideoSize(mCameraNode.getAvailableCodecSize(), 16, 9, 720);
        mPreviewSize = chooseOptimalSize(mCameraNode.getAvailableSurfaceSize(), mTextureView.getWidth(), mTextureView.getHeight(), mVideoSize1);

        Log.i(TAG, "Size# video1: [" + mVideoSize1 + "], video2: [" + mVideoSize2 + "], preview: [" + mPreviewSize + "]");

        mTextureView.post(() -> {
            int orientation = getResources().getConfiguration().orientation;
            int sensorOrientation = mCameraNode.getSensorOrientation();
            boolean sensorLandscape = sensorOrientation == 0 || sensorOrientation == 180;
            boolean deviceLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

            if (deviceLandscape ^ sensorLandscape) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
//            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//            } else {
//                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
//            }
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        });

        startPreview();
    };


    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i("==MyTest==", "onSurfaceTextureAvailable()# begin");
            openCamera();
            Log.i("==MyTest==", "onSurfaceTextureAvailable()# end");
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            Log.i("==MyTest==", "onSurfaceTextureSizeChanged()# begin");
            configureTransform(width, height);
            Log.i("==MyTest==", "onSurfaceTextureSizeChanged()# end");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    private void openCamera() {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }

        closeCamera();

        mCameraNode = new CameraRecordNode("Camera", getContext());
        mCameraNode.setCameraId(mCameraNode.getCameraIdList()[0]);

        mCameraNode.openCamera(mCameraOpenedListener);
    }

    private void closeCamera() {
        if (mCameraNode != null) {
            mCameraNode.closeCamera();
        }
    }

    public static Camera2VideoFragment newInstance() {
        return new Camera2VideoFragment();
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices, int ratioWidth, int ratioHeight, int minWidth) {
        List<Size> c = Stream.of(choices).map((item) -> (item.getWidth() > item.getHeight()) ? new Size(item.getHeight(), item.getWidth()) : item).collect(Collectors.toList());

        if (ratioWidth > ratioHeight) {
            int temp = ratioWidth;
            ratioWidth = ratioHeight;
            ratioHeight = temp;
        }

        for (int i=0; i<choices.length; i++) {
            Size size = c.get(i);
            if (size.getWidth() * ratioHeight == size.getHeight() * ratioWidth  && size.getWidth() <= minWidth) {
                return choices[i];
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.texture);
        view.findViewById(R.id.video).setOnClickListener(v -> {
            if (mStatus == STATUS_RECORDING) {
                ((TextView) v).setText(R.string.record);
                startPreview();
            } else {
                ((TextView) v).setText(R.string.stop);
                startRecordingVideo();
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
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        super.onPause();
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

        if (!mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(texture);

        // set stream count
        mCameraNode.setStreamCount(1);

        // create node
        SurfaceNode previewNode = new SurfaceNode("preview", SurfaceData.TYPE_PREVIEW, previewSurface);

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

        if (!mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(texture);

        // init audio & camera
        AudioNode audioNode = new AudioNode("audio", MediaRecorder.AudioSource.MIC, 48000, 2, AudioFormat.ENCODING_PCM_16BIT);
        mCameraNode.setStreamCount(3);

        // stream preview
        SurfaceNode previewNode = new SurfaceNode("preview", SurfaceData.TYPE_PREVIEW, previewSurface);
        previewNode.pipeline().addNode(mCameraNode);

        // stream 1
        H264EncoderNode videoEncoderNode1 = new H264EncoderNode("videoEncoder1", mVideoSize1.getWidth(), mVideoSize1.getHeight(), 10000000, 30);
        M4aEncoderNode audioEncoderNode1 = new M4aEncoderNode("audioEncoder1");
        MediaMuxerNode muxerNode1 = new MediaMuxerNode("MuxerNode1", "/sdcard/DCIM/a.mp4", mCameraNode.getSensorOrientation());
        videoEncoderNode1.inputPipelineSurface().addNode(mCameraNode);
        videoEncoderNode1.outputPipeline().addNode(muxerNode1);
        audioEncoderNode1.inputPipeline().addNode(audioNode);
        audioEncoderNode1.outputPipeline().addNode(muxerNode1);

        // stream 2
        H264EncoderNode videoEncoderNode2 = new H264EncoderNode("videoEncoder2", mVideoSize2.getWidth(), mVideoSize2.getHeight(), 5000000, 30);
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

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

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
