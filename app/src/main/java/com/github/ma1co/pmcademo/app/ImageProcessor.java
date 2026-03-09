package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.content.Intent;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageProcessor {

    public interface ProcessorCallback {
        void onPreloadStarted();
        void onPreloadFinished(boolean success);
        void onProcessStarted();
        void onProcessFinished(String resultPath);
    }

    private Context mContext;
    private ProcessorCallback mCallback;

    public ImageProcessor(Context context, ProcessorCallback callback) {
        this.mContext = context;
        this.mCallback = callback;
    }

    public void triggerLutPreload(String lutPath, String name) {
        Log.d("filmOS", "ImageProcessor: Triggering LUT Preload for " + name);
        new PreloadLutTask().execute(lutPath);
    }

    public void processJpeg(String inPath, String outDir, int qualityIndex, RTLProfile profile) {
        Log.d("filmOS", "ImageProcessor.processJpeg called! Queuing AsyncTask...");
        new ProcessTask(inPath, outDir, qualityIndex, profile).execute();
    }

    private class PreloadLutTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            if (mCallback != null) {
                mCallback.onPreloadStarted();
            }
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String path = params[0];
            if (path == null || path.equals("NONE")) {
                return false;
            }
            Log.d("filmOS", "PreloadLutTask.doInBackground running natively...");
            return LutEngine.loadLut(path);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Log.d("filmOS", "PreloadLutTask finished. Success: " + success);
            if (mCallback != null) {
                mCallback.onPreloadFinished(success);
            }
        }
    }

    private class ProcessTask extends AsyncTask<Void, Void, String> {
        private String inPath;
        private String outDir;
        private int qualityIndex;
        private RTLProfile profile;

        public ProcessTask(String inPath, String outDir, int qualityIndex, RTLProfile profile) {
            this.inPath = inPath;
            this.outDir = outDir;
            this.qualityIndex = qualityIndex;
            this.profile = profile;
        }

        @Override
        protected void onPreExecute() {
            Log.d("filmOS", "ProcessTask.onPreExecute running on UI thread. Setting to PROCESSING...");
            if (mCallback != null) {
                mCallback.onProcessStarted();
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            Log.d("filmOS", "ProcessTask.doInBackground started on worker thread!");
            
            File dir = new File(outDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String finalOutPath = new File(dir, "FILM_" + timeStamp + ".JPG").getAbsolutePath();

            String fileToProcess = inPath;
            int scaleDenom = 1;
            boolean usedThumbnail = false;

            // THUMBNAIL OPTIMIZATION: For Proxy quality, rip the 2MP thumbnail directly from EXIF
            if (qualityIndex == 0) {
                try {
                    Log.d("filmOS", "Attempting to rip EXIF thumbnail for Proxy quality...");
                    ExifInterface exif = new ExifInterface(inPath);
                    byte[] thumb = exif.getThumbnail();
                    if (thumb != null && thumb.length > 0) {
                        File temp = new File(outDir, "temp_rip.jpg");
                        FileOutputStream fos = new FileOutputStream(temp);
                        fos.write(thumb);
                        fos.close();
                        fileToProcess = temp.getAbsolutePath();
                        usedThumbnail = true;
                        Log.d("filmOS", "EXIF thumbnail extracted successfully.");
                    } else {
                        Log.w("filmOS", "No EXIF thumbnail found, falling back to full image scaleDown=4.");
                        scaleDenom = 4;
                    }
                } catch (Exception e) {
                    Log.e("filmOS", "Failed to rip thumbnail: " + e.getMessage());
                    scaleDenom = 4;
                }
            } else if (qualityIndex == 1) {
                scaleDenom = 2; // HIGH: ~6MP
            } else {
                scaleDenom = 1; // ULTRA: Full 24MP resolution
            }

            Log.d("filmOS", "Calling LutEngine.processImageNative...");
            // Route to C++ NEON native layer
            boolean success = LutEngine.processImageNative(
                    fileToProcess,
                    finalOutPath,
                    scaleDenom,
                    profile.opacity,
                    profile.grain,
                    profile.grainSize,
                    profile.vignette,
                    profile.rollOff
            );
            
            Log.d("filmOS", "LutEngine.processImageNative returned: " + success);

            // Cleanup temp file if used
            if (usedThumbnail) {
                new File(fileToProcess).delete();
            }

            if (success) {
                // EXIF RESTORATION: Copy essential metadata from original to processed file
                try {
                    Log.d("filmOS", "Restoring EXIF data...");
                    ExifInterface oldExif = new ExifInterface(inPath);
                    ExifInterface newExif = new ExifInterface(finalOutPath);
                    String[] tags = {
                        ExifInterface.TAG_ORIENTATION, 
                        ExifInterface.TAG_DATETIME, 
                        ExifInterface.TAG_MAKE, 
                        ExifInterface.TAG_MODEL,
                        "FNumber", 
                        "ExposureTime", 
                        "ISOSpeedRatings",
                        "FocalLength"
                    };
                    for (String t : tags) {
                        String v = oldExif.getAttribute(t);
                        if (v != null) newExif.setAttribute(t, v);
                    }
                    newExif.saveAttributes();
                } catch (Exception e) {
                    Log.e("filmOS", "Failed to restore EXIF: " + e.getMessage());
                }

                // CRITICAL: Notify the Sony Avindex (Playback Database) that a new file exists
                Log.d("filmOS", "Sending MEDIA_SCANNER broadcast for " + finalOutPath);
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(finalOutPath))));
                return finalOutPath;
            } else {
                Log.e("filmOS", "Processing failed, deleting output file if it exists.");
                File f = new File(finalOutPath);
                if (f.exists()) f.delete();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String resultPath) {
            Log.d("filmOS", "ProcessTask.onPostExecute completed. Result: " + resultPath);
            if (mCallback != null) {
                mCallback.onProcessFinished(resultPath);
            }
        }
    }
}