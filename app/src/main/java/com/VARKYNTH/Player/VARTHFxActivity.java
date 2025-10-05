package com.VARKYNTH.Player;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;

import java.util.Objects;

import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.AllId;

public class VARTHFxActivity extends AppCompatActivity {
	
	// === Service ===
	private VARTHMusicService service;
	private boolean bound = false;
	private boolean suppressFxUiEvents = false;
	
	private AllId.FxViewId v;
	
	private final android.content.ServiceConnection conn = new android.content.ServiceConnection() {
		@Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
			VARTHMusicService.MusicBinder b = (VARTHMusicService.MusicBinder) binder;
			service = b.getService();
			bound = true;
			initEffectsUI();
		}
		@Override public void onServiceDisconnected(android.content.ComponentName name) {
			bound = false;
			service = null;
		}
	};
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.varth_fx);
		
		VFont.boldAll(this, findViewById(android.R.id.content));
		
		DynamicColors.applyToActivityIfAvailable(this); int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
		
		v = AllId.FxViewId.bind(this);
	}
	
	private void initEffectsUI() {
		if (!bound || service == null) return;
		
		suppressFxUiEvents = true;
		
		// Dolby
		try { v.sw_dolby.setChecked(service.isDolbyEnabledSafe()); } catch (Throwable ignored) {}
		v.sw_dolby.setOnCheckedChangeListener((b, checked) -> {
			if (!suppressFxUiEvents && bound && service != null) service.toggleDolbySafe(checked);
		});
		
		// Loudness: -2000..+2000  (Slider НЕ вызывает listener на setValue — применяем руками)
		int loudVal = 0;
		try { loudVal = service.getLoudnessGainSafe(); } catch (Throwable ignored) {}
		v.seek_loudness.setValue(loudVal);
		// сразу применяем (важно для визуала/эффекта)
		if (bound && service != null) service.setLoudnessGainSafe(loudVal);
		v.seek_loudness.clearOnChangeListeners();
		v.seek_loudness.addOnChangeListener((slider, value, fromUser) -> {
			if (fromUser && !suppressFxUiEvents && bound && service != null) {
				service.setLoudnessGainSafe((int) value);
			}
		});
		
		// Bass: 0..1000
		int bassVal = 0;
		try { bassVal = service.getBassStrengthSafe(); } catch (Throwable ignored) {}
		v.seek_bass.setValue(bassVal);
		if (bound && service != null) service.setBassStrengthSafe((short) bassVal);
		v.seek_bass.clearOnChangeListeners();
		v.seek_bass.addOnChangeListener((slider, value, fromUser) -> {
			if (fromUser && !suppressFxUiEvents && bound && service != null) {
				service.setBassStrengthSafe((short) value);
			}
		});
		
		// Virtualizer: 0..1000
		int virtVal = 0;
		try { virtVal = service.getVirtualizerStrengthSafe(); } catch (Throwable ignored) {}
		v.seek_virtualizer.setValue(virtVal);
		if (bound && service != null) service.setVirtualizerStrengthSafe((short) virtVal);
		v.seek_virtualizer.clearOnChangeListeners();
		v.seek_virtualizer.addOnChangeListener((slider, value, fromUser) -> {
			if (fromUser && !suppressFxUiEvents && bound && service != null) {
				service.setVirtualizerStrengthSafe((short) value);
			}
		});
		
		// EQ on/off
		try { v.sw_eq_enabled.setChecked(service.isEqEnabledSafe()); } catch (Throwable ignored) {}
		v.sw_eq_enabled.setOnCheckedChangeListener((btn, checked) -> {
			if (!suppressFxUiEvents && bound && service != null) service.setEqEnabledSafe(checked);
		});
		
		// Presets
		String[] presets = new String[0];
		try { presets = service.getEqPresetsSafe(); } catch (Throwable ignored) {}
		android.widget.ArrayAdapter<String> aa =
		new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, presets);
		v.sp_eq_presets.setAdapter(aa);
		v.sp_eq_presets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			boolean first = true;
			@Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
				if (first) { first = false; return; }
				if (bound && service != null) {
					service.useEqPresetSafe(position);
					rebuildEqBands();
				}
			}
			@Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
		
		suppressFxUiEvents = false;
		
		rebuildEqBands(); // слайдеры полос EQ ты уже переделал
	}
	
	private void rebuildEqBands() {
		if (!bound || service == null || v.eq_bands_container == null) return;
		v.eq_bands_container.removeAllViews();
        
		short bands = 0;
		int[] range = new int[]{-1500, 1500};
		try {
			bands = service.getEqNumberOfBandsSafe();
			range = service.getEqBandLevelRangeSafe();
		} catch (Throwable ignored) {}
		
		for (short b = 0; b < bands; b++) {
			android.widget.TextView label = new android.widget.TextView(this);
			try { label.setText(service.getEqCenterFreqLabelSafe(b)); } catch (Throwable ignored) {}
			label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			label.setPadding(0, dp(6), 0, dp(2));
			v.eq_bands_container.addView(label);
			
			// --- Slider вместо SeekBar ---
			com.google.android.material.slider.Slider s = new com.google.android.material.slider.Slider(this);
			s.setValueFrom(range[0]);
			s.setValueTo(range[1]);
			s.setStepSize(1f);
            
			int cur = 0;
			try { cur = service.getEqBandLevelSafe(b); } catch (Throwable ignored) {}
			s.setValue(cur);
			
			v.eq_bands_container.addView(s);
			
			final short fb = b;
			s.addOnChangeListener((slider, value, fromUser) -> {
				if (fromUser && bound && service != null) {
					service.setEqBandLevelSafe(fb, (short) value);
				}
			});
		}
	}
	private int dp(int v) {
		float d = getResources().getDisplayMetrics().density;
		return Math.round(v * d);
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
		// НИЧЕГО не стартуем, просто привязываемся
		bindService(new Intent(this, VARTHMusicService.class), conn, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		if (bound) {
			unbindService(conn);
			bound = false;
		}
	}
	
}
