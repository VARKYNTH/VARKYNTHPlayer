package com.VARKYNTH.Player;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Process;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import java.util.Locale;

public class VARTHApplication extends Application {
	
	private static Context mApplicationContext;
	
	private static final String PREF_LANGUAGE = "app_language";
    
	public static Context getContext() { return mApplicationContext; }
	
	@Override
	public void onCreate() {
		super.onCreate();
		mApplicationContext = getApplicationContext();
        loadLocale();
        
		Thread.setDefaultUncaughtExceptionHandler(
		new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable throwable) {
				Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				intent.putExtra("error", Log.getStackTraceString(throwable));
				startActivity(intent);
				Process.killProcess(Process.myPid());
				System.exit(1);
			}
		});
		DynamicColors.applyToActivitiesIfAvailable(this);
	}
    public void loadLocale() {
		SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
		String language = sharedPreferences.getString(PREF_LANGUAGE, "en");
		setLocale(language);
	}
	
	// Método para aplicar a configuração do idioma
	private void setLocale(String lang) {
		Locale locale = new Locale(lang);
		Locale.setDefault(locale);
		Configuration config = new Configuration();
		config.setLocale(locale);
		getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		loadLocale();
	}
    
}