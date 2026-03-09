package com.github.ma1co.pmcademo.app;

import java.io.File;

public class SonyFileScanner {
    private String dcimRoot;
    private ScannerCallback mCallback;
    private boolean isPolling = false;
    private long lastNewestFileTime = 0;

    public interface ScannerCallback {
        void onNewPhotoDetected(String filePath);
        boolean isReadyToProcess(); 
    }

    public SonyFileScanner(String path, ScannerCallback callback) {
        // Path comes in as DCIM/100MSDCF. We step up one level to DCIM to catch 101MSDCF, 102MSDCF, etc.
        File f = new File(path);
        if (f.getParent() != null) {
            this.dcimRoot = f.getParent();
        } else {
            this.dcimRoot = path;
        }
        this.mCallback = callback;
    }

    public void start() {
        startPolling();
    }

    public void stop() {
        isPolling = false;
    }

    private void startPolling() {
        isPolling = true;
        new Thread(new Runnable() {
            @Override 
            public void run() {
                while (isPolling) {
                    try {
                        Thread.sleep(500); // Polling every half second is safe for the BIONZ CPU
                        File dcimDir = new File(dcimRoot);
                        if (dcimDir.exists() && dcimDir.isDirectory()) {
                            File newest = null; 
                            long maxModified = 0;
                            
                            // Search all subfolders inside DCIM
                            File[] subDirs = dcimDir.listFiles();
                            if (subDirs != null) {
                                for (File dir : subDirs) {
                                    if (dir.isDirectory()) {
                                        File[] files = dir.listFiles();
                                        if (files != null) {
                                            for (File f : files) {
                                                String name = f.getName().toUpperCase();
                                                // Ignore our own processed files
                                                if (name.endsWith(".JPG") && !name.startsWith("PRCS") && !name.startsWith("FILM_")) {
                                                    if (f.lastModified() > maxModified) { 
                                                        maxModified = f.lastModified(); 
                                                        newest = f; 
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (newest != null) {
                                if (lastNewestFileTime == 0) { 
                                    // Set baseline on boot so we don't process old photos
                                    lastNewestFileTime = maxModified; 
                                } else if (maxModified > lastNewestFileTime) {
                                    lastNewestFileTime = maxModified;
                                    if (mCallback.isReadyToProcess()) {
                                        mCallback.onNewPhotoDetected(newest.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }).start();
    }
}