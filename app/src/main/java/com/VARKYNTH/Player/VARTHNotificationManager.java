package com.VARKYNTH.Player;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

final class VARTHNotificationHelper {
    private final Context ctx;
    private final MediaSessionCompat mediaSession;

    VARTHNotificationHelper(Context ctx, MediaSessionCompat session){
        this.ctx = ctx.getApplicationContext();
        this.mediaSession = session;
    }

    void ensureChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    VARTHConstants.CHANNEL_ID, "VARKYNTHPlayer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("This is Offline VARKYNTHPlayer Notification");
            ch.setSound(null, null);
            ch.enableVibration(false);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    void startForeground(android.app.Service svc, boolean isPlaying, VARTHRepeatMode repeatMode){
        ensureChannel();

        PendingIntent piPrev = PendingIntent.getService(ctx, 10,
                new android.content.Intent(ctx, VARTHMusicService.class).setAction(VARTHConstants.ACTION_PREV),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent piToggle = PendingIntent.getService(ctx, 11,
                new android.content.Intent(ctx, VARTHMusicService.class).setAction(VARTHConstants.ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent piNext = PendingIntent.getService(ctx, 12,
                new android.content.Intent(ctx, VARTHMusicService.class).setAction(VARTHConstants.ACTION_NEXT),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent piRepeat = PendingIntent.getService(ctx, 13,
                new android.content.Intent(ctx, VARTHMusicService.class).setAction(VARTHConstants.ACTION_REPEAT),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent contentPI = mediaSession.getController().getSessionActivity();
        if (contentPI == null) {
            android.content.Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (launch != null) {
                contentPI = PendingIntent.getActivity(ctx, 101, launch, PendingIntent.FLAG_IMMUTABLE);
            }
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, VARTHConstants.CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(safeMeta(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE))
                .setContentText(safeMeta(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST))
                .setContentIntent(contentPI)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous, "Prev", piPrev)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play", piToggle)
                .addAction(android.R.drawable.ic_media_next, "Next", piNext)
                .addAction(getRepeatIconRes(repeatMode), getRepeatLabel(repeatMode), piRepeat)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0,1,2))
                .setOngoing(isPlaying);

        svc.startForeground(1, b.build());
    }

    void updateForeground(android.app.Service svc, boolean isPlaying, VARTHRepeatMode repeatMode){
        startForeground(svc, isPlaying, repeatMode);
    }

    private String safeMeta(String key) {
        if (mediaSession.getController()!=null && mediaSession.getController().getMetadata()!=null) {
            CharSequence s = mediaSession.getController().getMetadata().getText(key);
            return s!=null? s.toString() : "";
        }
        return "";
    }

    private static String getRepeatLabel(VARTHRepeatMode m){
        if (m==VARTHRepeatMode.ONE) return "Repeat 1";
        if (m==VARTHRepeatMode.ALL) return "Repeat All";
        return "Repeat Off";
    }
    private static int getRepeatIconRes(VARTHRepeatMode m){
        if (m==VARTHRepeatMode.ONE) return android.R.drawable.ic_menu_revert;
        if (m==VARTHRepeatMode.ALL) return android.R.drawable.ic_menu_rotate;
        return android.R.drawable.ic_menu_close_clear_cancel;
    }
}