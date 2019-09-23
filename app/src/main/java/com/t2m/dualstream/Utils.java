package com.t2m.dualstream;

import android.util.Log;
import android.util.Range;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    public static Range<Integer> chooseFps(Range<Integer>[] choices, int lower, int upper) {
        if (choices != null) {
            return Stream.of(choices).filter((choice) -> choice.getLower() >= lower && choice.getUpper() >= upper)
                    .findFirst().orElse(null);
        }
        return new Range<>(lower, upper);
    }

    @SuppressWarnings("unused")
    public static Range<Integer> chooseFps(Range<Integer>[] choices, Range<Integer> target) {
        return chooseFps(choices, target.getLower(), target.getUpper());
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @param minWidth The min edge of required size
     * @param ratioWidth The ratio width
     * @param ratioHeight The ratio height
     * @return The video size
     */
    @SuppressWarnings("WeakerAccess")
    public static Size chooseVideoSize(Size[] choices, int minWidth, int ratioWidth, int ratioHeight) {
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
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices  The list of available sizes
     * @param minWidth The min edge of required size
     * @param ratio    The ratio
     * @return The video size
     */
    public static Size chooseVideoSize(Size[] choices, int minWidth, Size ratio) {
        return chooseVideoSize(choices, minWidth, ratio.getWidth(), ratio.getHeight());
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param ratioWidth  The ratio width
     * @param ratioHeight  The ratio height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int width, int height, int ratioWidth, int ratioHeight) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();

        for (Size option : choices) {
            if ((option.getHeight() * ratioWidth == option.getWidth() * ratioHeight)  &&
                    (option.getWidth() >= width && option.getHeight() >= height)) {
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

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param size        The minimum desired size
     * @param ratio       The ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, Size size, Size ratio) {
        return chooseOptimalSize(
                choices,
                size.getWidth(), size.getHeight(),
                ratio.getWidth(), ratio.getHeight());
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
}
