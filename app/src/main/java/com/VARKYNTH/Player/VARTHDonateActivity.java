package com.VARKYNTH.Player;

import android.content.Context;

import android.os.Bundle;
import android.view.View;

import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import com.VARKYNTH.Player.info.AllId;
import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.VCopy;
import com.VARKYNTH.Player.ui.VGlobalDepth;

public class VARTHDonateActivity extends AppCompatActivity {
	
	private AllId.DonateViewId v;
    
    private SharedPreferences prefs;
    
    private boolean depthApplied = false;
	
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.varth_donate);
        
        prefs = getSharedPreferences("vplayer_prefs", MODE_PRIVATE);

        applyDepthFromPrefs();

        // Живое обновление при смене настройки в SettingsActivity
        prefListener = (sp, key) -> {
            if ("global_depth_enabled".equals(key)) applyDepthFromPrefs();
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        
        VFont.boldAll(this, findViewById(android.R.id.content));
		
		DynamicColors.applyToActivityIfAvailable(this); int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
		
		v = AllId.DonateViewId.bind(this);
		
		VCopy.attach(this, v.textview9);
        
	}
    private void applyDepthFromPrefs() {
        boolean enabled = prefs.getBoolean("global_depth_enabled", false); // default OFF
        if (enabled && !depthApplied) {
            VGlobalDepth.attach(this);   // включить эффект
            depthApplied = true;
        } else if (!enabled && depthApplied) {
            VGlobalDepth.detach();       // выключить эффект
            depthApplied = false;
        }
    }
}
