package com.t2m.stream;

import android.graphics.Camera;
import android.util.Log;
import android.util.Range;

import com.t2m.dualstream.Utils;
import com.t2m.npd.Task;
import com.t2m.npd.node.AudioNode;
import com.t2m.npd.node.CameraNode;
import com.t2m.stream.streams.AudioUploadStream;
import com.t2m.stream.streams.LocalVideoStream;
import com.t2m.stream.streams.PreviewStream;
import com.t2m.stream.streams.VideoUploadStream;

import java.util.ArrayList;
import java.util.List;

public class StreamTask extends Task {
    private static final String TAG = StreamTask.class.getSimpleName();

    private final List<Stream> mStreams = new ArrayList<>();
    private CameraNode mCameraNode;
    private AudioNode mAudioNode;

    public StreamTask(String name, CameraNode cameraNode, AudioNode audioNode) {
        super(name);

        mCameraNode = cameraNode;
        mAudioNode = audioNode;
    }

    public List<Stream> streams() {
        return mStreams;
    }

    @Override
    public Task start() {
        synchronized (mStreams) {
            // init for stream count
            int audioCount = Stream.audioStreamCount(mStreams);
            int videoCount = Stream.videoStreamCount(mStreams);
            mCameraNode.setStreamCount(videoCount);

            // config camera fps
            int minFps = Stream.minFrameRate(mStreams);
            int maxFps = Stream.maxFrameRate(mStreams);
            if (minFps > 0 && maxFps > 0) {
                Range<Integer> range = Utils.chooseFps(mCameraNode.getAvailableFps(), minFps, maxFps);
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

    public StreamTask addStream(Stream stream) {
        synchronized (mStreams) {
            if (stream == null || mStreams.contains(stream)) {
                Log.w(TAG, "addStream()# stream is null or already added");
                return this;
            }
            mStreams.add(stream);
            return this;
        }
    }

    public PreviewStream addPreviewStream(String name) {
        PreviewStream stream = new PreviewStream(name, mCameraNode);
        addStream(stream);
        return stream;
    }

    public LocalVideoStream addLocalVideoStream(String name) {
        LocalVideoStream stream = new LocalVideoStream(name, mCameraNode, mAudioNode);
        addStream(stream);
        return stream;
    }

    public AudioUploadStream addAudioUploadStream(String name) {
        AudioUploadStream stream = new AudioUploadStream(name);
        addStream(stream);
        return stream;
    }

    public VideoUploadStream addVideoUploadStream(String name) {
        VideoUploadStream stream = new VideoUploadStream(name);
        addStream(stream);
        return stream;
    }
}
