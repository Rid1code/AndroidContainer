package com.example.imageclassifier;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;


public class ClassifierActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_WRITE_PERMISSION = 786;

    public final int PICK_IMAGE = 1;
    ImageView imageView = null;
    ListView isImage = null;
    TextView pbText = null;
    Button nextImage = null;
    ProgressBar progressBar = null;
    Bitmap pickedBitmap = null;
    JSONObject jsonObject = new JSONObject();


    private static final String TAG = "TestActivity";
    private Classifier newClassifier;
    String whichPlaform = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classifier);
        initialiseViews();
        Intent intent = getIntent();
        initialiseImageClassifier(this);
        setTitle("Checked Image Locally");
        requestPermission();

    }


    private void initialiseViews() {

        imageView = (ImageView) findViewById(R.id.preview);
        isImage = (ListView) findViewById(R.id.textimage);
        pbText = (TextView) findViewById(R.id.pbText);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        isImage.setVisibility(View.GONE);
        pbText.setVisibility(View.GONE);
        nextImage = (Button) findViewById(R.id.next_image);
        nextImage.setOnClickListener(this);


    }


    private void initialiseImageClassifier(Context app) {
        String mode = getIntent().getStringExtra("mode");
        Log.d(TAG,mode);
        try {
            newClassifier = Classifier.getInstance(app,mode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize an image classifier.");
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_WRITE_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openFilePicker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        progressBar.setVisibility(View.VISIBLE);
//        pbText.setVisibility(View.VISIBLE);

            try {
                Uri uri = data.getData();
                pickedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                new BackgroundProcessLocal().execute(pickedBitmap);

            } catch (IOException e) {
                e.printStackTrace();
            }
            catch (NullPointerException e){
                e.printStackTrace();

            }

    }

    private void openFilePicker() {

        Intent pickPhoto = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhoto, PICK_IMAGE);//one can be replaced with any action code


    }


    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
        } else {
            openFilePicker();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next_image:
                openFilePicker();
                break;

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (newClassifier != null) {
            newClassifier.close();
        }


    }

    private class BackgroundProcessLocal extends AsyncTask<Bitmap, Void, ArrayList> {

        String class_label = "";
        Bitmap bitmap = null;
        ArrayList<String> list = new ArrayList<String>();


        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            progressBar.setVisibility(View.VISIBLE);
            pbText.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            isImage.setVisibility(View.GONE);
        }

        @Override
        protected ArrayList doInBackground(Bitmap... bitmaps) {

            bitmap = bitmaps[0];
            Bitmap reshapeBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);
            try {
                jsonObject = newClassifier.classifyFrame(reshapeBitmap);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            for(int i=0;i<jsonObject.names().length();i++ ){
                String item=null;
                try {
                    item = jsonObject.names().getString(i) + " : "+jsonObject.get(jsonObject.names().getString(i));
                    list.add(item);
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }

                Log.d("size",String.valueOf(list.size()));
            return list;
        }

        @Override
        protected void onPostExecute(ArrayList s) {
            super.onPostExecute(s);
            ArrayAdapter adapter = new ArrayAdapter<String>(ClassifierActivity.this,
                    android.R.layout.simple_list_item_1,android.R.id.text1, s);
            progressBar.setVisibility(View.GONE);
            pbText.setVisibility(View.GONE);
            isImage.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);

            imageView.setImageBitmap(bitmap);
            isImage.setAdapter(adapter);
//            UIUtils.setListViewHeightBasedOnChildren(isImage);
//            isImage.setText(s);
        }
    }

    private File persistImage(Bitmap bitmap, String name) {

        String extStorageDirectory = Environment.getExternalStorageDirectory().toString();

        File imageFile = new File(extStorageDirectory, name + ".jpg");

        OutputStream os;
        try {
            os = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();
        } catch (Exception e) {
            Log.e(TAG, "Error writing bitmap", e);
        }

        return imageFile;
    }


}