package com.github.ma1co.pmcademo.app;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import com.sony.scalar.hardware.CameraEx;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private CameraEx mCameraEx;
    private Camera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraEx = CameraEx.open(0, null);
        mCamera = mCameraEx.getNormalCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.release();
            mCameraEx.release();
        }
    }

    public void surfaceCreated(SurfaceHolder h) {
        try { mCamera.setPreviewDisplay(h); mCamera.startPreview(); } catch (Exception e) {}
    }
    public void surfaceChanged(SurfaceHolder h, int f, int w, int h2) {}
    public void surfaceDestroyed(SurfaceHolder h) {}
}