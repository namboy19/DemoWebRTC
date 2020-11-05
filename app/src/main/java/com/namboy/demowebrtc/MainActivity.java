package com.namboy.demowebrtc;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = "MainActivity";
    final int ALL_PERMISSIONS_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, ALL_PERMISSIONS_CODE);
        }

        findViewById(R.id.btn_makeCall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Fragment newFragment = VideoCallFragment.newInstance(true,((EditText)findViewById(R.id.edt_call_name)).getText().toString().replace(" ",""));
                getSupportFragmentManager().beginTransaction().add(R.id.container, newFragment).commit();
            }
        });

        findViewById(R.id.btn_receiveCall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Fragment newFragment = VideoCallFragment.newInstance(false,((EditText)findViewById(R.id.edt_call_name)).getText().toString().replace(" ",""));
                getSupportFragmentManager().beginTransaction().add(R.id.container, newFragment).commit();
            }
        });



    }





}