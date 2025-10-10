package com.VARKYNTH.Player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.info.AllId;

// core
import com.VARKYNTH.Player.core.MusicLoader;
import com.VARKYNTH.Player.core.PermissionManager;
import com.VARKYNTH.Player.core.DepthManager;
import com.VARKYNTH.Player.core.TrackManager;
import com.VARKYNTH.Player.core.MusicReceiver;
import com.VARKYNTH.Player.core.UIHelper;
import com.VARKYNTH.Player.core.Recyclerview1Adapter;
import com.VARKYNTH.Player.core.Utils;
import com.VARKYNTH.Player.ui.VGlobalDepth;

public class MainActivity extends AppCompatActivity {

    // ===== state =====
    private boolean mBound = false;
    private static VARTHMusicService mBoundService;
    static Context con;

    private ArrayList<HashMap<String, Object>> song_list = new ArrayList<>();
    private SharedPreferences SynMusic;
    private Intent SynIntent = new Intent();

    private AllId.MainViewId v;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private int repeatMode = 0;
    private boolean shuffleMode = false;

    private int pendingDeletePos = -1;
    private int pendingSharePos = -1;

    private double songPosition = -1;
    private String song_name = "";
    private String path = "";

    private DepthManager depthMgr;
    private Utils.AlbumArtLoader artLoader;
    private Recyclerview1Adapter adapter;
    private TrackManager trackManager;

    private AlertDialog SynDialog;

    // ===== activity result launchers =====
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permission -> {
                boolean allGranted = true;
                for (Boolean isGranted : permission.values()) {
                    if (!isGranted) { allGranted = false; break; }
                }
                if (allGranted) {
                    reloadSongs();
                }
            });

    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;

    // ===== receivers (compose from core) =====
    private BroadcastReceiver dataReceiver;
    private BroadcastReceiver stateReceiver;

    // ===== service connection =====
    private final android.content.ServiceConnection mConnection = new android.content.ServiceConnection() {
        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
            mBoundService = ((VARTHMusicService.MusicBinder)service).getService();
            mBound = true;
        }
        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
            mBound = false;
        }
    };

    // ===== lifecycle =====
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int savedMode = getSharedPreferences("settings", MODE_PRIVATE).getInt("theme_mode", 0);
        VARTHSettingsActivity.applyTheme(savedMode);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // base prefs
        prefs = getSharedPreferences("vplayer_prefs", MODE_PRIVATE);
        SynMusic = getSharedPreferences("SynMusic", Activity.MODE_PRIVATE);
        trackManager = new TrackManager(SynMusic);
        depthMgr = new DepthManager();
        artLoader = new Utils.AlbumArtLoader(this);

        // delete launcher (для MediaStore deleteRequest)
        deleteRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (v.music_view.getAdapter() != null) v.music_view.getAdapter().notifyDataSetChanged();
                });

        // global depth apply + listener
        depthMgr.applyDepthFromPrefs(this, prefs);
        prefListener = (sp, key) -> {
            if ("global_depth_enabled".equals(key)) depthMgr.applyDepthFromPrefs(this, prefs);
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        // fonts + bind views
        VFont.boldAll(this, findViewById(android.R.id.content));
        v = AllId.MainViewId.bind(this);

        // locale
        loadLocale();

        // dynamic colors
        DynamicColors.applyToActivityIfAvailable(this);
        int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);

        // slider live formatting on touch end
        v.slider_music.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) {}
            @Override public void onStopTrackingTouch(Slider slider) {
                if (mBound && mBoundService != null) {
                    long target = (long) v.slider_music.getValue();
                    mBoundService.seekToMs(target);
                }
            }
        });
        v.slider_music.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                new Utils.TimeFmt().set(v.timestart, value);
            }
        });

        // clicks
        v.player_card.setOnClickListener(_v -> UIHelper.togglePlayerView(v, this, true));

        v.ic_click_fx.setOnClickListener(_v -> {
            SynIntent.setClass(getApplicationContext(), VARTHFxActivity.class);
            startActivity(SynIntent);
        });

        v.ic_click_repeat.setOnClickListener(_v -> {
            repeatMode = (repeatMode + 1) % 3;
            switch (repeatMode) {
                case 0: v.ic_click_repeat.setImageResource(R.drawable.ic_repeat_off); break;
                case 1: v.ic_click_repeat.setImageResource(R.drawable.ic_repeat_one); break;
                case 2: v.ic_click_repeat.setImageResource(R.drawable.ic_repeat_on); break;
            }
            Intent i = new Intent(getApplicationContext(), VARTHMusicService.class);
            i.setAction("REPEAT");
            startService(i);
        });

        v.ic_click_shuffle.setOnClickListener(_v -> {
            shuffleMode = !shuffleMode;
            v.ic_click_shuffle.setImageResource(shuffleMode ? R.drawable.ic_shuffle : R.drawable.ic_shuffle_off);
            Intent i = new Intent(getApplicationContext(), VARTHMusicService.class);
            i.setAction("SHUFFLE");
            startService(i);
        });

        v.click_prev.setOnClickListener(_v -> {
            SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
            SynIntent.setAction("PRE");
            startService(SynIntent);
        });

        v.click_play.setOnClickListener(_v -> {
            SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
            SynIntent.setAction("TOGGLE");
            startService(SynIntent);
        });

        v.click_next.setOnClickListener(_v -> {
            SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
            SynIntent.setAction("NEXT");
            startService(SynIntent);
        });

        v.click_dialogs.setOnClickListener(_v -> {
            SynDialog = UIHelper.showMainDialog(this);
        });

        v.swipe_refresh_music_list.setOnRefreshListener(this::reloadSongs);

        // recycler
        v.music_view.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Recyclerview1Adapter(
                song_list,
                new Recyclerview1Adapter.OnItemClick() {
                    @Override public void onClick(int position) {
                        if (mBound && mBoundService != null) {
                            SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
                            mBoundService.setList(song_list);
                            mBoundService.setPosition(position);
                            SynIntent.setAction("START");
                            startService(SynIntent);

                            HashMap<String, Object> item = song_list.get(position);
                            trackManager.saveTrackInfo(
                                    String.valueOf(item.get("title")),
                                    String.valueOf(item.get("artist")),
                                    String.valueOf(item.get("path")),
                                    ((Number) item.get("time")).longValue(),
                                    position
                            );
                        }
                        UIHelper.togglePlayerView(v, MainActivity.this, true);
                    }

                    @Override public void onLongClick(int position) {
                        pendingDeletePos = position;
                        pendingSharePos = position;
                        UIHelper.showListDialog(MainActivity.this, new UIHelper.ListActions() {
                            @Override public void onConfirmDelete() { onConfirmDeleteFromDialog(); }
                            @Override public void onShare() { onShareFromDialog(); }
                        });
                    }
                },
                (imageView, albumId) -> artLoader.load(imageView, albumId)
        );
        v.music_view.setAdapter(adapter);
        v.music_view.setHasFixedSize(true);
        v.music_view.setItemViewCacheSize(20);

        // ui style
        UIHelper.applySliderStyle(v);

        // service + receivers
        registerReceivers();
        SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
        startService(SynIntent);
        bindService(SynIntent, mConnection, Context.BIND_AUTO_CREATE);
        con = this;

        // permissions + load
        PermissionManager.requestAll(this, requestPermissionLauncher, this::reloadSongs);

        // update check (оставил как у тебя)
        VARTHUpdate.startUpdateCheck(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBound) {
            ServiceBind_Start();
        }
        restoreTrackIntoUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        depthMgr.applyDepthFromPrefs(this, prefs);
        restoreTrackIntoUI();

        if ("pause".equals(SynMusic.getString("p", ""))) {
            v.ic_play_click.setImageResource(R.drawable.ic_play);
        } else {
            v.ic_play_click.setImageResource(R.drawable.ic_pause);
        }

        if (!SynMusic.getString("path", "").isEmpty()) {
            String pth = SynMusic.getString("path", "");
            v.duration_music.setText(pth);
            v.duration_music.setSingleLine(true);
            v.duration_music.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            v.duration_music.setSelected(true);

            v.name_duration_player.setText(pth);
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
    protected void onDestroy() {
        super.onDestroy();
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        depthMgr.detachIfApplied();

        try { unregisterReceiver(dataReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(stateReceiver); } catch (Exception ignored) {}

        Intent stopIntent = new Intent(MainActivity.this, VARTHMusicService.class);
        stopIntent.setAction("STOP");
        startService(stopIntent);
    }

    @Override
    public void onBackPressed() {
        if (v.player_root.getVisibility() == View.VISIBLE) {
            UIHelper.togglePlayerView(v, this, false);
        } else {
            UIHelper.togglePlayerView(v, this, true);
        }
    }

    // ===== helpers =====
    private void reloadSongs() {
        song_list.clear();
        song_list.addAll(MusicLoader.getAllSongs(this)); // перенесённая логика
        if (v.music_view.getAdapter() != null) v.music_view.getAdapter().notifyDataSetChanged();
        if (v.swipe_refresh_music_list.isRefreshing()) v.swipe_refresh_music_list.setRefreshing(false);
    }

    private void restoreTrackIntoUI() {
        TrackManager.RestoreResult r = trackManager.restoreTrackInfo();
        if (!r.path.isEmpty()) {
            v.name_music.setText(r.title);
            v.duration_music.setText(r.artist);
            if (r.duration > 0) {
                v.slider_music.setValueFrom(0f);
                v.slider_music.setValueTo(r.duration);
            }
            songPosition = r.position;
        }
    }

    private void registerReceivers() {
        dataReceiver  = new MusicReceiver.SongDataReceiver(v, trackManager, song_list);
        stateReceiver = new MusicReceiver.SongStateReceiver(v);
        registerReceiver(dataReceiver, new IntentFilter("com.dplay.SONG_DATA"), RECEIVER_EXPORTED);
        registerReceiver(stateReceiver, new IntentFilter("com.dplay.SONG_STATE"), RECEIVER_EXPORTED);
    }

    private void loadLocale() {
        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String language = sharedPreferences.getString("app_language", "en");
        setLocale(language);
    }

    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }

    public void ServiceBind_Start() {
        if (!mBound) {
            if (mBoundService == null) {
                SynIntent.setClass(getApplicationContext(), VARTHMusicService.class);
                SynIntent.putExtra("STATUS", "START_SERVICE");
                startService(SynIntent);
                bindService(SynIntent, mConnection, Context.BIND_AUTO_CREATE);
                con = this;
            }
        }
    }

    // ==== действия из диалога списка ====
    public void onConfirmDeleteFromDialog() {
        if (pendingDeletePos < 0 || pendingDeletePos >= song_list.size()) {
            pendingDeletePos = -1; return;
        }
        HashMap<String, Object> item = song_list.get(pendingDeletePos);
        if (item.containsKey("Id")) {
            long audioId = ((Number) item.get("Id")).longValue();
            trackManager.deleteTrackById(
                    this, audioId, pendingDeletePos, song_list, v.music_view.getAdapter(), deleteRequestLauncher);
        } else {
            String p = String.valueOf(item.get("path"));
            trackManager.deleteTrackByPath(
                    this, p, pendingDeletePos, song_list, v.music_view.getAdapter(), deleteRequestLauncher);
        }
        pendingDeletePos = -1;
    }

    public void onShareFromDialog() {
        if (pendingSharePos < 0 || pendingSharePos >= song_list.size()) return;
        HashMap<String, Object> item = song_list.get(pendingSharePos);
        String title = String.valueOf(item.get("title"));
        if (item.containsKey("Id")) {
            trackManager.shareTrackById(this, ((Number) item.get("Id")).longValue(), title);
        } else {
            trackManager.shareTrackByPath(this, String.valueOf(item.get("path")), title);
        }
        pendingSharePos = -1;
    }
}