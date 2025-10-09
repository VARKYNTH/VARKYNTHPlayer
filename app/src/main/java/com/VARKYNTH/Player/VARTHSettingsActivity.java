package com.VARKYNTH.Player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import com.VARKYNTH.Player.info.AllId;
import com.VARKYNTH.Player.info.VFont;
import com.VARKYNTH.Player.ui.VGlobalDepth;

public class VARTHSettingsActivity extends AppCompatActivity {

	private HashMap<String, Object> map = new HashMap<>();
	private String fontName = "";
	private String typeace = "";
    private static final String PREF_LANGUAGE = "app_language";

	private ArrayList<HashMap<String, Object>> languages = new ArrayList<>();
    
    private AllId.SettingsViewId v;

	private SharedPreferences sharedPreferences;
    private SharedPreferences prefs;
	private Intent intent = new Intent();
    
    private boolean depthApplied = false;
    
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
        // Восстанавливаем тему до setContentView, чтобы не мигало
        int savedMode = getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("theme_mode", 0);
        applyTheme(savedMode);
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.varth_settings);
        
        prefs = getSharedPreferences("vplayer_prefs", MODE_PRIVATE);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        applyDepthFromPrefs();

        // Живое обновление при смене настройки в SettingsActivity
        prefListener = (sp, key) -> {
            if ("global_depth_enabled".equals(key)) applyDepthFromPrefs();
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        
        VFont.boldAll(this, findViewById(android.R.id.content));

        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        DynamicColors.applyToActivityIfAvailable(this);
        int surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
        
        v = AllId.SettingsViewId.bind(this);

		// обработчики
		v.fx_card.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				intent.setClass(getApplicationContext(), VARTHFxActivity.class);
				startActivity(intent);
			}
		});
        
        v.ide_card.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				intent.setClass(getApplicationContext(), com.VARKYNTH.Player.VARTHSettingsExperimental.class);
				startActivity(intent);
			}
		});

		v.t_language.setOnClickListener(v -> showLanguageSelectionDialog());
        
        // Инициализация значений
        int mode = prefs.getInt("theme_mode", 0);
        if (mode == 1) v.rbLight.setChecked(true);
        else if (mode == 2) v.rbDark.setChecked(true);
        else v.rbSystem.setChecked(true);

        // Слушатели
        v.btnApplyTheme.setOnClickListener(v -> {
            int newMode = currentThemeSelection();
            applyTheme(newMode);
            prefs.edit().putInt("theme_mode", newMode).apply();
            Toast.makeText(this, "Тема применена", Toast.LENGTH_SHORT).show();
            recreate(); // мягкий перезапуск экрана
        });
	}

    private void showLanguageSelectionDialog() {
        String[] languages = new String[]{
            getString(R.string.dialog_language_english),
            getString(R.string.dialog_language_russian)
        };

        String currentLanguage = sharedPreferences.getString(PREF_LANGUAGE, "en");
        int checkedItem = -1;

        switch (currentLanguage) {
            case "en": checkedItem = 0; break;
            case "ru": checkedItem = 1; break;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_language_title))
            .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                String newLocale;
                switch (which) {
                    case 0: newLocale = "en"; break;
                    case 1: newLocale = "ru"; break;
                    default: newLocale = "en"; break;
                }

                if (!currentLanguage.equals(newLocale)) {
                    setLocale(newLocale);
                }
                dialog.dismiss();
            })
            .show();
    }

    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PREF_LANGUAGE, lang);
        editor.apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
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
    private int currentThemeSelection() {
        if (v.rbLight.isChecked()) return 1;
        if (v.rbDark.isChecked())  return 2;
        return 0; // System
    }

    public static void applyTheme(int mode) {
        int m = (mode == 1)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : (mode == 2)
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(m);
    }
	
}