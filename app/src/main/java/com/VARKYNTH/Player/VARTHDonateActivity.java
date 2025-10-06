package com.VARKYNTH.Player;

import android.content.Context;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import com.VARKYNTH.Player.info.AllId;
import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.VCopy;

public class VARTHDonateActivity extends AppCompatActivity {
	
	private AllId.DonateViewId v;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.varth_donate);
        
        com.VARKYNTH.Player.ui.VGlobalDepth.attach(this);
        
        VFont.boldAll(this, findViewById(android.R.id.content));
		
		DynamicColors.applyToActivityIfAvailable(this); int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
		
		v = AllId.DonateViewId.bind(this);
		
		VCopy.attach(this, v.textview9);
        
	}
}
