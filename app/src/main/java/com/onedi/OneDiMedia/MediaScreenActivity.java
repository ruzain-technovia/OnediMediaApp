package com.onedi.OneDiMedia;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Html;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.airbnb.lottie.LottieAnimationView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import anylife.scrolltextview.ScrollTextView;

public class MediaScreenActivity extends AppCompatActivity {

    private RequestQueue mRequestQueue;
    private StringRequest mStringRequest;

    TextView dateAndTempTextView, loaderText;
    ScrollTextView tv;
    String dateAndTempString, macAddress, mediaPath;
    String[] mediaNames, subMediaNames;
    Integer[] mediaIds, subMediaIds;
    LottieAnimationView lottie;
    int currentTemperature;
    private Handler mHandler;
    private Runnable mRunnable;
    private ImageView mImageView;
    private VideoView mVideoView;
    private List<String> mMediaFiles;
    File mediaFolder;
    private int mCurrentMediaIndex, screenVolume;

    private long mediaUpdateInterval, newsUpdateInterval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_screen);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        lottie = this.findViewById(R.id.loadingAnimation);
        loaderText = this.findViewById(R.id.loadingText);

        macAddress = getMacAddress();
        Log.d("TAG", "mac address = " + macAddress);

        insertDeviceOnOffDataDB(1);

        getParameterValues();
        changeDeviceVolume();

        mHandler = new Handler();
        mImageView = findViewById(R.id.mediaImage);
        mVideoView = findViewById(R.id.mediaVideo);

        mMediaFiles = getMediaFiles();

        mCurrentMediaIndex = 0;

        mRunnable = this::showNextMedia;
        getCurrentDate();
        showNextMedia();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                getNewsHeadline();
            }
        }, 0, newsUpdateInterval * 1000);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                getMedias();
                postMediaPlayData();
            }
        }, 0, mediaUpdateInterval * 1000);

        AppUpdater appUpdater = new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("ruzain-technovia", "OnediMediaApp")
                .setTitleOnUpdateAvailable("Update available")
                .setContentOnUpdateAvailable("A new version of the app is available. Do you want to update?");

        appUpdater.start();

    }

    @Override
    protected void onPause() {
        super.onPause();
        insertDeviceOnOffDataDB(2);

    }

    private void getParameterValues() {
        LocalDatabase dbHelper = new LocalDatabase(this);
        ArrayList<String> parameterValues = (ArrayList<String>) dbHelper.getAllGeneralParameter();
        Log.d("TAG", "parameter Values = " + parameterValues);

        mediaUpdateInterval = Long.parseLong(parameterValues.get(0));
        newsUpdateInterval = Long.parseLong(parameterValues.get(1));
        screenVolume = Integer.parseInt(parameterValues.get(2));

    }

    // Change Device Audio
    private void changeDeviceVolume() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, screenVolume, 0);
    }

    // Insert Device On Off Time To Local DB
    private void insertDeviceOnOffDataDB(Integer On_or_Off) {
        try (LocalDatabase localDatabase = new LocalDatabase(getApplicationContext())) {
            try (SQLiteDatabase db = localDatabase.getWritableDatabase()) {

                ContentValues onOffValues = new ContentValues();
                onOffValues.put("on_or_off", On_or_Off);

                Calendar calendar = Calendar.getInstance();
                String dateTime = DateFormat.format("yyyy-MM-dd HH:mm:ss", calendar).toString();
                onOffValues.put("created_on", dateTime);

                long rowId = db.insert("device_on_off_time", null, onOffValues);
                db.close();
                // Check if the insert was successful
                if (rowId != -1) {
                    Log.d("TAG", "On Off Row ID: " + rowId);
                    // You can use the rowId for further operations, such as updating or deleting the inserted row
                } else {
                    Log.e("TAG", "Failed to insert row");
                }

            } catch (Exception e) {
                // Handle exceptions as needed
                e.printStackTrace();
            }
        } catch (SQLException ex) {
            // Handle any SQL exceptions
        }
    }

    // Send Media Download History To Database Through API
    private void postMediaDownloadData() {
        RequestQueue queue = Volley.newRequestQueue(this);

        String url = "http://64.227.164.118:2244/mediadownloaddata";
//        String url = "http://192.168.29.14:2244/mediadownloaddata";

        LocalDatabase localDatabase = new LocalDatabase(getApplicationContext());
        SQLiteDatabase db = localDatabase.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM media_download_history WHERE status = 1", null);

        JSONArray postData = new JSONArray();

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex("id"));
                @SuppressLint("Range") String media_id = cursor.getString(cursor.getColumnIndex("media_id"));
                @SuppressLint("Range") String created_on = cursor.getString(cursor.getColumnIndex("created_on"));

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("id", id);
                    jsonObject.put("media_id", media_id);
                    jsonObject.put("mac_address", macAddress);
                    jsonObject.put("created_on", created_on);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                postData.put(jsonObject);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.POST, url, postData,
                response -> {
                    // Handle the response from the API
                    Log.d("TAG", "POST Response = " + response);
                    SQLiteDatabase database = localDatabase.getWritableDatabase();
                    for (int i = 0; i < response.length(); i++) {
                        int id;
                        try {
                            id = response.getInt(i);

                            // Create a ContentValues object to hold the new column value
                            ContentValues values = new ContentValues();
                            values.put("status", "2");

                            // Update the column for the current row using its id
                            database.update("media_download_history", values, "id=?", new String[]{String.valueOf(id)});
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    database.close();
                },
                error -> {
                    // Handle the error from the API
                    Log.d("TAG", "POST Error = " + error);

                });

        queue.add(request);

    }


    // Send Media Play History To Database Through API
    private void postMediaPlayData() {
        RequestQueue queue = Volley.newRequestQueue(this);

//        String url = "http://192.168.29.14:2244/mediaplaydata";
        String url = "http://64.227.164.118:2244/mediaplaydata";

        LocalDatabase localDatabase = new LocalDatabase(getApplicationContext());
        SQLiteDatabase db = localDatabase.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM media_play_history", null);

        JSONArray postData = new JSONArray();

        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex("id"));
                @SuppressLint("Range") String media_id = cursor.getString(cursor.getColumnIndex("media_id"));
                @SuppressLint("Range") String created_on = cursor.getString(cursor.getColumnIndex("created_on"));

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("id", id);
                    jsonObject.put("media_id", media_id);
                    jsonObject.put("mac_address", macAddress);
                    jsonObject.put("created_on", created_on);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                postData.put(jsonObject);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.POST, url, postData,
                response -> {
                    // Handle the response from the API
                    Log.d("TAG", "POST Response = " + response);
                    SQLiteDatabase database = localDatabase.getWritableDatabase();
                    for (int i = 0; i < response.length(); i++) {
                        int id;
                        try {
                            id = response.getInt(i);

                            database.execSQL("DELETE FROM " + "media_play_history" + " WHERE id=?", new String[]{String.valueOf(id)});

                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    database.close();
                },
                error -> {
                    // Handle the error from the API
                    Log.d("TAG", "POST Error = " + error);
                    db.close();
                });

        queue.add(request);

    }


    // View Medias
    public List<String> getMediaFiles() {
        String folderPath = Environment.getExternalStorageDirectory().getPath() + "/OneDiMedia/medias";
        LocalDatabase dbHelper = new LocalDatabase(this);
        ArrayList<String> fileNames = (ArrayList<String>) dbHelper.getAllMedia();

        List<String> mediaFiles = new ArrayList<>();
        // Iterate through the list of file names and check if they exist in the folder
        for (String fileName : fileNames) {
            File file = new File(folderPath, fileName);
            if (file.exists()) {
                mediaFiles.add(String.valueOf(file));
            }
        }

        return mediaFiles;
    }


    // Insert Downloaded Media Data to Local DB
    private void insertMediaDataDB() {
        if (mediaPath != null) {
            File file = new File(mediaPath);
            String mediaName = file.getName();
            Log.d("TAG", "Media Name = " + mediaName);

            try (LocalDatabase localDatabase = new LocalDatabase(getApplicationContext())) {
                try (SQLiteDatabase db = localDatabase.getWritableDatabase()) {
                    // Do something if the table is empty
                    LocalDatabase dbHelper = new LocalDatabase(this);
                    int media_id = dbHelper.getIdFromValue("media_store", "media_name", mediaName);
                    Log.d("TAG", "Media ID: " + media_id);

                    ContentValues MediaValues = new ContentValues();
                    MediaValues.put("media_id", media_id);
                    MediaValues.put("media_name", mediaName);

                    Calendar calendar = Calendar.getInstance();
                    String dateTime = DateFormat.format("yyyy-MM-dd HH:mm:ss", calendar).toString();
                    MediaValues.put("created_on", dateTime);

                    long rowId = db.insert("media_play_history", null, MediaValues);
                    db.close();
                    // Check if the insert was successful
                    if (rowId != -1) {
                        Log.d("TAG", "Row ID: " + rowId);
                        // You can use the rowId for further operations, such as updating or deleting the inserted row
                    } else {
                        Log.e("TAG", "Failed to insert row");
                    }

                } catch (Exception e) {
                    // Handle exceptions as needed
                    e.printStackTrace();
                }
            } catch (SQLException ex) {
                // Handle any SQL exceptions
            }
        }
    }


    // Show Media on Screen in Order
    private void showNextMedia() {
        if (!mMediaFiles.isEmpty()) {
            lottie.setVisibility(View.GONE);
            loaderText.setVisibility(View.GONE);
            mediaPath = mMediaFiles.get(mCurrentMediaIndex);

            if (mediaPath.endsWith(".jpg") || mediaPath.endsWith(".jpeg") ||
                    mediaPath.endsWith(".png") || mediaPath.endsWith(".gif") ||
                    mediaPath.endsWith(".bmp") || mediaPath.endsWith(".webp")) {
                mImageView.setVisibility(View.VISIBLE);
                mVideoView.setVisibility(View.GONE);
                Animation fade_in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
                mImageView.setAnimation(fade_in);
                Glide.with(this).load(mediaPath).into(mImageView);
                insertMediaDataDB();

                mHandler.postDelayed(mRunnable, 10000L);
            } else if (mediaPath.endsWith(".mp4") || mediaPath.endsWith(".3gp") ||
                    mediaPath.endsWith(".avi") || mediaPath.endsWith(".mkv") ||
                    mediaPath.endsWith(".wmv") || mediaPath.endsWith(".flv")) {
                mImageView.setVisibility(View.GONE);
                mVideoView.setVisibility(View.VISIBLE);
                Animation fade_in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
                mVideoView.setAnimation(fade_in);

                mVideoView.setVideoPath(mediaPath);
                mVideoView.start();
                insertMediaDataDB();

                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(mediaPath);
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                long durationMs = Long.parseLong(durationStr);

                mHandler.postDelayed(mRunnable, durationMs);
            }

            mCurrentMediaIndex = (mCurrentMediaIndex + 1) % mMediaFiles.size();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mRunnable);
    }


    // View Current Date & Temperature
    private void getCurrentDate() {
        Calendar calendar;
        SimpleDateFormat simpledateformat;
        String Date;
        dateAndTempTextView = findViewById(R.id.date_and_temp_textview1);
        calendar = Calendar.getInstance();
        simpledateformat = new SimpleDateFormat("dd MMMM", Locale.getDefault());
        Date = simpledateformat.format(calendar.getTime());
        currentTemperature = 32;
        dateAndTempString = Date + "  |  " + currentTemperature + "°C";
        dateAndTempTextView.setText(dateAndTempString);
    }


    // Get TV Mac Address
    private String getMacAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    // res1.append(Integer.toHexString(b & 0xFF) + ":");
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
            //handle exception
        }
        return "";
    }


    // Get News Headline
    public void getNewsHeadline() {
        tv = this.findViewById(R.id.NewsHeadline);
        tv.setSpeed(4);
        LocalDatabase dbHelper = new LocalDatabase(this);
        boolean isTableEmpty = dbHelper.isTableEmpty("news_headlines");

        Cursor cursor = dbHelper.getRowById(1);
        if (cursor.moveToFirst() && cursor.getCount() > 0) {
            @SuppressLint("Range") String news = cursor.getString(cursor.getColumnIndex("news"));
            Log.d("TAG", "news: " + news);
            tv.setText(news);
            dbHelper.close();
        }
        // RequestQueue initialized
        mRequestQueue = Volley.newRequestQueue(this);

        // String Request initialized
//        String newsHeadlineUrl = "http://signage.onedimedia.com/projects/screenmedia/signageNewsDetails.php?mac=" + macAddress + "&cardno=1020";
//        String newsHeadlineUrl = "http://192.168.29.14:2244/newsdata";
        String newsHeadlineUrl = "http://64.227.164.118:2244/newsdata";

        mStringRequest = new StringRequest(Request.Method.GET, newsHeadlineUrl, response -> {

            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String newsHeadline = null;
            try {
                assert jsonObject != null;
                newsHeadline = jsonObject.getString("data");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Log.d("TAG", "News Headline: " + newsHeadline);
//            String searchString = "<img src='http://screenmediapi.technovialabs.com/star.png' id='imgid' />";
//            String replacementString = getString(R.string.star);
//            assert newsHeadline != null;
//            String updatedString = newsHeadline.replace(searchString, replacementString);
//            String[] separated = updatedString.split(replacementString);
//            String[] reversedArray = new String[separated.length];

//            for (int i = 0; i < separated.length; i++) {
//                reversedArray[i] = separated[separated.length - i - 1];
//            }
//            StringBuilder result = new StringBuilder();
//            for (int i = 0; i < reversedArray.length - 1; i++) {
//                result.append(replacementString);
//                result.append(reversedArray[i]);
//            }

            String NewsHeadlineText = String.valueOf(Html.fromHtml(newsHeadline));
            tv.setText(NewsHeadlineText);

            // Create a new instance of DatabaseHelper
            try (LocalDatabase localDatabase = new LocalDatabase(getApplicationContext())) {
                try (SQLiteDatabase db = localDatabase.getWritableDatabase()) {
                    if (isTableEmpty) {
                        ContentValues HeadlineValue = new ContentValues();
                        HeadlineValue.put("news", NewsHeadlineText);
                        long rowId = db.insert("news_headlines", null, HeadlineValue);
                        db.close();
                        if (rowId != -1) {
                            Log.d("TAG", "Row ID: " + rowId);
                        } else {
                            Log.e("TAG", "Failed to insert row");
                        }
                    } else {
                        dbHelper.updateNewsById(1, NewsHeadlineText);
                    }

                } catch (Exception e) {
                    // Handle exceptions as needed
                    e.printStackTrace();
                }
            } catch (SQLException ex) {
                // Handle any SQL exceptions
            }
            dbHelper.close();
            // Close the database
            Log.d("TAG", "News Headlines:" + response);
        }, error -> {
            if (error instanceof TimeoutError) {
                Log.d("TAG", "No Internet");
                // handle timeout error
            } else {
                // handle other errors
                Log.d("TAG", "error" + error);
            }
        });

        mRequestQueue.add(mStringRequest);

    }


    // Get Medias
    private void getMedias() {
        mRequestQueue = Volley.newRequestQueue(this);

//        String mediaUrl = "http://64.227.164.118:19294/getMediaShowPlaylist/88:28:7D:0D:18:1B";
        String mediaUrl = "http://64.227.164.118:19294/getMediaShowPlaylist/" + macAddress;

//        String mediaUrl = "http://46.101.231.11:1030/getMediafiles/88:28:7D:0D:18:1B";
//        String mediaUrl = "http://46.101.231.11:1030/getMediafiles/" + macAddress;
        mStringRequest = new StringRequest(Request.Method.GET, mediaUrl, response -> {
            Log.d("TAG", "Json Response:" + response);
            JSONObject mediaJsonObject = null;
            try {
                mediaJsonObject = new JSONObject(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String masterPlaylist;
            String subPlaylist;

            try {
                // Get Master PlayList
//                assert mediaJsonObject != null;
//                masterPlaylist = mediaJsonObject.getString("masterplaylist");
//                JSONArray mediaList = new JSONArray(masterPlaylist);
//
//                int numRows = mediaList.length();
//                mediaNames = new String[numRows];
//                mediaIds = new Integer[numRows];
//                for (int i = 0; i < numRows; i++) {
//                    JSONArray row = mediaList.getJSONArray(i);
//                    mediaNames[i] = row.getString(1);
//                    mediaIds[i] = row.getInt(0);
//                }
//                Log.d("TAG", "MasterPlaylist:" + "id = " + Arrays.toString(mediaIds) + "media_name = " + Arrays.toString(mediaNames));
//
//
//                // Get Sub PlayList
//                subPlaylist = mediaJsonObject.getString("active");
//
//                JSONArray subMediaList = new JSONArray(subPlaylist);
//
//                int subNumRows = subMediaList.length();
//                subMediaNames = new String[subNumRows];
//                subMediaIds = new Integer[numRows];
//
//                for (int i = 0; i < subNumRows; i++) {
//                    JSONArray row = subMediaList.getJSONArray(i);
//                    subMediaNames[i] = row.getString(1);
//                    subMediaIds[i] = row.getInt(0);
//                }
//                Log.d("TAG", "SubPlaylist:" + "id = " + Arrays.toString(subMediaIds) + "media_name = " + Arrays.toString(subMediaNames));
                assert mediaJsonObject != null;
                String message = mediaJsonObject.getString("message");

                if (message.equals("Success")) {
                    masterPlaylist = mediaJsonObject.getString("mediasdata");
                    mediaUpdateInterval = Long.parseLong(mediaJsonObject.getString("media_update_interval"));
                    newsUpdateInterval = Long.parseLong(mediaJsonObject.getString("news_update_interval"));
                    screenVolume = Integer.parseInt(mediaJsonObject.getString("screen_volume_value"));
                    Log.d("TAG", "media_update_interval = " + mediaUpdateInterval + " news_update_interval = " + newsUpdateInterval + " screen_volume_value = " + screenVolume);

                    LocalDatabase localDatabase = new LocalDatabase(getApplicationContext());
                    SQLiteDatabase database = localDatabase.getWritableDatabase();

                    ContentValues values = new ContentValues();
                    if (mediaUpdateInterval != 0) {
                        values.put("value", mediaUpdateInterval);
                        database.update("general_parameter", values, "id=?", new String[]{String.valueOf(1)});
                    }
                    if (newsUpdateInterval != 0) {
                        values.put("value", newsUpdateInterval);
                        database.update("general_parameter", values, "id=?", new String[]{String.valueOf(2)});
                    }

                    if (screenVolume != 0) {
                        values.put("value", screenVolume);
                        database.update("general_parameter", values, "id=?", new String[]{String.valueOf(3)});

                        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, screenVolume, 0);
                    }

                    database.close();

                    JSONArray mediaList = new JSONArray(masterPlaylist);
                    if (!Objects.equals(mediaList.getJSONArray(0).getString(1), "null")) {
                        int numRows = mediaList.length();
                        mediaNames = new String[numRows];
                        mediaIds = new Integer[numRows];
                        for (int i = 0; i < numRows; i++) {
                            JSONArray row = mediaList.getJSONArray(i);
                            mediaNames[i] = row.getString(1);
                            mediaIds[i] = row.getInt(0);
                        }
                        Log.d("TAG", "mediasData:" + " id = " + Arrays.toString(mediaIds) + " media_name = " + Arrays.toString(mediaNames));

                        MediaScreenActivity.DownloadMediaTask task = new MediaScreenActivity.DownloadMediaTask(getApplicationContext(), "medias");
                        ArrayList<String> imageNames = new ArrayList<>();
                        Collections.addAll(imageNames, mediaNames);
//                Collections.addAll(imageNames, subMediaNames);
                        Log.d("TAG", "AllPLayList:" + imageNames);

                        ArrayList<Integer> mediaIdsArray = new ArrayList<>();
                        Collections.addAll(mediaIdsArray, mediaIds);
//                Collections.addAll(mediaIdsArray, subMediaIds);
                        Log.d("TAG", "All Media IDs:" + mediaIdsArray);

                        LocalDatabase dbHelper = new LocalDatabase(getApplicationContext());
                        dbHelper.clearAllMedia();

                        Calendar calendar = Calendar.getInstance();
                        String dateTime = DateFormat.format("yyyy-MM-dd HH:mm:ss", calendar).toString();

                        for (int i = 0; i < imageNames.size(); i++) {
                            dbHelper.addMedia(imageNames.get(i), mediaIdsArray.get(i), dateTime);
                            boolean isRowExists = dbHelper.isRowExists("media_download_history", mediaIdsArray.get(i));

                            if (isRowExists) {
                                dbHelper.addDownloadedMediaDB(imageNames.get(i), mediaIdsArray.get(i), dateTime);
                            }
                        }
                        dbHelper.close();
                        String folderPath = Environment.getExternalStorageDirectory().getPath() + "/OneDiMedia/medias";
                        mediaFolder = new File(folderPath);
                        File[] fileList = mediaFolder.listFiles();
                        Log.d("TAG", "File List = " + Arrays.toString(fileList));

                        if (fileList != null) {
                            for (File file : fileList) {
                                String fileName = file.getName();
                                boolean shouldDelete = true;
                                for (String fileToKeep : imageNames) {
                                    if (fileToKeep.equals(fileName)) {
                                        shouldDelete = false;
                                        break;
                                    }
                                }
                                if (shouldDelete) {
                                    if (file.delete()) {
                                        Log.d("TAG", "File deleted successfully.");
                                    } else {
                                        Log.d("TAG", "Failed to delete file.");
                                    }
                                }
                            }
                        }
                        List<String> AllMedias = dbHelper.getAllMedia();
                        task.execute((ArrayList<String>) AllMedias);
                    }
                } else {
                    Log.d("Error", "Internal Server Error");
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, error -> Log.i("TAG", "Error :" + error.toString()));

        mRequestQueue.add(mStringRequest);
    }


    // Download and Store Medias
    @SuppressLint("StaticFieldLeak")
    public class DownloadMediaTask extends AsyncTask<ArrayList<String>, Void, Void> {

        private final String folderName;

        public DownloadMediaTask(Context context, String folderName) {
            super();
            this.folderName = folderName;
        }

        @SafeVarargs
        @Override
        protected final Void doInBackground(ArrayList<String>... params) {
            if (!mediaFolder.exists()) {
                lottie = findViewById(R.id.loadingAnimation);
                loaderText = findViewById(R.id.loadingText);
                lottie.setVisibility(View.VISIBLE);
                loaderText.setVisibility(View.VISIBLE);
            }
            ArrayList<String> imageNames = params[0];

            try {
                File folder = new File(Environment.getExternalStorageDirectory().getPath() + "/OneDiMedia/", folderName);
                if (!folder.exists()) {
                    if (folder.mkdirs()) {
                        Log.d("TAG", "Directory created successfully");
                    } else {
                        Log.d("TAG", "Directory Exists");
                    }
                }
                for (String imageName : imageNames) {
                    File mediaFileName = new File(folder, imageName);
                    if (mediaFileName.exists()) {
                        Log.d("TAG", "File already exists: " + mediaFileName.getAbsolutePath());
                        continue; // skip the download if the file already exists
                    }
//                    URL url = new URL("http://46.101.231.11/projects/mediasignage_upload/" + imageName);
                    URL url = new URL("http://user.onedimedia.com/usermediaOnedi/imgup/" + imageName);
                    URLConnection connection = url.openConnection();
                    connection.connect();

                    // Get the input stream and set the buffer size
                    BufferedInputStream inputStream = new BufferedInputStream(url.openStream(), 8192);

                    // Create a new file output stream for saving the media
                    File mediaFile = new File(folder, imageName);
                    FileOutputStream outputStream = new FileOutputStream(mediaFile);

                    // Read from the input stream and write to the output stream
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    // Close the streams
                    outputStream.flush();
                    outputStream.close();
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e("DownloadMediaTask", "Error downloading media", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            lottie.setVisibility(View.GONE);
            loaderText.setVisibility(View.GONE);
            Log.d("DownloadMediaTask", "Media downloaded successfully");
            postMediaDownloadData();

            if (mMediaFiles.isEmpty()) {
                mMediaFiles = getMediaFiles();
                onDestroy();
                showNextMedia();
            } else {
                mMediaFiles = getMediaFiles();
            }
        }

    }


}

