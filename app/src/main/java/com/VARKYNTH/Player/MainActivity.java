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
	
	// URL JSON с информацией об обновлении
	private static final String UPDATE_JSON =
	"https://raw.githubusercontent.com/VARKYNTH/VARKYNTHPlayer/main/UpdateApk.json";
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.main);
        
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
		v.player_root.setVisibility(View.GONE);
		v.content_root.setVisibility(View.VISIBLE);
		v.topbarmain.setVisibility(View.VISIBLE);
		myPermissions();
		getAllSongs();
		startUpdateCheck();
        SynStyle();
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
	
	/**
* Тихая проверка: если апдейт есть — показываем ТОЛЬКО твой диалог (разметка dialog_update.xml).
* Если апдейта нет или произошла ошибка — НИЧЕГО не показываем и не меняем UI.
*/	
	private void startUpdateCheck() {
		new Thread(() -> {
			try {
				Info info = fetchInfo(); // твой метод
				if (info == null) return;
				
				int current = getCurrentVersionCode(MainActivity.this);
				boolean newer = info.versionCode > current;
				
				if (newer) {
					runOnUiThread(() -> showUpdateDialogFromXml(info));
				}
				// если не новее — ничего не делаем и молчим
			} catch (Throwable ignored) {
				// тихий режим: не трогаем UI и не показываем ошибок
			}
		}).start();
	}
	
	/**
* ВАЖНО: твоя разметка диалога должна быть res/layout/dialog_update.xml
* и содержать элементы с id:
*   @+id/tvTitle      (TextView)
*   @+id/tvChangelog  (TextView, можно внутри ScrollView)
*   @+id/btnLater     (Button/TextView)
*   @+id/btnUpdate    (Button/TextView)
*
* Этот метод лишь "надул" и повесил обработчики: "Позже" закрывает диалог,
* "Обновить" запускает существующую логику загрузки/установки.
*/	
	private void showUpdateDialogFromXml(@NonNull Info info) {
		try {
			LayoutInflater inflater = LayoutInflater.from(this);
			View view = inflater.inflate(R.layout.dialog_update, null, false);
			
			TextView tvDlgTitle     = view.findViewById(R.id.tvTitle);
			TextView tvDlgChangelog = view.findViewById(R.id.tvChangelog);
			View btnLaterView       = view.findViewById(R.id.btnLater);
			View btnUpdateView      = view.findViewById(R.id.btnUpdate);
			
			if (tvDlgTitle != null) tvDlgTitle.setText("Доступна версия " + (info.versionName == null ? "" : info.versionName));
			if (tvDlgChangelog != null) tvDlgChangelog.setText(info.changelog == null ? "" : info.changelog.trim());
			
			androidx.appcompat.app.AlertDialog dlg =
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
			.setView(view)
			.setCancelable(true)
			.create();
			
			if (btnLaterView != null) {
				btnLaterView.setOnClickListener(v -> { try { dlg.dismiss(); } catch (Exception ignored) {} });
			}
			
			if (btnUpdateView != null) {
				btnUpdateView.setOnClickListener(v -> {
					btnUpdateView.setEnabled(false);
					if (btnUpdateView instanceof TextView) ((TextView) btnUpdateView).setText("Загрузка…");
					
					if (!ensureUnknownSourcesAllowed(MainActivity.this)) {
						Toast.makeText(MainActivity.this,
						"Разрешите установку из неизвестных источников и нажмите «Обновить» снова",
						Toast.LENGTH_LONG).show();
						btnUpdateView.setEnabled(true);
						if (btnUpdateView instanceof TextView) ((TextView) btnUpdateView).setText("Обновить");
						return;
					}
					
					downloadAndInstall(MainActivity.this, info.apkUrl, new OnInstallCallback() {
						@Override public void onDownloadComplete() {
							runOnUiThread(() -> {
								try { dlg.dismiss(); } catch (Exception ignored) {}
								Toast.makeText(MainActivity.this, "Установка…", Toast.LENGTH_SHORT).show();
							});
						}
						
						@Override public void onError(String msg) {
							runOnUiThread(() -> {
								btnUpdateView.setEnabled(true);
								if (btnUpdateView instanceof TextView) ((TextView) btnUpdateView).setText("Обновить");
								Toast.makeText(MainActivity.this,
								msg == null ? "Ошибка загрузки" : msg,
								Toast.LENGTH_LONG).show();
							});
						}
					});
				});
			}
			
			dlg.show();
		} catch (Throwable ignored) {
			// тихо игнорируем
		}
	}
	// ---------- ВСПОМОГАТЕЛЬНОЕ ----------
	private static TextView makeButton(Context ctx, String text, int bgColor, int strokeColor) {
		TextView tv = new TextView(ctx);
		tv.setText(text);
		tv.setTextColor(Color.WHITE);
		tv.setTextSize(16);
		tv.setTypeface(Typeface.DEFAULT_BOLD);
		tv.setPadding(50, 20, 50, 20);
		tv.setGravity(Gravity.CENTER);
		
		GradientDrawable gd = new GradientDrawable();
		gd.setCornerRadius(25);
		gd.setColor(bgColor);
		if (strokeColor != Color.TRANSPARENT) gd.setStroke(2, strokeColor);
		tv.setBackground(gd);
		return tv;
	}
	
	private interface OnInstallCallback {
		void onDownloadComplete();
		void onError(String msg);
	}
	
	private static class Info {
		int versionCode;
		String versionName;
		String apkUrl;
		String changelog;
	}
	
	private static int getCurrentVersionCode(Context ctx) {
		try {
			PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
			if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
			return pi.versionCode;
		} catch (Exception e) {
			return 0;
		}
	}
	
	/**
* Оригинальная реализация fetchInfo() из твоего файла — использует UPDATE_JSON константу.
* Возвращает Info или выбрасывает исключение.
*/	
	private static Info fetchInfo() throws Exception {
		HttpURLConnection c = null;
		BufferedReader br = null;
		try {
			URL url = new URL(UPDATE_JSON);
			c = (HttpURLConnection) url.openConnection();
			c.setConnectTimeout(10000);
			c.setReadTimeout(15000);
			c.setRequestProperty("User-Agent", "VARKYNTH-Updater");
			
			br = new BufferedReader(new InputStreamReader(new BufferedInputStream(c.getInputStream())));
			StringBuilder sb = new StringBuilder();
			String line; while ((line = br.readLine()) != null) sb.append(line);
			
			JSONObject j = new JSONObject(sb.toString());
			Info i = new Info();
			i.versionCode = j.optInt("versionCode", 0);
			i.versionName = j.optString("versionName", "");
			i.apkUrl      = j.optString("apkUrl", "");
			i.changelog   = j.optString("changelog", "");
			if (i.apkUrl == null || i.apkUrl.isEmpty()) {
				throw new IllegalStateException("apkUrl пустой");
			}
			return i;
		} finally {
			try { if (br != null) br.close(); } catch (Exception ignore) {}
			if (c != null) c.disconnect();
		}
	}
	
	// Android 8+: просим разрешение до начала загрузки
	private static boolean ensureUnknownSourcesAllowed(Activity act) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			boolean can = act.getPackageManager().canRequestPackageInstalls();
			if (!can) {
				Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
				Uri.parse("package:" + act.getPackageName()));
				act.startActivity(i);
				return false;
			}
		}
		return true;
	}
	
	// Скачиваем APK и запускаем установку: broadcast + резервный опрос статуса
	private static void downloadAndInstall(Activity act, String apkUrl, OnInstallCallback cb) {
		try {
			final Context appCtx = act.getApplicationContext();
			final DownloadManager dm = (DownloadManager) appCtx.getSystemService(Context.DOWNLOAD_SERVICE);
			
			File dir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
			if (dir != null && !dir.exists()) dir.mkdirs();
			final File apk = new File(dir, "VARKYNTHPlayer_update.apk");
			if (apk.exists()) try { apk.delete(); } catch (Exception ignore) {}
			
			DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
			req.setTitle("VARKYNTHPlayer — обновление");
			req.setDescription("Загрузка APK…");
			req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			req.setDestinationInExternalFilesDir(appCtx, Environment.DIRECTORY_DOWNLOADS, "nyxsound_update.apk");
			req.setMimeType("application/vnd.android.package-archive");
			
			final long downloadId = dm.enqueue(req);
			
			final BroadcastReceiver r = new BroadcastReceiver() {
				@Override public void onReceive(Context c, Intent intent) {
					long done = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
					if (done != downloadId) return;
					try {
						startInstall(appCtx, dm, downloadId, apk);
						if (cb != null) cb.onDownloadComplete();
					} catch (Exception e) {
						if (cb != null) cb.onError(e.getMessage());
					}
					try { appCtx.unregisterReceiver(this); } catch (Exception ignore) {}
				}
			};
			IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
			if (Build.VERSION.SDK_INT >= 33) {
				appCtx.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED);
			} else {
				appCtx.registerReceiver(r, filter);
			}
			
			// Фоновая проверка статуса (резервный механизм)
			new Thread(() -> {
				try {
					boolean finished = false;
					while (!finished) {
						DownloadManager.Query q = new DownloadManager.Query().setFilterById(downloadId);
						try (android.database.Cursor cur = dm.query(q)) {
							if (cur != null && cur.moveToFirst()) {
								int status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
								if (status == DownloadManager.STATUS_SUCCESSFUL) {
									try {
										startInstall(appCtx, dm, downloadId, apk);
										if (cb != null) cb.onDownloadComplete();
									} catch (Exception e) {
										if (cb != null) cb.onError(e.getMessage());
									}
									finished = true;
									try { appCtx.unregisterReceiver(r); } catch (Exception ignore) {}
								} else if (status == DownloadManager.STATUS_FAILED) {
									if (cb != null) cb.onError("Загрузка не удалась");
									finished = true;
									try { appCtx.unregisterReceiver(r); } catch (Exception ignore) {}
								}
							}
						}
						if (!finished) try { Thread.sleep(800); } catch (InterruptedException ignored) {}
					}
				} catch (Exception ignored) {}
			}).start();
			
		} catch (Exception e) {
			if (cb != null) cb.onError(e.getMessage());
		}
	}
	
	// Надёжный старт системного установщика
	private static void startInstall(Context appCtx, DownloadManager dm, long downloadId, File fallbackApk) throws Exception {
		Uri uri = null;
		try { uri = dm.getUriForDownloadedFile(downloadId); } catch (Exception ignore) {}
		if (uri == null) {
			uri = FileProvider.getUriForFile(appCtx,
			appCtx.getPackageName() + ".fileprovider", fallbackApk);
		}
		Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
		install.setData(uri);
		install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
		install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
		install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
		appCtx.startActivity(install);
	}
	
	private boolean isNetworkAvailable() {
		try {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) return true;
			NetworkInfo ni = cm.getActiveNetworkInfo();
			return ni != null && ni.isConnected();
		} catch (Exception ignored) {
			return true;
		}
	}
	
	@Nullable
	private static String readFully(@Nullable InputStream is) throws Exception {
		if (is == null) return null;
		BufferedInputStream bis = new BufferedInputStream(is);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = bis.read(buf)) >= 0) bos.write(buf, 0, n);
		return bos.toString("UTF-8");
	}
	
	private static String safe(@Nullable String s) {
		return s == null ? "" : s;
	}
	
	@Override
	public void onBackPressed() {
		if (v.player_root.getVisibility() == View.VISIBLE) {
			v.player_root.setVisibility(View.GONE);
			v.content_root.setVisibility(View.VISIBLE);
			v.topbarmain.setVisibility(View.VISIBLE);
		} else {
			v.player_root.setVisibility(View.VISIBLE);
			v.content_root.setVisibility(View.GONE);
			v.topbarmain.setVisibility(View.GONE);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
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
		SharedPreferences SynMusic = getSharedPreferences("favourites_paths", MODE_PRIVATE);
		Set<String> set = SynMusic.getStringSet("paths", new HashSet<>());
		ServiceBind_Start();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Intent stopIntent = new Intent(MainActivity.this, VARTHMusicService.class);
		stopIntent.setAction("STOP");
		startService(stopIntent);
	}
	public void reflesh() {
		if (v.music_view.getAdapter() != null) v.music_view.getAdapter().notifyDataSetChanged();
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
			selArgs = new String[]{ "20000", "Music/%", musicAbs };
		} else {
			// Старые API — фильтр строго по абсолютному пути
			selection =
			android.provider.MediaStore.Audio.Media.IS_MUSIC + "!= 0" +
			" AND " + android.provider.MediaStore.Audio.Media.DURATION + ">= ?" +
			" AND " + android.provider.MediaStore.Audio.Media.DATA + " LIKE ?";
			selArgs = new String[]{ "20000", musicAbs };
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
				if (duration >= 50_000L && path != null && (path.startsWith(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Music/"))) {
					java.util.HashMap<String, Object> map = new java.util.HashMap<>();
					map.put("Id", Id);
					map.put("title", title);
					map.put("artist", artist);
					map.put("albumId", albumId);
					map.put("path", path);
					map.put("time", duration);
					
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
	
	public void recive() {
	}
	private BroadcastReceiver SongDataReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			total = intent.getStringExtra("total_duration");
			position = intent.getStringExtra("current_duration");
			song_name = intent.getStringExtra("song");
			path = intent.getStringExtra("path");
			songPosition = Double.parseDouble(intent.getStringExtra("pos"));
			v.slider_music.setValueTo((int) Double.parseDouble(total));
			v.slider_music.setValue((int) Double.parseDouble(position));
			getMusicTime(v.timestart, Double.parseDouble(position));
			getMusicTime(v.timeoff, Double.parseDouble(total));
			v.name_music.setText(song_name);
			v.name_music_player.setText(song_name);
			SynMusic.edit().putString("path", path).commit();
		}
	};
	private BroadcastReceiver SongStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			state = intent.getStringExtra("responce");
			if (state.equals("pause")) {
				v.ic_play_click.setImageResource(R.drawable.ic_play);
				SynMusic.edit().putString("p", "pause").commit();
			} else {
				if (state.equals("play")) {
					SynMusic.edit().putString("p", "play").commit();
					v.ic_play_click.setImageResource(R.drawable.ic_pause);
					SynTimer = new TimerTask() {
						@Override
						public void run() {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									v.duration_music.setText(SynMusic.getString("path", ""));
									v.duration_music.setSingleLine(true);
									v.duration_music.setEllipsize(TextUtils.TruncateAt.MARQUEE);
									v.duration_music.setSelected(true);
									v.name_duration_player.setText(SynMusic.getString("path", ""));
									v.name_duration_player.setSingleLine(true);
									v.name_duration_player.setEllipsize(TextUtils.TruncateAt.MARQUEE);
									v.name_duration_player.setSelected(true);
									reflesh();
								}
							});
						}
					};
					_timer.schedule(SynTimer, (int)(80));
				}
			}
		} };
	{
	}
	
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
			
			textview1.setText(_data.get((int)_position).get("title").toString());
			textview2.setText(_data.get((int)_position).get("artist").toString());
			getMusicTime(textview4, Double.parseDouble(_data.get((int)_position).get("time").toString()));
			if ((songPosition == _position) && _data.get((int)_position).get("title").toString().equals(song_name)) {
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
					}
					v.player_root.setVisibility(View.VISIBLE);
					v.content_root.setVisibility(View.GONE);
					v.topbarmain.setVisibility(View.GONE);
				}
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
