package com.su.launcherlayout;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** 跨页面共享的操作日志（内存 + SharedPreferences 持久化）。 */
final class UiLog {

    private static final String PREF = "llb";
    private static final String KEY = "log";
    private static final int MAX = 400;
    /** 日志保留期：360 天，超期自动清除。 */
    private static final long EXPIRE_MS = 360L * 24L * 3600L * 1000L;

    static final ArrayList<String> LINES = new ArrayList<String>();

    private static TextView sView;
    private static ScrollView sScroll;

    private UiLog() {
    }

    static void bind(ScrollView scroll, TextView view) {
        sScroll = scroll;
        sView = view;
        render();
    }

    static void unbind() {
        sScroll = null;
        sView = null;
    }

    static void log(String s) {
        final long now = System.currentTimeMillis();
        final String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(now));
        // 存成 "时间戳|显示文本"，时间戳用于 360 天过期清理，显示时会被剥掉
        String entry = now + "|" + t + "  " + s;
        synchronized (LINES) {
            LINES.add(entry);
            while (LINES.size() > MAX) {
                LINES.remove(0);
            }
        }
        render();
    }

    private static String display(String entry) {
        int i = entry.indexOf('|');
        return i > 0 ? entry.substring(i + 1) : entry;
    }

    private static long epochOf(String entry) {
        int i = entry.indexOf('|');
        if (i <= 0) {
            return -1L;
        }
        try {
            return Long.parseLong(entry.substring(0, i));
        } catch (Throwable t) {
            return -1L;
        }
    }

    static void render() {
        if (sView == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (LINES) {
            for (String l : LINES) {
                sb.append(display(l)).append('\n');
            }
        }
        sView.setText(sb.toString());
        if (sScroll != null) {
            sScroll.post(new Runnable() {
                @Override
                public void run() {
                    sScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    static void save(Context c) {
        try {
            StringBuilder sb = new StringBuilder();
            synchronized (LINES) {
                for (String l : LINES) {
                    sb.append(l).append('\n');
                }
            }
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            sp.edit().putString(KEY, sb.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 手动清空（保留跨启动日志，但允许用户自己清）。 */
    static void clear(Context c) {
        synchronized (LINES) {
            LINES.clear();
        }
        try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply();
        } catch (Throwable ignored) {
        }
        render();
    }

    static void load(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String s = sp.getString(KEY, "");
            if (s == null || s.isEmpty()) {
                return;
            }
            String[] lines = s.split("\n");
            int from = Math.max(0, lines.length - MAX);
            final long now = System.currentTimeMillis();
            synchronized (LINES) {
                if (!LINES.isEmpty()) {
                    return; // 已加载过
                }
                for (int i = from; i < lines.length; i++) {
                    if (lines[i].length() == 0) {
                        continue;
                    }
                    final long ep = epochOf(lines[i]);
                    // 老版本写的行没有时间戳 → 丢掉（顺便清掉历史噪音）
                    if (ep <= 0) {
                        continue;
                    }
                    // 超过 360 天自动清除
                    if (now - ep > EXPIRE_MS) {
                        continue;
                    }
                    LINES.add(lines[i]);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
