package com.VARKYNTH.Player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

final class VARTHPlaybackCore {
	interface Callback {
		void onStarted();
		void onPaused();
		void onCompletedAutoNext();
		void onMetadata(MediaMetadataCompat meta);
		void onError(Exception e);
	}
	
	private final Context ctx;
	private final VARTHEffectsManager fx;
	private final Callback cb;
	
	MediaPlayer mp;
	int position = 0;
	ArrayList<HashMap<String,Object>> list = new ArrayList<>();
	VARTHRepeatMode repeatMode = VARTHRepeatMode.OFF;
	boolean shuffle = false;

	VARTHPlaybackCore(Context ctx, VARTHEffectsManager fx, Callback cb) {
		this.ctx = ctx.getApplicationContext();
		this.fx = fx;
		this.cb = cb;
	}
	// VARTHPlaybackCore: утилита определения внешнего вывода
	private boolean isHeadsetOrBtConnected() {
		try {
			android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
			if (am == null) return false;
			if (android.os.Build.VERSION.SDK_INT >= 23) {
				for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
					int t = d.getType();
					if (t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES
					|| t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
					|| t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
					|| t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true;
				}
				return false;
			} else {
				// fallback для старых API
				return am.isWiredHeadsetOn() || am.isBluetoothA2dpOn();
			}
		} catch (Throwable ignore) { return false; }
	}
	
	void setList(ArrayList<HashMap<String,Object>> l){ list = (l!=null)? l : new ArrayList<>(); }
	void setPosition(int pos){
		if (list==null || list.isEmpty()) { position = 0; return; }
		if (pos < 0) pos = 0;
		if (pos >= list.size()) pos = list.size() - 1;
		position = pos;
	}
	
	public void seekToMs(long ms){
		if (mp==null) return;
		int dur = getDuration();
		int clamped = (int) Math.max(0, Math.min(ms, dur));
		try {
			// короткое приглушение, чтобы не хрипело на динамике
			if (!isHeadsetOrBtConnected()) mp.setVolume(0.6f, 0.6f);
			mp.seekTo(clamped);
			if (!isHeadsetOrBtConnected()) {
				new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
				() -> { try { mp.setVolume(1f,1f); } catch (Throwable ignore) {} },
				120
				);
			}
		} catch (Throwable ignore) {}
	}
	
	void start() { playFromCurrent(); }
    void next()  { if (!valid()) return; position = (position >= list.size()-1) ? 0 : position+1; playFromCurrent(); }
	void prev()  { if (!valid()) return; position = (position <= 0) ? list.size()-1 : position-1; playFromCurrent(); }
	void nextShuffled(){
		if (!valid()) return;
		int sz=list.size(); int newPos=position;
		if (sz>1){ int tries=0; do { newPos=(int)Math.floor(Math.random()*sz); tries++; } while(newPos==position && tries<10); }
		position = newPos;
		playFromCurrent();
	}
	
	private boolean valid(){ return list!=null && !list.isEmpty(); }
	
	private void playFromCurrent() {
		if (!valid()) return;
		
		Object p = list.get(position).get("path");
		Uri uri = (p!=null) ? (String.valueOf(p).startsWith("/") ? Uri.fromFile(new File(String.valueOf(p))) : Uri.parse(String.valueOf(p))) : null;
		if (uri == null) return;
		
		releasePlayer();
		
		mp = MediaPlayer.create(ctx, uri);
		if (mp == null) return;
		
		mp.setLooping(repeatMode == VARTHRepeatMode.ONE);
		applyAudioAttrs(mp);
		forceHQBluetooth();
		try {
			android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
			if (am!=null) {
				am.stopBluetoothSco();
				am.setBluetoothScoOn(false);
				am.setMode(android.media.AudioManager.MODE_NORMAL);
			}
		} catch (Throwable ignore) {}
		mp.start();
		
		// эффекты к новому sessionId
		fx.attachToSession(mp.getAudioSessionId());
		
		// метаданные
		cb.onMetadata(makeMetadata());
		
		// колбеки
		mp.setOnCompletionListener(m -> {
			if (shuffle) nextShuffled();
			else next();
			cb.onCompletedAutoNext();
		});
		
		cb.onStarted();
	}
	
	private void applyAudioAttrs(MediaPlayer m) {
		try {
			if (Build.VERSION.SDK_INT >= 21) {
				m.setAudioAttributes(new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build());
			} else {
				m.setAudioStreamType(AudioManager.STREAM_MUSIC);
			}
		} catch (Throwable ignored) {}
	}
	
	private void forceHQBluetooth() {
		try {
			AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
			if (am == null) return;
			try { am.stopBluetoothSco(); } catch (Throwable ignored) {}
			try { am.setBluetoothScoOn(false); } catch (Throwable ignored) {}
			try { am.setMode(AudioManager.MODE_NORMAL); } catch (Throwable ignored) {}
		} catch (Throwable ignored) {}
	}
	
	MediaMetadataCompat makeMetadata() {
		String title="", artist="", album="";
		if (valid()) {
			Object t=list.get(position).get("title");
			Object a=list.get(position).get("artist");
			Object al=list.get(position).get("album");
			Object p=list.get(position).get("path");
			if (t!=null) title=String.valueOf(t);
			if (a!=null) artist=String.valueOf(a);
			if (al!=null) album=String.valueOf(al);
			
			MediaMetadataRetriever mmr=null;
			try {
				if (isEmpty(title) || isEmpty(artist) || isEmpty(album)) {
					mmr = new MediaMetadataRetriever();
					if (p!=null) {
						String path=String.valueOf(p);
						Uri uri=path.startsWith("/")? Uri.fromFile(new File(path)) : Uri.parse(path);
						mmr.setDataSource(ctx, uri);
					}
					if (isEmpty(artist)) { String s=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST); if (s!=null) artist=s; }
					if (isEmpty(title))  { String s=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);  if (s!=null) title=s; }
					if (isEmpty(album))  { String s=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);  if (s!=null) album=s; }
				}
			} catch (Throwable ignored) {
			} finally { if (mmr!=null) try { mmr.release(); } catch (Throwable ignored) {} }
		}
		int dur = mp!=null? mp.getDuration():0;
		return new MediaMetadataCompat.Builder()
		.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
		.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
		.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
		.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur)
		.build();
	}
	
	private static boolean isEmpty(String s){ return s==null || s.isEmpty(); }
	
	void pause() { if (mp!=null && mp.isPlaying()) { mp.pause(); cb.onPaused(); } }
	void resume(){ if (mp!=null && !mp.isPlaying()) { mp.start(); cb.onStarted(); } }
	
	int  getCurrent(){ return mp!=null ? mp.getCurrentPosition() : 0; }
	int  getDuration(){ return mp!=null ? mp.getDuration() : 0; }
	boolean isPlaying(){ return mp!=null && mp.isPlaying(); }
	
	void releasePlayer(){
		try { if (mp!=null){ mp.release(); mp=null; } } catch (Throwable ignored) {}
	}
}
