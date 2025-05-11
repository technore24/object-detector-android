package com.github.technore24.objectdetector.utils;

import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

public interface ObjectDetectorInterface {

    List<Recognition> recognizeImage(Bitmap bitmap);

    void close();

    float getObjThresh();

    class Recognition {

        private final int id;
        private final String title;
        private final Float confidence;
        private RectF location;
        private int detectedClass;

        public Recognition(final int id, final String title, final Float confidence, final RectF location, int detectedClass) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.detectedClass = detectedClass;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public int getDetectedClass() {
            return detectedClass;
        }

        @NonNull
        @Override
        public String toString() {
            String resultString = "[" + id + "] ";
            if (title != null) {
                resultString += title + " ";
            }
            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }
            if (location != null) {
                resultString += location + " ";
            }
            return resultString.trim();
        }
    }
}
