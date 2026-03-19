package com.example.opengldemo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private MyGLSurfaceView glSurfaceView;

    //App launches
    // → onCreate() runs
    //    → check ES 2.0 supported?
    //        → YES: create GL canvas, make it the whole screen
    //        → NO: do nothing (app just shows blank)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check the device supports OpenGL ES 2.0
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo =
                activityManager.getDeviceConfigurationInfo();

        if (configInfo.reqGlEsVersion >= 0x20000) {
            glSurfaceView = new MyGLSurfaceView(this);
            setContentView(glSurfaceView);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) glSurfaceView.onPause();
    }
}