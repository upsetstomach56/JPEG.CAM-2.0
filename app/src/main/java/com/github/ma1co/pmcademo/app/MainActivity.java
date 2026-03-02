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

public class MainActivity extends Activity implements SurfaceHolder.Callback, CameraEx.ShutterListener, CameraEx.ShutterSpeedChangeListener {
    private CameraEx mCameraEx;
    private SurfaceHolder mSurfaceHolder;
    private TextView tvShutter, tvAperture, tvISO, tvExposure;
    private int curIso;
    private List<Integer> supportedIsos;
    
    enum DialMode { shutter, aperture, iso }
    private DialMode mDialMode = DialMode.shutter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); 
        
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView); 
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        // SONY HANDSHAKE: Forces the a5100 to feed the sensor to the screen
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        tvShutter = (TextView) findViewById(R.id.tvShutter); 
        tvAperture = (TextView) findViewById(R.id.tvAperture); 
        tvISO = (TextView) findViewById(R.id.tvISO); 
        tvExposure = (TextView) findViewById(R.id.tvExposure);
        
        setDialMode(DialMode.shutter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraEx = CameraEx.open(0, null);
        mCameraEx.setShutterListener(this);
        mCameraEx.setShutterSpeedChangeListener(this);
        mCameraEx.startDirectShutter();
        
        // Sync initial camera settings
        CameraEx.ParametersModifier pm = mCameraEx.createParametersModifier(mCameraEx.getNormalCamera().getParameters());
        supportedIsos = (List<Integer>) pm.getSupportedISOSensitivities();
        curIso = pm.getISOSensitivity();
        
        // Fix the "Exit on Play" bug
        notifySonyStatus(true);
        updateUI();
    }

    private void notifySonyStatus(boolean active) {
        Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive");
        intent.putExtra("package_name", getPackageName());
        intent.putExtra("class_name", getClass().getName());
        intent.putExtra("resume_key", active ? new String[]{"on"} : new String[]{});
        sendBroadcast(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();

        // TRASH BUTTON: Clean Exit
        if (scanCode == ScalarInput.ISV_KEY_DELETE) { 
            notifySonyStatus(false);
            finish(); 
            return true; 
        }
        
        // DOWN BUTTON: Cycle through Shutter -> Aperture -> ISO (Selected turns Green)
        if (scanCode == ScalarInput.ISV_KEY_DOWN) {
            if (mDialMode == DialMode.shutter) setDialMode(DialMode.aperture);
            else if (mDialMode == DialMode.aperture) setDialMode(DialMode.iso);
            else setDialMode(DialMode.shutter);
            return true;
        }

        // CONTROL WHEEL: Adjust the selected value
        if (scanCode == ScalarInput.ISV_DIAL_1_CLOCKWISE) { handleDialChange(1); return true; }
        if (scanCode == ScalarInput.ISV_DIAL_1_COUNTERCW) { handleDialChange(-1); return true; }

        return super.onKeyDown(keyCode, event);
    }

    private void handleDialChange(int direction) {
        if (mDialMode == DialMode.shutter) {
            if (direction > 0) mCameraEx.incrementShutterSpeed(); else mCameraEx.decrementShutterSpeed();
        } else if (mDialMode == DialMode.aperture) {
            if (direction > 0) mCameraEx.incrementAperture(); else mCameraEx.decrementAperture();
        } else if (mDialMode == DialMode.iso) {
            // ISO cycle logic from BetterManual
            int idx = supportedIsos.indexOf(curIso);
            int nextIdx = Math.max(0, Math.min(supportedIsos.size() - 1, idx + direction));
            curIso = supportedIsos.get(nextIdx);
            Camera.Parameters p = mCameraEx.getNormalCamera().getParameters();
            mCameraEx.createParametersModifier(p).setISOSensitivity(curIso);
            mCameraEx.getNormalCamera().setParameters(p);
            tvISO.setText("ISO " + curIso);
        }
    }

    private void setDialMode(DialMode mode) {
        mDialMode = mode;
        tvShutter.setTextColor(mode == DialMode.shutter ? Color.GREEN : Color.WHITE);
        tvAperture.setTextColor(mode == DialMode.aperture ? Color.GREEN : Color.WHITE);
        tvISO.setTextColor(mode == DialMode.iso ? Color.GREEN : Color.WHITE);
    }

    private void updateUI() {
        CameraEx.ParametersModifier pm = mCameraEx.createParametersModifier(mCameraEx.getNormalCamera().getParameters());
        Pair<Integer, Integer> speed = pm.getShutterSpeed();
        tvShutter.setText(speed.first + "/" + speed.second);
        tvAperture.setText("f/" + (pm.getAperture() / 100.0f));
        tvISO.setText("ISO " + curIso);
    }

    @Override
    public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo info, CameraEx camera) {
        tvShutter.setText(info.currentShutterSpeed_n + "/" + info.currentShutterSpeed_d);
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        try { 
            Camera cam = mCameraEx.getNormalCamera();
            cam.setPreviewDisplay(h); 
            cam.startPreview(); 
        } catch (Exception e) {}
    }
    
    @Override public void onShutter(int i, CameraEx c) {}
    @Override protected void onPause() { super.onPause(); if (mCameraEx != null) { mCameraEx.release(); mCameraEx = null; } }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int h1) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) {}
}