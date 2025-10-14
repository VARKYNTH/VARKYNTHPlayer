package com.VARKYNTH.Player;

import androidx.media3.common.C;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
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
	
	// включить поведение «если нет RG — играть на полной»
	private final boolean forceFullScaleIfNoRG = true;
	
	// --- ЕДИНЫЙ аудио-поток ---
	private android.os.HandlerThread audioThread;
	private android.os.Handler audioH;
	private void runAudio(Runnable r){ if (audioH!=null) audioH.post(r); else r.run(); }
	
	MediaPlayer mp;
	int position = 0;
	ArrayList<HashMap<String,Object>> list = new ArrayList<>();
	VARTHRepeatMode repeatMode = VARTHRepeatMode.OFF;
	boolean shuffle = false;
	
	// --- AudioFocus ---
	private final AudioManager audioManager;
	// было: в DUCK понижали громкость, а в GAIN возвращали
	private final AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> {
		try {
			switch (focusChange) {
				case AudioManager.AUDIOFOCUS_LOSS:
				case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				pause(); // пауза при звонках/навигации — оставляем
				break;
				case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				// НИЧЕГО НЕ ДЕЛАЕМ — НЕ ПРИГЛУШАЕМ
				break;
				case AudioManager.AUDIOFOCUS_GAIN:
				// Не трогаем громкость, не «восстанавливаем» — она и так 1.0
				break;
			}
		} catch (Throwable ignore) {}
	};
	
	VARTHPlaybackCore(Context ctx, VARTHEffectsManager fx, Callback cb) {
		this.ctx = ctx.getApplicationContext();
		this.fx = fx;
		this.cb = cb;
		this.audioManager = (AudioManager) this.ctx.getSystemService(Context.AUDIO_SERVICE);
		
		// Стартуем единый HandlerThread ОДИН РАЗ
		audioThread = new android.os.HandlerThread("VARTH-Audio");
		audioThread.start();
		audioH = new android.os.Handler(audioThread.getLooper());
	}
	
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
	
	private boolean hasReplayGainTags(Uri uri) {
		java.io.InputStream in = null;
		try {
			in = ctx.getContentResolver().openInputStream(uri);
			if (in == null) return false;
			byte[] buf = new byte[64 * 1024]; // первые 64KB достаточно для ID3/atoms/vorbis comment
			int n = in.read(buf);
			if (n <= 0) return false;
			String s = new String(buf, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1).toUpperCase();
			
			// самые распространённые маркеры
			if (s.contains("REPLAYGAIN_")) return true;        // REPLAYGAIN_TRACK_GAIN / ALBUM_GAIN / PEAK
			if (s.contains("R128_")) return true;              // Opus/OGG R128_* tags
			if (s.contains("ITUNNORM")) return true;           // iTunes normalization atom
			if (s.contains("LOUDNESSNORMALIZATION")) return true;
			
			// ID3 TXXX кастомные
			if (s.contains("TXXX") && (s.contains("REPLAYGAIN") || s.contains("ITUNNORM"))) return true;
			
			// mp4 atoms (ilst) — упрощённая проверка
			if (s.contains("----:COM.APPLE.ITUNES:ITUNNORM")) return true;
			
			return false;
		} catch (Throwable ignore) {
			return false;
		} finally {
			try { if (in != null) in.close(); } catch (Throwable ignore) {}
		}
	}
	
	void setList(ArrayList<HashMap<String,Object>> l){ list = (l!=null)? l : new ArrayList<>(); }
	void setPosition(int pos){
		if (list==null || list.isEmpty()) { position = 0; return; }
		if (pos < 0) pos = 0;
		if (pos >= list.size()) pos = list.size() - 1;
		position = pos;
	}
	
	public void seekToMs(long ms) {
		runAudio(() -> {
			if (mp == null) return;
			int dur = getDuration();
			int clamped = (int)Math.max(0, Math.min(ms, dur));
			mp.seekTo(clamped);
		});
	}
	
	void start(){ runAudio(this::playFromCurrent); }
	void next(){  runAudio(() -> { if(!valid())return; position = (position>=list.size()-1)?0:position+1; playFromCurrent(); }); }
	void prev(){  runAudio(() -> { if(!valid())return; position = (position<=0)?list.size()-1:position-1; playFromCurrent(); }); }
	void nextShuffled(){ runAudio(() -> { if(!valid())return; int sz=list.size(),np=position,tries=0;
			if(sz>1){ do{ np=(int)Math.floor(Math.random()*sz);} while(np==position && ++tries<10); }
			position=np; playFromCurrent(); });
	}
	
	private boolean valid(){ return list!=null && !list.isEmpty(); }
	
	private void setHeadroomVolume(float l, float r){
		final float HR = 1f; // ≈ -1 dBFS
		try { if (mp!=null) mp.setVolume(l*HR, r*HR); } catch (Throwable ignored) {}
	}    
	
	private void playFromCurrent() {
		if (!valid()) return;
		
		Object p = list.get(position).get("path");
		Uri uri = (p!=null) ? (String.valueOf(p).startsWith("/") ? Uri.fromFile(new File(String.valueOf(p))) : Uri.parse(String.valueOf(p))) : null;
		if (uri == null) return;
		
		releasePlayer();
		
		try {
			if (audioManager != null) {
				audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
			}
		} catch (Throwable ignore) {}
		
		mp = MediaPlayer.create(ctx, uri);
		if (mp == null) return;
		
		try { mp.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK); } catch (Throwable ignore) {}
		
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
		setHeadroomVolume(1f, 1f);
		
		// эффекты к новому sessionId
		fx.attachToSession(mp.getAudioSessionId());
		fx.forceFullScaleNoLimiter();
		
		cb.onMetadata(makeMetadata());
		
		// !!! ВАЖНО: НИКАКИХ audioThread.start() ЗДЕСЬ !!!
		// Поток уже запущен в конструкторе.
		
		// === если нет RG — играем на полной ===
		try {
			if (forceFullScaleIfNoRG && uri != null && !hasReplayGainTags(uri)) {
				mp.setVolume(1f, 1f); // полная громкость
				try {
					fx.getClass().getMethod("forceFullScaleNoLimiter").invoke(fx);
				} catch (Throwable ignore) {
					// метода нет — просто игнор, громкость уже 1.0
				}
			}
		} catch (Throwable ignore) {}
		
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
		try {
			if (Build.VERSION.SDK_INT >= 23) {
				android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
				if (am != null) {
					for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
						if (d.getType() == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
						d.getType() == android.media.AudioDeviceInfo.TYPE_USB_DEVICE) {
							try {
								// не на всех сборках доступно, поэтому через рефлексию, чтобы не ломать API
								java.lang.reflect.Method ma = mp.getClass().getMethod("setPreferredDevice", android.media.AudioDeviceInfo.class);
								ma.invoke(mp, d);
							} catch (Throwable ignored) {}
							break;
						}
					}
				}
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
		int dur = getDuration();
		return new MediaMetadataCompat.Builder()
		.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
		.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
		.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
		.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur)
		.build();
	}
	
	private static boolean isEmpty(String s){ return s==null || s.isEmpty(); }
	
	void pause() {
		runAudio(() -> {
			try { if (mp!=null && mp.isPlaying()) { mp.pause(); cb.onPaused(); } } catch (IllegalStateException ignored) {}
		});
	}
	void resume(){
		runAudio(() -> {
			try { if (mp!=null && !mp.isPlaying()) { mp.start(); setHeadroomVolume(1f, 1f); cb.onStarted(); } } catch (IllegalStateException ignored) {}
		});
	}
	
	int  getCurrent(){ try{ return mp!=null? mp.getCurrentPosition():0;}catch(IllegalStateException e){return 0;}}
	int  getDuration(){ try{ return mp!=null? mp.getDuration():0;}catch(IllegalStateException e){return 0;}}
	boolean isPlaying(){ try{ return mp!=null && mp.isPlaying(); }catch(IllegalStateException e){return false;}}
	
	void shutdownAudioThread(){
		try { if (audioThread != null) { audioThread.quitSafely(); } } catch (Throwable ignored) {}
	}
	
	void releasePlayer(){
		try { if (mp!=null){ mp.release(); mp=null; } } catch (Throwable ignored) {}
		try { if (audioManager != null) audioManager.abandonAudioFocus(focusListener); } catch (Throwable ignore) {}
	}
}
