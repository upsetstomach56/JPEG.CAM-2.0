package com.github.ma1co.pmcademo.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RecipeManager {
    private File recipeDir;
    private File activeSlotsFile;
    private List<String> activeFilenames = new ArrayList<String>();
    private RTLProfile[] loadedProfiles = new RTLProfile[6]; // Cache for the 6 active slots
    private int currentSlot = 0;

    public RecipeManager() {
        recipeDir = new File(Filepaths.getAppDir(), "RECIPES");
        if (!recipeDir.exists()) recipeDir.mkdirs();
        
        activeSlotsFile = new File(recipeDir, "ACTIVE_SLOTS.TXT");
        
        loadActiveRoster();
        loadAllActiveProfiles();
    }

    // --- ROSTER MANAGEMENT ---
    private void loadActiveRoster() {
        activeFilenames.clear();
        if (activeSlotsFile.exists()) {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(activeSlotsFile), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null && activeFilenames.size() < 6) {
                    activeFilenames.add(line.trim());
                }
                br.close();
            } catch (Exception e) {
                android.util.Log.e("JPEG.CAM", "Failed to read roster.");
            }
        }
        
        // Failsafe: If roster is missing or incomplete, generate default slot names
        while (activeFilenames.size() < 6) {
            String defaultName = String.format("R_SLOT%d.TXT", activeFilenames.size() + 1);
            activeFilenames.add(defaultName);
        }
        saveActiveRoster(); // Lock in the roster
    }

    private void saveActiveRoster() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String filename : activeFilenames) {
                sb.append(filename).append("\n");
            }
            FileOutputStream fos = new FileOutputStream(activeSlotsFile);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            android.util.Log.e("JPEG.CAM", "Failed to save roster.");
        }
    }

    // --- VAULT MANAGEMENT (JSON) ---
    private void loadAllActiveProfiles() {
        for (int i = 0; i < 6; i++) {
            loadedProfiles[i] = loadProfileFromFile(activeFilenames.get(i), i + 1);
        }
    }

    private RTLProfile loadProfileFromFile(String filename, int slotNumber) {
        File file = new File(recipeDir, filename);
        RTLProfile p = new RTLProfile();
        
        if (!file.exists()) {
            // If the file doesn't exist in the vault, initialize a safe default
            p.profileName = "SLOT " + slotNumber;
            saveProfileToFile(file, p);
            return p;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            JSONObject json = new JSONObject(new String(data, "UTF-8"));
            
            p.profileName = json.optString("profileName", "RECIPE");
            p.lutName = json.optString("lutName", "OFF");
            p.lutOpacity = json.optInt("lutOpacity", 100);
            p.shadowToe = json.optInt("shadowToe", 0);
            p.rollOff = json.optInt("rollOff", 0);
            p.colorChrome = json.optInt("colorChrome", 0);
            p.chromeBlue = json.optInt("chromeBlue", 0);
            p.subtractiveSat = json.optInt("subtractiveSat", 0);
            p.halation = json.optInt("halation", 0);
            p.vignette = json.optInt("vignette", 0);
            p.grain = json.optInt("grain", 0);
            p.grainSize = json.optInt("grainSize", 0);
            p.contrast = json.optInt("contrast", 0);
            p.saturation = json.optInt("saturation", 0);
            p.wbShift = json.optInt("wbShift", 0);
            p.wbShiftGM = json.optInt("wbShiftGM", 0);
            p.colorMode = json.optString("colorMode", "Standard");
            p.whiteBalance = json.optString("whiteBalance", "Auto");
            p.shadingRed = json.optInt("shadingRed", 0);
            p.shadingBlue = json.optInt("shadingBlue", 0);
            p.colorDepthRed = json.optInt("colorDepthRed", 0);
            p.colorDepthGreen = json.optInt("colorDepthGreen", 0);
            p.colorDepthBlue = json.optInt("colorDepthBlue", 0);
            p.colorDepthCyan = json.optInt("colorDepthCyan", 0);
            p.colorDepthMagenta = json.optInt("colorDepthMagenta", 0);
            p.colorDepthYellow = json.optInt("colorDepthYellow", 0);
            p.dro = json.optString("dro", "OFF");
            p.pictureEffect = json.optString("pictureEffect", "off");
            p.proColorMode = json.optString("proColorMode", "off");
            p.sharpness = json.optInt("sharpness", 0);
            p.sharpnessGain = json.optInt("sharpnessGain", 0);
            p.vignetteHardware = json.optInt("vignetteHardware", 0);

            JSONArray arr = json.optJSONArray("advMatrix");
            if (arr != null && arr.length() == 9) {
                for (int i = 0; i < 9; i++) p.advMatrix[i] = arr.getInt(i);
            } else {
                p.advMatrix = new int[]{100, 0, 0, 0, 100, 0, 0, 0, 100};
            }

            // --- THE LUT VALIDATION FALLBACK ---
            // Verify that the specified LUT actually exists on the SD card
            if (p.lutName != null && !p.lutName.equalsIgnoreCase("OFF") && !p.lutName.isEmpty()) {
                File lutDir = new File(Filepaths.getAppDir(), "LUTS");
                // Note: Adjust the ".cube" extension here if your app uses .png or something else for LUTs!
                File expectedLut = new File(lutDir, p.lutName + ".cube"); 
                
                if (!expectedLut.exists()) {
                    android.util.Log.w("JPEG.CAM", "Missing LUT: " + p.lutName + ". Falling back to OFF.");
                    p.lutName = "OFF"; 
                    
                    // Optional: Append a warning to the profile name so the user sees it in the menu
                    p.profileName = p.profileName + " (NO LUT)";
                }
            }

        } catch (Exception e) {
            android.util.Log.e("JPEG.CAM", "Failed to parse JSON: " + filename);
            p.profileName = "ERROR";
        }
        return p;
    }

    private void saveProfileToFile(File file, RTLProfile p) {
        try {
            // Manual StringBuilder to bypass Sony JSON limitations and make it highly readable
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"profileName\": \"").append(p.profileName.replace("\"", "\\\"")).append("\",\n");
            sb.append("  \"lutName\": \"").append(p.lutName.replace("\"", "\\\"")).append("\",\n");
            sb.append("  \"lutOpacity\": ").append(p.lutOpacity).append(",\n");
            sb.append("  \"shadowToe\": ").append(p.shadowToe).append(",\n");
            sb.append("  \"rollOff\": ").append(p.rollOff).append(",\n");
            sb.append("  \"colorChrome\": ").append(p.colorChrome).append(",\n");
            sb.append("  \"chromeBlue\": ").append(p.chromeBlue).append(",\n");
            sb.append("  \"subtractiveSat\": ").append(p.subtractiveSat).append(",\n");
            sb.append("  \"halation\": ").append(p.halation).append(",\n");
            sb.append("  \"vignette\": ").append(p.vignette).append(",\n");
            sb.append("  \"grain\": ").append(p.grain).append(",\n");
            sb.append("  \"grainSize\": ").append(p.grainSize).append(",\n");
            sb.append("  \"contrast\": ").append(p.contrast).append(",\n");
            sb.append("  \"saturation\": ").append(p.saturation).append(",\n");
            sb.append("  \"wbShift\": ").append(p.wbShift).append(",\n");
            sb.append("  \"wbShiftGM\": ").append(p.wbShiftGM).append(",\n");
            sb.append("  \"colorMode\": \"").append(p.colorMode).append("\",\n");
            sb.append("  \"whiteBalance\": \"").append(p.whiteBalance).append("\",\n");
            sb.append("  \"shadingRed\": ").append(p.shadingRed).append(",\n");
            sb.append("  \"shadingBlue\": ").append(p.shadingBlue).append(",\n");
            sb.append("  \"colorDepthRed\": ").append(p.colorDepthRed).append(",\n");
            sb.append("  \"colorDepthGreen\": ").append(p.colorDepthGreen).append(",\n");
            sb.append("  \"colorDepthBlue\": ").append(p.colorDepthBlue).append(",\n");
            sb.append("  \"colorDepthCyan\": ").append(p.colorDepthCyan).append(",\n");
            sb.append("  \"colorDepthMagenta\": ").append(p.colorDepthMagenta).append(",\n");
            sb.append("  \"colorDepthYellow\": ").append(p.colorDepthYellow).append(",\n");
            
            sb.append("  \"advMatrix\": [\n    ");
            for (int i = 0; i < 9; i++) {
                sb.append(p.advMatrix[i]);
                if (i < 8) sb.append(",\n    ");
                else sb.append("\n  ],\n");
            }
            
            sb.append("  \"dro\": \"").append(p.dro).append("\",\n");
            sb.append("  \"pictureEffect\": \"").append(p.pictureEffect).append("\",\n");
            sb.append("  \"proColorMode\": \"").append(p.proColorMode).append("\",\n");
            sb.append("  \"sharpness\": ").append(p.sharpness).append(",\n");
            sb.append("  \"sharpnessGain\": ").append(p.sharpnessGain).append(",\n");
            sb.append("  \"vignetteHardware\": ").append(p.vignetteHardware).append("\n");
            sb.append("}");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            android.util.Log.e("JPEG.CAM", "Failed to save profile.");
        }
    }

    // --- PUBLIC API FOR MAINACTIVITY ---
    public void setCurrentSlot(int slot) {
        if (slot >= 0 && slot < 6) currentSlot = slot;
    }

    public int getCurrentSlot() {
        return currentSlot;
    }

    public RTLProfile getCurrentProfile() {
        return loadedProfiles[currentSlot];
    }

    public RTLProfile getProfile(int slot) {
        if (slot >= 0 && slot < 6) return loadedProfiles[slot];
        return null;
    }

    public void savePreferences() {
        // Saves the current live profile directly into its assigned Vault file
        String currentFilename = activeFilenames.get(currentSlot);
        File file = new File(recipeDir, currentFilename);
        saveProfileToFile(file, loadedProfiles[currentSlot]);
    }

    // --- HOT SWAP HELPER (For Future Use) ---
    // Use this later when we build the "Load from SD Card" menu
    public void assignFileToCurrentSlot(String newFilename) {
        activeFilenames.set(currentSlot, newFilename);
        saveActiveRoster();
        loadedProfiles[currentSlot] = loadProfileFromFile(newFilename, currentSlot + 1);
    }
}