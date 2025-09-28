package com.VARKYNTH.Player.info;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

public final class VCopy {
	private VCopy(){}
	
	public static void attach(Context ctx, TextView... views) {
		for (TextView tv : views) {
			if (tv == null) continue;
			tv.setOnClickListener(v -> {
				ClipboardManager cm = (ClipboardManager)
				ctx.getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null) {
					cm.setPrimaryClip(ClipData.newPlainText("clipboard",String.valueOf(tv.getText())));
					Toast.makeText(ctx,"Текст Скопирован",Toast.LENGTH_SHORT).show();
				}
			});
		}
	}
	public static void attachOnLongPress(Context ctx, TextView... views) {
		for (TextView tv :views) {
			if (tv == null) continue;
			tv.setOnLongClickListener(v -> {
				ClipboardManager cm = (ClipboardManager)
				ctx.getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null) {
					cm.setPrimaryClip(ClipData.newPlainText("clipboard",String.valueOf(tv.getText())));
					Toast.makeText(ctx,"Текст Скопирован",Toast.LENGTH_SHORT).show();
					return true;
				}
				return false;
			});
		}
	}
}
