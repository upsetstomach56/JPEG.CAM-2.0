package com.github.ma1co.pmcademo.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/**
 * Shared visual system for JPEG.CAM 2.0.
 *
 * Uses only simple colors and GradientDrawable panels so the UI stays light on
 * camera memory and does not add bitmap assets or heavier rendering paths.
 */
public final class UiTheme {
    public static final int SURFACE = Color.argb(238, 7, 11, 13);
    public static final int SURFACE_SOFT = Color.argb(188, 9, 14, 16);
    public static final int SURFACE_STRONG = Color.argb(248, 6, 9, 11);
    public static final int TEXT = Color.rgb(239, 246, 243);
    public static final int TEXT_MUTED = Color.rgb(143, 157, 154);
    public static final int TEXT_DIM = Color.rgb(68, 78, 78);
    public static final int ACCENT = Color.rgb(56, 198, 175);
    public static final int ACCENT_DARK = Color.rgb(19, 89, 82);
    public static final int WARN = Color.rgb(236, 186, 84);
    public static final int SUCCESS = Color.rgb(70, 218, 142);
    public static final int ERROR = Color.rgb(235, 74, 83);
    public static final int BORDER = Color.argb(130, 117, 145, 140);
    public static final int SHADOW = Color.argb(180, 0, 0, 0);

    private UiTheme() {}

    public static GradientDrawable rect(int color, int strokeColor, int strokeWidth, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    public static void panel(View view) {
        view.setBackgroundDrawable(rect(SURFACE, BORDER, 1, 8));
    }

    public static void softPanel(View view) {
        view.setBackgroundDrawable(rect(SURFACE_SOFT, Color.argb(90, 117, 145, 140), 1, 6));
    }

    public static void selected(View view) {
        view.setBackgroundDrawable(rect(Color.argb(215, 17, 110, 100), ACCENT, 1, 6));
    }

    public static void clear(View view) {
        view.setBackgroundColor(Color.TRANSPARENT);
    }

    public static void selectedText(TextView tv) {
        tv.setTextColor(TEXT);
        tv.setShadowLayer(2, 0, 0, SHADOW);
    }

    public static void mutedText(TextView tv) {
        tv.setTextColor(TEXT_MUTED);
        tv.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    }

    public static void dimText(TextView tv) {
        tv.setTextColor(TEXT_DIM);
        tv.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    }

    public static void applyStatusText(TextView tv, float size, Typeface typeface) {
        tv.setTextColor(TEXT);
        tv.setTextSize(size);
        tv.setShadowLayer(3, 0, 0, SHADOW);
        tv.setTypeface(typeface != null ? typeface : Typeface.DEFAULT_BOLD);
    }
}
