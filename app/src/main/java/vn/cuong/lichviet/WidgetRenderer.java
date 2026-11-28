package vn.cuong.lichviet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/** Vẽ nội dung cho tất cả các loại widget (phong cách Material You, sáng/tối theo máy). */
public final class WidgetRenderer {
    private WidgetRenderer() {}

    static final String[] THU = {"Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
    static final String[] THU_NGAN = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};

    @SuppressWarnings("unchecked")
    static final Class<? extends BaseWidget>[] PROVIDERS = new Class[]{
            ClockWidget.class, DateWidget.class, WeatherWidget.class, WeatherSmallWidget.class,
            CalendarWidget.class, CalendarWideWidget.class, CompactWidget.class, SummaryWidget.class, SummaryQuoteWidget.class, MapWidget.class};

    /** Đang tải thời tiết (để hiện "Đang cập nhật…" trên widget). */
    static volatile boolean refreshing = false;

    /* ---------------- Cấu hình từng widget ---------------- */
    public static final class Cfg {
        int opacity = 95;          // 0..100
        String theme = "auto";     // auto | light | dark
        String accent = "blue";    // blue | green | orange | purple | system
        String text = "auto";      // auto | white | dark
        String date = "full";      // full | long | short
        boolean h24 = true, bold = true, lunar = true, weather = true, week = true, place = true, tuoi = true;
        String style = "glass";    // widget 5×3: glass | clear
        String tone = "auto";      // widget 5×3: auto | dark | light

        static Cfg load(Context c, int id) {
            Cfg f = new Cfg();
            try {
                String s = Weather.prefs(c).getString("wcfg_" + id, null);
                if (s == null) return f;
                JSONObject j = new JSONObject(s);
                f.opacity = j.optInt("opacity", f.opacity);
                f.theme = j.optString("theme", f.theme);
                f.accent = j.optString("accent", f.accent);
                f.text = j.optString("text", f.text);
                f.date = j.optString("date", f.date);
                f.h24 = j.optBoolean("h24", f.h24);
                f.bold = j.optBoolean("bold", f.bold);
                f.lunar = j.optBoolean("lunar", f.lunar);
                f.weather = j.optBoolean("weather", f.weather);
                f.week = j.optBoolean("week", f.week);
                f.place = j.optBoolean("place", f.place);
                f.tuoi = j.optBoolean("tuoi", f.tuoi);
                f.style = j.optString("style", f.style);
                f.tone = j.optString("tone", f.tone);
            } catch (Exception ignored) {}
            return f;
        }
        /** Nền gần như trong suốt → chữ trắng nổi trên hình nền. */
        boolean onWall() { return "white".equals(text) || ("auto".equals(text) && opacity < 35); }
    }

    public static void saveCfg(Context c, int id, String json) { Weather.prefs(c).edit().putString("wcfg_" + id, json).apply(); }
    public static String cfgJson(Context c, int id) { return Weather.prefs(c).getString("wcfg_" + id, "{}"); }

    /* ---------------- Bảng màu ---------------- */
    static final class Pal {
        int surface, fg, fg2, acc, red, gold, rain, chipA, onChipA, chipB, onChipB, chipAlpha;
    }

    static int accentOf(Context c, String a, boolean dark) {
        if ("system".equals(a) && Build.VERSION.SDK_INT >= 31) {
            try { return c.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600); }
            catch (Exception ignored) {}
        }
        switch (a) {
            case "green": return dark ? 0xFF7FD8B0 : 0xFF1F7A5C;
            case "orange": return dark ? 0xFFF4A65A : 0xFFC2620E;
            case "purple": return dark ? 0xFFCDBDFF : 0xFF6B4FBB;
            default: return dark ? 0xFFB9C8FF : 0xFF2D4BA0;
        }
    }

    static Pal pal(Context c, Cfg f, boolean dark) {
        Pal p = new Pal();
        p.surface = dark ? 0xFF1C212B : 0xFFF6F8FD;
        if (f.onWall()) {
            p.fg = 0xFFFFFFFF; p.fg2 = 0xE6FFFFFF; p.acc = 0xFFFFFFFF; p.red = 0xFFFFC2B8; p.gold = 0xFFFFD98A; p.rain = 0xFFCFE6FF;
            p.chipA = 0xFFFFFFFF; p.onChipA = 0xFFFFFFFF; p.chipB = 0xFFFFFFFF; p.onChipB = 0xFFFFFFFF; p.chipAlpha = 64;
            return p;
        }
        boolean darkText = "dark".equals(f.text) || (!"white".equals(f.text) && !dark);
        boolean darkSurface = dark;
        p.fg = darkText ? 0xFF1B1F2A : 0xFFE7EAF2;
        p.fg2 = darkText ? 0xFF5A6275 : 0xFFA9B1C2;
        p.acc = accentOf(c, f.accent, !darkText);
        p.red = darkText ? 0xFFC0392B : 0xFFFF8A7A;
        p.gold = darkText ? 0xFFB8760A : 0xFFFFC94D;
        p.rain = darkText ? 0xFF2D6FD8 : 0xFF8DB8FF;
        p.chipA = darkSurface ? 0xFF4A3220 : 0xFFFFE2C7;   // âm lịch (cam đào)
        p.onChipA = darkSurface ? 0xFFFFDCC0 : 0xFF5C3410;
        p.chipB = darkSurface ? 0xFF26345F : 0xFFDDE5FF;   // tuần / mưa (xanh)
        p.onChipB = darkSurface ? 0xFFD6E0FF : 0xFF1F3A80;
        p.chipAlpha = 255;
        return p;
    }

    /** Tô màu: nếu theo máy (Android 12+) thì để launcher tự đổi sáng/tối. */
    static final class Painter {
        final RemoteViews v; final Pal L, D; final boolean auto, useDark;
        Painter(RemoteViews v, Pal L, Pal D, boolean auto, boolean useDark) { this.v = v; this.L = L; this.D = D; this.auto = auto; this.useDark = useDark; }
        void col(int id, String method, int light, int dark) {
            if (auto && Build.VERSION.SDK_INT >= 31) v.setColorInt(id, method, light, dark);
            else v.setInt(id, method, useDark ? dark : light);
        }
        void fg(int id) { col(id, "setTextColor", L.fg, D.fg); }
        void fg2(int id) { col(id, "setTextColor", L.fg2, D.fg2); }
        void acc(int id) { col(id, "setTextColor", L.acc, D.acc); }
        void red(int id) { col(id, "setTextColor", L.red, D.red); }
        void gold(int id) { col(id, "setTextColor", L.gold, D.gold); }
        /** Màu theo quy tắc ngày: 1 đỏ, 2 vàng; 0 → màu mặc định (fg hoặc accent). */
        void day(int id, int kind, boolean accent) {
            if (kind == 1) red(id); else if (kind == 2) gold(id); else if (accent) acc(id); else fg(id);
        }
        void rain(int id) { col(id, "setTextColor", L.rain, D.rain); }
        void tint(int id, boolean accent) { col(id, "setColorFilter", accent ? L.acc : L.fg2, accent ? D.acc : D.fg2); }
        /** kind 0 = cam đào (âm lịch), 1 = xanh (tuần, mưa) */
        void chip(int bgId, int textId, int kind) {
            col(bgId, "setColorFilter", kind == 0 ? L.chipA : L.chipB, kind == 0 ? D.chipA : D.chipB);
            v.setInt(bgId, "setImageAlpha", L.chipAlpha);
            col(textId, "setTextColor", kind == 0 ? L.onChipA : L.onChipB, kind == 0 ? D.onChipA : D.onChipB);
        }
    }

    static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /* ---------------- Cập nhật ---------------- */
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<? extends BaseWidget> cls : PROVIDERS) {
            String type = typeOf(cls);
            for (int id : m.getAppWidgetIds(new ComponentName(c, cls))) {
                try { render(c, m, id, type); } catch (Exception ignored) {}
            }
        }
    }

    public static String typeOf(Class<?> cls) {
        if (cls == CompactWidget.class) return "compact";
        if (cls == WeatherWidget.class) return "weather";
        if (cls == CalendarWidget.class) return "calendar";
        if (cls == DateWidget.class) return "date";
        if (cls == WeatherSmallWidget.class) return "weather2";
        if (cls == CalendarWideWidget.class) return "calendar3";
        if (cls == SummaryWidget.class) return "summary";
        if (cls == SummaryQuoteWidget.class) return "summaryq";
        if (cls == MapWidget.class) return "map";
        return "clock";
    }
    public static Class<? extends BaseWidget> classOf(String type) {
        switch (type) {
            case "compact": return CompactWidget.class;
            case "weather": return WeatherWidget.class;
            case "calendar": return CalendarWidget.class;
            case "date": return DateWidget.class;
            case "weather2": return WeatherSmallWidget.class;
            case "calendar3": return CalendarWideWidget.class;
            case "summary": return SummaryWidget.class;
            case "summaryq": return SummaryQuoteWidget.class;
            case "map": return MapWidget.class;
            default: return ClockWidget.class;
        }
    }
    public static String typeOfId(Context c, int id) {
        try {
            ComponentName cn = AppWidgetManager.getInstance(c).getAppWidgetInfo(id).provider;
            for (Class<? extends BaseWidget> cls : PROVIDERS)
                if (cn.getClassName().equals(cls.getName())) return typeOf(cls);
        } catch (Exception ignored) {}
        return "clock";
    }

    static int layoutOf(String type) {
        switch (type) {
            case "compact": return R.layout.widget_compact;
            case "weather": return R.layout.widget_weather;
            case "calendar": return R.layout.widget_calendar;
            case "date": return R.layout.widget_date;
            case "weather2": return R.layout.widget_weather2;
            case "calendar3": return R.layout.widget_calendar3;
            default: return R.layout.widget_clock;
        }
    }

    public static void render(Context c, AppWidgetManager m, int id, String type) {
        Cfg f = Cfg.load(c, id);
        if ("summary".equals(type)) { Summary.render(c, m, id, f, false); return; }
        if ("summaryq".equals(type)) { Summary.render(c, m, id, f, true); return; }
        if ("map".equals(type)) { MapW.render(c, m, id, f); return; }
        RemoteViews v = new RemoteViews(c.getPackageName(), layoutOf(type));
        boolean auto = "auto".equals(f.theme);
        boolean useDark = "dark".equals(f.theme) || (auto && isNight(c));
        Pal L = pal(c, f, "dark".equals(f.theme)), D = pal(c, f, !"light".equals(f.theme));
        Painter p = new Painter(v, L, D, auto, useDark);

        // Nền
        p.col(R.id.bg, "setColorFilter", L.surface, D.surface);
        v.setInt(R.id.bg, "setImageAlpha", Math.round(f.opacity * 2.55f));

        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Calendar now = Calendar.getInstance();
        int d = now.get(Calendar.DAY_OF_MONTH), mo = now.get(Calendar.MONTH) + 1, y = now.get(Calendar.YEAR);
        int wd = now.get(Calendar.DAY_OF_WEEK) - 1;
        Lunar.Date Lu = Lunar.fromSolar(d, mo, y);
        int week = Lunar.isoWeek(d, mo, y);
        int kind = dayKind(d, mo, wd, Lu);
        JSONObject w = Weather.cached(c);

        switch (type) {
            case "date": renderDate(c, p, f, d, mo, y, wd, week, Lu); dayColors(p, kind); break;
            case "calendar": renderCalendar(p, f, d, mo, y, wd, week, Lu, w); dayColors(p, kind); break;
            case "weather": renderWeather(c, p, f, w, id); break;
            case "weather2": renderWeatherSmall(c, p, f, w, id); break;
            case "calendar3":
                renderCalendarWide(c, p, f, d, mo, y, wd, week, Lu, w); dayColors(p, kind);
                if (Lunar.suKien(d, mo, Lu) != null) { if (Lunar.ngayLe(d, mo, Lu) != null) p.red(R.id.d_l3); else p.gold(R.id.d_l3); }
                break;
            case "compact":
                clock(p, f);
                v.setTextViewText(R.id.date, THU_NGAN[wd] + ", " + two(d) + "/" + two(mo) + (f.week ? " · W" + week : ""));
                p.day(R.id.date, kind, false);
                v.setViewVisibility(R.id.lunar, f.lunar ? View.VISIBLE : View.GONE);
                v.setTextViewText(R.id.lunar, "ÂL " + lunarDM(Lu) + " · " + Lunar.canChiYear(Lu.year));
                if (kind == 2) p.gold(R.id.lunar); else p.fg2(R.id.lunar);
                p.tint(R.id.divider, false);
                current(c, p, f, w, false);
                break;
            default: // clock
                clock(p, f);
                v.setTextViewText(R.id.date, dateText(f.date, d, mo, y, wd) + (f.week ? " · W" + week : ""));
                p.day(R.id.date, kind, false);
                v.setViewVisibility(R.id.lunar, f.lunar ? View.VISIBLE : View.GONE);
                v.setTextViewText(R.id.lunar, "Âm lịch " + lunarDM(Lu) + " · " + Lunar.canChiYear(Lu.year)
                        + (Lu.day == 1 ? " · Mùng 1" : Lu.day == 15 ? " · Rằm" : ""));
                if (Lu.day == 1 || Lu.day == 15) p.gold(R.id.lunar); else p.fg2(R.id.lunar);
                current(c, p, f, w, true);
                bottomRow(c, p, f, w, id, type);
        }
        m.updateAppWidget(id, v);
    }

    /** Tô thứ + số ngày theo quy tắc màu chung. */
    static void dayColors(Painter p, int kind) {
        p.day(R.id.d_weekday, kind, false);
        p.day(R.id.d_day, kind, true);
    }

    static void clock(Painter p, Cfg f) {
        RemoteViews v = p.v;
        v.setViewVisibility(R.id.clock_thin, f.bold ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.clock_bold, f.bold ? View.VISIBLE : View.GONE);
        String fmt = f.h24 ? "HH:mm" : "h:mm";
        for (int cid : new int[]{R.id.clock_thin, R.id.clock_bold}) {
            v.setCharSequence(cid, "setFormat24Hour", fmt);
            v.setCharSequence(cid, "setFormat12Hour", fmt);
            p.acc(cid);
        }
    }

    static String dateText(String style, int d, int m, int y, int wd) {
        switch (style) {
            case "long": return THU[wd] + ", " + d + " tháng " + m;
            case "short": return THU_NGAN[wd] + " · " + two(d) + "/" + two(m);
            default: return THU[wd] + ", " + two(d) + "/" + two(m) + "/" + y;
        }
    }
    /** QUY TẮC MÀU NGÀY (dùng chung mọi widget):
     *  1 = đỏ: ngày lễ chính thức, Thứ Bảy, Chủ nhật; 2 = vàng: mùng 1, rằm; 0 = thường.
     *  Thứ tự ưu tiên: ngày lễ > mùng 1/rằm > cuối tuần. */
    static int dayKind(int d, int mo, int wd, Lunar.Date L) {
        if (Lunar.ngayLe(d, mo, L) != null) return 1;
        if (L.day == 1 || L.day == 15) return 2;
        if (wd == 0 || wd == 6) return 1;
        return 0;
    }

    /** Nhãn vị trí GPS thống nhất: "● Bến Nghé" hoặc "● Bến Nghé · TP.HCM". */
    static CharSequence placeLabel(String place, boolean withProv, int textColor) {
        String s = shortPlace(place);
        if (s.isEmpty()) return "";
        if (withProv) { String pv = province(place); if (!pv.isEmpty() && !pv.equals(s)) s += " · " + pv; }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        Summary.append(sb, "● ", 0xFFFF8A00, true);
        Summary.append(sb, s, textColor, false);
        return sb;
    }
    static CharSequence placeLabel(String place, boolean withProv) {
        String s = shortPlace(place);
        if (s.isEmpty()) return "";
        if (withProv) { String pv = province(place); if (!pv.isEmpty() && !pv.equals(s)) s += " · " + pv; }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("● " + s);
        sb.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF8A00), 0, 1, 0);
        return sb;
    }
    static String province(String place) {
        if (place == null) return "";
        String[] parts = place.split(",");
        if (parts.length < 2) return "";
        String pv = parts[parts.length - 1].trim();
        if (pv.equals("Việt Nam") && parts.length >= 3) pv = parts[parts.length - 2].trim();
        if (pv.contains("Hồ Chí Minh")) return "TP.HCM";
        if (pv.startsWith("Thành phố ")) return pv.substring(10);
        if (pv.startsWith("TP. ")) return pv.substring(4);
        if (pv.startsWith("Tỉnh ")) return pv.substring(5);
        return pv;
    }

    static String lunarDM(Lunar.Date L) { return L.day + "/" + L.month + (L.leap ? "N" : ""); }
    static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
    /** Tên ngắn cho widget: "Phường Bến Nghé, Quận 1, …" → "Bến Nghé"; "Phường 5, Quận 3" → "P.5, Q.3". */
    static String shortPlace(String p) {
        if (p == null || p.trim().isEmpty()) return "";
        String[] parts = p.split(",");
        String first = shortOne(parts[0].trim());
        if (!first.isEmpty() && Character.isDigit(first.charAt(first.length() - 1)) && parts.length > 1)
            return first + ", " + shortOne(parts[1].trim());
        return first;
    }
    static String shortOne(String s) {
        String[][] pre = {{"Thành phố ", "TP."}, {"TP. ", "TP."}, {"TP ", "TP."}, {"Tỉnh ", ""}, {"Phường ", "P."}, {"Quận ", "Q."},
                {"Huyện ", "H."}, {"Thị xã ", "TX."}, {"Thị trấn ", "TT."}, {"Xã ", "X."}, {"Đặc khu ", ""}};
        for (String[] x : pre) {
            if (s.startsWith(x[0])) {
                String rest = s.substring(x[0].length()).trim();
                return (!rest.isEmpty() && Character.isDigit(rest.charAt(0))) ? x[1] + rest : rest;
            }
        }
        return s;
    }
    static String updatedText(Context c) {
        if (refreshing) return "Đang cập nhật…";
        long ts = Weather.cachedTs(c);
        if (ts == 0) return "";
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ts);
        return two(t.get(Calendar.HOUR_OF_DAY)) + ":" + two(t.get(Calendar.MINUTE));
    }

    /** Tuổi của người dùng (chi), -1 nếu chưa nhập ngày sinh. */
    static int chiTuoi(Context c) {
        try {
            String b = Weather.prefs(c).getString("birth", "");
            if (b == null || !b.matches("\\d{4}-\\d{2}-\\d{2}")) return -1;
            String[] s = b.split("-");
            return Lunar.chiTuoi(Integer.parseInt(s[2]), Integer.parseInt(s[1]), Integer.parseInt(s[0]));
        } catch (Exception e) { return -1; }
    }

    private static void refreshButton(Context c, Painter p, int widgetId, String type) {
        Intent r = new Intent(c, classOf(type)).setAction(BaseWidget.ACTION_REFRESH);
        p.v.setOnClickPendingIntent(R.id.btn_refresh, PendingIntent.getBroadcast(c, 1000 + widgetId, r,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        p.col(R.id.rf_bg, "setColorFilter", p.L.chipB, p.D.chipB);
        p.v.setInt(R.id.rf_bg, "setImageAlpha", p.L.chipAlpha);
        p.tint(R.id.rf_icon, true);
    }

    private static void current(Context c, Painter p, Cfg f, JSONObject w, boolean full) {
        RemoteViews v = p.v;
        boolean show = f.weather && w != null;
        v.setViewVisibility(R.id.weather_box, show ? View.VISIBLE : View.GONE);
        if (!show) return;
        try {
            JSONObject cur = w.getJSONObject("current");
            int code = cur.optInt("weather_code", 3);
            v.setImageViewResource(R.id.wx_icon, Weather.iconRes(code, cur.optInt("is_day", 1) == 1));
            v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
            p.fg(R.id.wx_temp);
            if (full) {
                JSONObject dl = w.getJSONObject("daily");
                v.setTextViewText(R.id.wx_desc, Weather.desc(code) + " · " + Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0))
                        + "°/" + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "°");
                p.fg2(R.id.wx_desc);
                CharSequence place = placeLabel(Weather.place(c), false);
                v.setViewVisibility(R.id.wx_place, f.place && place.length() > 0 ? View.VISIBLE : View.GONE);
                v.setTextViewText(R.id.wx_place, place);
                p.fg2(R.id.wx_place);
            }
        } catch (Exception ignored) {}
    }

    private static void bottomRow(Context c, Painter p, Cfg f, JSONObject w, int widgetId, String type) {
        RemoteViews v = p.v;
        if (!f.weather) { v.setViewVisibility(R.id.bottom_row, View.GONE); return; }
        v.setViewVisibility(R.id.bottom_row, View.VISIBLE);
        String hint = w != null ? Weather.rainHint(w) : "Mở app để lấy vị trí";
        v.setViewVisibility(R.id.rain_box, hint.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        v.setTextViewText(R.id.rain, hint);
        p.chip(R.id.rain_bg, R.id.rain, 1);
        v.setTextViewText(R.id.wx_updated, updatedText(c));
        p.fg2(R.id.wx_updated);
        refreshButton(c, p, widgetId, type);
    }

    private static final int[][] H_IDS = {
            {R.id.h0, R.id.h0_time, R.id.h0_icon, R.id.h0_temp, R.id.h0_pop},
            {R.id.h1, R.id.h1_time, R.id.h1_icon, R.id.h1_temp, R.id.h1_pop},
            {R.id.h2, R.id.h2_time, R.id.h2_icon, R.id.h2_temp, R.id.h2_pop},
            {R.id.h3, R.id.h3_time, R.id.h3_icon, R.id.h3_temp, R.id.h3_pop},
            {R.id.h4, R.id.h4_time, R.id.h4_icon, R.id.h4_temp, R.id.h4_pop}};

    private static void renderWeather(Context c, Painter p, Cfg f, JSONObject w, int widgetId) {
        RemoteViews v = p.v;
        refreshButton(c, p, widgetId, "weather");
        p.fg(R.id.wx_place); p.fg2(R.id.wx_updated); p.fg(R.id.wx_temp); p.fg(R.id.wx_desc); p.fg2(R.id.wx_hilo);
        p.chip(R.id.rain_bg, R.id.rain, 1);
        v.setTextViewText(R.id.wx_updated, updatedText(c));
        CharSequence place = placeLabel(Weather.place(c), true);
        v.setTextViewText(R.id.wx_place, f.place ? (place.length() == 0 ? "Vị trí hiện tại" : place) : "");
        if (w == null) {
            v.setTextViewText(R.id.wx_temp, "--°");
            v.setTextViewText(R.id.wx_desc, "Chưa có dữ liệu");
            v.setTextViewText(R.id.wx_hilo, "Mở app để lấy vị trí");
            v.setViewVisibility(R.id.hourly, View.INVISIBLE);
            v.setViewVisibility(R.id.rain_box, View.GONE);
            return;
        }
        try {
            JSONObject cur = w.getJSONObject("current"), dl = w.getJSONObject("daily"), h = w.getJSONObject("hourly");
            int code = cur.optInt("weather_code", 3);
            v.setImageViewResource(R.id.wx_icon, Weather.iconRes(code, cur.optInt("is_day", 1) == 1));
            v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
            v.setTextViewText(R.id.wx_desc, Weather.desc(code) + " · ẩm " + cur.optInt("relative_humidity_2m", 0) + "%");
            v.setTextViewText(R.id.wx_hilo, "Cảm giác " + Math.round(cur.optDouble("apparent_temperature", 0)) + "° · "
                    + Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0)) + "°/"
                    + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "°");
            v.setViewVisibility(R.id.hourly, View.VISIBLE);
            JSONArray ht = h.getJSONArray("time");
            int i0 = Weather.currentHourIndex(w);
            for (int k = 0; k < 5; k++) {
                int i = i0 + 1 + k * 2;
                int[] ids = H_IDS[k];
                if (i >= ht.length()) { v.setViewVisibility(ids[0], View.INVISIBLE); continue; }
                v.setViewVisibility(ids[0], View.VISIBLE);
                v.setTextViewText(ids[1], Weather.hourOf(ht.getString(i)) + "h");
                v.setImageViewResource(ids[2], Weather.iconRes(h.getJSONArray("weather_code").optInt(i, 3), h.getJSONArray("is_day").optInt(i, 1) == 1));
                v.setTextViewText(ids[3], Math.round(h.getJSONArray("temperature_2m").optDouble(i, 0)) + "°");
                double pop = h.getJSONArray("precipitation_probability").optDouble(i, Double.NaN);
                double mm = h.getJSONArray("precipitation").optDouble(i, 0);
                v.setTextViewText(ids[4], !Double.isNaN(pop) ? Math.round(pop) + "%" : (mm >= 0.1 ? String.format(Locale.US, "%.1fmm", mm) : ""));
                p.fg2(ids[1]); p.fg(ids[3]); p.rain(ids[4]);
            }
            String hint = Weather.rainHint(w);
            v.setViewVisibility(R.id.rain_box, f.weather && !hint.isEmpty() ? View.VISIBLE : View.GONE);
            v.setTextViewText(R.id.rain, hint);
        } catch (Exception ignored) {}
    }

    private static void renderDate(Context c, Painter p, Cfg f, int d, int mo, int y, int wd, int week, Lunar.Date Lu) {
        RemoteViews v = p.v;
        v.setTextViewText(R.id.d_weekday, THU[wd]);
        if (wd == 0) p.red(R.id.d_weekday); else p.fg(R.id.d_weekday);
        v.setTextViewText(R.id.d_day, String.valueOf(d));
        p.acc(R.id.d_day);
        v.setTextViewText(R.id.d_month, "T" + mo + "/" + y);
        p.fg2(R.id.d_month);
        v.setViewVisibility(R.id.week_box, f.week ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.week, "W" + week);
        p.chip(R.id.week_bg, R.id.week, 1);
        v.setTextViewText(R.id.lchip, "ÂL " + lunarDM(Lu));
        p.chip(R.id.lchip_bg, R.id.lchip, 0);
        v.setTextViewText(R.id.d_l1, "Ngày " + Lunar.canChiDay(Lu.jd) + " · tháng " + Lunar.canChiMonth(Lu.month, Lu.year));
        p.fg(R.id.d_l1);
        v.setTextViewText(R.id.d_l2, "Năm " + Lunar.canChiYear(Lu.year) + " · tháng " + Lunar.THANG[Lu.month] + (Lu.leap ? " nhuận" : ""));
        p.fg2(R.id.d_l2);
        int ct = f.tuoi ? chiTuoi(c) : -1;
        Lunar.DanhGia g = Lunar.danhGia(d, mo, y, ct);
        if (ct >= 0) v.setTextViewText(R.id.d_l3, "Tuổi " + Lunar.CHI[ct] + ": " + g.muc);
        else v.setTextViewText(R.id.d_l3, "Ngày " + (g.hoangDao ? "Hoàng đạo" : "Hắc đạo") + " · " + g.than);
        v.setTextViewText(R.id.d_l4, "Giờ tốt: " + Lunar.gioTot(Lu.jd, ct, 4));
        if (ct >= 0) { if (g.diem >= 4) p.col(R.id.d_l3, "setTextColor", p.L.acc, p.D.acc); else if (g.diem <= 2) p.red(R.id.d_l3); else p.fg(R.id.d_l3); }
        else p.fg(R.id.d_l3);
        p.fg2(R.id.d_l4);
    }

    private static void renderCalendar(Painter p, Cfg f, int d, int mo, int y, int wd, int week, Lunar.Date Lu, JSONObject w) {
        RemoteViews v = p.v;
        v.setTextViewText(R.id.d_weekday, THU[wd]);
        if (wd == 0) p.red(R.id.d_weekday); else p.fg(R.id.d_weekday);
        v.setViewVisibility(R.id.week_box, f.week ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.week, "W" + week);
        p.chip(R.id.week_bg, R.id.week, 1);
        v.setTextViewText(R.id.d_day, String.valueOf(d));
        p.acc(R.id.d_day);
        v.setTextViewText(R.id.d_month, "T" + mo + "/" + y);
        p.fg2(R.id.d_month);
        v.setViewVisibility(R.id.lchip_box, f.lunar ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.lchip, "ÂL " + lunarDM(Lu));
        p.chip(R.id.lchip_bg, R.id.lchip, 0);
        boolean show = f.weather && w != null;
        v.setViewVisibility(R.id.weather_box, show ? View.VISIBLE : View.GONE);
        if (show) {
            try {
                JSONObject cur = w.getJSONObject("current");
                v.setImageViewResource(R.id.wx_icon, Weather.iconRes(cur.optInt("weather_code", 3), cur.optInt("is_day", 1) == 1));
                v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
                p.fg(R.id.wx_temp);
            } catch (Exception ignored) {}
        }
    }

    private static void renderWeatherSmall(Context c, Painter p, Cfg f, JSONObject w, int widgetId) {
        RemoteViews v = p.v;
        refreshButton(c, p, widgetId, "weather2");
        p.fg(R.id.wx_place); p.fg2(R.id.wx_updated); p.fg(R.id.wx_temp); p.fg(R.id.wx_desc); p.fg2(R.id.wx_hilo);
        p.chip(R.id.rain_bg, R.id.rain, 1);
        CharSequence place = placeLabel(Weather.place(c), false);
        v.setTextViewText(R.id.wx_place, f.place ? (place.length() == 0 ? "Vị trí hiện tại" : place) : "Thời tiết");
        String up = updatedText(c);
        v.setTextViewText(R.id.wx_updated, up.isEmpty() || refreshing ? up : "Cập nhật " + up);
        if (w == null) {
            v.setTextViewText(R.id.wx_temp, "--°");
            v.setTextViewText(R.id.wx_desc, "Chưa có dữ liệu");
            v.setTextViewText(R.id.wx_hilo, "Mở app để lấy vị trí");
            v.setViewVisibility(R.id.rain_box, View.GONE);
            return;
        }
        try {
            JSONObject cur = w.getJSONObject("current"), dl = w.getJSONObject("daily");
            int code = cur.optInt("weather_code", 3);
            v.setImageViewResource(R.id.wx_icon, Weather.iconRes(code, cur.optInt("is_day", 1) == 1));
            v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
            v.setTextViewText(R.id.wx_desc, Weather.desc(code) + " · CG " + Math.round(cur.optDouble("apparent_temperature", 0)) + "°");
            v.setTextViewText(R.id.wx_hilo, Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0)) + "°/"
                    + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "° · ẩm " + cur.optInt("relative_humidity_2m", 0) + "%");
            String hint = Weather.rainHintShort(w);
            v.setViewVisibility(R.id.rain_box, f.weather && !hint.isEmpty() ? View.VISIBLE : View.GONE);
            v.setTextViewText(R.id.rain, hint);
        } catch (Exception ignored) {}
    }

    private static void renderCalendarWide(Context c, Painter p, Cfg f, int d, int mo, int y, int wd, int week, Lunar.Date Lu, JSONObject w) {
        RemoteViews v = p.v;
        v.setTextViewText(R.id.d_weekday, THU[wd]);
        if (wd == 0) p.red(R.id.d_weekday); else p.fg(R.id.d_weekday);
        v.setTextViewText(R.id.d_day, String.valueOf(d));
        p.acc(R.id.d_day);
        v.setTextViewText(R.id.d_month, "T" + mo + "/" + y);
        p.fg2(R.id.d_month);
        v.setViewVisibility(R.id.week_box, f.week ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.week, "W" + week);
        p.chip(R.id.week_bg, R.id.week, 1);
        v.setTextViewText(R.id.lchip, "ÂL " + lunarDM(Lu));
        p.chip(R.id.lchip_bg, R.id.lchip, 0);
        v.setTextViewText(R.id.d_l1, "Ngày " + Lunar.canChiDay(Lu.jd));
        p.fg(R.id.d_l1);
        v.setTextViewText(R.id.d_l2, "Tháng " + Lunar.canChiMonth(Lu.month, Lu.year));
        p.fg2(R.id.d_l2);
        v.setTextViewText(R.id.d_l6, "Năm " + Lunar.canChiYear(Lu.year));
        p.fg2(R.id.d_l6);
        String ev = Lunar.suKien(d, mo, Lu);
        v.setTextViewText(R.id.d_l3, ev != null ? ev : "Tiết " + Lunar.tietKhi(Lu.jd));
        if (ev != null) p.red(R.id.d_l3); else p.fg2(R.id.d_l3);
        int ct = f.tuoi ? chiTuoi(c) : -1;
        Lunar.DanhGia g = Lunar.danhGia(d, mo, y, ct);
        if (ct >= 0) {
            v.setTextViewText(R.id.d_l4, "Tuổi " + Lunar.CHI[ct] + ": " + g.muc);
            if (g.diem >= 4) p.acc(R.id.d_l4); else if (g.diem <= 2) p.red(R.id.d_l4); else p.fg(R.id.d_l4);
        } else {
            v.setTextViewText(R.id.d_l4, (g.hoangDao ? "Hoàng đạo" : "Hắc đạo") + " · " + g.than);
            p.fg(R.id.d_l4);
        }
        v.setTextViewText(R.id.d_l5, "Giờ tốt: " + Lunar.gioTot(Lu.jd, ct, 3));
        p.fg2(R.id.d_l5);
        boolean show = f.weather && w != null;
        v.setViewVisibility(R.id.weather_box, show ? View.VISIBLE : View.GONE);
        if (show) {
            try {
                JSONObject cur = w.getJSONObject("current");
                v.setImageViewResource(R.id.wx_icon, Weather.iconRes(cur.optInt("weather_code", 3), cur.optInt("is_day", 1) == 1));
                v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
                p.fg(R.id.wx_temp);
            } catch (Exception ignored) {}
        }
    }

    /* ---------------- Hẹn giờ ---------------- */
    /** Cập nhật lúc 0h để đổi ngày dương/âm. */
    public static void scheduleMidnight(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, 1);
        t.set(Calendar.HOUR_OF_DAY, 0);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 15);
        Intent i = new Intent(c, UpdateReceiver.class).setAction(UpdateReceiver.ACTION_MIDNIGHT);
        am.set(AlarmManager.RTC, t.getTimeInMillis(), PendingIntent.getBroadcast(c, 77, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        scheduleGreeting(c, am);
    }

    /** Đổi lời chào đúng các mốc 6h, 12h, 15h, 18h, 22h. */
    static void scheduleGreeting(Context c, AlarmManager am) {
        int[] marks = {6, 12, 15, 18, 22};
        Calendar now = Calendar.getInstance(), t = Calendar.getInstance();
        t.set(Calendar.MINUTE, 0); t.set(Calendar.SECOND, 5); t.set(Calendar.MILLISECOND, 0);
        int h = now.get(Calendar.HOUR_OF_DAY), next = -1;
        for (int mk : marks) if (mk > h) { next = mk; break; }
        if (next < 0) { t.add(Calendar.DAY_OF_MONTH, 1); next = 6; }
        t.set(Calendar.HOUR_OF_DAY, next);
        Intent i = new Intent(c, UpdateReceiver.class).setAction(UpdateReceiver.ACTION_MIDNIGHT);
        am.set(AlarmManager.RTC, t.getTimeInMillis(), PendingIntent.getBroadcast(c, 79, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    /** Tự cập nhật thời tiết theo chu kỳ người dùng chọn (15/30/60 phút). */
    public static void schedulePeriodic(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long ms = Weather.interval(c) * 60_000L;
        Intent i = new Intent(c, UpdateReceiver.class).setAction(UpdateReceiver.ACTION_PERIODIC);
        PendingIntent pi = PendingIntent.getBroadcast(c, 78, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + ms, ms, pi);
    }
}
