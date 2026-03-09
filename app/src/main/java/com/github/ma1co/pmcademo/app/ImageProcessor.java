package com.github.ma1co.pmcademo.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import java.io.File;
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

    // We bring Context back so we can trigger the Sony Media Scanner
    public ImageProcessor(Context context, ProcessorCallback callback) {
        this.mContext = context;
        this.mCallback = callback;
    }

    public void triggerLutPreload(String lutPath, String name) {
        new PreloadLutTask().execute(lutPath);
    }

    public void processJpeg(String inPath, String outDir, int qualityIndex, RTLProfile profile) {
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
            return LutEngine.loadLut(path);
        }

        @Override
        protected void onPostExecute(Boolean success) {
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
            if (mCallback != null) {
                mCallback.onProcessStarted();
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            File original = new File(inPath);
            if (!original.exists()) return null;

            // CRITICAL: Wait for the camera hardware to finish writing the file to the SD Card
            long lastSize = -1;
            int timeout = 0;
            while (timeout < 100) {
                long currentSize = original.length();
                if (currentSize > 0 && currentSize == lastSize) break;
                lastSize = currentSize;
                try { Thread.sleep(50); } catch (Exception e) {}
                timeout++;
            }

            File dir = new File(outDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String finalOutPath = new File(dir, "FILM_" + timeStamp + ".JPG").getAbsolutePath();

            // libjpeg-turbo scale_denom handles downscaling natively during IDCT.
            // This is lightning fast and lets the C++ layer perfectly preserve the Sony MakerNote EXIF.
            int scaleDenom = 1;
            if (qualityIndex == 0) { 
                scaleDenom = 4; // PROXY: ~1.5MP (Skip 3/4 of the IDCT calculation)
            } else if (qualityIndex == 1) { 
                scaleDenom = 2; // HIGH: ~6MP (Skip 1/2 of the IDCT calculation)
            } else { 
                scaleDenom = 1; // ULTRA: Full 24MP resolution
            }

            // Route straight to C++ NEON layer
            boolean success = LutEngine.processImageNative(
                    inPath,
                    finalOutPath,
                    scaleDenom,
                    profile.opacity,
                    profile.grain,
                    profile.grainSize,
                    profile.vignette,
                    profile.rollOff
            );

            if (success) {
                // CRITICAL: Tell the Sony Avindex (Playback Database) that a new file exists
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(finalOutPath))));
                return finalOutPath;
            } else {
                // Failsafe: Clean up 0-byte files if native crash/OOM occurs
                File failedOut = new File(finalOutPath);
                if (failedOut.exists()) {
                    failedOut.delete();
                }
                return null;
            }
        }

        @Override
        protected void onPostExecute(String resultPath) {
            if (mCallback != null) {
                mCallback.onProcessFinished(resultPath);
            }
        }
    }
}