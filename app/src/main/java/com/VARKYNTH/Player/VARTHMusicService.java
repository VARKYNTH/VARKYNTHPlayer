package com.VARKYNTH.Player;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class VARTHMusicService extends Service {
	
	private static final String CHANNEL_ID = "VARTHMusicServiceChannel";
	private static final String FX_PREFS = "fx_prefs";
	
	private static final String START = "com.VARKYNTH.Player.START";
	private static final String NEXT = "com.VARKYNTH.Player.NEXT";
	private static final String PRE = "com.VARKYNTH.Player.PRE";
	private static final String STOP = "com.VARKYNTH.Player.STOP";
	
	private final IBinder ibinder = new MusicBinder();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Timer timer = new Timer();
    
	private MediaPlayer mediaPlayer;
	private MediaSessionCompat mediaSession;
	
	private int position = 0;
	private ArrayList<HashMap<String, Object>> list = new ArrayList<>();
	
	private TimerTask progressTask;
	
	public enum RepeatMode { OFF, ONE, ALL }
	private RepeatMode repeatMode = RepeatMode.OFF;
	private boolean shuffle = false;
	
	// ===== FX =====
	private Equalizer eq;
	private BassBoost bass;
	private Virtualizer virt;
	private PresetReverb presetReverb;
	private EnvironmentalReverb envReverb;
	private LoudnessEnhancer le;
	// API 28+ многофункциональный процессор (pre/post EQ, multiband + лимитер)
	private android.media.audiofx.DynamicsProcessing dp;
	private android.media.audiofx.AudioEffect dolbyEffect;
	
	private final BroadcastReceiver toggleReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			if (mediaPlayer == null) return;
			if (mediaPlayer.isPlaying()) {
				mediaPlayer.pause();
				pauseIntent();
			} else {
				mediaPlayer.start();
				playIntent();
			}
			updateNotification();
		}
	};
	
	private final BroadcastReceiver seekReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			String data = intent.getStringExtra("data");
			if (data == null || mediaPlayer == null) return;
			try {
				long ms = (long) Double.parseDouble(data);
				seekToMs(ms);
				updateNotification();
			} catch (NumberFormatException ignore) {}
		}
	};
	
	private void registerReceiverCompat(BroadcastReceiver r, IntentFilter f) {
		if (Build.VERSION.SDK_INT >= 33) {
			registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(r, f);
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		registerReceiverCompat(toggleReceiver, new IntentFilter("com.dplay.SONG_CONTROL"));
		registerReceiverCompat(seekReceiver,  new IntentFilter("com.dplay.SONG_SEEK"));
		
		mediaSession = new MediaSessionCompat(this, "VARTHMusicService");
		mediaSession.setActive(true);
		mediaSession.setCallback(new MediaSessionCompat.Callback() {
			@Override public void onPlay() {
				if (mediaPlayer != null) {
					mediaPlayer.start();
					mediaSession.setPlaybackState(getPlayBackState());
					updateNotification();
					playIntent();
				}
			}
			@Override public void onPause() {
				if (mediaPlayer != null) {
					mediaPlayer.pause();
					mediaSession.setPlaybackState(getPlayBackState());
					updateNotification();
					pauseIntent();
				}
			}
			@Override public void onSkipToNext() { Next_Music(); updateNotification(); }
			@Override public void onSkipToPrevious() { Previous_Music(); updateNotification(); }
			@Override public void onSeekTo(long pos) { seekToMs(pos); mediaSession.setPlaybackState(getPlayBackState()); }
			@Override public void onStop() {
				if (mediaPlayer != null) {
					mediaPlayer.stop();
					mediaSession.setPlaybackState(getPlayBackState());
				}
				stopForeground(true);
				stopSelf();
				pauseIntent();
			}
		});
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if ("START".equals(action)) {
				startMedia();
				startForegroundServiceCompat();
				playIntent();
			} else if ("NEXT".equals(action)) {
				Next_Music();
				updateNotification();
			} else if ("PRE".equals(action)) {
				Previous_Music();
				updateNotification();
			} else if ("STOP".equals(action)) {
				mediaSession.getController().getTransportControls().stop();
				pauseIntent();
			} else if ("TOGGLE".equals(action)) {
				if (mediaPlayer != null) {
					if (mediaPlayer.isPlaying()) { mediaPlayer.pause(); pauseIntent(); }
					else { mediaPlayer.start(); playIntent(); }
					updateNotification();
				}
			} else if ("REPEAT".equals(action)) {
				switch (repeatMode) {
					case OFF: repeatMode = RepeatMode.ONE; break;
					case ONE: repeatMode = RepeatMode.ALL; break;
					default:  repeatMode = RepeatMode.OFF; break;
				}
				if (mediaPlayer != null) mediaPlayer.setLooping(repeatMode == RepeatMode.ONE);
				updateNotification();
			} else if ("SHUFFLE".equals(action)) {
				shuffle = !shuffle;
				updateNotification();
				
			}
		}
		
		try {
			if (progressTask != null) progressTask.cancel();
			progressTask = new TimerTask() {
				@Override public void run() {
					handler.post(() -> {
						if (mediaPlayer != null && mediaPlayer.isPlaying()) {
							String current = String.valueOf(mediaPlayer.getCurrentPosition());
							String duration = String.valueOf(mediaPlayer.getDuration());
							Intent i = new Intent("com.dplay.SONG_DATA");
							i.putExtra("total_duration", duration);
							i.putExtra("current_duration", current);
							if (list != null && !list.isEmpty() && position >= 0 && position < list.size()) {
								i.putExtra("song", String.valueOf(list.get(position).get("title")));
								i.putExtra("pos", String.valueOf(position));
								i.putExtra("path", String.valueOf(list.get(position).get("path")));
							}
							sendBroadcast(i);
						}
					});
				}
			};
			timer.scheduleAtFixedRate(progressTask, 0, 500);
		} catch (Exception e) {
			Intent er = new Intent("com.dplay.ERR");
			er.putExtra("error", e.getMessage());
			sendBroadcast(er);
		}
		
		return START_NOT_STICKY;
	}
	
	// === Public API ===
	public void setList(ArrayList<HashMap<String, Object>> liste) {
		list = (liste != null) ? liste : new ArrayList<>();
	}
	public void setPosition(int pos) {
		if (list == null || list.isEmpty()) { position = 0; return; }
		if (pos < 0) pos = 0;
		if (pos >= list.size()) pos = list.size() - 1;
		position = pos;
	}
	public void seekToMs(long ms) {
		if (mediaPlayer != null) {
			long clamped = Math.max(0, Math.min(ms, mediaPlayer.getDuration()));
			mediaPlayer.seekTo((int) clamped);
			mediaSession.setPlaybackState(getPlayBackState());
		}
	}
	
	private void playFromUri(Uri uri) {
		if (uri == null) return;
		
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
		
		mediaPlayer = MediaPlayer.create(this, uri);
		if (mediaPlayer == null) return;
		
		mediaPlayer.setLooping(repeatMode == RepeatMode.ONE);
		mediaPlayer.start();
		
		forceHighQualityBluetooth();
		applyAudioAttributes(mediaPlayer);
		// привязываем и применяем эффекты к АКТУАЛЬНОМУ sessionId
		int sessionId = mediaPlayer.getAudioSessionId();
		attachSystemEffects(sessionId);
		applySavedFxToCurrentSession();
		enableSystemDolbyIfPresent(sessionId);
		
		setMediaMetadata();
		playIntent();
		
		mediaPlayer.setOnCompletionListener(mp -> {
			if (shuffle) nextShuffled(); else Next_Music();
			updateNotification();
		});
	}
	
	// === заменённый startMedia ===
	private void startMedia() {
		if (list == null || list.isEmpty()) return;
		
		Object p = list.get(position).get("path");
		Uri uri = (p != null) ? (String.valueOf(p).startsWith("/") ?
		Uri.fromFile(new File(String.valueOf(p))) : Uri.parse(String.valueOf(p))) : null;
		
		playFromUri(uri);
	}
	
	// === заменённый Next_Music ===
	public void Next_Music() {
		if (list == null || list.isEmpty()) return;
		position = (position >= list.size() - 1) ? 0 : position + 1;
		
		Object p = list.get(position).get("path");
		Uri uri = (p != null) ? (String.valueOf(p).startsWith("/") ?
		Uri.fromFile(new File(String.valueOf(p))) : Uri.parse(String.valueOf(p))) : null;
		
		playFromUri(uri);
	}
	
	// === заменённый Previous_Music ===
	public void Previous_Music() {
		if (list == null || list.isEmpty()) return;
		position = (position <= 0) ? list.size() - 1 : position - 1;
		
		Object p = list.get(position).get("path");
		Uri uri = (p != null) ? (String.valueOf(p).startsWith("/") ?
		Uri.fromFile(new File(String.valueOf(p))) : Uri.parse(String.valueOf(p))) : null;
		
		playFromUri(uri);
	}
	
	// === заменённый nextShuffled ===
	private void nextShuffled() {
		if (list == null || list.isEmpty()) return;
		int sz = list.size();
		int newPos = position;
		if (sz > 1) {
			int tries = 0;
			do {
				newPos = (int) Math.floor(Math.random() * sz);
				tries++;
			} while (newPos == position && tries < 10);
		}
		position = newPos;
		
		Object p = list.get(position).get("path");
		Uri uri = (p != null) ? (String.valueOf(p).startsWith("/") ?
		Uri.fromFile(new File(String.valueOf(p))) : Uri.parse(String.valueOf(p))) : null;
		
		playFromUri(uri);
	}
	
	// вызывать перед prepare()/start()
	private void applyAudioAttributes(android.media.MediaPlayer mp) {
		if (mp == null) return;
		try {
			if (android.os.Build.VERSION.SDK_INT >= 21) {
				mp.setAudioAttributes(new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build());
			} else {
				// Фолбэк для до-Lollipop
				mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			}
		} catch (Throwable ignored) {}
	}
	
	// уже есть у тебя — оставь вызов сразу после start()
	private void forceHighQualityBluetooth() {
		try {
			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (am == null) return;
			try { am.stopBluetoothSco(); } catch (Throwable ignored) {}
			try { am.setBluetoothScoOn(false); } catch (Throwable ignored) {}
			try { am.setMode(AudioManager.MODE_NORMAL); } catch (Throwable ignored) {}
		} catch (Throwable ignored) {}
	}
	
	private void setMediaMetadata() {
		String title = "";
		String artist = "";
		String album = "";
		
		if (list != null && !list.isEmpty() && position >= 0 && position < list.size()) {
			Object t = list.get(position).get("title");
			Object a = list.get(position).get("artist");
			Object al = list.get(position).get("album");
			Object p = list.get(position).get("path");
			
			if (t != null) title = String.valueOf(t);
			if (a != null) artist = String.valueOf(a);
			if (al != null) album = String.valueOf(al);
			
			MediaMetadataRetriever mmr = null;
			try {
				if ((artist == null || artist.isEmpty()) || (title == null || title.isEmpty()) || (album == null || album.isEmpty())) {
					mmr = new MediaMetadataRetriever();
					if (p != null) {
						String path = String.valueOf(p);
						Uri uri = path.startsWith("/") ? Uri.fromFile(new File(path)) : Uri.parse(path);
						mmr.setDataSource(this, uri);
					}
					if (artist == null || artist.isEmpty()) {
						String tagArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
						if (tagArtist != null) artist = tagArtist;
					}
					if (title == null || title.isEmpty()) {
						String tagTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
						if (tagTitle != null) title = tagTitle;
					}
					if (album == null || album.isEmpty()) {
						String tagAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
						if (tagAlbum != null) album = tagAlbum;
					}
				}
			} catch (Exception ignore) {
			} finally {
				if (mmr != null) try { mmr.release(); } catch (Exception ignored) {}
			}
		}
		
		MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
		.putString(MediaMetadataCompat.METADATA_KEY_TITLE,  title != null ? title : "")
		.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist != null ? artist : "")
		.putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  album != null ? album : "")
		.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer != null ? mediaPlayer.getDuration() : 0)
		.build();
		
		mediaSession.setMetadata(metadata);
	}
	
	private void startForegroundServiceCompat() {
		createNotificationChannel();
		updateNotification();
	}
	
	private void updateNotification() {
		Intent prev = new Intent(this, VARTHMusicService.class).setAction("PRE");
		PendingIntent piPrev = PendingIntent.getService(this, 10, prev, PendingIntent.FLAG_IMMUTABLE);
		
		Intent toggle = new Intent(this, VARTHMusicService.class).setAction("TOGGLE");
		PendingIntent piToggle = PendingIntent.getService(this, 11, toggle, PendingIntent.FLAG_IMMUTABLE);
		
		Intent next = new Intent(this, VARTHMusicService.class).setAction("NEXT");
		PendingIntent piNext = PendingIntent.getService(this, 12, next, PendingIntent.FLAG_IMMUTABLE);
		
		Intent repeat = new Intent(this, VARTHMusicService.class).setAction("REPEAT");
		PendingIntent piRepeat = PendingIntent.getService(this, 13, repeat, PendingIntent.FLAG_IMMUTABLE);
		
		boolean isPlaying = (mediaPlayer != null && mediaPlayer.isPlaying());
		
		PendingIntent contentPI = mediaSession != null && mediaSession.getController() != null
		? mediaSession.getController().getSessionActivity()
		: null;
		if (contentPI == null) {
			Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
			if (launch != null) {
				contentPI = PendingIntent.getActivity(this, 101, launch, PendingIntent.FLAG_IMMUTABLE);
			}
		}
		
		NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
		.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
		.setPriority(NotificationCompat.PRIORITY_LOW)
		.setSmallIcon(R.drawable.ic_logo)
		.setContentTitle(safeMeta(MediaMetadataCompat.METADATA_KEY_TITLE))
		.setContentText(safeMeta(MediaMetadataCompat.METADATA_KEY_ARTIST))
		.setContentIntent(contentPI)
		.setOnlyAlertOnce(true)
		.setShowWhen(false)
		.addAction(android.R.drawable.ic_media_previous, "Prev", piPrev)
		.addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
		isPlaying ? "Pause" : "Play", piToggle)
		.addAction(android.R.drawable.ic_media_next, "Next", piNext)
		.addAction(getRepeatIconRes(), getRepeatLabel(), piRepeat)
		.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
		.setMediaSession(mediaSession.getSessionToken())
		.setShowActionsInCompactView(0, 1, 2))
		.setOngoing(isPlaying);
		
		setMediaMetadata();
		mediaSession.setPlaybackState(getPlayBackState());
		
		startForeground(1, b.build());
	}
	
	private String getRepeatLabel() {
		if (repeatMode == RepeatMode.ONE) return "Repeat 1";
		if (repeatMode == RepeatMode.ALL) return "Repeat All";
		return "Repeat Off";
	}
	
	private int getRepeatIconRes() {
		if (repeatMode == RepeatMode.ONE) return android.R.drawable.ic_menu_revert;
		if (repeatMode == RepeatMode.ALL) return android.R.drawable.ic_menu_rotate;
		return android.R.drawable.ic_menu_close_clear_cancel;
	}
	
	private String safeMeta(String key) {
		if (mediaSession != null
		&& mediaSession.getController() != null
		&& mediaSession.getController().getMetadata() != null) {
			CharSequence s = mediaSession.getController().getMetadata().getText(key);
			return s != null ? s.toString() : "";
		}
		return "";
	}
	
	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel ch = new NotificationChannel(
			CHANNEL_ID, "VARKYNTHPlayer", NotificationManager.IMPORTANCE_LOW);
			ch.setDescription("This is Offline VARKYNTHPlayer Notification");
            ch.setSound(null, null);
            ch.enableVibration(false);
			NotificationManager nm = getSystemService(NotificationManager.class);
			nm.createNotificationChannel(ch);
		}
	}
	
	public PlaybackStateCompat getPlayBackState() {
		if (mediaPlayer == null) {
			return new PlaybackStateCompat.Builder()
			.setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
			.build();
		}
		boolean playing = mediaPlayer.isPlaying();
		float speed = playing ? 1f : 0f;
		long pos = mediaPlayer.getCurrentPosition();
		return new PlaybackStateCompat.Builder()
		.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, pos, speed)
		.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
		| PlaybackStateCompat.ACTION_SEEK_TO
		| PlaybackStateCompat.ACTION_SKIP_TO_NEXT
		| PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
		.build();
	}
	
	private void playIntent() {
		Intent i = new Intent("com.dplay.SONG_STATE");
		i.putExtra("responce", "play");
		sendBroadcast(i);
	}
	
	private void pauseIntent() {
		Intent i = new Intent("com.dplay.SONG_STATE");
		i.putExtra("responce", "pause");
		sendBroadcast(i);
		try {
			if (mediaPlayer != null && mediaPlayer.isPlaying()) {
				mediaPlayer.pause();
			}
		} catch (Throwable ignored) {}
	}
	
	@Nullable @Override
	public IBinder onBind(Intent intent) { return ibinder; }
	
	public class MusicBinder extends Binder {
		public VARTHMusicService getService() { return VARTHMusicService.this; }
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (progressTask != null) progressTask.cancel();
		try { unregisterReceiver(toggleReceiver); } catch (Exception ignore) {}
		try { unregisterReceiver(seekReceiver);  } catch (Exception ignore) {}
		releaseEffects();
		disableSystemDolby();
		if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
		if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
	}
	
	// ================== EFFECTS UTILS ==================
	private void releaseEffects() {
		try { if (eq != null) { eq.release(); eq = null; } } catch (Exception ignore) {}
		try { if (bass != null) { bass.release(); bass = null; } } catch (Exception ignore) {}
		try { if (virt != null) { virt.release(); virt = null; } } catch (Exception ignore) {}
		try { if (presetReverb != null) { presetReverb.release(); presetReverb = null; } } catch (Exception ignore) {}
		try { if (envReverb != null) { envReverb.release(); envReverb = null; } } catch (Exception ignore) {}
		try { if (le != null) { le.release(); le = null; } } catch (Exception ignore) {}
		try { if (dp != null) { dp.release(); dp = null; } } catch (Exception ignore) {}
	}
	
	private void attachSystemEffects(int sessionId) {
		releaseEffects();
		try {
			eq = new Equalizer(0, sessionId);
			eq.setEnabled(true);
		} catch (Throwable ignore) { eq = null; }
		try {
			bass = new BassBoost(0, sessionId);
			bass.setEnabled(true);
			if (bass.getStrengthSupported()) bass.setStrength((short) 0);
		} catch (Throwable ignore) { bass = null; }
		try {
			virt = new Virtualizer(0, sessionId);
			virt.setEnabled(true);
			if (virt.getStrengthSupported()) virt.setStrength((short) 0);
		} catch (Throwable ignore) { virt = null; }
		try {
			presetReverb = new PresetReverb(0, sessionId);
			presetReverb.setPreset(PresetReverb.PRESET_NONE);
			presetReverb.setEnabled(true);
		} catch (Throwable ignore) { presetReverb = null; }
		try {
			envReverb = new EnvironmentalReverb(0, sessionId);
			envReverb.setEnabled(true);
		} catch (Throwable ignore) { envReverb = null; }
		try {
			le = new LoudnessEnhancer(sessionId);
			le.setTargetGain(600);
			le.setEnabled(true);
		} catch (Throwable ignore) { le = null; }
		// === DynamicsProcessing: мягкий мастер-лимитер и pre-gain ===
		try { le = new LoudnessEnhancer(sessionId); le.setTargetGain(0); le.setEnabled(false); } catch (Throwable ignore) { le = null; }
		
		// ВКЛЮЧАЕМ мастер-лимитер/компрессию, если доступно на устройстве
		initDynamicsProcessingSafe(sessionId);
	}
	private void initDynamicsProcessingSafe(int sessionId) {
		// Работает только на Android 9+ (API 28)
		if (android.os.Build.VERSION.SDK_INT < 28) {
			dp = null;
			return;
		}
		try {
			// 1) Собираем конфиг: 2 канала, MBC на 4 полосы, лимитер включён.
			int channels = 2;
			android.media.audiofx.DynamicsProcessing.Config cfg =
			new android.media.audiofx.DynamicsProcessing.Config.Builder(
			android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
			channels,
			/*preEqInUse*/ false, 0,
			/*mbcInUse*/ false, 4,
			/*postEqInUse*/ false, 0,
			/*limiterInUse*/ false
			).build();
			
			dp = new android.media.audiofx.DynamicsProcessing(0, sessionId, cfg);
			
			// 2) Пытаемся сделать небольшой пред-гейн (-1.5 dB), через любые доступные методы.
			boolean gainApplied = false;
			try {
				// Метод 1: setInputGainAllChannelsTo(float)
				java.lang.reflect.Method mAll = dp.getClass().getMethod("setInputGainAllChannelsTo", float.class);
				mAll.invoke(dp, -1.5f);
				gainApplied = true;
			} catch (Throwable ignore1) { /* нет метода — пробуем по каналам */ }
			
			if (!gainApplied) {
				try {
					// Метод 2: setInputGainByChannelIndex(int,float)
					java.lang.reflect.Method mOne = dp.getClass().getMethod("setInputGainByChannelIndex", int.class, float.class);
					for (int ch = 0; ch < channels; ch++) {
						try { mOne.invoke(dp, ch, -1.5f); } catch (Throwable ignoreEach) {}
					}
				} catch (Throwable ignore2) { /* и этого может не быть — пропускаем */ }
			}
			
			// 3) Достаём лимитер (возможны разные API; пробуем оба пути)
			Object limiter = null;
			
			// Вариант A: dp.getLimiter()
			try {
				java.lang.reflect.Method mGetLim = dp.getClass().getMethod("getLimiter");
				limiter = mGetLim.invoke(dp);
			} catch (Throwable ignoreA) {
				// Вариант B: dp.getChannelByChannelIndex(0).getLimiter()
				try {
					java.lang.reflect.Method mGetCh = dp.getClass().getMethod("getChannelByChannelIndex", int.class);
					Object ch0 = mGetCh.invoke(dp, 0);
					if (ch0 != null) {
						java.lang.reflect.Method mGetLim2 = ch0.getClass().getMethod("getLimiter");
						limiter = mGetLim2.invoke(ch0);
					}
				} catch (Throwable ignoreB) { /* лимитер не доступен — ок */ }
			}
			
			if (limiter != null) {
				// Настраиваем лимитер мягко (ceiling ≈ -1 dBFS)
				try { limiter.getClass().getMethod("setAttackTime", int.class).invoke(limiter, 0); } catch (Throwable ignored) {}
				try { limiter.getClass().getMethod("setReleaseTime", int.class).invoke(limiter, 0); } catch (Throwable ignored) {}
				try { limiter.getClass().getMethod("setRatio", float.class).invoke(limiter, 0f); } catch (Throwable ignored) {}
				try { limiter.getClass().getMethod("setThreshold", float.class).invoke(limiter, 0f); } catch (Throwable ignored) {}
				try { limiter.getClass().getMethod("setPostGain", float.class).invoke(limiter, 0f); } catch (Throwable ignored) {}
				
				// Применяем настройки лимитера, если это требуется API (иногда достаточно просто модифицировать объект)
				boolean applied = false;
				try {
					java.lang.reflect.Method mSetLim = dp.getClass().getMethod(
					"setLimiter",
					Class.forName("android.media.audiofx.DynamicsProcessing$Limiter")
					);
					mSetLim.invoke(dp, limiter);
					applied = true;
				} catch (Throwable ignoreSetGlobal) { /* не у всех API есть */ }
				
				if (!applied) {
					try {
						java.lang.reflect.Method mGetCh = dp.getClass().getMethod("getChannelByChannelIndex", int.class);
						Object ch0 = mGetCh.invoke(dp, 0);
						if (ch0 != null) {
							Class<?> chCls = Class.forName("android.media.audiofx.DynamicsProcessing$Channel");
							Class<?> limCls = Class.forName("android.media.audiofx.DynamicsProcessing$Limiter");
							java.lang.reflect.Method mSetLim2 = chCls.getMethod("setLimiter", limCls);
							mSetLim2.invoke(ch0, limiter);
						}
					} catch (Throwable ignoreSetPerCh) { /* тоже опционально */ }
				}
			}
			
			try { dp.setEnabled(true); } catch (Throwable ignored) {}
		} catch (Throwable t) {
			// Любая несовместимость — тихо отключаем DP
			dp = null;
		}
	}
	
	private void applySavedFxToCurrentSession() {
		android.content.SharedPreferences p = getSharedPreferences(FX_PREFS, MODE_PRIVATE);
		try {
			if (le != null) {
				int lg = p.getInt("loudness_gain", 0);
				le.setTargetGain(lg);
				le.setEnabled(true);
			}
		} catch (Throwable ignored) {}
		
		try {
			if (bass != null) {
				short bs = (short) p.getInt("bass_strength", 0);
				if (bass.getStrengthSupported()) bass.setStrength(bs);
				bass.setEnabled(true);
			}
		} catch (Throwable ignored) {}
		
		try {
			if (virt != null) {
				short vs = (short) p.getInt("virt_strength", 0);
				if (virt.getStrengthSupported()) virt.setStrength(vs);
				virt.setEnabled(true);
			}
		} catch (Throwable ignored) {}
		
		try {
			if (eq != null) {
				boolean enabled = p.getBoolean("eq_enabled", false);
				eq.setEnabled(enabled);
				
				int preset = p.getInt("eq_preset", -1);
				if (preset >= 0) eq.usePreset((short) preset);
				
				try {
					short bands = eq.getNumberOfBands();
					for (short b = 0; b < bands; b++) {
						int lvl = p.getInt("eq_band_" + b, 0);
						eq.setBandLevel(b, (short) lvl);
					}
				} catch (Throwable ignoredBands) {}
			}
		} catch (Throwable ignored) {}
		
		try {
			if (dolbyEffect != null) {
				boolean dolbyOn = p.getBoolean("dolby_enabled", false);
				dolbyEffect.setEnabled(dolbyOn);
			}
		} catch (Throwable ignored) {}
	}
	
	private void enableSystemDolbyIfPresent(int sessionId) {
		disableSystemDolby();
		try {
			android.media.audiofx.AudioEffect.Descriptor[] descs =
			android.media.audiofx.AudioEffect.queryEffects();
			if (descs == null) return;
			
			for (android.media.audiofx.AudioEffect.Descriptor d : descs) {
				String impl = d.implementor != null ? d.implementor.toLowerCase() : "";
				String name = d.name != null ? d.name.toLowerCase() : "";
				String type = d.type != null ? d.type.toString().toLowerCase() : "";
				
				boolean looksLikeDolby =
				impl.contains("dolby") ||
				name.contains("dolby") ||
				type.contains("dolby");
				
				if (looksLikeDolby) {
					java.util.UUID typeUuid = d.type;
					java.util.UUID implUuid = d.uuid;
					
					try {
						java.lang.reflect.Constructor<android.media.audiofx.AudioEffect> ctor =
						android.media.audiofx.AudioEffect.class.getDeclaredConstructor(
						java.util.UUID.class, java.util.UUID.class, int.class, int.class
						);
						ctor.setAccessible(true);
						dolbyEffect = ctor.newInstance(typeUuid, implUuid, 0, sessionId);
						try { dolbyEffect.setEnabled(true); } catch (Throwable ignore) {}
					} catch (NoSuchMethodException e) {
						// пропускаем
					} catch (Throwable t) {
						// гасим
					}
					break;
				}
			}
		} catch (Throwable ignore) {
		}
	}
	
	private void disableSystemDolby() {
		if (dolbyEffect != null) {
			try { dolbyEffect.release(); } catch (Throwable ignore) {}
			dolbyEffect = null;
		}
	}
	
	// ================== PUBLIC SAFE API FOR SETTINGS ==================
	
	public boolean isDolbyEnabledSafe() {
		try { return dolbyEffect != null && dolbyEffect.getEnabled(); }
		catch (Throwable t) { return false; }
	}
	public void toggleDolbySafe(boolean enable) {
		try {
			if (dolbyEffect != null) dolbyEffect.setEnabled(enable);
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putBoolean("dolby_enabled", enable).apply();
		} catch (Throwable ignored) {}
	}
	
	public int getLoudnessGainSafe() {
		try { return (le != null) ? (int) le.getTargetGain() : 0; }
		catch (Throwable t) { return 0; }
	}
	public void setLoudnessGainSafe(int gainMilliBels) {
		try {
			if (le != null) { le.setTargetGain(gainMilliBels); le.setEnabled(true); }
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putInt("loudness_gain", gainMilliBels).apply();
		} catch (Throwable ignored) {}
	}
	
	public int getBassStrengthSafe() {
		try { return (bass != null) ? bass.getRoundedStrength() : 0; }
		catch (Throwable t) { return 0; }
	}
	public void setBassStrengthSafe(short strength) {
		try {
			if (bass != null && bass.getStrengthSupported()) { bass.setStrength(strength); bass.setEnabled(true); }
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putInt("bass_strength", strength).apply();
		} catch (Throwable ignored) {}
	}
	
	public int getVirtualizerStrengthSafe() {
		try { return (virt != null) ? virt.getRoundedStrength() : 0; }
		catch (Throwable t) { return 0; }
	}
	public void setVirtualizerStrengthSafe(short strength) {
		try {
			if (virt != null && virt.getStrengthSupported()) { virt.setStrength(strength); virt.setEnabled(true); }
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putInt("virt_strength", strength).apply();
		} catch (Throwable ignored) {}
	}
	
	public boolean isEqEnabledSafe() {
		try { return eq != null && eq.getEnabled(); }
		catch (Throwable t) { return false; }
	}
	public void setEqEnabledSafe(boolean enable) {
		try {
			if (eq != null) eq.setEnabled(enable);
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putBoolean("eq_enabled", enable).apply();
		} catch (Throwable ignored) {}
	}
	
	public String[] getEqPresetsSafe() {
		try {
			if (eq == null) return new String[0];
			short n = eq.getNumberOfPresets();
			String[] out = new String[n];
			for (short i = 0; i < n; i++) out[i] = eq.getPresetName(i);
			return out;
		} catch (Throwable t) { return new String[0]; }
	}
	
	public void useEqPresetSafe(int index) {
		try {
			if (eq != null) eq.usePreset((short) index);
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putInt("eq_preset", index).apply();
		} catch (Throwable ignored) {}
	}
	
	public short getEqNumberOfBandsSafe() {
		try { return (eq != null) ? eq.getNumberOfBands() : 0; }
		catch (Throwable t) { return 0; }
	}
	
	public int[] getEqBandLevelRangeSafe() {
		try {
			if (eq == null) return new int[]{-1500, 1500};
			short[] s = eq.getBandLevelRange();
			return new int[]{ (int) s[0], (int) s[1] };
		} catch (Throwable t) {
			return new int[]{-1500, 1500};
		}
	}
	
	public String getEqCenterFreqLabelSafe(short band) {
		try {
			if (eq == null) return "";
			int hz = eq.getCenterFreq(band) / 1000;
			return hz + " Hz";
		} catch (Throwable t) { return ""; }
	}
	
	public int getEqBandLevelSafe(short band) {
		try { return (eq != null) ? eq.getBandLevel(band) : 0; }
		catch (Throwable t) { return 0; }
	}
	
	public void setEqBandLevelSafe(short band, short level) {
		try {
			if (eq != null) eq.setBandLevel(band, level);
			getSharedPreferences(FX_PREFS, MODE_PRIVATE).edit()
			.putInt("eq_band_" + band, level).apply();
		} catch (Throwable ignored) {}
	}
}
