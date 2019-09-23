package com.t2m.stream.streams;

import android.util.Log;
import android.util.Size;

import com.t2m.dualstream.Utils;
import com.t2m.npd.Task;
import com.t2m.npd.node.AudioNode;
import com.t2m.npd.node.CameraNode;
import com.t2m.npd.node.CodecNode;
import com.t2m.npd.node.H264EncoderNode;
import com.t2m.npd.node.H265EncoderNode;
import com.t2m.npd.node.M4aEncoderNode;
import com.t2m.npd.node.MediaMuxerNode;
import com.t2m.stream.IAudioStream;
import com.t2m.stream.IVideoStream;
import com.t2m.stream.Stream;

public class VideoRecordStream extends Stream implements IAudioStream<VideoRecordStream>, IVideoStream<VideoRecordStream> {
    private static final String TAG = VideoRecordStream.class.getSimpleName();

    public static final int CODEC_H264 = 0;
    @SuppressWarnings("WeakerAccess")
    public static final int CODEC_H265 = 1;

    private CameraNode mCameraNode;
    private AudioNode mAudioNode;

    private int mPreferredVideoMinWidth = 0;
    private Size mPreferredVideoRatio = null;
    private Size mVideoSize = null;
    private int mBitRate = 10000000;
    private int mFrameRate = 30;
    private int mVideoCodecType = CODEC_H264;
    private String mPath = null;

    public VideoRecordStream(String name, CameraNode cameraNode, AudioNode audioNode) {
        super(name);

        mCameraNode = cameraNode;
        mAudioNode = audioNode;
    }


    @Override
    public boolean build(Task task) {
        if (!mCameraNode.isCameraOpened()) {
            Log.e(TAG, "Cannot build template due to camera node not opened.");
            return false;
        }

        // prepare video size
        if (mVideoSize == null) {
            if (mPreferredVideoMinWidth <= 0 || mPreferredVideoRatio == null) {
                Log.e(TAG, "Cannot build template due to video size not set. see: setVideoSize() or setPreferredVideoSize()");
                return false;
            }
            mVideoSize = Utils.chooseVideoSize(mCameraNode.getAvailableCodecSize(), mPreferredVideoMinWidth, mPreferredVideoRatio);
            if (mVideoSize == null) {
                Log.e(TAG, "Cannot build template due to preferred video size not valid.");
                return false;
            }
        }

        // check
        if (mPath == null) {
            Log.e(TAG, "Cannot build template due to path not set. see: setPath().");
            return false;
        }

        // create node
        CodecNode videoEncoderNode = (mVideoCodecType == CODEC_H265) ?
                new H265EncoderNode(subName("VE265"), mVideoSize.getWidth(), mVideoSize.getHeight(), mBitRate, mFrameRate) :
                new H264EncoderNode(subName("VE264"), mVideoSize.getWidth(), mVideoSize.getHeight(), mBitRate, mFrameRate);
        M4aEncoderNode audioEncoderNode = new M4aEncoderNode(subName("AE"));
        MediaMuxerNode muxerNode = new MediaMuxerNode(subName("MX"), mPath, mCameraNode.getSensorOrientation());

        // init pipeline
        videoEncoderNode.inputPipelineSurface().addNode(mCameraNode);
        videoEncoderNode.outputPipeline().addNode(muxerNode);
        audioEncoderNode.inputPipeline().addNode(mAudioNode);
        audioEncoderNode.outputPipeline().addNode(muxerNode);

        // create task
        task
                .addNode(mCameraNode)
                .addNode(mAudioNode)
                .addNode(videoEncoderNode)
                .addNode(audioEncoderNode)
                .addNode(muxerNode);

        // return task
        return true;
    }

    public VideoRecordStream setPreferredVideoSize(int minWidth, int ratioWidth, int ratioHeight) {
        mPreferredVideoMinWidth = minWidth;
        mPreferredVideoRatio = new Size(ratioWidth, ratioHeight);
        return this;
    }

    @SuppressWarnings("unused")
    public VideoRecordStream setVideoSize(int width, int height) {
        mVideoSize = new Size(width, height);
        return this;
    }

    @SuppressWarnings("unused")
    public VideoRecordStream setPreviewSize(Size size) {
        mVideoSize = size;
        return this;
    }

    @SuppressWarnings("unused")
    public Size getVideoSize() {
        return mVideoSize;
    }

    public VideoRecordStream setBitRate(int bitRate) {
        mBitRate = bitRate;
        return this;
    }

    @Override
    public VideoRecordStream setFrameRate(int frameRate) {
        mFrameRate = frameRate;
        return this;
    }

    @Override
    public int getFrameRate() {
        return mFrameRate;
    }

    public VideoRecordStream setVideoCodecType(int codecType) {
        mVideoCodecType = codecType;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public VideoRecordStream setPath(String path) {
        mPath = path;
        return this;
    }
}
