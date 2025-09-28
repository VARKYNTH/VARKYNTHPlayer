package com.VARKYNTH.Player;

import android.content.Intent;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.AllId;

public class VARTHAboutActivity extends AppCompatActivity {

    private AllId.AboutViewId v;
	
	private Intent SynIntent = new Intent();
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.varth_about);
        
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
}
