package com.VARKYNTH.Player;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Formatter;
import java.util.Locale;

public class VARTHUpdate {

    private static final String UPDATE_JSON =
            "https://raw.githubusercontent.com/VARKYNTH/VARKYNTHPlayer/main/UpdateApk.json";

    public static void startUpdateCheck(Activity activity) {
        new Thread(() -> {
            try {
                Info info = fetchInfo();
                if (info == null) return;

                int current = getCurrentVersionCode(activity);
                boolean newer = info.versionCode > current;

                if (newer) {
                    activity.runOnUiThread(() -> showUpdateDialogFromXml(activity, info));
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    private static void showUpdateDialogFromXml(Activity act, @NonNull Info info) {
    try {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(act);

        // 1. Загружаем разметку
        LayoutInflater inflater = LayoutInflater.from(act);
        View view = inflater.inflate(R.layout.dialog_update, null);

        // 2. Находим элементы
        TextView tvTitle       = view.findViewById(R.id.tvTitle);
        TextView tvChangelog   = view.findViewById(R.id.tvChangelog);
        com.google.android.material.button.MaterialButton btnLater  =
                view.findViewById(R.id.btnLater);
        com.google.android.material.button.MaterialButton btnUpdate =
                view.findViewById(R.id.btnUpdate);

        // 3. Заполняем данными
        tvTitle.setText("Доступна версия " + safe(info.versionName));
        tvChangelog.setText(safe(info.changelog).trim());

        // 4. Создаём диалог
        AlertDialog dlg = builder
                .setView(view)
                .setCancelable(true)
                .create();

        // 5. Обработчики
        btnLater.setOnClickListener(v -> dlg.dismiss());

        btnUpdate.setOnClickListener(v -> {
            btnUpdate.setEnabled(false);
            btnUpdate.setText("Загрузка…");

            if (!ensureUnknownSourcesAllowed(act)) {
                Toast.makeText(act,
                        "Разрешите установку из неизвестных источников и нажмите «Обновить» снова",
                        Toast.LENGTH_LONG).show();
                btnUpdate.setEnabled(true);
                btnUpdate.setText("Обновить");
                return;
            }

            downloadAndInstall(act, info.apkUrl, new OnInstallCallback() {
                @Override public void onDownloadComplete() {
                    act.runOnUiThread(() -> {
                        dlg.dismiss();
                        Toast.makeText(act, "Установка…", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onError(String msg) {
                    act.runOnUiThread(() -> {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Обновить");
                        Toast.makeText(act,
                                msg == null ? "Ошибка загрузки" : msg,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        dlg.show();
    } catch (Throwable ignored) {}
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
        } catch (Exception e) {
            if (cb != null) cb.onError(e.getMessage());
        }
    }

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

    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }
}