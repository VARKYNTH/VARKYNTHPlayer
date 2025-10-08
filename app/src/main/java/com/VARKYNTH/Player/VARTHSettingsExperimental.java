package com.VARKYNTH.Player;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import android.widget.CompoundButton;

import android.content.SharedPreferences;

import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.AllId;
import com.VARKYNTH.Player.ui.VGlobalDepth;

public class VARTHSettingsExperimental extends AppCompatActivity {
    
    private AllId.SettingsViewExperimentalId v;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.varth_settings_experimental);
        
        VFont.boldAll(this, findViewById(android.R.id.content));
        
        DynamicColors.applyToActivityIfAvailable(this); int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
        
        v = AllId.SettingsViewExperimentalId.bind(this);
        
        prefs = getSharedPreferences("vplayer_prefs", MODE_PRIVATE);
        
        boolean disabled = prefs.getBoolean("global_depth_enabled", false);

        v.ide_sw.setChecked(disabled);

        v.ide_sw.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean("global_depth_enabled", isChecked).apply());
                
    }
}