package vn.cuong.lichviet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Vẽ nội dung cho tất cả các loại widget. */
public final class WidgetRenderer {
    private WidgetRenderer() {}

    static final String[] THU = {"Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
    static final String[] THU_NGAN = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};

    @SuppressWarnings("unchecked")
    static final Class<? extends BaseWidget>[] PROVIDERS = new Class[]{
            ClockWidget.class, CompactWidget.class, WeatherWidget.class, CalendarWidget.class};

    /* ---------------- Cấu hình widget ---------------- */
    public static final class Cfg {
        int opacity = 60;          // 0..100 (0 = trong suốt hoàn toàn)
        String theme = "dark";     // dark | black | blue | light
        String text = "auto";      // auto | white | dark
        String date = "full";      // full | long | short
        boolean h24 = true, bold = false, lunar = true, weather = true, rain = true, place = true;

        static Cfg load(Context c, int id) {
            Cfg f = new Cfg();
            try {
                String s = Weather.prefs(c).getString("wcfg_" + id, null);
                if (s == null) return f;
                JSONObject j = new JSONObject(s);
                f.opacity = j.optInt("opacity", f.opacity);
                f.theme = j.optString("theme", f.theme);
                f.text = j.optString("text", f.text);
                f.date = j.optString("date", f.date);
                f.h24 = j.optBoolean("h24", f.h24);
                f.bold = j.optBoolean("bold", f.bold);
                f.lunar = j.optBoolean("lunar", f.lunar);
                f.weather = j.optBoolean("weather", f.weather);
                f.rain = j.optBoolean("rain", f.rain);
                f.place = j.optBoolean("place", f.place);
            } catch (Exception ignored) {}
            return f;
        }

        int bgColor() {
            switch (theme) {
                case "black": return Color.BLACK;
                case "blue": return 0xFF1E3A66;
                case "light": return Color.WHITE;
                default: return 0xFF0F1B2D;
            }
        }
        boolean darkText() {
            if ("white".equals(text)) return false;
            if ("dark".equals(text)) return true;
            return "light".equals(theme) && opacity >= 35;
        }
        int fg() { return darkText() ? 0xFF1B2533 : 0xFFFFFFFF; }
        int fg2() { return darkText() ? 0xB31B2533 : 0xCCFFFFFF; }
        int accent() { return darkText() ? 0xFFC8741A : 0xFFFFB547; }
    }

    public static void saveCfg(Context c, int id, String json) {
        Weather.prefs(c).edit().putString("wcfg_" + id, json).apply();
    }
    public static String cfgJson(Context c, int id) {
        return Weather.prefs(c).getString("wcfg_" + id, "{}");
    }

    /* ---------------- Cập nhật ---------------- */
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<? extends BaseWidget> cls : PROVIDERS) {
            String type = typeOf(cls);
            for (int id : m.getAppWidgetIds(new ComponentName(c, cls))) render(c, m, id, type);
        }
    }

    public static String typeOf(Class<?> cls) {
        if (cls == CompactWidget.class) return "compact";
        if (cls == WeatherWidget.class) return "weather";
        if (cls == CalendarWidget.class) return "calendar";
        return "clock";
    }
    public static Class<? extends BaseWidget> classOf(String type) {
        switch (type) {
            case "compact": return CompactWidget.class;
            case "weather": return WeatherWidget.class;
            case "calendar": return CalendarWidget.class;
            default: return ClockWidget.class;
        }
    }

    public static String typeOfId(Context c, int id) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        try {
            ComponentName cn = m.getAppWidgetInfo(id).provider;
            for (Class<? extends BaseWidget> cls : PROVIDERS)
                if (cn.getClassName().equals(cls.getName())) return typeOf(cls);
        } catch (Exception ignored) {}
        return "clock";
    }

    public static void render(Context c, AppWidgetManager m, int id, String type) {
        Cfg f = Cfg.load(c, id);
        int layout;
        switch (type) {
            case "compact": layout = R.layout.widget_compact; break;
            case "weather": layout = R.layout.widget_weather; break;
            case "calendar": layout = R.layout.widget_calendar; break;
            default: layout = R.layout.widget_clock;
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);

        // Nền: màu + độ trong suốt
        v.setInt(R.id.bg, "setColorFilter", f.bgColor());
        v.setInt(R.id.bg, "setImageAlpha", Math.round(f.opacity * 2.55f));

        // Mở app khi chạm
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Calendar now = Calendar.getInstance();
        int d = now.get(Calendar.DAY_OF_MONTH), mo = now.get(Calendar.MONTH) + 1, y = now.get(Calendar.YEAR);
        int wd = now.get(Calendar.DAY_OF_WEEK) - 1;
        Lunar.Date L = Lunar.fromSolar(d, mo, y);
        JSONObject w = Weather.cached(c);

        if ("calendar".equals(type)) {
            renderCalendar(c, v, f, d, mo, y, wd, L, w);
        } else {
            if ("weather".equals(type)) {
                renderWeatherWidget(c, v, f, w, id);
            } else {
                // Đồng hồ
                v.setViewVisibility(R.id.clock_thin, f.bold ? View.GONE : View.VISIBLE);
                v.setViewVisibility(R.id.clock_bold, f.bold ? View.VISIBLE : View.GONE);
                String fmt = f.h24 ? "HH:mm" : "h:mm";
                for (int cid : new int[]{R.id.clock_thin, R.id.clock_bold}) {
                    v.setCharSequence(cid, "setFormat24Hour", fmt);
                    v.setCharSequence(cid, "setFormat12Hour", fmt);
                    v.setTextColor(cid, f.fg());
                }
                v.setTextViewText(R.id.date, dateText(f.date, d, mo, y, wd));
                v.setTextColor(R.id.date, f.fg());
                v.setViewVisibility(R.id.lunar, f.lunar ? View.VISIBLE : View.GONE);
                v.setTextViewText(R.id.lunar, lunarText(L, "compact".equals(type)));
                v.setTextColor(R.id.lunar, f.fg2());
                if ("compact".equals(type)) v.setInt(R.id.divider, "setColorFilter", f.fg2());
                renderCurrent(c, v, f, w, "clock".equals(type));
                if ("clock".equals(type)) renderRain(v, f, w);
            }
        }
        m.updateAppWidget(id, v);
    }

    static String dateText(String style, int d, int m, int y, int wd) {
        switch (style) {
            case "long": return THU[wd] + ", " + d + " tháng " + m;
            case "short": return THU_NGAN[wd] + " · " + two(d) + "/" + two(m);
            default: return THU[wd] + ", " + two(d) + "/" + two(m) + "/" + y;
        }
    }

    static String lunarText(Lunar.Date L, boolean shortForm) {
        String dm = L.day + "/" + L.month + (L.leap ? "N" : "");
        if (shortForm) return "ÂL " + dm + " · " + Lunar.canChiYear(L.year);
        String extra = L.day == 1 ? " · Mùng 1" : (L.day == 15 ? " · Rằm" : "");
        return "Âm lịch " + dm + " · " + Lunar.canChiYear(L.year) + extra;
    }

    static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    private static void renderCurrent(Context c, RemoteViews v, Cfg f, JSONObject w, boolean full) {
        boolean show = f.weather && w != null;
        v.setViewVisibility(R.id.weather_box, show ? View.VISIBLE : View.GONE);
        if (!show) return;
        try {
            JSONObject cur = w.getJSONObject("current");
            int code = cur.optInt("weather_code", 3);
            v.setImageViewResource(R.id.wx_icon, Weather.iconRes(code, cur.optInt("is_day", 1) == 1));
            v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
            v.setTextColor(R.id.wx_temp, f.fg());
            if (full) {
                JSONObject dl = w.getJSONObject("daily");
                String hilo = Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0)) + "°/"
                        + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "°";
                v.setTextViewText(R.id.wx_desc, Weather.desc(code) + " · " + hilo);
                v.setTextColor(R.id.wx_desc, f.fg2());
                String place = shortPlace(Weather.place(c));
                v.setViewVisibility(R.id.wx_place, f.place && !place.isEmpty() ? View.VISIBLE : View.GONE);
                v.setTextViewText(R.id.wx_place, place);
                v.setTextColor(R.id.wx_place, f.fg2());
            }
        } catch (Exception ignored) {}
    }

    private static void renderRain(RemoteViews v, Cfg f, JSONObject w) {
        String hint = (f.rain && w != null) ? Weather.rainHint(w) : "";
        v.setViewVisibility(R.id.rain_box, hint.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.rain, hint);
        v.setTextColor(R.id.rain, f.fg2());
    }

    static String shortPlace(String p) {
        if (p == null) return "";
        int i = p.indexOf(',');
        return i > 0 ? p.substring(0, i) : p;
    }

    private static final int[][] H_IDS = {
            {R.id.h0, R.id.h0_time, R.id.h0_icon, R.id.h0_temp, R.id.h0_pop},
            {R.id.h1, R.id.h1_time, R.id.h1_icon, R.id.h1_temp, R.id.h1_pop},
            {R.id.h2, R.id.h2_time, R.id.h2_icon, R.id.h2_temp, R.id.h2_pop},
            {R.id.h3, R.id.h3_time, R.id.h3_icon, R.id.h3_temp, R.id.h3_pop},
            {R.id.h4, R.id.h4_time, R.id.h4_icon, R.id.h4_temp, R.id.h4_pop}};

    private static void renderWeatherWidget(Context c, RemoteViews v, Cfg f, JSONObject w, int widgetId) {
        Intent r = new Intent(c, WeatherWidget.class).setAction(BaseWidget.ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.btn_refresh, PendingIntent.getBroadcast(c, 1000 + widgetId, r,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setInt(R.id.btn_refresh, "setColorFilter", f.fg2());
        for (int id : new int[]{R.id.wx_temp, R.id.wx_desc}) v.setTextColor(id, f.fg());
        for (int id : new int[]{R.id.wx_hilo, R.id.wx_place, R.id.wx_updated}) v.setTextColor(id, f.fg2());

        if (w == null) {
            v.setTextViewText(R.id.wx_temp, "--°");
            v.setTextViewText(R.id.wx_desc, "Mở app để lấy vị trí");
            v.setViewVisibility(R.id.hourly, View.INVISIBLE);
            renderRain(v, f, null);
            return;
        }
        try {
            JSONObject cur = w.getJSONObject("current"), dl = w.getJSONObject("daily"), h = w.getJSONObject("hourly");
            int code = cur.optInt("weather_code", 3);
            v.setImageViewResource(R.id.wx_icon, Weather.iconRes(code, cur.optInt("is_day", 1) == 1));
            v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
            v.setTextViewText(R.id.wx_desc, Weather.desc(code) + " · cảm giác " + Math.round(cur.optDouble("apparent_temperature", 0)) + "°");
            v.setTextViewText(R.id.wx_hilo, "Cao " + Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0))
                    + "° · Thấp " + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0))
                    + "° · Ẩm " + cur.optInt("relative_humidity_2m", 0) + "%");
            String place = shortPlace(Weather.place(c));
            v.setTextViewText(R.id.wx_place, f.place ? (place.isEmpty() ? "Vị trí hiện tại" : place) : "");
            Calendar t = Calendar.getInstance();
            t.setTimeInMillis(Weather.cachedTs(c));
            v.setTextViewText(R.id.wx_updated, "Cập nhật " + two(t.get(Calendar.HOUR_OF_DAY)) + ":" + two(t.get(Calendar.MINUTE)));

            v.setViewVisibility(R.id.hourly, View.VISIBLE);
            JSONArray ht = h.getJSONArray("time");
            int i0 = Weather.currentHourIndex(w);
            for (int k = 0; k < 5; k++) {
                int i = i0 + 1 + k * 2;
                int[] ids = H_IDS[k];
                if (i >= ht.length()) { v.setViewVisibility(ids[0], View.INVISIBLE); continue; }
                v.setViewVisibility(ids[0], View.VISIBLE);
                v.setTextViewText(ids[1], Weather.hourOf(ht.getString(i)) + "h");
                v.setImageViewResource(ids[2], Weather.iconRes(h.getJSONArray("weather_code").optInt(i, 3),
                        h.getJSONArray("is_day").optInt(i, 1) == 1));
                v.setTextViewText(ids[3], Math.round(h.getJSONArray("temperature_2m").optDouble(i, 0)) + "°");
                double pop = h.getJSONArray("precipitation_probability").optDouble(i, Double.NaN);
                double mm = h.getJSONArray("precipitation").optDouble(i, 0);
                String ps = !Double.isNaN(pop) ? Math.round(pop) + "%" : (mm >= 0.1 ? String.format(java.util.Locale.US, "%.1fmm", mm) : "");
                v.setTextViewText(ids[4], ps);
                v.setTextColor(ids[1], f.fg2());
                v.setTextColor(ids[3], f.fg());
                v.setTextColor(ids[4], f.darkText() ? 0xFF2F7FD9 : 0xFF7CC0FF);
            }
        } catch (Exception ignored) {}
        renderRain(v, f, w);
    }

    private static void renderCalendar(Context c, RemoteViews v, Cfg f, int d, int mo, int y, int wd, Lunar.Date L, JSONObject w) {
        v.setTextViewText(R.id.cal_weekday, THU[wd]);
        v.setTextViewText(R.id.cal_day, String.valueOf(d));
        v.setTextViewText(R.id.cal_month, "Tháng " + mo + ", " + y);
        v.setTextViewText(R.id.cal_lday, String.valueOf(L.day));
        v.setTextViewText(R.id.cal_lmonth, "Tháng " + Lunar.THANG[L.month] + (L.leap ? " (N)" : ""));
        v.setTextViewText(R.id.cal_lyear, "Năm " + Lunar.canChiYear(L.year));
        v.setTextColor(R.id.cal_weekday, wd == 0 ? 0xFFFF6B6B : f.fg());
        v.setTextColor(R.id.cal_day, f.fg());
        v.setTextColor(R.id.cal_month, f.fg2());
        v.setTextColor(R.id.cal_lday, f.accent());
        v.setTextColor(R.id.cal_lmonth, f.fg2());
        v.setTextColor(R.id.cal_lyear, f.fg2());
        v.setInt(R.id.divider, "setColorFilter", f.fg2());
        int vis = f.lunar ? View.VISIBLE : View.GONE;
        v.setViewVisibility(R.id.cal_lday, vis);
        v.setViewVisibility(R.id.cal_lmonth, vis);
        v.setViewVisibility(R.id.cal_lyear, vis);
        v.setViewVisibility(R.id.divider, vis);
        renderCurrent(c, v, f, w, false);
    }

    /** Hẹn giờ cập nhật lúc 0h để đổi ngày dương/âm. */
    public static void scheduleMidnight(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, 1);
        t.set(Calendar.HOUR_OF_DAY, 0);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 15);
        Intent i = new Intent(c, UpdateReceiver.class).setAction(UpdateReceiver.ACTION_MIDNIGHT);
        PendingIntent pi = PendingIntent.getBroadcast(c, 77, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.set(AlarmManager.RTC, t.getTimeInMillis(), pi);
    }
}
