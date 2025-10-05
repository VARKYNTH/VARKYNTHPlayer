package com.VARKYNTH.Player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MediaButtonReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        androidx.media.session.MediaButtonReceiver.handleIntent(
                new android.support.v4.media.session.MediaSessionCompat(context, "VARTHMediaButtons"),
                intent);
    }
}