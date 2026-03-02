package com.github.ma1co.pmcademo.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import com.sony.scalar.hardware.CameraEx;
import com.sony.scalar.sysutil.ScalarInput;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity implements SurfaceHolder.Callback, CameraEx.ShutterSpeedChangeListener {
    private CameraEx mCameraEx;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private TextView tvShutter, tvAperture, tvISO, tvExposure, tvRecipe;
    
    private ArrayList<String> recipeList = new ArrayList<String>();
    private int recipeIndex = 0;
    private String currentDcimPath = "";
    private boolean isBaking = false;

    public enum DialMode { shutter, aperture, iso, exposure, recipe }
    private DialMode mDialMode = DialMode.shutter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(this);
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        tvShutter = (TextView) findViewById(R.id.tvShutter);
        tvAperture = (TextView) findViewById(R.id.tvAperture);
        tvISO = (TextView) findViewById(R.id.tvISO);
        tvExposure = (TextView) findViewById(R.id.tvExposure);
        tvRecipe = (TextView) findViewById(R.id.tvRecipe);
        
        scanRecipes();
        setDialMode(DialMode.shutter);
    }

    private String getLatestPhotoPath() {
        File dcim = new File(Environment.getExternalStorageDirectory(), "DCIM");
        File[] folders = dcim.listFiles();
        if (folders == null) return null;
        
        // Find the newest MSDCF folder
        File sonyFolder = null;
        for (File f : folders) {
            if (f.getName().endsWith("MSDCF")) {
                if (sonyFolder == null || f.getName().compareTo(sonyFolder.getName()) > 0) sonyFolder = f;
            }
        }
        
        if (sonyFolder != null) {
            File[] files = sonyFolder.listFiles();
            if (files != null && files.length > 0) {
                // Sort by date to get the absolute latest
                Arrays.sort(files, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                    }
                });
                for (File f : files) {
                    if (f.getName().toUpperCase().endsWith(".JPG") && !f.getName().startsWith("MIRROR_")) return f.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private class ManualMirrorTask extends AsyncTask<Void, Void, Boolean> {
        String path;
        ManualMirrorTask(String path) { this.path = path; }

        @Override protected void onPreExecute() { 
            isBaking = true;
            tvRecipe.setText("MANUAL BAKE STARTING...");
            tvRecipe.setTextColor(Color.YELLOW);
        }

        @Override protected Boolean doInBackground(Void... voids) {
            try {
                File original = new File(path);
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 4; // 4MP for stability
                
                // Try reading the file directly
                Bitmap bmp = BitmapFactory.decodeFile(original.getAbsolutePath(), opt);
                
                if (bmp != null) {
                    File mirrorFile = new File(original.getParent(), "MIRROR_" + original.getName());
                    FileOutputStream fos = new FileOutputStream(mirrorFile);
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                    bmp.recycle();
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(mirrorFile)));
                    return true;
                }
            } catch (Exception e) {}
            return false;
        }

        @Override protected void onPostExecute(Boolean success) {
            isBaking = false;
            if (success) {
                tvRecipe.setText("MANUAL MIRROR SUCCESS!");
                tvRecipe.setTextColor(Color.GREEN);
            } else {
                tvRecipe.setText("MANUAL BAKE FAILED");
                tvRecipe.setTextColor(Color.RED);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();
        // EXIT APP
        if (scanCode == ScalarInput.ISV_KEY_DELETE) { exitApp(); return true; }
        
        // --- THE MANUAL TRIGGER ---
        if (scanCode == ScalarInput.ISV_KEY_UP) { 
            String latest = getLatestPhotoPath();
            if (latest != null) new ManualMirrorTask(latest).execute();
            return true; 
        }

        if (isBaking) return true;
        if (scanCode == ScalarInput.ISV_KEY_DOWN) { cycleMode(); return true; }
        if (scanCode == ScalarInput.ISV_DIAL_1_CLOCKWISE) { handleInput(1); return true; }
        if (scanCode == ScalarInput.ISV_DIAL_1_COUNTERCW) { handleInput(-1); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // --- STANDARD UI / CAMERA LOGIC (FIXED BOTTOM BAR) ---
    private void syncUI() {
        if (mCamera == null) return;
        try {
            Camera.Parameters p = mCamera.getParameters();
            CameraEx.ParametersModifier pm = mCameraEx.createParametersModifier(p);
            Pair<Integer, Integer> speed = pm.getShutterSpeed();
            if (speed.first == 1 && speed.second != 1) tvShutter.setText(speed.first + "/" + speed.second);
            else tvShutter.setText(speed.first + "\"");
            tvAperture.setText("f/" + (pm.getAperture() / 100.0f));
            tvISO.setText("ISO " + pm.getISOSensitivity());
            tvExposure.setText(String.format("%.1f", p.getExposureCompensation() * p.getExposureCompensationStep()));
        } catch (Exception e) {}
    }

    private void reopenCamera() {
        try {
            mCameraEx = CameraEx.open(0, null);
            mCamera = mCameraEx.getNormalCamera();
            mCameraEx.startDirectShutter();
            mCameraEx.setShutterSpeedChangeListener(this);
            mCamera.setPreviewDisplay(mSurfaceView.getHolder());
            mCamera.startPreview();
            syncUI();
        } catch (Exception e) {}
    }

    @Override protected void onResume() { super.onResume(); reopenCamera(); sendSonyBroadcast(true); }
    private void handleInput(int d) { if (mCameraEx == null) return; try { if (mDialMode == DialMode.shutter) { if (d > 0) mCameraEx.incrementShutterSpeed(); else mCameraEx.decrementShutterSpeed(); } else if (mDialMode == DialMode.aperture) { if (d > 0) mCameraEx.incrementAperture(); else mCameraEx.decrementAperture(); } else if (mDialMode == DialMode.recipe) { recipeIndex = (recipeIndex + d + recipeList.size()) % recipeList.size(); } syncUI(); updateRecipeDisplay(); } catch (Exception e) {} }
    private void cycleMode() { DialMode[] modes = DialMode.values(); int next = (mDialMode.ordinal() + 1) % modes.length; setDialMode(modes[next]); }
    private void setDialMode(DialMode mode) { mDialMode = mode; tvShutter.setTextColor(mode == DialMode.shutter ? Color.GREEN : Color.WHITE); tvAperture.setTextColor(mode == DialMode.aperture ? Color.GREEN : Color.WHITE); tvISO.setTextColor(mode == DialMode.iso ? Color.GREEN : Color.WHITE); tvExposure.setTextColor(mode == DialMode.exposure ? Color.GREEN : Color.WHITE); updateRecipeDisplay(); }
    private void scanRecipes() { recipeList.clear(); recipeList.add("NONE (DEFAULT)"); File lutDir = new File(Environment.getExternalStorageDirectory(), "LUTS"); if (lutDir.exists()) { File[] files = lutDir.listFiles(); if (files != null) { for (File f : files) { if (f.getName().toUpperCase().contains("CUB")) recipeList.add(f.getName()); } } } updateRecipeDisplay(); }
    private void updateRecipeDisplay() { String name = recipeList.get(recipeIndex); String display = name.split("\\.")[0].toUpperCase(); tvRecipe.setText("<  " + display + "  >"); tvRecipe.setTextColor(mDialMode == DialMode.recipe ? Color.GREEN : Color.WHITE); }
    private void sendSonyBroadcast(boolean active) { Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive"); intent.putExtra("package_name", getPackageName()); intent.putExtra("resume_key", active ? new String[]{"on"} : new String[]{}); sendBroadcast(intent); }
    private void exitApp() { Intent intent = new Intent("com.android.server.DAConnectionManagerService.AppInfoReceive"); intent.putExtra("package_name", getPackageName()); intent.putExtra("class_name", getClass().getName()); intent.putExtra("pullingback_key", new String[] {}); intent.putExtra("resume_key", new String[] {}); sendBroadcast(intent); finish(); }
    @Override public void onShutterSpeedChange(CameraEx.ShutterSpeedInfo info, CameraEx camera) { syncUI(); }
    @Override public void surfaceCreated(SurfaceHolder h) { try { if (mCamera != null) { mCamera.setPreviewDisplay(h); mCamera.startPreview(); syncUI(); } } catch (Exception e) {} }
    @Override protected void onPause() { super.onPause(); if (mCameraEx != null) mCameraEx.release(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int h1) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) {}
}