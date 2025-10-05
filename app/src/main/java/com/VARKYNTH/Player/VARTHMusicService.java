package com.VARKYNTH.Player;

import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.ArrayList;
import java.util.HashMap;

import android.media.AudioManager;

import static com.VARKYNTH.Player.VARTHConstants.*;

public class VARTHMusicService extends Service {
	
	private final IBinder ibinder = new MusicBinder();
	
	private MediaSessionCompat mediaSession;
	private VARTHEffectsManager fx;
	private VARTHPlaybackCore core;
	private VARTHNotificationHelper notifier;
	private VARTHProgressDispatcher progress;
	
	// 1) AudioFocus
	private AudioManager af;
	private AudioManager.OnAudioFocusChangeListener afc = focus -> {
		if (focus == AudioManager.AUDIOFOCUS_LOSS
		|| focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
			if (core != null && core.isPlaying()) { core.pause(); sendPause(); updateNotification(); }
		} else if (focus == AudioManager.AUDIOFOCUS_GAIN) {
			// опционально: вернуть громкость/возобновить
		}
	};
	
	private final BroadcastReceiver toggleReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			if (core == null) return;
			if (core.isPlaying()) { core.pause(); sendPause(); }
			else { core.resume(); sendPlay(); }
			updateNotification();
		}
	};
	private final BroadcastReceiver seekReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			String data = intent.getStringExtra("data");
			if (data == null) return;
			try { core.seekToMs((long)Double.parseDouble(data)); updatePlaybackState(); updateNotification(); }
			catch (NumberFormatException ignored) {}
		}
	};
	
	private void reg(BroadcastReceiver r, IntentFilter f){
		if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
		else registerReceiver(r, f);
	}
	
	@Override public void onCreate() {
		super.onCreate();
		
		af = (AudioManager) getSystemService(AUDIO_SERVICE);
		
		reg(toggleReceiver, new IntentFilter(BR_SONG_CONTROL));
		reg(seekReceiver,  new IntentFilter(BR_SONG_SEEK));
		
		mediaSession = new MediaSessionCompat(this, "VARTHMusicService");
		mediaSession.setActive(true);
		mediaSession.setCallback(new MediaSessionCompat.Callback() {
			@Override public void onPlay()  { core.resume(); updatePlaybackState(); updateNotification(); sendPlay(); }
			@Override public void onPause() { core.pause();  updatePlaybackState(); updateNotification(); sendPause(); }
			@Override public void onSkipToNext() { core.next(); onTrackChanged(); }
			@Override public void onSkipToPrevious() { core.prev(); onTrackChanged(); }
			@Override public void onSeekTo(long pos) { core.seekToMs(pos); updatePlaybackState(); }
			@Override public void onStop() {
				stopForeground(true); stopSelf(); sendPause();
			}
		});
		
		fx = new VARTHEffectsManager(this);
		core = new VARTHPlaybackCore(this, fx, new VARTHPlaybackCore.Callback() {
			@Override public void onStarted() { updatePlaybackState(); updateNotification(); sendPlay(); }
			@Override public void onPaused()  { updatePlaybackState(); updateNotification(); sendPause(); }
			@Override public void onCompletedAutoNext() { updateNotification(); }
			@Override public void onMetadata(MediaMetadataCompat meta) { mediaSession.setMetadata(meta); }
			@Override public void onError(Exception e) { sendError(e); }
		});
		
		notifier = new VARTHNotificationHelper(this, mediaSession);
		progress = new VARTHProgressDispatcher(this, core);
	}
	
	private boolean requestFocus(){
		if (af == null) return true;
		int r = af.requestAudioFocus(afc, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}
	private void abandonFocus(){ if (af != null) af.abandonAudioFocus(afc); }
	
	// 2) Becoming noisy
	private final BroadcastReceiver noisy = new BroadcastReceiver() {
		@Override public void onReceive(Context c, Intent i) {
			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) {
				if (core != null && core.isPlaying()) { core.pause(); sendPause(); updateNotification(); }
			}
		}
	};
	private void regNoisy(boolean on){
		if (on) registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
		else try { unregisterReceiver(noisy); } catch (Exception ignore) {}
	}
	
	// 3) Start with focus
	private void startPlayback(){
		if (requestFocus()) {
			core.start();
			startForegroundCompat();
			sendPlay();
		} else {
			sendError(new Exception("AUDIO_FOCUS_DENIED"));
		}
	}
	
	@Override public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent!=null ? intent.getAction() : null;
		if (ACTION_START.equals(action)) {
			core.start();
			startForegroundCompat();
			sendPlay();
		} else if (ACTION_NEXT.equals(action)) {
			core.next(); onTrackChanged();
		} else if (ACTION_PREV.equals(action)) {
			core.prev(); onTrackChanged();
		} else if (ACTION_STOP.equals(action)) {
			mediaSession.getController().getTransportControls().stop();
			sendPause();
		} else if (ACTION_TOGGLE.equals(action)) {
			if (core.isPlaying()) { core.pause(); sendPause(); } else { core.resume(); sendPlay(); }
			updateNotification();
		} else if (ACTION_REPEAT.equals(action)) {
			switch (core.repeatMode) {
				case OFF: core.repeatMode = VARTHRepeatMode.ONE; break;
				case ONE: core.repeatMode = VARTHRepeatMode.ALL; break;
				default:  core.repeatMode = VARTHRepeatMode.OFF; break;
			}
			if (core.mp!=null) core.mp.setLooping(core.repeatMode == VARTHRepeatMode.ONE);
			updateNotification();
		} else if (ACTION_SHUFFLE.equals(action)) {
			core.shuffle = !core.shuffle;
			updateNotification();
		}
		
		// прогресс-тикер
		progress.start(core.list, core.position);
		return START_NOT_STICKY;
	}
	
	private void onTrackChanged(){
		mediaSession.setMetadata(core.makeMetadata());
		updatePlaybackState();
		updateNotification();
	}
	
	private void startForegroundCompat(){
		notifier.startForeground(this, core.isPlaying(), core.repeatMode);
	}
	private void updateNotification(){
		mediaSession.setMetadata(core.makeMetadata());
		updatePlaybackState();
		notifier.updateForeground(this, core.isPlaying(), core.repeatMode);
	}
	private void updatePlaybackState(){
		PlaybackStateCompat st = new PlaybackStateCompat.Builder()
		.setState(core.isPlaying()? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
		core.getCurrent(), core.isPlaying()? 1f : 0f)
		.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
		| PlaybackStateCompat.ACTION_SEEK_TO
		| PlaybackStateCompat.ACTION_SKIP_TO_NEXT
		| PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
		.build();
		mediaSession.setPlaybackState(st);
	}
	
	private void sendPlay(){ sendState("play"); }
	private void sendPause(){ sendState("pause"); }
	private void sendState(String s){
		Intent i = new Intent(BR_SONG_STATE); i.putExtra("responce", s); sendBroadcast(i);
	}
	private void sendError(Exception e){
		Intent er = new Intent(BR_ERR); er.putExtra("error", e.getMessage()); sendBroadcast(er);
	}
	
	// ===== Публичный API: сигнатуры сохранены =====
	public void setList(ArrayList<HashMap<String, Object>> liste) { core.setList(liste); }
	public void setPosition(int pos) { core.setPosition(pos); }
	public void seekToMs(long ms) { core.seekToMs(ms); updatePlaybackState(); }
	
	public void Next_Music() { core.next(); onTrackChanged(); }
	public void Previous_Music() { core.prev(); onTrackChanged(); }
	
	public PlaybackStateCompat getPlayBackState() {
		return new PlaybackStateCompat.Builder()
		.setState(core.isPlaying()? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
		core.getCurrent(), core.isPlaying()? 1f : 0f)
		.build();
	}
	
	// ==== Проксирование SAFE API эффектов ====
	public boolean isDolbyEnabledSafe(){ return fx.isDolbyEnabledSafe(); }
	public void toggleDolbySafe(boolean enable){ fx.toggleDolbySafe(enable); }
	
	public int  getLoudnessGainSafe(){ return fx.getLoudnessGainSafe(); }
	public void setLoudnessGainSafe(int g){ fx.setLoudnessGainSafe(g); }
	
	public int  getBassStrengthSafe(){ return fx.getBassStrengthSafe(); }
	public void setBassStrengthSafe(short s){ fx.setBassStrengthSafe(s); }
	
	public int  getVirtualizerStrengthSafe(){ return fx.getVirtualizerStrengthSafe(); }
	public void setVirtualizerStrengthSafe(short s){ fx.setVirtualizerStrengthSafe(s); }
	
	public boolean isEqEnabledSafe(){ return fx.isEqEnabledSafe(); }
	public void setEqEnabledSafe(boolean en){ fx.setEqEnabledSafe(en); }
	
	public String[] getEqPresetsSafe(){ return fx.getEqPresetsSafe(); }
	public void useEqPresetSafe(int idx){ fx.useEqPresetSafe(idx); }
	
	public short getEqNumberOfBandsSafe(){ return fx.getEqNumberOfBandsSafe(); }
	public int[] getEqBandLevelRangeSafe(){ return fx.getEqBandLevelRangeSafe(); }
	public String getEqCenterFreqLabelSafe(short band){ return fx.getEqCenterFreqLabelSafe(band); }
	public int getEqBandLevelSafe(short band){ return fx.getEqBandLevelSafe(band); }
	public void setEqBandLevelSafe(short band, short level){ fx.setEqBandLevelSafe(band, level); }
	
	@Nullable @Override public IBinder onBind(Intent intent) { return ibinder; }
	public class MusicBinder extends Binder { public VARTHMusicService getService(){ return VARTHMusicService.this; } }
	
	@Override public void onDestroy() {
		super.onDestroy();
		progress.stop();
		try { unregisterReceiver(toggleReceiver); } catch (Exception ignored) {}
		try { unregisterReceiver(seekReceiver); }  catch (Exception ignored) {}
		if (core!=null) core.releasePlayer();
		if (fx!=null) fx.releaseAll();
		if (mediaSession!=null) mediaSession.release();
	}
}
