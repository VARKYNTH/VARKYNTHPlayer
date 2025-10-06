package com.VARKYNTH.Player;

import android.content.Intent;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.AllId;
import com.VARKYNTH.Player.ui.VGlobalDepth;

public class VARTHAboutActivity extends AppCompatActivity {

    private AllId.AboutViewId v;
	
	private Intent SynIntent = new Intent();
    
    private SharedPreferences prefs;
    
    private boolean depthApplied = false;
    
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.varth_about);
        
        prefs = getSharedPreferences("vplayer_prefs", MODE_PRIVATE);

        applyDepthFromPrefs();

        // Живое обновление при смене настройки в SettingsActivity
        prefListener = (sp, key) -> {
            if ("global_depth_enabled".equals(key)) applyDepthFromPrefs();
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        
        VFont.boldAll(this, findViewById(android.R.id.content));
        
        DynamicColors.applyToActivityIfAvailable(this); int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
        
        v = AllId.AboutViewId.bind(this);
		
		v.dev_owner_click.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setAction(Intent.ACTION_VIEW);
				SynIntent.setData(Uri.parse("https://t.me/VARKYNTH"));
				startActivity(SynIntent);
			}
		});
		
		v.dev_anna_click.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setAction(Intent.ACTION_VIEW);
				SynIntent.setData(Uri.parse("https://www.instagram.com/anna_love_you20?igsh=dXgxb28ybXc4MG5u"));
				startActivity(SynIntent);
			}
		});
		
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
