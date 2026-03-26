private void findNewestFile(boolean triggerCallback) {
        File dcimDir = Filepaths.getDcimDir(); 
        if (!dcimDir.exists() || !dcimDir.isDirectory()) {
            if (triggerCallback) Log.e("JPEG.CAM", "DCIM directory not found: " + dcimDir.getAbsolutePath());
            return;
        }

        File newestFile = null;
        long maxModified = 0;

        File[] subDirs = dcimDir.listFiles();
        if (subDirs != null) {
            for (File dir : subDirs) {
                // UNIVERSAL FIX: Don't guess the folder name. Look inside ALL directories in DCIM.
                if (dir.isDirectory()) { 
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            String name = f.getName().toUpperCase();
                            // Look for original Sony JPEGs (Ignore our FILM_ outputs and temp files)
                            if (name.endsWith(".JPG") && !name.startsWith("FILM_") && !name.startsWith("PRCS") && !name.startsWith("TEMP_")) {
                                if (f.lastModified() > maxModified) {
                                    maxModified = f.lastModified();
                                    newestFile = f;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (newestFile != null) {
            final String currentPath = newestFile.getAbsolutePath();
            if (!currentPath.equals(lastSeenFilePath)) {
                Log.d("JPEG.CAM", "NEW FILE DETECTED: " + currentPath);
                lastSeenFilePath = currentPath;
                
                if (triggerCallback && mCallback != null) {
                    boolean isReady = mCallback.isReadyToProcess();
                    Log.d("JPEG.CAM", "isReadyToProcess() evaluated to: " + isReady);
                    
                    if (isReady) {
                        Log.d("JPEG.CAM", "Firing onNewPhotoDetected callback to main thread!");
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onNewPhotoDetected(currentPath);
                            }
                        });
                    } else {
                        Log.w("JPEG.CAM", "Engine blocked processing. (Either LUT is 0/OFF or processor is not initialized).");
                    }
                }
            }
        }
    }