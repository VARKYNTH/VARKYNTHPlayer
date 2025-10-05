package com.VARKYNTH.Player;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

final class VARTHProgressDispatcher {
    private final Context ctx;
    private final VARTHPlaybackCore core;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Timer timer = new Timer();
    private TimerTask task;

    VARTHProgressDispatcher(Context ctx, VARTHPlaybackCore core){
        this.ctx = ctx.getApplicationContext();
        this.core = core;
    }

    void start(ArrayList<HashMap<String,Object>> list, int position){
        stop();
        task = new TimerTask() {
            @Override public void run() {
                handler.post(() -> {
                    if (core.isPlaying()) {
                        Intent i = new Intent(VARTHConstants.BR_SONG_DATA);
                        i.putExtra("total_duration", String.valueOf(core.getDuration()));
                        i.putExtra("current_duration", String.valueOf(core.getCurrent()));
                        if (list!=null && !list.isEmpty() && position>=0 && position<list.size()) {
                            i.putExtra("song", String.valueOf(list.get(position).get("title")));
                            i.putExtra("pos", String.valueOf(position));
                            i.putExtra("path", String.valueOf(list.get(position).get("path")));
                        }
                        ctx.sendBroadcast(i);
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(task, 0, 500);
    }

    void stop(){
        if (task!=null){ task.cancel(); task=null; }
    }
}