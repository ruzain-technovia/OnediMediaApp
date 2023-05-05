package com.onedi.OneDiMedia;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 123;


    private boolean arePermissionsGranted() {
        int readPermission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        int writePermission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int coarseLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        return readPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                writePermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                }, REQUEST_PERMISSIONS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        File databaseFile = getDatabasePath(LocalDatabase.DATABASE_NAME);
        if (!databaseFile.exists()) {
            // Database file does not exist, create the database
            LocalDatabase dbHelper = new LocalDatabase(this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.close();
        }

        if (!arePermissionsGranted()) {
            Toast.makeText(this, "Please Allow Permissions", Toast.LENGTH_SHORT).show();
            requestPermissions();
        } else {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                Intent i = new Intent(getApplicationContext(), MediaScreenActivity.class);
                startActivity(i);
                finish();
            }, 1000);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // all permissions granted

                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    Intent i = new Intent(getApplicationContext(), MediaScreenActivity.class);
                    startActivity(i);
                    finish();
                }, 1000);
            }  // some permissions denied

        }
    }
}