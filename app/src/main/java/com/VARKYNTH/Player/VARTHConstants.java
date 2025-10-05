package com.VARKYNTH.Player;

public final class VARTHConstants {
    private VARTHConstants() {}

    // Service + уведомления
    public static final String CHANNEL_ID = "VARTHMusicServiceChannel";
    public static final String FX_PREFS   = "fx_prefs";

    // Actions (совместимы с прежними)
    public static final String ACTION_START   = "START";
    public static final String ACTION_NEXT    = "NEXT";
    public static final String ACTION_PREV    = "PRE";
    public static final String ACTION_STOP    = "STOP";
    public static final String ACTION_TOGGLE  = "TOGGLE";
    public static final String ACTION_REPEAT  = "REPEAT";
    public static final String ACTION_SHUFFLE = "SHUFFLE";

    // Local broadcasts
    public static final String BR_SONG_CONTROL = "com.dplay.SONG_CONTROL";
    public static final String BR_SONG_SEEK    = "com.dplay.SONG_SEEK";
    public static final String BR_SONG_DATA    = "com.dplay.SONG_DATA";
    public static final String BR_SONG_STATE   = "com.dplay.SONG_STATE";
    public static final String BR_ERR          = "com.dplay.ERR";
}