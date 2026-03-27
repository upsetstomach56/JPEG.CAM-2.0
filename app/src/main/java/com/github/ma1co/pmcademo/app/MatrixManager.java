package com.github.ma1co.pmcademo.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MatrixManager {
    private File matrixDir;
    private List<String> matrixNames = new ArrayList<String>();
    private List<int[]> matrixValues = new ArrayList<int[]>();
    private List<String> matrixNotes = new ArrayList<String>();

    public MatrixManager() {
        matrixDir = new File(Filepaths.getAppDir(), "MATRIX");
        if (!matrixDir.exists()) matrixDir.mkdirs();
    }

    public void scanMatrices() {
        matrixNames.clear();
        matrixValues.clear();
        matrixNotes.clear();

        File[] files = matrixDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".json")) {
                loadMatrixFile(f);
            }
        }
    }

    private void loadMatrixFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            JSONObject json = new JSONObject(new String(data, "UTF-8"));
            JSONArray arr = json.getJSONArray("advMatrix");
            
            int[] values = new int[9];
            for (int i = 0; i < 9; i++) values[i] = arr.getInt(i);

            matrixNames.add(file.getName().replace(".json", "").replace("_", " ").toUpperCase());
            matrixValues.add(values);
            matrixNotes.add(json.optString("note", "User defined matrix."));
        } catch (Exception e) {
            android.util.Log.e("JPEG.CAM", "Failed to load matrix: " + file.getName());
        }
    }

    public void saveMatrix(String name, int[] values, String note) {
        try {
            JSONObject json = new JSONObject();
            JSONArray arr = new JSONArray();
            for (int v : values) arr.put(v);
            json.put("advMatrix", arr);
            json.put("note", note);

            File file = new File(matrixDir, name.replace(" ", "_") + ".json");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(json.toString(2).getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {
            android.util.Log.e("JPEG.CAM", "Save failed: " + e.getMessage());
        }
    }

    public List<String> getNames() { return matrixNames; }
    public int[] getValues(int index) { return matrixValues.get(index); }
    public String getNote(int index) { return matrixNotes.get(index); }
    public int getCount() { return matrixNames.size(); }
}