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
					// Внутри TimerTask.run():
					if (core.isPlaying()) {
						Intent i = new Intent(VARTHConstants.BR_SONG_DATA);
						i.putExtra("total_duration", String.valueOf(core.getDuration()));
						i.putExtra("current_duration", String.valueOf(core.getCurrent()));
						
						// Брать актуальные значения прямо из core:
						ArrayList<HashMap<String,Object>> curList = core.list;
						int curPos = core.position;
						
						if (curList != null && !curList.isEmpty()
						&& curPos >= 0 && curPos < curList.size()) {
							i.putExtra("song", String.valueOf(curList.get(curPos).get("title")));
							i.putExtra("pos", String.valueOf(curPos));
							i.putExtra("path", String.valueOf(curList.get(curPos).get("path")));
						}
						ctx.sendBroadcast(i);
					}
				});
			}
		};
		timer.scheduleAtFixedRate(task, 80, 80);
	}
	
	void stop(){
		if (task!=null){ task.cancel(); task=null; }
	}
}