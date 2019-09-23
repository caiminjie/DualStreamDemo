package com.t2m.stream;

import android.util.Log;
import android.util.Range;

import com.t2m.dualstream.Utils;
import com.t2m.npd.Task;
import com.t2m.npd.node.AudioNode;
import com.t2m.npd.node.CameraNode;
import com.t2m.stream.streams.AudioRecordStream;
import com.t2m.stream.streams.AudioUploadStream;
import com.t2m.stream.streams.VideoRecordStream;
import com.t2m.stream.streams.PreviewStream;
import com.t2m.stream.streams.VideoUploadStream;

import java.util.ArrayList;
import java.util.List;

public class StreamTask extends Task {
    private static final String TAG = StreamTask.class.getSimpleName();

    private final List<Stream> mStreams = new ArrayList<>();
    private CameraNode mCameraNode;
    private AudioNode mAudioNode;

    @SuppressWarnings("unused")
    private int mAudioCount = 0;
    private int mVideoCount = 0;
    private int mMinFrameRate = Integer.MAX_VALUE;
    private int mMaxFrameRate = 0;

    public StreamTask(String name, CameraNode cameraNode, AudioNode audioNode) {
        super(name);

        mCameraNode = cameraNode;
        mAudioNode = audioNode;
    }

    @SuppressWarnings("unused")
    public List<Stream> streams() {
        return mStreams;
    }

    @Override
    public Task start() {
        synchronized (mStreams) {
            // init for stream count
            mCameraNode.setStreamCount(mVideoCount);

            // config camera fps
            if (mMinFrameRate > 0 && mMaxFrameRate > 0) {
                Range<Integer> range = Utils.chooseFps(mCameraNode.getAvailableFps(), mMinFrameRate, mMaxFrameRate);
                mCameraNode.setFps(range);
            }

            // build task nodes from streams
            for (Stream stream : mStreams) {
                stream.build(this);
            }
        }

        // start task
        return super.start();
    }

    @SuppressWarnings("unused | WeakerAccess | UnusedReturnValue")
    public StreamTask addStream(Stream stream) {
        synchronized (mStreams) {
            if (stream == null || mStreams.contains(stream)) {
                Log.w(TAG, "addStream()# stream is null or already added. Ignore it.");
                return this;
            }

            // config
            if (stream.isAudio()) {
                mAudioCount ++;
            }
            if (stream.isVideo()) {
                mVideoCount ++;
                int frameRate = ((IVideoStream) stream).getFrameRate();
                if (frameRate > mMaxFrameRate)  mMaxFrameRate = frameRate;
                if (frameRate < mMinFrameRate)  mMinFrameRate = frameRate;
            }

            // add
            mStreams.add(stream);
            return this;
        }
    }

    @SuppressWarnings("unused")
    public PreviewStream addPreviewStream(String name) {
        PreviewStream stream = new PreviewStream(name, mCameraNode);
        addStream(stream);
        return stream;
    }

    @SuppressWarnings("unused")
    public VideoRecordStream addVideoRecordStream(String name) {
        VideoRecordStream stream = new VideoRecordStream(name, mCameraNode, mAudioNode);
        addStream(stream);
        return stream;
    }

    @SuppressWarnings("unused")
    public AudioUploadStream addAudioUploadStream(String name) {
        AudioUploadStream stream = new AudioUploadStream(name);
        addStream(stream);
        return stream;
    }

    @SuppressWarnings("unused")
    public VideoUploadStream addVideoUploadStream(String name) {
        VideoUploadStream stream = new VideoUploadStream(name);
        addStream(stream);
        return stream;
    }

    @SuppressWarnings("unused")
    public AudioRecordStream addAudioRecordStream(String name) {
        AudioRecordStream stream = new AudioRecordStream(name, mAudioNode);
        addStream(stream);
        return stream;
    }
}
