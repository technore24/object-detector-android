package com.github.technore24.objectdetector.utils;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
//import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Vector;

public class ObjectDetector implements ObjectDetectorInterface {

    private static final float MINIMUM_CONFIDENCE = 0.6f;

    private final float IMAGE_MEAN = 0;

    private final float IMAGE_STD = 255.0f;

    private int INPUT_SIZE = -1;

    private  int output_box;

    private static final int NUM_THREADS = 1;

    private static final boolean isNNAPI = false;

    private boolean isModelQuantized;

    private NnApiDelegate nnapiDelegate = null;

    private MappedByteBuffer tfliteModel;

    private final Vector<String> labels = new Vector<>();
    private int[] intValues;

    private ByteBuffer imgData;
    private ByteBuffer outData;

    private Interpreter tfLite;
    private float inp_scale;
    private int inp_zero_point;
    private float oup_scale;
    private int oup_zero_point;
    private int numClass;

    protected float mNmsThresh = 0.6f;

    private ObjectDetector() {}

    public static ObjectDetector create(final AssetManager assetManager, final String modelFilename, final String[] labels, final boolean isQuantized, final int inputSize) throws IOException {

        final ObjectDetector d = new ObjectDetector();

        d.labels.addAll(Arrays.asList(labels));

        try {
            Interpreter.Options options = (new Interpreter.Options());
            options.setNumThreads(NUM_THREADS);
            if (isNNAPI) {
                d.nnapiDelegate = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    d.nnapiDelegate = new NnApiDelegate();
                    options.addDelegate(d.nnapiDelegate);
                    options.setNumThreads(NUM_THREADS);
                    options.setUseNNAPI(true);
                }
            }
            d.tfliteModel = loadModelFile(assetManager, modelFilename);
            d.tfLite = new Interpreter(d.tfliteModel, options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        d.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }
        d.INPUT_SIZE = inputSize;
        d.imgData = ByteBuffer.allocateDirect(1 * d.INPUT_SIZE * d.INPUT_SIZE * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.INPUT_SIZE * d.INPUT_SIZE];
        d.output_box = 25200;
        //d.output_box = (int) ((Math.pow((inputSize / 32), 2) + Math.pow((inputSize / 16), 2) + Math.pow((inputSize / 8), 2)) * 3);

        if (d.isModelQuantized){
            Tensor inpten = d.tfLite.getInputTensor(0);
            d.inp_scale = inpten.quantizationParams().getScale();
            d.inp_zero_point = inpten.quantizationParams().getZeroPoint();
            Tensor oupten = d.tfLite.getOutputTensor(0);
            d.oup_scale = oupten.quantizationParams().getScale();
            d.oup_zero_point = oupten.quantizationParams().getZeroPoint();
        }

        int[] shape = d.tfLite.getOutputTensor(0).shape();
        int numClass = shape[shape.length - 1] - 5;
        d.numClass = numClass;
        d.outData = ByteBuffer.allocateDirect(d.output_box * (numClass + 6) * numBytesPerChannel);
        d.outData.order(ByteOrder.nativeOrder());
        return d;
    }

    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public int getInputSize() {
        return INPUT_SIZE;
    }

    @Override
    public void close() {
        tfLite.close();
        tfLite = null;
        if (nnapiDelegate != null) {
            nnapiDelegate.close();
            nnapiDelegate = null;
        }
        tfliteModel = null;
    }

    @Override
    public float getObjThresh() {
        return MINIMUM_CONFIDENCE;
    }

    //non maximum suppression
    protected ArrayList<Recognition> nms(ArrayList<Recognition> list) {

        ArrayList<Recognition> nmsList = new ArrayList<Recognition>();

        for (int k = 0; k < labels.size(); k++) {

            //1.find max confidence per class
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            50,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition lhs, final Recognition rhs) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                                }
                            });

            for (int i = 0; i < list.size(); ++i) {
                if (list.get(i).getDetectedClass() == k) {
                    pq.add(list.get(i));
                }
            }

            //2.do non maximum suppression
            while (!pq.isEmpty()) {
                //insert detection with max confidence
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsList.add(max);
                pq.clear();

                for (int j = 1; j < detections.length; j++) {
                    Recognition detection = detections[j];
                    RectF b = detection.getLocation();
                    if (box_iou(max.getLocation(), b) < mNmsThresh) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsList;
    }

    protected float box_iou(RectF a, RectF b) {
        return box_intersection(a, b) / box_union(a, b);
    }

    protected float box_intersection(RectF a, RectF b) {
        float w = overlap((a.left + a.right) / 2, a.right - a.left,
                (b.left + b.right) / 2, b.right - b.left);
        float h = overlap((a.top + a.bottom) / 2, a.bottom - a.top,
                (b.top + b.bottom) / 2, b.bottom - b.top);
        if (w < 0 || h < 0) return 0;
        float area = w * h;
        return area;
    }

    protected float box_union(RectF a, RectF b) {
        float i = box_intersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
        return u;
    }

    protected float overlap(float x1, float w1, float x2, float w2) {
        float l1 = x1 - w1 / 2;
        float l2 = x2 - w2 / 2;
        float left = l1 > l2 ? l1 : l2;
        float r1 = x1 + w1 / 2;
        float r2 = x2 + w2 / 2;
        float right = r1 < r2 ? r1 : r2;
        return right - left;
    }

    protected void convertBitmapToByteBuffer(Bitmap bitmap) {

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                int pixelValue = intValues[i * INPUT_SIZE + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD / inp_scale + inp_zero_point));
                    imgData.put((byte) ((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD / inp_scale + inp_zero_point));
                    imgData.put((byte) (((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD / inp_scale + inp_zero_point));
                } else {
                    // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
    }

    public ArrayList<Recognition> recognizeImage(Bitmap bitmap) {

        convertBitmapToByteBuffer(bitmap);

        Map<Integer, Object> outputMap = new HashMap<>();

        outData.rewind();
        outputMap.put(0, outData);

        Object[] inputArray = {imgData};
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        ByteBuffer byteBuffer = (ByteBuffer) outputMap.get(0);
        byteBuffer.rewind();

        ArrayList<Recognition> detections = new ArrayList<Recognition>();

        float[][][] out = new float[1][output_box][numClass + 5];

        for (int i = 0; i < output_box; ++i) {
            for (int j = 0; j < numClass + 5; ++j) {
                if (isModelQuantized){
                    out[0][i][j] = oup_scale * (((int) byteBuffer.get() & 0xFF) - oup_zero_point);
                }
                else {
                    out[0][i][j] = byteBuffer.getFloat();
                }
            }
            for (int j = 0; j < 4; ++j) {
                out[0][i][j] *= getInputSize();
            }
        }
        for (int i = 0; i < output_box; ++i){

            final float confidence = out[0][i][4];
            int detectedClass = -1;
            float maxClass = 0;

            final float[] classes = new float[labels.size()];

            for (int c = 0; c < labels.size(); ++c) {
                classes[c] = out[0][i][5 + c];
            }

            for (int c = 0; c < labels.size(); ++c) {
                if (classes[c] > maxClass) {
                    detectedClass = c;
                    maxClass = classes[c];
                }
            }

            final float confidenceInClass = maxClass * confidence;

            if (confidenceInClass > getObjThresh()) {
                final float xPos = out[0][i][0];
                final float yPos = out[0][i][1];

                final float w = out[0][i][2];
                final float h = out[0][i][3];

                final RectF rect = new RectF(
                                Math.max(0, xPos - w / 2),
                                Math.max(0, yPos - h / 2),
                                Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                                Math.min(bitmap.getHeight() - 1, yPos + h / 2));

                detections.add(new Recognition(i, labels.get(detectedClass), confidenceInClass, rect, detectedClass));
            }
        }
        return nms(detections);
    }
}
