package com.t2m.stream.streams;

import android.util.Log;
import android.util.Size;

import com.t2m.dualstream.Utils;
import com.t2m.pan.Task;
import com.t2m.pan.node.conn.ByteBufferCacheNode;
import com.t2m.pan.node.conn.GlNode;
import com.t2m.pan.node.tail.AudioNode;
import com.t2m.pan.node.tail.CameraNode;
import com.t2m.pan.node.head.CodecNode;
import com.t2m.pan.node.head.H264EncoderNode;
import com.t2m.pan.node.head.H265EncoderNode;
import com.t2m.pan.node.head.M4aEncoderNode;
import com.t2m.pan.node.tail.MediaMuxerNode;
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

    private CodecNode mVideoEncoderNode = null;
    private CodecNode mAudioEncoderNode = null;
    private ByteBufferCacheNode mVideoCacheNode = null;
    private ByteBufferCacheNode mAudioCacheNode = null;
    private MediaMuxerNode mMuxerNode = null;

    private boolean mEnableOutput = true;
    private long mBlockDurationUs = 0;

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
        mVideoEncoderNode = (mVideoCodecType == CODEC_H265) ?
                new H265EncoderNode(subName("VE265"), mVideoSize.getWidth(), mVideoSize.getHeight(), mBitRate, mFrameRate, CodecNode.TYPE_SURFACE, CodecNode.TYPE_BYTE_BUFFER) :
                new H264EncoderNode(subName("VE264"), mVideoSize.getWidth(), mVideoSize.getHeight(), mBitRate, mFrameRate, CodecNode.TYPE_SURFACE, CodecNode.TYPE_BYTE_BUFFER);
        mAudioEncoderNode = new M4aEncoderNode(subName("AE"), CodecNode.TYPE_BYTE_BUFFER, CodecNode.TYPE_BYTE_BUFFER);
        mVideoCacheNode = new ByteBufferCacheNode("VC");
        mAudioCacheNode = new ByteBufferCacheNode("AC");
        mMuxerNode = new MediaMuxerNode(subName("MX"), mPath, mCameraNode.getSensorOrientation());
        GlNode glNode = new GlNode("WaterMark");


        // config node
        mVideoCacheNode.enableOutput(mEnableOutput);
        mAudioCacheNode.enableOutput(mEnableOutput);
        mVideoCacheNode.cacheDurationUs(mBlockDurationUs);
        mAudioCacheNode.cacheDurationUs(mBlockDurationUs);

        // config video to GlNode input
        task.addPipeline("VideoGlInput")
                .addNode(mCameraNode)
                .addNode(glNode.getInputNode());

        // config video to codec input
        task.addPipeline("VideoGlOutput")
                .addNode(glNode.getOutputNode())
                .addNode(mVideoEncoderNode.getInputNode());

        // config video output pipeline
        task.addPipeline("VideoOutput")
                .addNode(mVideoEncoderNode.getOutputNode())
                .addNode(mVideoCacheNode)
                .addNode(mMuxerNode);

        // config audio input pipeline
        task.addPipeline("AudioInput")
                .addNode(mAudioNode)
                .addNode(mAudioEncoderNode.getInputNode());

        // config audio output pipeline
        task.addPipeline("AudioOutput")
                .addNode(mAudioEncoderNode.getOutputNode())
                .addNode(mAudioCacheNode)
                .addNode(mMuxerNode);

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

    public void enableOutput(boolean enable) {
        mEnableOutput = enable;
        if (mVideoCacheNode != null) {
            mVideoCacheNode.enableOutput(mEnableOutput);
        }
        if (mAudioCacheNode != null) {
            mAudioCacheNode.enableOutput(mEnableOutput);
        }
    }

    public boolean enableOutput() {
        return mEnableOutput;
    }

    public void setBlockDurationUs(long durationUs) {
        mBlockDurationUs = durationUs;
        if (mVideoCacheNode != null) {
            mVideoCacheNode.cacheDurationUs(mBlockDurationUs);
        }
        if (mAudioCacheNode != null) {
            mAudioCacheNode.cacheDurationUs(mBlockDurationUs);
        }
    }

    public long getBlockDurationUs() {
        return mBlockDurationUs;
    }

    public void setBlockDurationMs(long durationMs) {
        setBlockDurationUs(durationMs * 1000);
    }

    public long getBlockDurationMs() {
        return mBlockDurationUs / 1000;
    }

    public void newSegment(String path) {
        mMuxerNode.newSegment(path);
    }
}
