package com.VARKYNTH.Player.info;

import android.app.Activity;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;

import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import com.VARKYNTH.Player.R;

public final class AllId {
	public static final class DonateViewId {
		
		public final LinearLayout topbarother;
		public final TextView textview8;
		public final TextView textview9;
		
		private DonateViewId(Activity a) {
			
			topbarother = a.findViewById(R.id.topbarother);
			textview8 = a.findViewById(R.id.textview8);
			textview9 = a.findViewById(R.id.textview9);
		}
		public static DonateViewId bind(Activity a) {
			return new DonateViewId(a);
		}
	}
	public static final class AboutViewId {
		
		public final LinearLayout topbarother;
		public final com.google.android.material.card.MaterialCardView dev_owner_click;
		public final com.google.android.material.card.MaterialCardView dev_anna_click;
		
		private AboutViewId(Activity a) {
			
			topbarother = a.findViewById(R.id.topbarother);
			dev_owner_click = a.findViewById(R.id.dev_owner_click);
			dev_anna_click = a.findViewById(R.id.dev_anna_click);
		}
		public static AboutViewId bind(Activity a) {
			return new AboutViewId(a);
		}
	}
	public static final class SettingsViewId {
		
		public final LinearLayout topbarother;
		public final com.google.android.material.card.MaterialCardView fx_card;
		public final com.google.android.material.card.MaterialCardView t_language;
		
		private SettingsViewId(Activity a) {
			
			topbarother = a.findViewById(R.id.topbarother);
			fx_card = a.findViewById(R.id.fx_card);
			t_language = a.findViewById(R.id.t_language);
		}
		public static SettingsViewId bind(Activity a) {
			return new SettingsViewId(a);
		}
	}
	public static final class FxViewId {
		
		public final LinearLayout topbarother;
		public final SwitchMaterial sw_dolby;
		public final Slider seek_loudness;
		public final Slider seek_bass;
		public final Slider seek_virtualizer;
		public final SwitchMaterial sw_eq_enabled;
		public final Spinner sp_eq_presets;
		public final LinearLayout eq_bands_container;
		
		private FxViewId(Activity a) {
			
			topbarother = a.findViewById(R.id.topbarother);
			sw_dolby = a.findViewById(R.id.sw_dolby);
			seek_loudness = a.findViewById(R.id.seek_loudness);
			seek_bass = a.findViewById(R.id.seek_bass);
			seek_virtualizer = a.findViewById(R.id.seek_virtualizer);
			sw_eq_enabled = a.findViewById(R.id.sw_eq_enabled);
			sp_eq_presets = a.findViewById(R.id.sp_eq_presets);
			eq_bands_container = a.findViewById(R.id.eq_bands_container);
		}
		public static FxViewId bind(Activity a) {
			return new FxViewId(a);
		}
	}
	public static final class MainViewId {
		
		public final LinearLayout topbarmain;
		public final FrameLayout content_root;
		public final LinearLayout player_root;
		public final RecyclerView music_view;
		public final com.google.android.material.card.MaterialCardView player_card;
		public final TextView name_music;
		public final TextView duration_music;
		public final TextView name_music_player;
		public final TextView name_duration_player;
		public final ImageView click_dialogs;
		public final ImageView ic_click_fx;
		public final ImageView ic_click_repeat;
		public final ImageView ic_click_shuffle;
		public final TextView timestart;
		public final Slider slider_music;
		public final TextView timeoff;
		public final com.google.android.material.card.MaterialCardView click_prev;
		public final com.google.android.material.card.MaterialCardView click_play;
		public final com.google.android.material.card.MaterialCardView click_next;
		public final ImageView ic_play_click;
		public final SwipeRefreshLayout swipe_refresh_music_list;
		
		private MainViewId(Activity a) {
			
			topbarmain = a.findViewById(R.id.topbarmain);
			swipe_refresh_music_list = a.findViewById(R.id.swipe_refresh_music_list);
			content_root = a.findViewById(R.id.content_root);
			player_root = a.findViewById(R.id.player_root);
			music_view = a.findViewById(R.id.music_view);
			player_card = a.findViewById(R.id.player_card);
			name_music = a.findViewById(R.id.name_music);
			duration_music = a.findViewById(R.id.duration_music);
			name_music_player = a.findViewById(R.id.name_music_player);
			name_duration_player = a.findViewById(R.id.name_duration_player);
			ic_click_fx = a.findViewById(R.id.ic_click_fx);
			ic_click_repeat = a.findViewById(R.id.ic_click_repeat);
			ic_click_shuffle = a.findViewById(R.id.ic_click_shuffle);
			timestart = a.findViewById(R.id.timestart);
			slider_music = a.findViewById(R.id.slider_music);
			timeoff = a.findViewById(R.id.timeoff);
			click_prev = a.findViewById(R.id.click_prev);
			click_play = a.findViewById(R.id.click_play);;
			click_next = a.findViewById(R.id.click_next);
			click_dialogs = a.findViewById(R.id.click_dialogs);
			ic_play_click = a.findViewById(R.id.ic_play_click);
		}
		public static MainViewId bind(Activity a) {
			return new MainViewId(a);
		}
	}
}
