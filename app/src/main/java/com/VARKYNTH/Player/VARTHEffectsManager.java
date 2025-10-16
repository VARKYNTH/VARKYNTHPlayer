package com.VARKYNTH.Player;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.*;
import android.os.Build;

import java.util.UUID;

public final class VARTHEffectsManager {
	private final Context ctx;
	private Equalizer eq;
	private BassBoost bass;
	private Virtualizer virt;
	private PresetReverb presetReverb;
	private EnvironmentalReverb envReverb;
	private LoudnessEnhancer le;
	private DynamicsProcessing dp;     // API 28+
	private AudioEffect dolbyEffect;   // если есть в системе
	
	public VARTHEffectsManager(Context ctx) { this.ctx = ctx.getApplicationContext(); }
	
	public void attachToSession(int sessionId) {
		releaseAll();
		try { eq = new Equalizer(0, sessionId); eq.setEnabled(true); } catch (Throwable ignored) {}
		try { bass = new BassBoost(0, sessionId); if (bass.getStrengthSupported()) bass.setStrength((short)0); bass.setEnabled(true);} catch (Throwable ignored) {}
		try { virt = new Virtualizer(0, sessionId); if (virt.getStrengthSupported()) virt.setStrength((short)0); virt.setEnabled(true);} catch (Throwable ignored) {}
		try { presetReverb = new PresetReverb(0, sessionId); presetReverb.setPreset(PresetReverb.PRESET_NONE); presetReverb.setEnabled(true);} catch (Throwable ignored) {}
		try { envReverb = new EnvironmentalReverb(0, sessionId); envReverb.setEnabled(true);} catch (Throwable ignored) {}
		try { le = new LoudnessEnhancer(sessionId); le.setTargetGain(0); le.setEnabled(true);} catch (Throwable ignored) {}
		
		initDynamicsProcessingSafe(sessionId);
		enableSystemDolbyIfPresent(sessionId);
		applySavedToCurrentSession();
	}
	
	public void releaseAll() {
		try { if (eq!=null){eq.release(); eq=null;} } catch (Throwable ignored) {}
		try { if (bass!=null){bass.release(); bass=null;} } catch (Throwable ignored) {}
		try { if (virt!=null){virt.release(); virt=null;} } catch (Throwable ignored) {}
		try { if (presetReverb!=null){presetReverb.release(); presetReverb=null;} } catch (Throwable ignored) {}
		try { if (envReverb!=null){envReverb.release(); envReverb=null;} } catch (Throwable ignored) {}
		try { if (le!=null){le.release(); le=null;} } catch (Throwable ignored) {}
		try { if (dp!=null){dp.release(); dp=null;} } catch (Throwable ignored) {}
		disableSystemDolby();
	}
	
	private void initDynamicsProcessingSafe(int sessionId) {
		if (Build.VERSION.SDK_INT < 28) { dp = null; return; }
		try {
			int channels = 2;
			DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
			DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
			channels, false, 0, false, 4, false, 0, false
			).build();
			dp = new DynamicsProcessing(0, sessionId, cfg);
			try {
				// попытка безопасного input gain
				dp.getClass().getMethod("setInputGainAllChannelsTo", float.class).invoke(dp, -1.5f);
			} catch (Throwable ignored) {
				try {
					for (int ch = 0; ch < channels; ch++) {
						dp.getClass().getMethod("setInputGainByChannelIndex", int.class, float.class).invoke(dp, ch, -1.5f);
					}
				} catch (Throwable ignored2) {}
			}
			try { dp.setEnabled(false); } catch (Throwable ignored) {}
		} catch (Throwable t) { dp = null; }
	}
	
	private void enableSystemDolbyIfPresent(int sessionId) {
		try {
			AudioEffect.Descriptor[] descs = AudioEffect.queryEffects();
			if (descs == null) return;
			for (AudioEffect.Descriptor d : descs) {
				String impl = d.implementor != null ? d.implementor.toLowerCase() : "";
				String name = d.name != null ? d.name.toLowerCase() : "";
				String type = d.type != null ? d.type.toString().toLowerCase() : "";
				boolean looksLikeDolby = impl.contains("dolby") || name.contains("dolby") || type.contains("dolby");
				if (!looksLikeDolby) continue;
				
				try {
					java.lang.reflect.Constructor<AudioEffect> ctor =
					AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, int.class, int.class);
					ctor.setAccessible(true);
					dolbyEffect = ctor.newInstance(d.type, d.uuid, 0, sessionId);
					try { dolbyEffect.setEnabled(true); } catch (Throwable ignored) {}
				} catch (Throwable ignored) {}
				break;
			}
		} catch (Throwable ignored) {}
	}
	
	private void disableSystemDolby() {
		if (dolbyEffect != null) {
			try { dolbyEffect.release(); } catch (Throwable ignored) {}
			dolbyEffect = null;
		}
	}
	
	public void applySavedToCurrentSession() {
		SharedPreferences p = ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE);
		try { if (le!=null){ le.setTargetGain(p.getInt("loudness_gain",0)); le.setEnabled(true);} } catch (Throwable ignored) {}
		try { if (bass!=null){ short s=(short)p.getInt("bass_strength",0); if(bass.getStrengthSupported()) bass.setStrength(s); bass.setEnabled(true);} } catch (Throwable ignored) {}
		try { if (virt!=null){ short s=(short)p.getInt("virt_strength",0); if(virt.getStrengthSupported()) virt.setStrength(s); virt.setEnabled(true);} } catch (Throwable ignored) {}
		try {
			if (eq!=null) {
				boolean en = p.getBoolean("eq_enabled", false);
				eq.setEnabled(en);
				int preset = p.getInt("eq_preset", -1);
				if (preset >= 0) {
					eq.usePreset((short)preset);
				} else {
					try {
						short bands = eq.getNumberOfBands();
						for (short b=0;b<bands;b++) {
							int lvl = p.getInt("eq_band_"+b, 0);
							eq.setBandLevel(b, (short)lvl);
						}
					} catch (Throwable ignored) {}
				}
			}
		} catch (Throwable ignored) {}
		try { if (dolbyEffect!=null){ boolean on=p.getBoolean("dolby_enabled",false); dolbyEffect.setEnabled(on);} } catch (Throwable ignored) {}
	}
	
	// ==== SAFE API для настроек (совместимо с прежними вызовами) ====
	public boolean isDolbyEnabledSafe() {
		try { return dolbyEffect != null && dolbyEffect.getEnabled(); } catch (Throwable t) { return false; }
	}
	public void toggleDolbySafe(boolean enable) {
		try {
			if (dolbyEffect != null) dolbyEffect.setEnabled(enable);
			ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE)
			.edit().putBoolean("dolby_enabled", enable).apply();
		} catch (Throwable ignored) {}
	}
	public int  getLoudnessGainSafe() { try { return le!=null ? (int)le.getTargetGain() : 0; } catch (Throwable t){ return 0; } }
	public void setLoudnessGainSafe(int g){
		try {
			if (le!=null){ le.setTargetGain(g); le.setEnabled(true); }
			ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE).edit().putInt("loudness_gain", g).apply();
		} catch (Throwable ignored) {}
	}
	public int  getBassStrengthSafe(){ try { return bass!=null ? bass.getRoundedStrength() : 0; } catch (Throwable t){ return 0; } }
	public void setBassStrengthSafe(short s){
		try {
			if (bass!=null && bass.getStrengthSupported()) { bass.setStrength(s); bass.setEnabled(true); }
			ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE).edit().putInt("bass_strength", s).apply();
		} catch (Throwable ignored) {}
	}
	public int  getVirtualizerStrengthSafe(){ try { return virt!=null ? virt.getRoundedStrength() : 0; } catch (Throwable t){ return 0; } }
	public void setVirtualizerStrengthSafe(short s){
		try {
			if (virt!=null && virt.getStrengthSupported()) { virt.setStrength(s); virt.setEnabled(true); }
			ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE).edit().putInt("virt_strength", s).apply();
		} catch (Throwable ignored) {}
	}
	public boolean isEqEnabledSafe(){ try { return eq!=null && eq.getEnabled(); } catch (Throwable t){ return false; } }
	public void setEqEnabledSafe(boolean en){
		try {
			if (eq!=null) eq.setEnabled(en);
			ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE).edit().putBoolean("eq_enabled", en).apply();
		} catch (Throwable ignored) {}
	}
	public String[] getEqPresetsSafe(){
		try {
			if (eq==null) return new String[0];
			short n = eq.getNumberOfPresets(); String[] out = new String[n];
			for (short i=0;i<n;i++) out[i] = eq.getPresetName(i);
			return out;
		} catch (Throwable t){ return new String[0]; }
	}
	public void useEqPresetSafe(int idx){
		try {
			if (eq!=null) eq.usePreset((short)idx);
			SharedPreferences p = ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE);
			SharedPreferences.Editor ed = p.edit().putInt("eq_preset", idx);
			try {
				if (eq!=null) {
					short bands = eq.getNumberOfBands();
					for (short b=0;b<bands;b++) ed.remove("eq_band_"+b);
				}
			} catch (Throwable ignored) {}
			ed.apply();
		} catch (Throwable ignored) {}
	}
	public short getEqNumberOfBandsSafe(){ try { return eq!=null ? eq.getNumberOfBands() : 0; } catch (Throwable t){ return 0; } }
	public int[] getEqBandLevelRangeSafe(){
		try { if (eq==null) return new int[]{-1500,1500}; short[] r=eq.getBandLevelRange(); return new int[]{r[0], r[1]}; }
		catch (Throwable t){ return new int[]{-1500,1500}; }
	}
	public String getEqCenterFreqLabelSafe(short band){
		try { if (eq==null) return ""; int hz = eq.getCenterFreq(band)/1000; return hz+" Hz"; }
		catch (Throwable t){ return ""; }
	}
	public int getEqBandLevelSafe(short band){ try { return eq!=null ? eq.getBandLevel(band) : 0; } catch (Throwable t){ return 0; } }
	public void setEqBandLevelSafe(short band, short level){
		try {
			if (eq!=null) eq.setBandLevel(band, level);
			ctx.getSharedPreferences(VARTHConstants.FX_PREFS, Context.MODE_PRIVATE)
			.edit()
			.putInt("eq_band_"+band, level)
			.putInt("eq_preset", -1)   // <- добавь это
			.apply();
		} catch (Throwable ignored) {}
	}
}
