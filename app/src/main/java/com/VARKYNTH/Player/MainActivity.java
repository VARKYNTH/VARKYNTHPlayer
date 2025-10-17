package com.VARKYNTH.Player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.app.DownloadManager;
import android.database.Cursor;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.android.material.textview.MaterialTextView;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Format;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.AllId;

import android.transition.TransitionManager;
import android.transition.Fade;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.ContentUris;

import android.provider.MediaStore;
import android.provider.BaseColumns;
import android.content.IntentSender;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.content.ClipData;
import androidx.core.content.FileProvider;

import com.VARKYNTH.Player.ui.VGlobalDepth;

public class MainActivity extends AppCompatActivity {
	
	private Timer _timer = new Timer();
	
	private boolean mBound = false;
	private HashMap<String, Object> map = new HashMap<>();
	private String position = "";
	private String total = "";
	private String song_name = "";
	private String path = "";
	private String state = "";
	private double songPosition = 0;
	
	private String fontName = "";
	private String typeace = "";
	
	private static final String PREF_LANGUAGE = "app_language";
	
	private ArrayList<HashMap<String, Object>> song_list = new ArrayList<>();
	
	private SharedPreferences SynMusic;
	private Intent SynIntent = new Intent();
	
	private ExecutorService artExecutor = Executors.newFixedThreadPool(2);
	private LruCache<Long, Bitmap> artCache;
	
	private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permission ->{ 
		
		boolean allGranted = true; for (Boolean isGranted : permission.values()){ 
			if (!isGranted){ 
				allGranted = false; break; 
			} } 
		if (allGranted){ 
			// All is granted 
			getAllSongs();
		} 
		else { 
			// All is not granted 
		} });
	
	private int repeatMode = 0;
	private boolean shuffleMode = false;
	private AlertDialog SynDialog;
	private TimerTask SynTimer;
	
	private AllId.MainViewId v;
	
	private AudioManager audioManager;
	
	private SharedPreferences prefs;
	// поле класса
	private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;
	
	private int pendingDeletePos = -1;
	private int pendingSharePos = -1;
	
	private boolean depthApplied = false;
	
	private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		int savedMode = getSharedPreferences("settings", MODE_PRIVATE).getInt("theme_mode", 0);
		VARTHSettingsActivity.applyTheme(savedMode);
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.main);
		
		VARTHUpdate.startUpdateCheck(this);
		
		int maxMem = (int) (Runtime.getRuntime().maxMemory() / 1024);
		int cacheSize = maxMem / 16; // ~6% памяти под мини-обложки
		artCache = new LruCache<Long, Bitmap>(cacheSize) {
			@Override protected int sizeOf(Long key, Bitmap value) {
				return value.getByteCount() / 1024;
			}
		};
		// в onCreate(), после setContentView(...)
		deleteRequestLauncher = registerForActivityResult(
		new ActivityResultContracts.StartIntentSenderForResult(),
		result -> {
			// Если нужно — перезагрузи список/уведомь адаптер
			v.music_view.getAdapter().notifyDataSetChanged();
		}
		);
		
		prefs = getSharedPreferences("vplayer_prefs", MODE_PRIVATE);
		
		applyDepthFromPrefs();
		
		// Живое обновление при смене настройки в SettingsActivity
		prefListener = (sp, key) -> {
			if ("global_depth_enabled".equals(key)) applyDepthFromPrefs();
		};
		prefs.registerOnSharedPreferenceChangeListener(prefListener);
		
		VFont.boldAll(this, findViewById(android.R.id.content));
		
		v = AllId.MainViewId.bind(this);
		
		loadLocale();
		
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		DynamicColors.applyToActivityIfAvailable(this); int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
		
		SynMusic = getSharedPreferences("SynMusic", Activity.MODE_PRIVATE);
		
		v.click_dialogs.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynD_C();
			}
		});
		v.slider_music.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
			@Override
			public void onStartTrackingTouch(Slider slider) {
				// ничего не делаем
			}
			
			@Override
			public void onStopTrackingTouch(Slider slider) {
				if (mBound && mBoundService != null) {
					long target = (long) v.slider_music.getValue(); // Slider
					mBoundService.seekToMs(target);
				}
			}
		});
		
		v.slider_music.addOnChangeListener((slider, value, fromUser) -> {
			if (fromUser) {
				getMusicTime(v.timestart, (long) value);
			}
		});
		
		v.player_card.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				v.player_root.setVisibility(View.VISIBLE);
				v.content_root.setVisibility(View.GONE);
				v.topbarmain.setVisibility(View.GONE);
			}
		});
		
		v.ic_click_fx.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHFxActivity.class);
				startActivity(SynIntent);
			}
		});
		
		v.info_audio.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				new VARTHAudioInfoDialog(MainActivity.this).show();
			}
		});
		
		v.ic_click_repeat.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				repeatMode = (repeatMode + 1) % 3;
				switch (repeatMode) {
					case 0:
					v.ic_click_repeat.setImageResource(R.drawable.ic_repeat_off);
					break;
					case 1:
					v.ic_click_repeat.setImageResource(R.drawable.ic_repeat_one);
					break;
					case 2:
					v.ic_click_repeat.setImageResource(R.drawable.ic_repeat_on);
					break;
				}
				Intent SynIntent = new Intent(getApplicationContext(), VARTHMusicService.class);
				SynIntent.setAction("REPEAT");
				startService(SynIntent);
			}
		});
		
		v.ic_click_shuffle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				shuffleMode = !shuffleMode;
				if (shuffleMode) {
					v.ic_click_shuffle.setImageResource(R.drawable.ic_shuffle);
				} else {
					v.ic_click_shuffle.setImageResource(R.drawable.ic_shuffle_off);
				}
				Intent SynIntent = new Intent(getApplicationContext(), VARTHMusicService.class);
				SynIntent.setAction("SHUFFLE");
				startService(SynIntent);
				
			}
		});
		
		v.click_prev.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
				SynIntent.setAction("PRE");
				startService(SynIntent);
			}
		});
		
		v.click_play.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
				SynIntent.setAction("TOGGLE");
				startService(SynIntent);
			}
		});
		
		v.click_next.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
				SynIntent.setAction("NEXT");
				startService(SynIntent);
			}
		});
		
		v.swipe_refresh_music_list.setOnRefreshListener(() -> {
			getAllSongs();
		});
		
		registerReceiver(SongDataReceiver,new IntentFilter("com.dplay.SONG_DATA"), RECEIVER_EXPORTED);
		registerReceiver(SongStateReceiver,new IntentFilter("com.dplay.SONG_STATE"), RECEIVER_EXPORTED);
		SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
		startService(SynIntent);
		bindService(SynIntent,mConnection,Context.BIND_AUTO_CREATE);
		con = this;
		v.music_view.setLayoutManager(new LinearLayoutManager(this));
		v.music_view.setAdapter(new Recyclerview1Adapter(song_list));
		v.music_view.setHasFixedSize(true);
		v.music_view.setItemViewCacheSize(20);
		togglePlayerView(false);
		myPermissions();
		getAllSongs();
		SynStyle();
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
	
	private int dp(int v) {
		float d = getResources().getDisplayMetrics().density;
		return Math.round(v * d);
	}
	
	private void loadAlbumArtAsync(ImageView target, long albumId) {
		if (albumId <= 0) {
			target.setImageResource(R.drawable.ic_logo);
			return;
		}
		target.setTag(albumId);
		
		Bitmap cached = artCache.get(albumId);
		if (cached != null) {
			target.setImageBitmap(cached);
			return;
		}
		
		// placeholder сразу, чтобы не было «пустых» иконок
		target.setImageResource(R.drawable.ic_logo);
		
		artExecutor.submit(() -> {
			try {
				Uri base = Uri.parse("content://media/external/audio/albumart");
				Uri uri = android.content.ContentUris.withAppendedId(base, albumId);
				
				// пробуем прочитать и даунсемплить
				BitmapFactory.Options bounds = new BitmapFactory.Options();
				InputStream is1 = getContentResolver().openInputStream(uri);
				if (is1 == null) return;
				bounds.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(is1, null, bounds);
				is1.close();
				
				int req = dp(56); // целевой размер иконки ~56dp
				int sample = 1;
				while ((bounds.outWidth / sample) > req * 2 || (bounds.outHeight / sample) > req * 2) {
					sample *= 2;
				}
				
				BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inSampleSize = sample;
				InputStream is2 = getContentResolver().openInputStream(uri);
				if (is2 == null) return;
				Bitmap bmp = BitmapFactory.decodeStream(is2, null, opts);
				is2.close();
				
				if (bmp != null) {
					artCache.put(albumId, bmp);
					runOnUiThread(() -> {
						Object tag = target.getTag();
						if (tag instanceof Long && ((Long) tag) == albumId) {
							target.setImageBitmap(bmp);
						}
					});
				}
			} catch (Exception ignore) {
				// нет обложки — оставим placeholder
			}
		});
	}
	
	/** Удалить по MediaStore _ID (предпочтительно). */
	private void deleteTrackById(long audioId, int adapterPositionOrMinusOne) {
		Uri itemUri = ContentUris.withAppendedId(
		MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId);
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				ArrayList<Uri> uris = new ArrayList<>();
				uris.add(itemUri);
				IntentSender sender = MediaStore.createDeleteRequest(
				getContentResolver(), uris).getIntentSender();
				deleteRequestLauncher.launch(
				new IntentSenderRequest.Builder(sender).build());
			} else {
				int rows = getContentResolver().delete(itemUri, null, null);
				if (rows > 0) {
					if (adapterPositionOrMinusOne >= 0 &&
					adapterPositionOrMinusOne < song_list.size()) {
						song_list.remove(adapterPositionOrMinusOne);
						v.music_view.getAdapter().notifyItemRemoved(adapterPositionOrMinusOne);
					} else {
						v.music_view.getAdapter().notifyDataSetChanged();
					}
				}
			}
		} catch (Exception ignored) {}
	}
	
	/** Если у тебя только абсолютный путь к файлу. */
	private void deleteTrackByPath(String absolutePath, int adapterPositionOrMinusOne) {
		String[] projection = { BaseColumns._ID };
		try (Cursor c = getContentResolver().query(
		MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
		projection,
		MediaStore.Audio.Media.DATA + " = ?",
		new String[]{ absolutePath },
		null)) {
			if (c != null && c.moveToFirst()) {
				long id = c.getLong(0);
				deleteTrackById(id, adapterPositionOrMinusOne);
			} else {
				java.io.File f = new java.io.File(absolutePath);
				if (f.exists() && f.delete()) {
					if (adapterPositionOrMinusOne >= 0 &&
					adapterPositionOrMinusOne < song_list.size()) {
						song_list.remove(adapterPositionOrMinusOne);
						v.music_view.getAdapter().notifyItemRemoved(adapterPositionOrMinusOne);
					} else {
						v.music_view.getAdapter().notifyDataSetChanged();
					}
					sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
				}
			}
		} catch (Exception ignored) {}
	}
	
	private void togglePlayerView(boolean showPlayer) {
		ViewGroup root = findViewById(android.R.id.content);
		TransitionManager.beginDelayedTransition(root, new Fade());
		v.player_root.setVisibility(showPlayer ? View.VISIBLE : View.GONE);
		v.content_root.setVisibility(showPlayer ? View.GONE : View.VISIBLE);
		v.topbarmain.setVisibility(showPlayer ? View.GONE : View.VISIBLE);
	}
	
	private void loadLocale() {
		SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
		String language = sharedPreferences.getString(PREF_LANGUAGE, "en");
		setLocale(language);
	}
	
	private void setLocale(String lang) {
		Locale locale = new Locale(lang);
		Locale.setDefault(locale);
		Configuration config = new Configuration();
		config.setLocale(locale);
		getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
	}
	
	@Override
	public void onBackPressed() {
		if (v.player_root.getVisibility() == View.VISIBLE) {
			togglePlayerView(false);
		} else {
			togglePlayerView(true);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		applyDepthFromPrefs();
		restoreTrackInfo();
		if (SynMusic.getString("p", "").contains("pause")) {
			v.ic_play_click.setImageResource(R.drawable.ic_play);
		} else {
			v.ic_play_click.setImageResource(R.drawable.ic_pause);
		}
		if (SynMusic.getString("path", "").equals("")) {
			
		} else {
			v.duration_music.setText(SynMusic.getString("path", ""));
			v.duration_music.setSingleLine(true);
			v.duration_music.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			v.duration_music.setSelected(true);
			v.name_duration_player.setText(SynMusic.getString("path", ""));
			v.name_duration_player.setSingleLine(true);
			v.name_duration_player.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			v.name_duration_player.setSelected(true);
		}
		Set<String> set = SynMusic.getStringSet("paths", new HashSet<>());
		ServiceBind_Start();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if (!mBound) {
			ServiceBind_Start();
		}
		restoreTrackInfo();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
		if (depthApplied) {
			VGlobalDepth.detach();
			depthApplied = false;
		}
		Intent stopIntent = new Intent(MainActivity.this, VARTHMusicService.class);
		stopIntent.setAction("STOP");
		startService(stopIntent);
	}
	public void reflesh() {
		if (v.music_view.getAdapter() != null) v.music_view.getAdapter().notifyDataSetChanged();
	}
	
	private void saveTrackInfo(String title, String artist, String path, long duration, int position) {
		SharedPreferences.Editor e = SynMusic.edit();
		e.putString("title", title);
		e.putString("artist", artist);
		e.putString("path", path);
		e.putLong("duration", duration);
		e.putInt("position", position);
		e.apply();
	}
	
	private void restoreTrackInfo() {
		String title = SynMusic.getString("title", "");
		String artist = SynMusic.getString("artist", "");
		String path = SynMusic.getString("path", "");
		long duration = SynMusic.getLong("duration", 0);
		int position = SynMusic.getInt("position", -1);
		if (!path.isEmpty()) {
			v.name_music.setText(title);
			v.duration_music.setText(artist);
			if (duration > 0) {
				v.slider_music.setValueFrom(0f);
				v.slider_music.setValueTo(duration);
				// нет currentMs в onResume — не трогаем value, или можешь поставить 0f:
				// v.slider_music.setValue(0f);
			}
			songPosition = position;
		}
	}
	
	public void getMusicTime(final TextView _text, final double _time) {
		
		StringBuilder mFormatBuilder = null;
		Formatter mFormatter = null;
		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
		int totalSeconds = (int) _time / 1000;
		int seconds = totalSeconds % 60;
		int minutes = (totalSeconds / 60) % 60;
		int hours = totalSeconds / 3600;
		
		mFormatBuilder.setLength(0);
		if (hours > 0) {
			_text.setText(mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString());
		}
		else {
			_text.setText(mFormatter.format("%02d:%02d", minutes, seconds).toString());
		}
	}
	
	public void servisi_kontrol() {
		mBound = false;
	}
	private static VARTHMusicService mBoundService;
	static Context con;
	{
	}
	private android.content.ServiceConnection mConnection = new android.content.ServiceConnection() {
		public void onServiceConnected(ComponentName className, android.os.IBinder service) {
			mBoundService = ((VARTHMusicService.MusicBinder)service).getService();
			mBound =true;
		}
		public void onServiceDisconnected(ComponentName className) {
			mBoundService = null;
			mBound = false;
		}
	};
	{
	}
	
	public void ServiceBind_Start() {
		if (mBound == false) {
			if (mBoundService == null) {
				SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
				SynIntent.putExtra("STATUS", "START_SERVICE");
				startService(SynIntent);
				bindService(SynIntent,mConnection,Context.BIND_AUTO_CREATE);
				con = this;
			}
		}
	}
	
	private void myPermissions(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){ String[] permissions = new String[]{ 
				android.Manifest.permission.READ_MEDIA_AUDIO, 
				android.Manifest.permission.POST_NOTIFICATIONS,
				android.Manifest.permission.RECORD_AUDIO,
			};
			List<String> permissionsTORequest = new ArrayList<>(); 
			for (String permission : permissions){ 
				if (ContextCompat.checkSelfPermission(this,permission) != PackageManager.PERMISSION_GRANTED){ 
					
					permissionsTORequest.add(permission); 
				} } 
			if (permissionsTORequest.isEmpty()){ 
				// All permissions are already granted
			} else { 
				String[] permissionsArray = permissionsTORequest.toArray(new String[0]); 
				boolean shouldShowRationale = false; for (String permission : permissionsArray){ 
					if (shouldShowRequestPermissionRationale(permission)){ 
						shouldShowRationale = true; break; 
					} } 
				if (shouldShowRationale){ 
					new AlertDialog.Builder(this) .setMessage("Please allow all permissions") .setCancelable(false) .setPositiveButton("YES", new DialogInterface.OnClickListener() { 
						
						@Override public void onClick(DialogInterface dialogInterface, int i) {
							requestPermissionLauncher.launch(permissionsArray); 
						} }) 
					.setNegativeButton("NO", new DialogInterface.OnClickListener() { 
						
						@Override public void onClick(DialogInterface dialogInterface, int i) { 
							
							dialogInterface.dismiss(); 
						} }) 
					
					.show(); 
					
				} else { 
					
					requestPermissionLauncher.launch(permissionsArray); 
				} 
			}
			
		} else 
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { String[] permissions = new String[]{ 
				android.Manifest.permission.READ_EXTERNAL_STORAGE, }; 
			List<String> permissionsTORequest = new ArrayList<>(); for (String permission : permissions){ 
				if (ContextCompat.checkSelfPermission(this,permission) != PackageManager.PERMISSION_GRANTED){ 
					permissionsTORequest.add(permission); 
				} } 
			if (permissionsTORequest.isEmpty()){ 
				// All permissions are already granted
			} else { 
				String[] permissionsArray = permissionsTORequest.toArray(new String[0]); boolean shouldShowRationale = false; for (String permission : permissionsArray){ 
					if (shouldShowRequestPermissionRationale(permission)){ 
						shouldShowRationale = true; break; 
					} }
				if (shouldShowRationale){ 
					new AlertDialog.Builder(this) .setMessage("Please allow all permissions") .setCancelable(false) .setPositiveButton("YES", new DialogInterface.OnClickListener() { 
						@Override public void onClick(DialogInterface dialogInterface, int i) { 
							requestPermissionLauncher.launch(permissionsArray); 
						} }) .setNegativeButton("NO", new DialogInterface.OnClickListener() { 
						@Override public void onClick(DialogInterface dialogInterface, int i) { 
							dialogInterface.dismiss();
						} }) .show(); 
				} else { requestPermissionLauncher.launch(permissionsArray); 
				} 
			} 
		} 
	} // myPermissions end here
	{
	}
	
	public void getAllSongs() {
		song_list.clear();
		map.clear();
		
		String[] projection = {
			android.provider.MediaStore.Audio.Media._ID,
			android.provider.MediaStore.Audio.Media.ALBUM,
			android.provider.MediaStore.Audio.Media.ALBUM_ID,
			android.provider.MediaStore.Audio.Media.ALBUM_KEY,
			android.provider.MediaStore.Audio.Media.ARTIST,
			android.provider.MediaStore.Audio.Media.DATA,
			android.provider.MediaStore.Audio.Media.TITLE,
			android.provider.MediaStore.Audio.Media.DURATION,
			// На новых API может помочь RELATIVE_PATH, но он бывает null
			android.provider.MediaStore.Audio.Media.RELATIVE_PATH
		};
		
		// Сортировка — последние добавленные
		String sortOrder = android.provider.MediaStore.Audio.Media.DATE_ADDED + " DESC";
		
		String selection;
		String[] selArgs;
		
		// Базовый абсолютный путь к Music на основном хранилище
		String musicAbs = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music/%";
		
		if (android.os.Build.VERSION.SDK_INT >= 29) {
			// Только музыка, длительностью >= 20 сек, и ТОЛЬКО каталог Music/
			// Учитываем случаи, когда RELATIVE_PATH == null (тогда страхуемся по DATA)
			selection =
			android.provider.MediaStore.Audio.Media.IS_MUSIC + "!= 0" +
			" AND " + android.provider.MediaStore.Audio.Media.DURATION + ">= ?" +
			" AND ( " +
			android.provider.MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ? " +
			" OR (" + android.provider.MediaStore.Audio.Media.RELATIVE_PATH + " IS NULL AND " +
			android.provider.MediaStore.Audio.Media.DATA + " LIKE ?)" +
			" )";
			selArgs = new String[]{ "10000", "Music/%", musicAbs };
		} else {
			// Старые API — фильтр строго по абсолютному пути
			selection =
			android.provider.MediaStore.Audio.Media.IS_MUSIC + "!= 0" +
			" AND " + android.provider.MediaStore.Audio.Media.DURATION + ">= ?" +
			" AND " + android.provider.MediaStore.Audio.Media.DATA + " LIKE ?";
			selArgs = new String[]{ "10000", musicAbs };
		}
		
		android.database.Cursor cursor = getContentResolver().query(
		android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
		projection,
		selection,
		selArgs,
		sortOrder
		);
		
		if (cursor != null) {
			int IdIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID);
			int titleIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE);
			int artistIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST);
			int albumIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM);
			int albumIdIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID);
			int dataIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA);
			int durationIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION);
			
			while (cursor.moveToNext()) {
				long Id = cursor.getLong(IdIdx);
				String title = cursor.getString(titleIdx);
				String artist = cursor.getString(artistIdx);
				String album = cursor.getString(albumIdx);
				long albumId = cursor.getLong(albumIdIdx);
				path = cursor.getString(dataIdx);
				long duration = cursor.getLong(durationIdx);
				
				// Доп. страховка: вдруг в медиатеке затесалось лишнее
				if (duration >= 10_000L && path != null && (path.startsWith(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music/"))) {
					java.util.HashMap<String, Object> map = new java.util.HashMap<>();
					map.put("Id", Id);
					map.put("title", title);
					map.put("artist", artist);
					map.put("albumId", albumId);
					map.put("path", path);
					map.put("time", duration);
					Uri albumArtUri = Uri.parse("content://media/external/audio/albumart");
					Uri albumUri = ContentUris.withAppendedId(albumArtUri, albumId);
					map.put("albumArt", albumUri.toString());
					
					if (title == null || title.isEmpty()) {
						String fileName = new java.io.File(path).getName();
						if (fileName.contains(".")) {
							title = fileName.substring(0, fileName.lastIndexOf('.'));
						} else {
							title = fileName;
						}
					}
					
					if (artist == null || artist.isEmpty()) {
						artist = "Unknown Artist";
					}
					
					song_list.add(map);
				}
			}
			cursor.close();
		}
		reflesh();
		if (v.swipe_refresh_music_list.isRefreshing()) {
			v.swipe_refresh_music_list.setRefreshing(false);
		}
	}
	
	public void SynStyle() {
		int color = Color.parseColor("#fa4242");
		v.slider_music.setThumbTintList(ColorStateList.valueOf(color));
		v.slider_music.setTrackActiveTintList(ColorStateList.valueOf(color));
		v.slider_music.setTrackInactiveTintList(ColorStateList.valueOf(color));
		
		if (song_list != null && !song_list.isEmpty()) {
			int pos = (int) songPosition; // текущая позиция
			if (pos >= 0 && pos < song_list.size()) {
				Object aIdObj = song_list.get(pos).get("albumId");
				long albumIdVal = 0L;
				if (aIdObj instanceof Number) albumIdVal = ((Number) aIdObj).longValue();
				
				v.ic_album_player.setImageResource(R.drawable.ic_logo); // placeholder
				loadAlbumArtAsync(v.ic_album_player, albumIdVal);
				v.ic_lyi.setImageResource(R.drawable.ic_logo); // placeholder
				loadAlbumArtAsync(v.ic_lyi, albumIdVal);
			}
		}
	}
	
	public void SynD_C() {
		SynDialog = new AlertDialog.Builder(MainActivity.this).create();
		LayoutInflater SynDialogLI = getLayoutInflater();
		View SynDialogCV = (View) SynDialogLI.inflate(R.layout.setting_syn_cast, null);
		SynDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		SynDialog.setView(SynDialogCV);
		final LinearLayout l1 = (LinearLayout)
		SynDialogCV.findViewById(R.id.l1);
		final CardView c1 = (CardView)
		SynDialogCV.findViewById(R.id.c1);
		final CardView c2 = (CardView)
		SynDialogCV.findViewById(R.id.c2);
		final CardView c3 = (CardView)
		SynDialogCV.findViewById(R.id.c3);
		final CardView c4 = (CardView)
		SynDialogCV.findViewById(R.id.c4);
		final MaterialTextView t1 = (MaterialTextView)
		SynDialogCV.findViewById(R.id.t1);
		final MaterialTextView t2 = (MaterialTextView)
		SynDialogCV.findViewById(R.id.t2);
		final MaterialTextView t3 = (MaterialTextView)
		SynDialogCV.findViewById(R.id.t3);
		final MaterialTextView t4 = (MaterialTextView)
		SynDialogCV.findViewById(R.id.t4);
		SynDialog.getWindow().setBackgroundDrawable(new ColorDrawable(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorSurface)));
		c2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHSettingsActivity.class);
				startActivity(SynIntent);
				SynDialog.dismiss();
			}
		});
		c3.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHDonateActivity.class);
				startActivity(SynIntent);
				SynDialog.dismiss();
			}
		});
		c4.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				SynIntent.setClass(getApplicationContext(), VARTHAboutActivity.class);
				startActivity(SynIntent);
				SynDialog.dismiss();
			}
		});
		SynDialog.show();
	}
	
	public void SynD_C_List() {
		SynDialog = new AlertDialog.Builder(MainActivity.this).create();
		LayoutInflater SynDialogLI = getLayoutInflater();
		View SynDialogCV = (View) SynDialogLI.inflate(R.layout.list_view_settings, null);
		SynDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		SynDialog.setView(SynDialogCV);
		final CardView c1 = (CardView)
		SynDialogCV.findViewById(R.id.c1);
		final CardView c2 = (CardView)
		SynDialogCV.findViewById(R.id.c2);
		SynDialog.getWindow().setBackgroundDrawable(new ColorDrawable(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorSurface)));
		c1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				onConfirmDeleteFromDialog();
				getAllSongs();
			}
		});
		c2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				onShareFromDialog();
			}
		});
		SynDialog.show();
	}
	
	public void onShareFromDialog() {
		if (pendingSharePos < 0 || pendingSharePos >= song_list.size()) return;
		HashMap<String, Object> item = song_list.get(pendingSharePos);
		if (item.containsKey("Id")) {
			shareTrackById(((Number) item.get("Id")).longValue(), String.valueOf(item.get("title")));
		} else {
			shareTrackByPath(String.valueOf(item.get("path")), String.valueOf(item.get("title")));
		}
		pendingSharePos = -1;
	}
	
	private void shareTrackById(long audioId, String titleForChooser) {
		try {
			Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId);
			
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("audio/*");
			send.putExtra(Intent.EXTRA_STREAM, contentUri);
			send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			
			// на всякий случай — ClipData для корректного грaнта
			send.setClipData(ClipData.newRawUri("audio", contentUri));
			
			startActivity(Intent.createChooser(send, titleForChooser != null ? titleForChooser : "Share audio"));
		} catch (Exception ignored) {}
	}
	
	private void shareTrackByPath(String absolutePath, String titleForChooser) {
		// 1) пытаемся найти _ID по пути и делегируем в shareTrackById(...)
		String[] projection = { BaseColumns._ID };
		try (Cursor c = getContentResolver().query(
		MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
		projection,
		MediaStore.Audio.Media.DATA + " = ?",
		new String[]{ absolutePath },
		null)) {
			
			if (c != null && c.moveToFirst()) {
				long id = c.getLong(0);
				shareTrackById(id, titleForChooser);
				return;
			}
		} catch (Exception ignored) {}
		
		// 2) fallback: FileProvider (нужен provider в манифесте)
		try {
			java.io.File f = new java.io.File(absolutePath);
			Uri uri = FileProvider.getUriForFile(
			this,
			getPackageName() + ".fileprovider", // см. примечание ниже
			f
			);
			
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("audio/*");
			send.putExtra(Intent.EXTRA_STREAM, uri);
			send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			send.setClipData(ClipData.newRawUri("audio", uri));
			
			startActivity(Intent.createChooser(send, titleForChooser != null ? titleForChooser : "Share audio"));
		} catch (Exception ignored) {}
	}
	
	/** Вызывай из своего кастомного диалога, передавая позицию элемента. */
	public void onConfirmDeleteFromDialog() {
		if (pendingDeletePos < 0 || pendingDeletePos >= song_list.size()) {
			pendingDeletePos = -1;
			return;
		}
		HashMap<String, Object> item = song_list.get(pendingDeletePos);
		if (item.containsKey("Id")) {
			long audioId = ((Number) item.get("Id")).longValue();
			deleteTrackById(audioId, pendingDeletePos);
		} else {
			String p = String.valueOf(item.get("path"));
			deleteTrackByPath(p, pendingDeletePos);
		}
		pendingDeletePos = -1;
	}
	
	public void recive() {
	}
	// ==== Utils ====
	private static String safeGetString(Intent i, String key) {
		String v = i != null ? i.getStringExtra(key) : null;
		return v != null ? v : "";
	}
	private static int getIntOrStringAsInt(Intent i, String key, int def) {
		if (i == null || i.getExtras() == null) return def;
		Object o = i.getExtras().get(key);
		if (o instanceof Integer) return (Integer) o;
		if (o instanceof Long)    return (int) (long) (Long) o;
		if (o instanceof String) {
			String s = ((String) o).trim();
			if (!s.isEmpty()) {
				try { return (int) Math.round(Double.parseDouble(s)); }
				catch (NumberFormatException ignore) {}
			}
		}
		return def;
	}
	
	// ==== Receiver: song data ====
	private BroadcastReceiver SongDataReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			int totalMs   = getIntOrStringAsInt(intent, "total_duration", 0);
			int currentMs = getIntOrStringAsInt(intent, "current_duration", 0);
			int pos       = getIntOrStringAsInt(intent, "pos", -1);
			
			song_name = safeGetString(intent, "song");
			path      = safeGetString(intent, "path");
			songPosition = pos; // если нужно double: songPosition = (double) pos;
			
			if (totalMs > 0) {
				v.slider_music.setValueFrom(0f);
				v.slider_music.setValueTo(totalMs);
				v.slider_music.setValue(currentMs);
			}
			
			getMusicTime(v.timestart, currentMs);
			getMusicTime(v.timeoff,  totalMs);
			
			v.name_music.setText(song_name);
			v.name_music_player.setText(song_name);
			
			SynMusic.edit().putString("path", path).apply();
			
			// Сохраняем все поля трека (подтягиваем из списка, если есть)
			String artist = "Unknown Artist";
			long duration = totalMs;
			if (pos >= 0 && pos < song_list.size()) {
				HashMap<String, Object> m = song_list.get(pos);
				if (m.get("artist") != null) artist = String.valueOf(m.get("artist"));
				if (m.get("time") != null) duration = (long) ( (Number) m.get("time") ).longValue();
			}
			saveTrackInfo(song_name, artist, path, duration, pos);
		}
	};
	
	// ==== Receiver: play/pause state ====
	private BroadcastReceiver SongStateReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			String s = safeGetString(intent, "responce");
			if ("pause".equalsIgnoreCase(s)) {
				v.ic_play_click.setImageResource(R.drawable.ic_play);
				SynMusic.edit().putString("p", "pause").apply();
				return;
			}
			if ("play".equalsIgnoreCase(s)) {
				SynMusic.edit().putString("p", "play").apply();
				v.ic_play_click.setImageResource(R.drawable.ic_pause);
				
				SynTimer = new TimerTask() {
					@Override public void run() {
						runOnUiThread(() -> {
							String pth = SynMusic.getString("path", "");
							v.duration_music.setText(pth);
							v.duration_music.setSingleLine(true);
							v.duration_music.setEllipsize(TextUtils.TruncateAt.MARQUEE);
							v.duration_music.setSelected(true);
							
							v.name_duration_player.setText(pth);
							v.name_duration_player.setSingleLine(true);
							v.name_duration_player.setEllipsize(TextUtils.TruncateAt.MARQUEE);
							v.name_duration_player.setSelected(true);
							
							reflesh();
						});
					}
				};
				_timer.schedule(SynTimer, 80);
			}
		}
	};
	
	public class Recyclerview1Adapter extends RecyclerView.Adapter<Recyclerview1Adapter.ViewHolder> {
		ArrayList<HashMap<String, Object>> _data;
		public Recyclerview1Adapter(ArrayList<HashMap<String, Object>> _arr) {
			_data = _arr;
		}
		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater _inflater = getLayoutInflater();
			View _v = _inflater.inflate(R.layout.list_music_view, null);
			RecyclerView.LayoutParams _lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			_v.setLayoutParams(_lp);
			return new ViewHolder(_v);
		}
		@Override
		public void onBindViewHolder(ViewHolder _holder, final int _position) {
			View _view = _holder.itemView;
			
			final com.google.android.material.card.MaterialCardView cardview1 = _view.findViewById(R.id.cardview1);
			final LinearLayout linear1 = _view.findViewById(R.id.linear1);
			final com.google.android.material.card.MaterialCardView cardimg = _view.findViewById(R.id.cardimg);
			final LinearLayout linear4 = _view.findViewById(R.id.linear4);
			final ImageView imageview1 = _view.findViewById(R.id.imageview1);
			final LinearLayout linear2 = _view.findViewById(R.id.linear2);
			final LinearLayout linear3 = _view.findViewById(R.id.linear3);
			final com.google.android.material.textview.MaterialTextView textview1 = _view.findViewById(R.id.textview1);
			final com.google.android.material.textview.MaterialTextView textview4 = _view.findViewById(R.id.textview4);
			final com.google.android.material.textview.MaterialTextView textview2 = _view.findViewById(R.id.textview2);
			
			HashMap<String, Object> item = _data.get(_position);
			
			textview1.setText(String.valueOf(item.get("title")));
			textview2.setText(String.valueOf(item.get("artist")));
			getMusicTime(textview4, Double.parseDouble(String.valueOf(item.get("time"))));
			
			long albumIdVal = 0L;
			Object aIdObj = _data.get(_position).get("albumId");
			if (aIdObj instanceof Number) albumIdVal = ((Number) aIdObj).longValue();
			imageview1.setImageResource(R.drawable.ic_logo); // placeholder сразу
			loadAlbumArtAsync(imageview1, albumIdVal);
			
			if ((songPosition == _position) && String.valueOf(item.get("title")).equals(song_name)) {
				textview1.setTextColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorErrorContainer));
				textview2.setTextColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorOnSurfaceVariant));
				textview4.setTextColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorOnSurfaceVariant));
				cardview1.setCardBackgroundColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorOnSurfaceInverse));
				cardimg.setCardBackgroundColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorSurfaceVariant));
				cardview1.setCardElevation((float)25);
			} else {
				textview1.setTextColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorOnSurface));
				textview2.setTextColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorOnSurfaceVariant));
				textview4.setTextColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorOnSurfaceVariant));
				cardview1.setCardBackgroundColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorSurface));
				cardimg.setCardBackgroundColor(SketchwareUtil.getMaterialColor(MainActivity.this, R.attr.colorSurfaceVariant));
				cardview1.setCardElevation((float)10);
			}
			linear1.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View _view) {
					if (mBound) {
						SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
						
						mBoundService.setList(song_list);
						mBoundService.setPosition((int)_position);
						SynIntent.setAction("START");
						startService(SynIntent);
						
						saveTrackInfo(
						String.valueOf(item.get("title")),
						String.valueOf(item.get("artist")),
						String.valueOf(item.get("path")),
						((Number) item.get("time")).longValue(),
						_position
						);
					}
					togglePlayerView(true);
				}
			});
			linear1.setOnLongClickListener(vLong -> {
				SynD_C_List();
				pendingDeletePos = _position;
				pendingSharePos = _position;
				return true; // long-click consumed
			});
		}
		
		@Override
		public int getItemCount() {
			return _data.size();
		}
		
		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}
	}
}
