// /storage/emulated/0/.sketchware/data/<ID>/files/java/com/VARKYNTH/Player/ui/VGlobalDepth.java
package com.VARKYNTH.Player.ui;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AbsListView;

import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Глобальная глубина/параллакс без изменения стилей и цветов.
 * - XML не трогаем.
 * - TextView/ImageView не двигаем.
 * - Для списков глубину даём элементам, а не контейнеру.
 * Подключение: VGlobalDepth.attach(this); // после setContentView(...)
 */
public final class VGlobalDepth {
    private VGlobalDepth() {}

    // ======== ПУБЛИЧНЫЙ API ========
    /** Включить эффект на активности */
    public static void attach(Activity a) { I().start(a); }

    /** Выключить эффект (напр., в onDestroy) */
    public static void detach() { I().stop(); }

    /** Назначить глубину конкретному View. 0..2. Текст/картинки игнорируются. */
    public static void setDepth(View v, float d) {
        if (v == null) return;
        I().depth.put(v, clamp(d, 0f, 2f));
    }

    /** Снять кастомную глубину у View */
    public static void clearDepth(View v) {
        if (v == null) return;
        I().depth.remove(v);
    }

    // ======== SINGLETON ========
    private static VGlobalDepth s;
    private static VGlobalDepth I() { return s != null ? s : (s = new VGlobalDepth()); }

    // ======== СОСТОЯНИЕ ========
    private WeakReference<Activity> actRef;
    private final Map<View, Float> depth = new IdentityHashMap<>();

    private SensorManager sm;
    private Sensor accel;

    // целевые значения от сенсора/пальца
    private float tiltTX = 0f, tiltTY = 0f;
    // сглаженные значения
    private float tiltX = 0f, tiltY = 0f;
    // параллакс от пальца
    private float touchX = 0f, touchY = 0f;

    // базовый сдвиг (px) на глубину=1
    private float maxShiftPx = 8f;

    private boolean running = false;
    private long lastFrameNs = 0;

    // ======== СЕНСОР ========
    private final SensorEventListener sensorL = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            // нормализация в [-1..1]
            float nx = clamp(e.values[0] / 9.81f, -1f, 1f);
            float ny = clamp(e.values[1] / 9.81f, -1f, 1f);
            // инверсия, чтобы “свет/наклон” были интуитивны
            tiltTX = -nx;
            tiltTY = -ny;
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    // ======== КАДРОВЫЙ ЦИКЛ ========
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;

            float dt = lastFrameNs == 0 ? 0.016f : (frameTimeNanos - lastFrameNs) / 1_000_000_000f;
            lastFrameNs = frameTimeNanos;

            // экспоненциальное сглаживание; tau ≈ 120 мс
            final float tau = 0.12f;
            float alpha = 1f - (float) Math.exp(-dt / tau);

            tiltX += (tiltTX + touchX - tiltX) * alpha;
            tiltY += (tiltTY + touchY - tiltY) * alpha;

            applyTranslations();

            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    // ======== СТАРТ/СТОП ========
    private void start(Activity a) {
        stop();
        actRef = new WeakReference<>(a);

        maxShiftPx = 8f * a.getResources().getDisplayMetrics().density;

        // корень
        View root = a.getWindow().getDecorView().findViewById(android.R.id.content);

        // клиппинг у контейнеров, но не у скролл-контейнеров
        if (root instanceof ViewGroup) {
            enforceClipping((ViewGroup) root);
            // авто-назначение глубины только контейнерам/прочим, текст/изображения пропускаем
            autoAssignDepths((ViewGroup) root, 0);
            // добавить хуки для списков
            walkAndHook((ViewGroup) root);
        }

        // мягкий параллакс от пальца; не потребляем события
        root.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    float cx = v.getWidth() * 0.5f, cy = v.getHeight() * 0.5f;
                    touchX = clamp((ev.getX() - cx) / Math.max(cx, 1f) * 0.4f, -0.4f, 0.4f);
                    touchY = clamp((ev.getY() - cy) / Math.max(cy, 1f) * 0.4f, -0.4f, 0.4f);
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    touchX = touchY = 0f;
                    break;
            }
            return false;
        });

        // сенсор
        sm = (SensorManager) a.getSystemService(Activity.SENSOR_SERVICE);
        if (sm != null) accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sm != null && accel != null) {
            sm.registerListener(sensorL, accel, SensorManager.SENSOR_DELAY_GAME);
        }

        running = true;
        lastFrameNs = 0;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        if (sm != null) sm.unregisterListener(sensorL);
        sm = null; accel = null;
        depth.clear();
        actRef = null;
        lastFrameNs = 0;
        touchX = touchY = 0f;
        tiltTX = tiltTY = 0f;
        tiltX = tiltY = 0f;
    }

    // ======== ПРИМЕНЕНИЕ СМЕЩЕНИЙ ========
    private void applyTranslations() {
        Activity a = actRef != null ? actRef.get() : null;
        if (a == null) return;

        for (Map.Entry<View, Float> e : depth.entrySet()) {
            View v = e.getKey();
            if (v == null || v.getWindowToken() == null) continue;

            // защита: не трогаем текст/картинки и сами скролл-контейнеры
            if (v instanceof TextView || v instanceof ImageView || isScrollContainer(v)) continue;

            float d = e.getValue();
            float k = maxShiftPx * d;

            v.setTranslationX(k * tiltX);
            v.setTranslationY(k * tiltY);
        }
    }

    // ======== АВТО-НАЗНАЧЕНИЕ ГЛУБИНЫ ========
    private void autoAssignDepths(ViewGroup g, int level) {
        final float base = 0.30f, step = 0.18f;
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof TextView || v instanceof ImageView) {
                // не трогаем
            } else if (isScrollContainer(v)) {
                // контейнеры списков не двигаем
            } else if (v instanceof ViewGroup) {
                setIfAbsent(v, clamp(base + level * step * 0.8f, 0f, 2f));
                autoAssignDepths((ViewGroup) v, level + 1);
            } else {
                setIfAbsent(v, clamp(base + level * step * 0.4f, 0f, 2f));
            }
        }
    }

    // ======== КЛИППИНГ ========
    private void enforceClipping(ViewGroup g) {
        if (!isScrollContainer(g)) {
            g.setClipChildren(true);
            g.setClipToPadding(true);
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof ViewGroup) enforceClipping((ViewGroup) v);
        }
    }

    // ======== ХУКИ ДЛЯ СПИСКОВ ========
    private void walkAndHook(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof AbsListView) {
                hookAbsList((AbsListView) v);
            } else if (v instanceof RecyclerView) {
                hookRecycler((RecyclerView) v);
            }
            if (v instanceof ViewGroup) walkAndHook((ViewGroup) v);
        }
    }

    private void hookAbsList(AbsListView lv) {
        // гарантированно не двигаем сам ListView
        depth.remove(lv);

        lv.getViewTreeObserver().addOnGlobalLayoutListener(() -> applyListDepth(lv));
        lv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                applyListDepth(lv);
            }
        });
    }

    private void applyListDepth(AbsListView lv) {
        final int childCount = lv.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View row = lv.getChildAt(i);
            if (row == null) continue;
            // берём первый дочерний как “контент” строки
            View target = (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0)
                    ? ((ViewGroup) row).getChildAt(0) : row;
            // не трогаем чистый текст/картинку
            if (target instanceof TextView || target instanceof ImageView) continue;
            setIfAbsent(target, 0.9f); // базовая глубина для строк
        }
    }

    private void hookRecycler(RecyclerView rv) {
        // сам RecyclerView не двигаем
        depth.remove(rv);

        rv.setItemViewCacheSize(0);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) { applyRecyclerDepth(r); }
        });
        rv.getViewTreeObserver().addOnGlobalLayoutListener(() -> applyRecyclerDepth(rv));
    }

    private void applyRecyclerDepth(RecyclerView rv) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            View item = rv.getChildAt(i);
            if (item == null) continue;
            View target = (item instanceof ViewGroup && ((ViewGroup) item).getChildCount() > 0)
                    ? ((ViewGroup) item).getChildAt(0) : item;
            if (target instanceof TextView || target instanceof ImageView) continue;
            setIfAbsent(target, 1.0f);
        }
    }

    // ======== УТИЛИТЫ ========
    private static boolean isScrollContainer(View v) {
        if (v == null) return false;
        if (v instanceof AbsListView) return true;              // ListView, GridView
        if (v instanceof RecyclerView) return true;             // RecyclerView
        if (v instanceof android.widget.ScrollView) return true;
        if (v instanceof androidx.core.widget.NestedScrollView) return true;
        // ViewPager / ViewPager2 по имени класса
        String name = v.getClass().getName();
        return name.contains("ViewPager");
    }

    private void setIfAbsent(View v, float d) {
        if (v != null && !depth.containsKey(v)) depth.put(v, d);
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}