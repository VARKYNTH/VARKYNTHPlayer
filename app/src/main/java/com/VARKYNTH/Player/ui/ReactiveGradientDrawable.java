package com.VARKYNTH.Player.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.audiofx.Visualizer;
import android.view.Choreographer;

/**
 * ReactiveGradientDrawable
 * — агрессивная частотно-зависимая визуализация:
 *   бас (красно-оранж) + мощный пульс/шоквейв,
 *   лоу-мид (зелёный), хи-мид (синий), тревл (фиолет) — влияют на цвета и скорость.
 * Ставится как background контейнера (не перекрывает UI).
 */
public class ReactiveGradientDrawable extends Drawable implements Choreographer.FrameCallback {

    /* ===== НАСТРОЙКИ ===== */
    // Общая чувствительность. 2.2–2.8 — агрессивно.
    private float sensitivity = 2.6f;

    // Бас – атака/релиз и порог удара
    private static final float ATTACK_BASS   = 0.65f;
    private static final float RELEASE_BASS  = 0.14f;
    private static final float HIT_THRESHOLD = 0.10f;

    // Влияние полос на динамику
    private static final float SPEED_GAIN_BASS   = 0.55f;
    private static final float SPEED_GAIN_LMID   = 0.25f;
    private static final float SPEED_GAIN_HMID   = 0.32f;
    private static final float SPEED_GAIN_TREBLE = 0.42f;

    private static final float HUE_GAIN_TREBLE   = 0.11f; // высота даёт искры

    private static final float BRIGHT_GAIN_BASS  = 1.15f; // яркость очевидно растёт от баса
    private static final float BRIGHT_GAIN_OTHERS= 0.25f;

    // Пульс и шоквейв
    private static final float PULSE_BASE_FREQ   = 1.0f;
    private static final float PULSE_BASS_GAIN   = 4.5f;
    private static final float PULSE_DECAY       = 0.48f;

    private static final float WAVE_SPEED_BASE   = 1.6f;
    private static final float WAVE_SPEED_GAIN   = 1.5f;
    private static final float WAVE_DECAY        = 1.10f;

    /* ===== СЛУЖЕБНЫЕ ===== */
    public enum Mode { IDLE, PLAYING, REACTIVE }

    private final Paint basePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient linear;
    private boolean running = false;
    private long lastNs = 0L;

    // Базовая «течка»
    private float phase = 0f;      // скорость зависит от полос
    private float hueShift = 0f;   // трясём оттенок (особенно от тревла)

    // Режим/яркость
    private Mode mode = Mode.IDLE;
    private float baseSpeed = 0.20f;
    private float targetBrightness = 0.32f;
    private float brightness = 0.32f;

    // Аудио
    private Visualizer visualizer;
    // Энергии полос (0..1+):
    private float eBass=0f, eLMid=0f, eHMid=0f, eTreble=0f;
    private float sBass=0f, sLMid=0f, sHMid=0f, sTreble=0f; // сглаженные
    private float lastBass = 0f;

    // Пульс и шоквейв
    private float pulsePhase = 0f, pulseLevel = 0f;
    private float hitEnergy = 0f, hitT = 0f;

    public ReactiveGradientDrawable() {
        basePaint.setStyle(Paint.Style.FILL);
        bloomPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.FILL);
    }

    /* ===== ПУБЛИЧНЫЙ API ===== */
    public void setModeIdle()     { mode = Mode.IDLE;     targetBrightness = 0.28f; baseSpeed = 0.10f; }
    public void setModePlaying()  { mode = Mode.PLAYING;  targetBrightness = 0.58f; baseSpeed = 0.24f; }
    public void setModeReactive() { mode = Mode.REACTIVE; targetBrightness = 0.50f; baseSpeed = 0.20f; }
    public void setSensitivity(float s){ sensitivity = Math.max(0.5f, Math.min(3.2f, s)); }

    public void start() {
        if (running) return;
        running = true;
        lastNs = System.nanoTime();
        Choreographer.getInstance().postFrameCallback(this);
    }
    public void stop() {
        if (!running) return;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    /** Микс устройства (0). Для точной реакции лучше вызвать startVisualizerForSession(sessionId). */
    public void startVisualizer(){ startVisualizerForSession(0); }

    public void startVisualizerForSession(int sessionId){
        stopVisualizer();
        try{
            visualizer = new Visualizer(sessionId <= 0 ? 0 : sessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] wf, int sr){
                    // Не основной источник, но чуть подмешиваем:
                    float peak = 0f;
                    for (byte b: wf){
                        float val = Math.abs((b & 0xFF) - 128f)/128f;
                        if (val>peak) peak = val;
                    }
                    eBass = Math.max(eBass*0.8f, peak*0.35f); // чуть «жирка»
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int sr){
                    // Дробим спектр на 4 зоны по индексам бинов.
                    // Визуализатор не даёт стабильную реальную частоту для каждого бина — используем эмпирику.
                    // Берём первые ~12 бинов как бас ~ <200 Гц, далее по нарастающей.
                    int n = fft.length/2;

                    double e0=0, e1=0, e2=0, e3=0;
                    int i0s=2,  i0e=Math.min(12,n);          // BASS ~ <200 Hz
                    int i1s=i0e, i1e=Math.min(i0e+18,n);     // LOW-MID
                    int i2s=i1e, i2e=Math.min(i1e+22,n);     // HIGH-MID
                    int i3s=i2e, i3e=n;                      // TREBLE (верхушка)

                    for (int i=i0s;i<i0e;i++){ int re=fft[2*i], im=fft[2*i+1]; e0 += Math.hypot(re,im); }
                    for (int i=i1s;i<i1e;i++){ int re=fft[2*i], im=fft[2*i+1]; e1 += Math.hypot(re,im); }
                    for (int i=i2s;i<i2e;i++){ int re=fft[2*i], im=fft[2*i+1]; e2 += Math.hypot(re,im); }
                    for (int i=i3s;i<i3e;i++){ int re=fft[2*i], im=fft[2*i+1]; e3 += Math.hypot(re,im); }

                    // нормализация и усиление чувствительности
                    float nb0 = (i0e-i0s); if (nb0<=0) nb0=1;
                    float nb1 = (i1e-i1s); if (nb1<=0) nb1=1;
                    float nb2 = (i2e-i2s); if (nb2<=0) nb2=1;
                    float nb3 = (i3e-i3s); if (nb3<=0) nb3=1;

                    eBass  = (float)Math.min(1.6, (e0/(nb0*128.0))*sensitivity*1.10);
                    eLMid  = (float)Math.min(1.2, (e1/(nb1*128.0))*sensitivity*0.85);
                    eHMid  = (float)Math.min(1.2, (e2/(nb2*128.0))*sensitivity*0.90);
                    eTreble= (float)Math.min(1.2, (e3/(nb3*128.0))*sensitivity*0.95);
                }
            }, Visualizer.getMaxCaptureRate()/2, true, true);
            visualizer.setEnabled(true);
        }catch(Throwable ignore){ visualizer = null; }
    }
    public void stopVisualizer(){
        if (visualizer != null){
            try { visualizer.setEnabled(false); visualizer.release(); } catch(Throwable ignore){}
            visualizer = null;
        }
        eBass=eLMid=eHMid=eTreble=0f;
        sBass=sLMid=sHMid=sTreble=0f;
        lastBass=0f; hitEnergy=hitT=0f; pulseLevel=0f;
    }

    /* ===== Анимация ===== */
    @Override public void doFrame(long nowNs){
        if (!running) return;
        float dt = (nowNs - lastNs)/1_000_000_000f;
        if (dt<0f) dt=0f;
        lastNs = nowNs;

        // Сглаживание полос (бас — быстрая атака)
        sBass    += (eBass    - sBass)   * clamp01(((eBass> sBass)?ATTACK_BASS:RELEASE_BASS)*60f*dt);
        sLMid    += (eLMid    - sLMid)   * clamp01(0.30f*60f*dt);
        sHMid    += (eHMid    - sHMid)   * clamp01(0.34f*60f*dt);
        sTreble  += (eTreble  - sTreble) * clamp01(0.40f*60f*dt);

        // детект «удара» баса -> шоквейв
        if (sBass - lastBass > HIT_THRESHOLD){
            hitEnergy = clamp01(hitEnergy + (sBass*1.2f));
            if (hitT <= 0f) hitT = 0.0001f;
        }
        lastBass = sBass;

        // скорость и яркость: бас — доминирует, прочие добавляют
        float speed = baseSpeed
                + SPEED_GAIN_BASS*sBass
                + SPEED_GAIN_LMID*sLMid
                + SPEED_GAIN_HMID*sHMid
                + SPEED_GAIN_TREBLE*sTreble;

        float brightGoal = targetBrightness
                + BRIGHT_GAIN_BASS*sBass
                + BRIGHT_GAIN_OTHERS*(sLMid*0.6f + sHMid*0.8f + sTreble);

        // ограничим яркость; при REACTIVE разрешаем чуть выше
        float maxB = (mode==Mode.REACTIVE) ? 1.15f : 1.0f;
        if (brightGoal>maxB) brightGoal=maxB;

        brightness += (brightGoal - brightness) * clamp01(0.18f*60f*dt);

        // палитра/перелив
        phase    += speed*dt;                   // скорость градиента
        hueShift += (0.045f + HUE_GAIN_TREBLE*sTreble) * dt; // высокий добавляет «искры»

        // пульс: синус + зависимость от баса
        float f = PULSE_BASE_FREQ + PULSE_BASS_GAIN*sBass;
        pulsePhase += (float)(2*Math.PI*f*dt);
        float rawPulse = 0.5f*(1f+(float)Math.sin(pulsePhase)); // 0..1
        float targetPulse = (float)(Math.pow(sBass,0.85f)*0.70 + rawPulse*0.60);
        pulseLevel += (targetPulse - pulseLevel) * clamp01(PULSE_DECAY*60f*dt);

        // шоквейв движется и затухает
        if (hitT>0f){
            hitT += dt*(WAVE_SPEED_BASE + WAVE_SPEED_GAIN*sBass);
            hitEnergy *= Math.pow(1.0f - 0.016f, (dt*60f)*WAVE_DECAY);
            if (hitEnergy<0.01f){ hitEnergy=0f; hitT=0f; }
        }

        invalidateSelf();
        Choreographer.getInstance().postFrameCallback(this);
    }

    /* ===== Рисование ===== */
    @Override public void draw(Canvas c){
        Rect b = getBounds(); if (b.isEmpty()) return;
        float w=b.width(), h=b.height();
        float cx=b.exactCenterX(), cy=b.exactCenterY();
        float diag=(float)Math.hypot(w,h);

        // Цвета по полосам:
        // Бас: 10–25° (красно-оранж) — сильно «жарит»
        int colBass   = hsv( lerp(10f,25f, clamp01(sBass)),     clamp01(0.95f), clamp01(0.70f+0.30f*sBass), 225 );
        // Лоу-мид: 100–140° (зелёный/бирюза)
        int colLMid   = hsv( lerp(100f,140f, clamp01(sLMid)),   clamp01(0.80f), clamp01(0.60f+0.25f*sLMid), 190 );
        // Хи-мид: 190–220° (синий)
        int colHMid   = hsv( lerp(190f,220f, clamp01(sHMid)),   clamp01(0.85f), clamp01(0.62f+0.25f*sHMid), 190 );
        // Тревл: 280–320° (фиолет/пурпур) — «искра»
        int colTreble = hsv( lerp(280f,320f, clamp01(sTreble)), clamp01(0.95f), clamp01(0.68f+0.30f*sTreble), 200 );

        // Веса (нормализуем), бас чуть бустим
        float w0 = sBass*1.30f, w1=sLMid, w2=sHMid, w3=sTreble*0.9f;
        float wsum = w0+w1+w2+w3; if (wsum<=0f) { w0=1f; wsum=1f; }
        w0/=wsum; w1/=wsum; w2/=wsum; w3/=wsum;

        // Строим многостопный градиент: BASS -> LMID -> HMID -> TREBLE
        int[] cols = new int[]{
                applyGlobalShift(colBass,   hueShift, brightness),
                applyGlobalShift(colLMid,   hueShift, brightness),
                applyGlobalShift(colHMid,   hueShift, brightness),
                applyGlobalShift(colTreble, hueShift, brightness)
        };
        // Позиции — по весам, чтобы доминирующая полоса давала больший сегмент
        float p0 = 0f;
        float p1 = clamp01(p0 + 0.25f + 0.35f*w0);   // больше бас — шире начало
        float p2 = clamp01(p1 + 0.15f + 0.30f*w1);
        float p3 = clamp01(p2 + 0.15f + 0.25f*w2);
        float[] pos = new float[]{ p0, p1, p2, 1f };

        float ang=(phase*360f)%360f;
        double rad=Math.toRadians(ang);
        float r = diag*0.75f;
        float x0 = cx + (float)Math.cos(rad)*r, y0=cy + (float)Math.sin(rad)*r;
        float x1 = cx - (float)Math.cos(rad)*r, y1=cy - (float)Math.sin(rad)*r;

        linear = new LinearGradient(x0,y0,x1,y1, cols, pos, Shader.TileMode.CLAMP);
        basePaint.setShader(linear);
        c.drawRect(b, basePaint);

        // BLOOM — яркий шар в центре (на бас и пульс)
        if (mode != Mode.IDLE){
            float bloomR = diag*(0.18f + 0.62f*pulseLevel);
            int bloomCol = aggressiveBassColor(sBass, pulseLevel);
            int a = (int)(Math.min(1f, 0.35f + 0.70f*(sBass + pulseLevel)) * 255);
            int center = Color.argb(a, Color.red(bloomCol), Color.green(bloomCol), Color.blue(bloomCol));
            int edge   = Color.argb(0,0,0,0);
            RadialGradient bloom = new RadialGradient(cx,cy,bloomR, new int[]{center,edge}, new float[]{0f,1f}, Shader.TileMode.CLAMP);
            bloomPaint.setShader(bloom);
            c.drawRect(b, bloomPaint);
        }

        // SHOCKWAVE — разлетающееся яркое кольцо от удара баса
        if (hitT>0f && hitEnergy>0f){
            float ringR  = diag*(0.16f + 0.56f*hitT);
            float thick  = Math.max(diag*0.08f, ringR*0.22f);
            float innerR = Math.max(0f, ringR - thick*0.5f);
            float outerR = ringR + thick*0.5f;

            int col = aggressiveBassColor(Math.min(1f, hitEnergy*1.3f), Math.min(1f, sBass*1.2f));
            int a   = (int)(Math.min(1f, 0.85f*hitEnergy) * 255);
            int core= Color.argb(a, Color.red(col), Color.green(col), Color.blue(col));
            int mid = Color.argb((int)(a*0.82f), Color.red(col), Color.green(col), Color.blue(col));
            int far = Color.argb(0,0,0,0);

            RadialGradient ring = new RadialGradient(
                    cx, cy, outerR,
                    new int[]{far, far, core, mid, far},
                    new float[]{
                            0f,
                            clamp01((innerR/outerR)-0.05f),
                            clamp01(innerR/outerR),
                            clamp01((ringR/outerR)+0.05f),
                            1f
                    },
                    Shader.TileMode.CLAMP
            );
            ringPaint.setShader(ring);
            c.drawRect(b, ringPaint);
        }
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; }

    /* ===== Утилиты цвета/мата ===== */
    private static float clamp01(float v){ return v<0f?0f:(v>1f?1f:v); }
    private static float lerp(float a,float b,float t){ return a + (b-a)*t; }

    private static int hsv(float h, float s, float v, int a){
        return Color.HSVToColor(a, new float[]{ (h%360f+360f)%360f, clamp01(s), clamp01(v) });
    }
    private static int applyGlobalShift(int color, float hueShift, float bright){
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift*360f) % 360f;
        // миксируем яркость с глобальным уровнем
        hsv[2] = clamp01(hsv[2]*0.45f + bright*0.55f);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    /** Агрессивный цвет для баса (красно-оранж, уходит в белый при сверхударах). */
    private static int aggressiveBassColor(float bass, float pulse){
        float hue = lerp(10f, 25f, clamp01(bass));               // красно-оранж
        float sat = clamp01(0.90f + 0.20f*bass);
        float val = clamp01(0.78f + 0.35f*(bass + pulse*0.6f));  // яркость от баса/пульса
        int col = hsv(hue, sat, val, 255);
        // клэмп к «белому» при экстремуме (ощущение клиппинга удара)
        float k = Math.max(0f, (bass + pulse) - 1.05f);
        if (k>0f){
            int r = Color.red(col), g=Color.green(col), b=Color.blue(col);
            r = Math.min(255, (int)(r*(1f+0.6f*k)));
            g = Math.min(255, (int)(g*(1f+0.55f*k)));
            b = Math.min(255, (int)(b*(1f+0.5f*k)));
            col = Color.rgb(r,g,b);
        }
        return col;
    }
}