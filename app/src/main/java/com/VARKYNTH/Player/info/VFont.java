package com.VARKYNTH.Player.info;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public final class VFont {
    private VFont(){}

    /** Применить жирный шрифт ко всем TextView в указанном контейнере (например, root layout) */
    public static void boldAll(Context ctx, View root) {
        Typeface tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/robotobold.ttf");
        applyToAll(root, tf);
    }

    private static void applyToAll(View v, Typeface tf) {
        if (v instanceof TextView) {
            ((TextView) v).setTypeface(tf, Typeface.BOLD);
        } else if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToAll(group.getChildAt(i), tf);
            }
        }
    }
}