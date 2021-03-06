package com.example.imageclassifier;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;


public class Classifier_AGE {

    private static volatile Classifier_AGE INSTANCE;


    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "TfLiteCameraDemo";

    /**
     * Name of the model file stored in Assets.
     */

    private static final String MODEL_PATH = "PC_model_e13_l0.15_a95.tflite";

    /**
     * Name of the label file stored in Assets.
     */
    private static final String AGE_LABEL_PATH = "label-age.txt";


    /**
     * Number of results to show in the UI.
     */
    private static final int RESULTS_TO_SHOW = 1;

    /**
     * Dimensions of inputs.
     */
    private static final int DIM_BATCH_SIZE = 1;

    private static final int DIM_PIXEL_SIZE = 3;

    static final int DIM_IMG_SIZE_X = 224;
    static final int DIM_IMG_SIZE_Y = 224;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;


    /* Preallocated buffers for storing image data in. */
    private int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    private Interpreter tflite;

    /**
     * Labels corresponding to the output of the vision model.
     */
    private List<String> labelList;
    final ArrayList<String> outputlist = new ArrayList<String>();

    JSONObject jsonObject = new JSONObject();

    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    private ByteBuffer imgData = null;

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs.
     */
    private float[][] labelProbArray = null;
    /**
     * multi-stage low pass filter
     **/
    private float[][] filterLabelProbArray = null;
    private static final int FILTER_STAGES = 3;
    private static final float FILTER_FACTOR = 0.4f;

    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });


    /**
     * Initializes an {@code ImageClassifier}.
     */

    private Classifier_AGE(Context activity) throws IOException {
//        check if model is in storage
        File file = new File(activity.getExternalFilesDir(null), MODEL_PATH);
        if (file.exists()){
            tflite = new Interpreter(loadModelFileFromStorage(activity));
        }
        else{
            tflite = new Interpreter(loadModelFile(activity));
        }


        labelList = loadLabelList(activity);
        imgData =
                ByteBuffer.allocateDirect(
                        4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        labelProbArray = new float[1][labelList.size()];
        filterLabelProbArray = new float[FILTER_STAGES][labelList.size()];
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");


    }


    public static Classifier_AGE getInstance(Context context) {

        if (INSTANCE == null) {
            try {
                INSTANCE = new Classifier_AGE(context);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return INSTANCE;
    }

    /**
     * Classifies a frame from the preview stream.
     */


    /**
     * Classifies a frame from the preview stream.
     */
    public JSONObject classifyFrame(Bitmap bitmap) throws JSONException {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
//            return "Uninitialized Classifier.";
        }
        convertBitmapToByteBuffer(bitmap);
        // Here's where the magic happens!!!
        long startTime = SystemClock.uptimeMillis();
        tflite.run(imgData, labelProbArray);
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));

        String result;
        float max = largest(labelProbArray,labelProbArray.length);
        int index=findMaximumIndex(labelProbArray);
//        result= labelList.get(index);
        for (int i=0; i<labelProbArray[0].length;i++){

            float score= labelProbArray[0][i]*100;
//
            jsonObject.put(labelList.get(i),score);
//            String item=
//                    (String)labelList.get(i) + " :"+score;
//            outputlist.add(item);

        }
        Log.d(TAG,String.valueOf(outputlist.size()));
        return jsonObject;
//        return result;
    }
    static float largest(float [][]arr,
                       int n)
    {
        Arrays.sort(arr);
        return arr[0][n - 1];
    }
    public static int findMaximumIndex(float[ ][ ] a)
    {
        float maxVal = -99999;
        int[] answerArray = new int[2];
        int row=0;
        for(int col = 0; col < a.length; col++)
        {
                if(a[row][col] > maxVal)
                {
                    maxVal = a[row][col];
                    answerArray[0] = row;
                    answerArray[1] = col;
                }
            }

        return answerArray[1];
    }
    public static int findIndex(float[][] arr, float t)
    {
        int index=1;
        for (int vae=0;vae<arr.length;vae++){
            if (arr[0][vae]==t){
                index=vae;
            }
    }
        //int index = Arrays.binarySearch(arr, t);
        //return (index < 0) ? -1 : index;
        return index;
    }

    /**
     * Closes tflite to release resources.
     */
    public void close() {
        tflite.close();
        tflite = null;

        if (INSTANCE != null) {
            INSTANCE = null;
        }
    }

    /**
     * Reads label list from Assets.
     */
    private List<String> loadLabelList(Context activity) throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(activity.getAssets().open(AGE_LABEL_PATH )));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    /**
     * Memory-map the model file in Assets.
     */
    private MappedByteBuffer loadModelFile(Context activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private MappedByteBuffer loadModelFileFromStorage(Context activity) throws IOException {
        File appSpecificExternalDir = new File(activity.getExternalFilesDir(null), MODEL_PATH);
        InputStream in = new FileInputStream(appSpecificExternalDir);
        RandomAccessFile filenew=new RandomAccessFile (appSpecificExternalDir,"r");
        FileChannel fileChannel= filenew.getChannel();
        long startOffset = filenew.getFilePointer();
        long declaredLength = filenew.length();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        long startTime = SystemClock.uptimeMillis();
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                imgData.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to put values into ByteBuffer: " + Long.toString(endTime - startTime));
    }

}