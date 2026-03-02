package com.github.ma1co.pmcademo.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarInput;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private CameraEx mCameraEx;
    private Camera mCamera;
    private TextView tvShutter, tvAperture, tvISO, tvExposure, tvRecipe;
    
    private List<Integer> supportedIsos;
    private int curIso;
    
    enum DialMode { shutter, aperture, iso, recipe }
    private DialMode mDialMode = DialMode.shutter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        tvShutter = (TextView) findViewById(R.id.tvShutter);
        tvAperture = (TextView) findViewById(R.id.tvAperture);
        tvISO = (TextView) findViewById(R.id.tvISO);
        tvExposure = (TextView) findViewById(R.id.tvExposure);
        tvRecipe = (TextView) findViewById(R.id.tvRecipe);
        
        setDialMode(DialMode.shutter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            mCameraEx = CameraEx.open(0, null);
            mCamera = mCameraEx.getNormalCamera();
            mCameraEx.startDirectShutter();
            
            // Initialize ISO list for the wheel
            CameraEx.ParametersModifier pm = mCameraEx.createParametersModifier(mCamera.getParameters());
            supportedIsos = (List<Integer>) pm.getSupportedISOSensitivities();
            curIso = pm.getISOSensitivity();

            notifySonyStatus(true);
            syncUI();
        } catch (Exception e) {}
    }

    private void syncUI() {
        if (mCamera == null) return;
        try {
            Camera.Parameters p = mCamera.getParameters();
            CameraEx.ParametersModifier pm = mCameraEx.createParametersModifier(p);
            
            // Format Shutter (Issue 1 Fix)
            Pair<Integer, Integer> speed = pm.getShutterSpeed();
            if (speed.first >= speed.second) tvShutter.setText((speed.first / speed.second) + "\"");
            else tvShutter.setText(speed.first + "/" + speed.second);
            
            // Format Aperture (Issue 2 Fix)
            tvAperture.setText("f/" + (pm.getAperture() / 100.0f));
            
            // Format ISO (Issue 3 Fix)
            tvISO.setText(curIso == 0 ? "ISO AUTO" : "ISO " + curIso);
            
            // Format Exposure (Issue 4 Fix)
            tvExposure.setText(String.format("%.1f", p.getExposureCompensation() * p.getExposureCompensationStep()));
        } catch (Exception e) {}
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();

        if (scanCode == ScalarInput.ISV_KEY_DELETE) {
            notifySonyStatus(false);
            finish();
            return true;
        }

        // Cycle through bottom/top labels using the DOWN button
        if (scanCode == ScalarInput.ISV_KEY_DOWN) {
            if (mDialMode == DialMode.shutter) setDialMode(DialMode.aperture);
            else if (mDialMode == DialMode.aperture) setDialMode(DialMode.iso);
            else if (mDialMode == DialMode.iso) setDialMode(DialMode.recipe);
            else setDialMode(DialMode.shutter);
            return true;
        }

        // Adjust selected value using the wheel
        if (scanCode == ScalarInput.ISV_DIAL_1_CLOCKWISE) { handleInput(1); return true; }
        if (scanCode == ScalarInput.ISV_DIAL_1_COUNTERCW) { handleInput(-1); return true; }

        return super.onKeyDown(keyCode, event);
    }

    private void handleInput(int delta) {
        if (mCameraEx == null) return;
        try {
            if (mDialMode == DialMode.shutter) {
                if (delta > 0) mCameraEx.incrementShutterSpeed(); else mCameraEx.decrementShutterSpeed();
            } else if (mDialMode == DialMode.aperture) {
                if (delta > 0) mCameraEx.incrementAperture(); else mCameraEx.decrementAperture();
            } else if (mDialMode == DialMode.iso) {
                int idx = supportedIsos.indexOf(curIso);
                int next = Math.max(0, Math.min(supportedIsos.size() - 1, idx + delta));
                curIso = supportedIsos.get(next);
                Camera.Parameters p = mCamera.getParameters();
                mCameraEx.createParametersModifier(p).setISOSensitivity(curIso);
                mCamera.setParameters(p);
            }
            syncUI();
        } catch (Exception e) {}
    }

    private void setDialMode(DialMode mode) {
        mDialMode = mode;
        tvShutter.setTextColor(mode == DialMode.shutter ? Color.GREEN : Color.WHITE);
        tvAperture.setTextColor(mode == DialMode.aperture ? Color.GREEN : Color.WHITE);
        tvISO.setTextColor(mode == DialMode.iso ? Color.GREEN : Color.WHITE);
        tvRecipe.setTextColor(mode == DialMode.recipe ? Color.GREEN : Color.WHITE);
    }

    private void notifySonyStatus(boolean active) {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getPackageName());
        intent.putExtra("resume_key", active ? new String[]{"on"} : new String[]{});
        sendBroadcast(intent);
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        try { if (mCamera != null) { mCamera.setPreviewDisplay(h); mCamera.startPreview(); } } catch (Exception e) {}
    }

    @Override protected void onPause() { super.onPause(); if (mCameraEx != null) { mCameraEx.release(); mCameraEx = null; } }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int h1) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) {}
}