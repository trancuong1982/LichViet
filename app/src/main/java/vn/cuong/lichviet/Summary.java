package vn.cuong.lichviet;

import android.app.PendingIntent;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

/** Widget tổng hợp 5×3: thời tiết, ngày 3D, lịch tháng, sự kiện & ngày lễ, tốt xấu theo tuổi. */
public final class Summary {
    private Summary() {}

    /* ---------------- Màu theo hình nền ---------------- */
    static final class P {
        boolean darkText;
        int fg, fg2, red, orange, good, mid, acc, todayBg, todayFg;
        int faceTop, faceBot, faceRedTop, faceRedBot, ex, exRed, glow, glowRed, gold;
        int faceGoldTop = 0xFFFFF6D6, faceGoldBot = 0xFFFFB020, exGold = 0xFFB36B00, glowGold = 0x8CFFB020;
    }

    /** Hình nền sáng? (Android cho biết qua màu hình nền) */
    static boolean wallpaperLight(Context c) {
        if (Build.VERSION.SDK_INT < 27) return false;
        try {
            WallpaperColors wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return false;
            if (Build.VERSION.SDK_INT >= 31) return (wc.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
            return wc.getPrimaryColor().luminance() > 0.6f;
        } catch (Exception e) { return false; }
    }

    static P palette(boolean darkText) {
        P p = new P();
        p.darkText = darkText;
        if (darkText) {
            p.fg = 0xFF16203A; p.fg2 = 0xB816203A; p.red = 0xFFE0322A; p.orange = 0xFFB8560A; p.good = 0xFF1F7A5C;
            p.mid = 0xFF8A6400; p.acc = 0xFF2D4BA0; p.todayBg = 0xFF3F66F0; p.todayFg = 0xFFFFFFFF;
            p.faceTop = 0xFFB9D0FF; p.faceBot = 0xFF4A78FF; p.ex = 0xFF2342A8; p.glow = 0x6678AAFF;
        } else {
            p.fg = 0xFFFFFFFF; p.fg2 = 0xE6FFFFFF; p.red = 0xFFFF7A6E; p.orange = 0xFFFFD49A; p.good = 0xFFA6F2CF;
            p.mid = 0xFFFFE29A; p.acc = 0xFFFFFFFF; p.todayBg = 0xFFFFFFFF; p.todayFg = 0xFF1B2A5C;
            p.faceTop = 0xFFFFFFFF; p.faceBot = 0xFFFFFFFF; p.ex = 0xFF3F6BE8; p.glow = 0x8C78AAFF;
        }
        p.gold = darkText ? 0xFFB8760A : 0xFFFFC94D;
        p.faceRedTop = 0xFFFFE9E5; p.faceRedBot = 0xFFFF4D3D; p.exRed = 0xFFB3261E; p.glowRed = 0x8CFF5A46;
        return p;
    }

    static String greeting(int h) {
        if (h >= 6 && h < 12) return "Buổi sáng an lành";
        if (h >= 12 && h < 15) return "Buổi trưa vui vẻ";
        if (h >= 15 && h < 18) return "Buổi chiều bình an";
        if (h >= 18 && h < 22) return "Buổi tối hạnh phúc";
        return "Chúc ngủ ngon";
    }

    static final int[][] EV = {
            {R.id.ev0_d, R.id.ev0_t}, {R.id.ev1_d, R.id.ev1_t}, {R.id.ev2_d, R.id.ev2_t}, {R.id.ev3_d, R.id.ev3_t}, {R.id.ev4_d, R.id.ev4_t}};

    /* ---------------- Vẽ widget ---------------- */
    static void render(Context c, AppWidgetManager m, int id, WidgetRenderer.Cfg f) { render(c, m, id, f, false); }

    /** q = true: bản có châm ngôn (sự kiện & tuổi gọn lại). */
    static void render(Context c, AppWidgetManager m, int id, WidgetRenderer.Cfg f, boolean q) {
        RemoteViews v = new RemoteViews(c.getPackageName(), q ? R.layout.widget_summary_q : R.layout.widget_summary);
        boolean glass = "glass".equals(f.style), solid = "solid".equals(f.style);
        boolean light = "light".equals(f.tone) || ("auto".equals(f.tone) && wallpaperLight(c));
        P p = palette(light);
        float dens = c.getResources().getDisplayMetrics().density;

        // Nền kính
        if (glass) {
            v.setViewVisibility(R.id.bg, View.VISIBLE);
            v.setViewVisibility(R.id.gloss, View.VISIBLE);
            v.setViewVisibility(R.id.rim, View.VISIBLE);
            v.setInt(R.id.bg, "setColorFilter", light ? 0xFFFFFFFF : 0xFF0E1428);
            v.setInt(R.id.bg, "setImageAlpha", light ? 140 : 105);
            v.setImageViewBitmap(R.id.gloss, gloss(light));
            v.setInt(R.id.rim, "setImageAlpha", light ? 230 : 95);
        } else if (solid) {
            // Nền đặc, chỉnh được độ trong suốt
            v.setViewVisibility(R.id.bg, View.VISIBLE);
            v.setViewVisibility(R.id.gloss, View.GONE);
            v.setViewVisibility(R.id.rim, View.GONE);
            v.setInt(R.id.bg, "setColorFilter", light ? 0xFFF6F8FD : 0xFF1C212B);
            v.setInt(R.id.bg, "setImageAlpha", Math.round(f.opacity * 2.55f));
        } else {
            v.setViewVisibility(R.id.bg, View.GONE);
            v.setViewVisibility(R.id.gloss, View.GONE);
            v.setViewVisibility(R.id.rim, View.GONE);
        }

        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Calendar now = Calendar.getInstance();
        int d = now.get(Calendar.DAY_OF_MONTH), mo = now.get(Calendar.MONTH) + 1, y = now.get(Calendar.YEAR);
        int wd = now.get(Calendar.DAY_OF_WEEK) - 1;
        Lunar.Date L = Lunar.fromSolar(d, mo, y);
        int kind = WidgetRenderer.dayKind(d, mo, wd, L);

        // ----- Cột thời tiết -----
        JSONObject w = Weather.cached(c);
        v.setTextViewText(R.id.greet, greeting(now.get(Calendar.HOUR_OF_DAY)));
        v.setTextColor(R.id.greet, p.fg);
        int[] wxText = {R.id.wx_temp, R.id.wx_desc};
        for (int t : wxText) v.setTextColor(t, p.fg);
        v.setTextColor(R.id.wx_place, p.fg2);
        v.setTextColor(R.id.wx_hilo, p.fg2);
        v.setTextColor(R.id.rain, p.fg);
        v.setViewVisibility(R.id.wx_col, f.weather ? View.VISIBLE : View.GONE);
        CharSequence place = WidgetRenderer.placeLabel(Weather.place(c), false, p.fg2);
        String upd = WidgetRenderer.updatedText(c);
        v.setTextViewText(R.id.wx_place, f.place && place.length() > 0 && !WidgetRenderer.refreshing ? place : upd);
        if (w != null) {
            try {
                JSONObject cur = w.getJSONObject("current"), dl = w.getJSONObject("daily");
                int code = cur.optInt("weather_code", 3);
                v.setImageViewResource(R.id.wx_icon, Weather.iconRes(code, cur.optInt("is_day", 1) == 1));
                v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
                v.setTextViewText(R.id.wx_desc, Weather.desc(code));
                v.setTextViewText(R.id.wx_hilo, Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0)) + "°/"
                        + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "° · ẩm " + cur.optInt("relative_humidity_2m", 0) + "%");
                String hint = Weather.rainHintShort(w);
                v.setViewVisibility(R.id.rain_box, hint.isEmpty() ? View.GONE : View.VISIBLE);
                v.setTextViewText(R.id.rain, hint);
            } catch (Exception ignored) {}
        } else {
            v.setTextViewText(R.id.wx_temp, "--°");
            v.setTextViewText(R.id.wx_desc, "Chưa có dữ liệu");
            v.setTextViewText(R.id.wx_hilo, "Mở app để lấy vị trí");
            v.setViewVisibility(R.id.rain_box, View.GONE);
        }
        Intent r = new Intent(c, SummaryQuoteWidget.class).setAction(BaseWidget.ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.wx_col, PendingIntent.getBroadcast(c, 1000 + id, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // ----- Cột ngày 3D -----
        v.setTextViewText(R.id.d_month, "T" + mo + "/" + y);
        v.setTextColor(R.id.d_month, p.fg);
        v.setImageViewBitmap(R.id.big3d, draw3D(String.valueOf(d), kind, p, dens, 112, 84));
        v.setTextViewText(R.id.d_weekday, WidgetRenderer.THU[wd]);
        v.setTextColor(R.id.d_weekday, kind == 1 ? p.red : kind == 2 ? p.gold : p.fg);
        v.setTextViewText(R.id.d_sub, "W" + Lunar.isoWeek(d, mo, y) + " · ÂL " + WidgetRenderer.lunarDM(L));
        v.setTextColor(R.id.d_sub, p.fg);

        // ----- Sự kiện -----
        List<CalendarEvents.Ev> all = CalendarEvents.upcoming(c, 90);
        Set<Integer> personal = CalendarEvents.personalDays(all);
        // Danh sách sự kiện: chỉ lịch hẹn và ngày lễ chính thức (không hiện mùng 1, rằm)
        List<CalendarEvents.Ev> evs = new java.util.ArrayList<>();
        for (CalendarEvents.Ev e : all) if (e.kind != CalendarEvents.LUNAR) evs.add(e);
        v.setImageViewBitmap(R.id.cal, drawMonth(y, mo, d, personal, p, dens));
        v.setTextColor(R.id.ev_head, p.fg2);
        boolean perm = CalendarEvents.hasPermission(c);
        int maxRows = q ? 3 : 5;
        int rows = perm ? maxRows : maxRows - 1;
        for (int i = 0; i < maxRows; i++) {
            int[] ids = EV[i];
            if (i < rows && i < evs.size()) {
                CalendarEvents.Ev e = evs.get(i);
                int col = e.kind == CalendarEvents.HOLIDAY ? p.red : (e.kind == CalendarEvents.LUNAR ? p.gold : p.acc);
                SpannableStringBuilder sb = new SpannableStringBuilder();
                append(sb, "● ", col, false);
                append(sb, label(e, now), p.fg2, true);
                v.setTextViewText(ids[0], sb);
                v.setTextViewText(ids[1], e.title);
                v.setTextColor(ids[1], e.kind == CalendarEvents.HOLIDAY ? p.red : p.fg);
                v.setViewVisibility(ids[0], View.VISIBLE);
                v.setViewVisibility(ids[1], View.VISIBLE);
            } else if (i == rows && !perm) {
                v.setTextViewText(ids[0], "");
                v.setTextViewText(ids[1], "Mở app → cho phép đọc lịch máy");
                v.setTextColor(ids[1], p.fg2);
            } else {
                v.setTextViewText(ids[0], "");
                v.setTextViewText(ids[1], i == 0 ? "Không có sự kiện" : "");
                v.setTextColor(ids[1], p.fg2);
            }
        }

        // ----- Theo tuổi -----
        int ct = f.tuoi ? WidgetRenderer.chiTuoi(c) : -1;
        Lunar.DanhGia g = Lunar.danhGia(d, mo, y, ct);
        int mc = g.diem >= 4 ? p.good : (g.diem == 3 ? p.mid : p.red);
        v.setTextColor(R.id.t_head, p.fg2);
        for (int t : q ? new int[]{R.id.t_l1} : new int[]{R.id.t_l1, R.id.t_l2, R.id.t_l3}) v.setTextColor(t, p.fg);
        if (ct >= 0) {
            int lunarYear = Lunar.namAmTuoi(birthD(c), birthM(c), birthY(c));
            v.setTextViewText(R.id.t_head, "TUỔI " + Lunar.canChiYear(lunarYear).toUpperCase());
            SpannableStringBuilder dots = new SpannableStringBuilder();
            for (int k = 0; k < 5; k++) append(dots, "●", k < g.diem ? mc : (mc & 0x00FFFFFF) | 0x4D000000, false);
            v.setTextViewText(R.id.t_dots, dots);
            v.setTextViewText(R.id.t_muc, g.diem == 3 ? "Bình thường" : "Ngày " + g.muc);
            v.setTextColor(R.id.t_muc, mc);
            if (q) {
                v.setTextViewText(R.id.t_l1, compactReasons(g, ct, p));
            } else {
            v.setTextViewText(R.id.t_l1, reason(g.hoangDao ? 1 : -1, (g.hoangDao ? "Hoàng đạo · " : "Hắc đạo · ") + g.than, p));
            v.setTextViewText(R.id.t_l2, reason(g.trucDiem, "Trực " + g.truc + " · " + g.trucMoTa, p));
            String ten = Lunar.CHI[ct];
            String qh = g.quanHe == -1 ? "Xung tuổi " + ten : g.quanHe == 1 ? "Lục hợp tuổi " + ten : g.quanHe == 2 ? "Tam hợp tuổi " + ten : "Không xung tuổi";
            v.setTextViewText(R.id.t_l3, reason(g.quanHe == -1 ? -1 : g.quanHe > 0 ? 1 : 0, qh, p));
            }
        } else {
            v.setTextViewText(R.id.t_head, "XEM NGÀY " + Lunar.canChiDay(L.jd).toUpperCase());
            v.setTextViewText(R.id.t_dots, "");
            v.setTextViewText(R.id.t_muc, g.hoangDao ? "Hoàng đạo" : "Hắc đạo");
            v.setTextColor(R.id.t_muc, g.hoangDao ? p.good : p.red);
            if (q) {
                v.setTextViewText(R.id.t_l1, compactReasons(g, -1, p));
            } else {
                v.setTextViewText(R.id.t_l1, reason(g.hoangDao ? 1 : -1, "Thần " + g.than, p));
                v.setTextViewText(R.id.t_l2, reason(g.trucDiem, "Trực " + g.truc + " · " + g.trucMoTa, p));
                v.setTextViewText(R.id.t_l3, "Nhập ngày sinh trong app");
                v.setTextColor(R.id.t_l3, p.fg2);
            }
        }
        SpannableStringBuilder gio = new SpannableStringBuilder();
        append(gio, "Giờ tốt: ", p.fg2, false);
        append(gio, Lunar.gioTot(L.jd, ct, 3), p.fg, true);
        v.setTextViewText(R.id.t_gio, gio);
        v.setViewVisibility(R.id.tuoi_col, View.VISIBLE);

        if (q) {
            v.setTextViewText(R.id.quote, Quotes.today(c));
            v.setTextColor(R.id.quote, p.fg);
            v.setInt(R.id.quote_bg, "setColorFilter", light ? 0xFF2D4BA0 : 0xFFFFFFFF);
            v.setInt(R.id.quote_bg, "setImageAlpha", (!glass && !solid) ? 0 : (light ? 22 : 30));
            v.setInt(R.id.quote_icon, "setColorFilter", light ? 0xFF2D4BA0 : 0xFFFFE29A);
            Intent nq = new Intent(c, SummaryQuoteWidget.class).setAction(BaseWidget.ACTION_NEXT_QUOTE);
            v.setOnClickPendingIntent(R.id.quote_box, PendingIntent.getBroadcast(c, 3000 + id, nq, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        v.setInt(R.id.divider, "setColorFilter", p.fg);
        v.setInt(R.id.divider, "setImageAlpha", light ? 36 : 70);
        m.updateAppWidget(id, v);
    }

    static String label(CalendarEvents.Ev e, Calendar now) {
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(e.start);
        Calendar tm = (Calendar) now.clone();
        tm.add(Calendar.DAY_OF_MONTH, 1);
        String time = e.allDay ? "" : " " + WidgetRenderer.two(t.get(Calendar.HOUR_OF_DAY)) + ":" + WidgetRenderer.two(t.get(Calendar.MINUTE));
        if (sameDay(t, now)) return "Nay" + time;
        if (sameDay(t, tm)) return "Mai" + time;
        return WidgetRenderer.two(e.d) + "/" + WidgetRenderer.two(e.m);
    }
    static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Một dòng gọn: "✕ Hắc đạo · ✕ Trực Phá · ✓ Hợp tuổi" */
    static CharSequence compactReasons(Lunar.DanhGia g, int ct, P p) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, g.hoangDao ? "✓ " : "✕ ", g.hoangDao ? p.good : p.red, true);
        append(sb, g.hoangDao ? "Hoàng đạo" : "Hắc đạo", p.fg, false);
        append(sb, " · ", p.fg2, false);
        append(sb, g.trucDiem > 0 ? "✓ " : g.trucDiem < 0 ? "✕ " : "– ", g.trucDiem > 0 ? p.good : g.trucDiem < 0 ? p.red : p.fg2, true);
        append(sb, "Trực " + g.truc, p.fg, false);
        if (ct >= 0 && g.quanHe != 0) {
            append(sb, " · ", p.fg2, false);
            append(sb, g.quanHe < 0 ? "✕ " : "✓ ", g.quanHe < 0 ? p.red : p.good, true);
            append(sb, g.quanHe < 0 ? "Xung tuổi" : "Hợp tuổi", p.fg, false);
        }
        return sb;
    }

    static CharSequence reason(int ok, String text, P p) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, ok > 0 ? "✓ " : ok < 0 ? "✕ " : "– ", ok > 0 ? p.good : ok < 0 ? p.red : p.fg2, true);
        append(sb, text, p.fg, false);
        return sb;
    }

    static void append(SpannableStringBuilder sb, String s, int color, boolean bold) {
        int a = sb.length();
        sb.append(s);
        sb.setSpan(new ForegroundColorSpan(color), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    static int[] birth(Context c) {
        try {
            String[] s = Weather.birth(c).split("-");
            return new int[]{Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2])};
        } catch (Exception e) { return new int[]{2000, 1, 1}; }
    }
    static int birthY(Context c) { return birth(c)[0]; }
    static int birthM(Context c) { return birth(c)[1]; }
    static int birthD(Context c) { return birth(c)[2]; }

    /* ---------------- Ảnh: số ngày 3D ---------------- */
    static Typeface heavy() {
        if (Build.VERSION.SDK_INT >= 28) return Typeface.create(Typeface.DEFAULT, 900, false);
        return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    }

    public static Bitmap draw3D(String num, boolean red, P p, float dens) { return draw3D(num, red ? 1 : 0, p, dens, 112, 84); }

    /** kind: 0 thường, 1 đỏ (T7/CN/lễ), 2 vàng (mùng 1, rằm) */
    public static Bitmap draw3D(String num, int kind, P p, float dens, int wDp, int hDp) {
        boolean red = kind == 1, gold = kind == 2;
        int W = Math.round(wDp * dens), H = Math.round(hDp * dens);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float ext = 4 * dens, step = dens, pad = 5 * dens;   // pad: chừa chỗ cho bóng mờ, không bị cắt thành khung
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setTypeface(heavy());
        pt.setLetterSpacing(-0.03f);
        float ts = hDp * 0.9f * dens;
        pt.setTextSize(ts);
        float avail = W - ext - 2 * pad, tw = pt.measureText(num);
        if (tw > avail) { ts = ts * avail / tw; pt.setTextSize(ts); tw = pt.measureText(num); }
        Rect bd = new Rect();
        pt.getTextBounds(num, 0, num.length(), bd);
        float availH = H - ext - 2 * pad;
        if (bd.height() > availH) { ts = ts * availH / bd.height(); pt.setTextSize(ts); tw = pt.measureText(num); pt.getTextBounds(num, 0, num.length(), bd); }
        float x = (W - ext - tw) / 2f, yb = (H - ext - bd.height()) / 2f - bd.top;
        int ex = red ? p.exRed : gold ? p.exGold : p.ex, glow = red ? p.glowRed : gold ? p.glowGold : p.glow;
        int fTop = red ? p.faceRedTop : gold ? p.faceGoldTop : p.faceTop, fBot = red ? p.faceRedBot : gold ? p.faceGoldBot : p.faceBot;

        // bóng mềm
        pt.setColor(ex);
        pt.setShadowLayer(3 * dens, 1.5f * dens, 2 * dens, 0x4D000000);
        cv.drawText(num, x + ext, yb + ext, pt);
        // quầng sáng
        pt.setShadowLayer(4 * dens, 0, 0, glow);
        cv.drawText(num, x + ext / 2, yb + ext / 2, pt);
        pt.clearShadowLayer();
        // khối nổi
        for (float i = ext; i >= step; i -= step) cv.drawText(num, x + i, yb + i, pt);
        // mặt số
        float top = yb + bd.top, bot = yb + bd.bottom;
        pt.setShader(new LinearGradient(0, top, 0, bot, fTop, fBot, Shader.TileMode.CLAMP));
        cv.drawText(num, x, yb, pt);
        // vệt sáng
        pt.setShader(new LinearGradient(0, top, 0, top + (bot - top) * 0.4f, 0xD9FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawText(num, x, yb, pt);
        return b;
    }

    /* ---------------- Ảnh: lịch tháng ---------------- */
    public static Bitmap drawMonth(int y, int m, int today, Set<Integer> personal, P p, float dens) {
        int W = Math.round(152 * dens), H = Math.round(140 * dens);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setTextAlign(Paint.Align.CENTER);
        if (!p.darkText) pt.setShadowLayer(2 * dens, 0, dens, 0x55000000);
        String[] hd = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        float cw = W / 7f, headH = 16 * dens;
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        pt.setTextSize(10 * dens);
        for (int i = 0; i < 7; i++) {
            pt.setColor(i >= 5 ? p.red : p.fg);
            cv.drawText(hd[i], cw * i + cw / 2, 11 * dens, pt);
        }
        Calendar k = Calendar.getInstance();
        k.clear();
        k.set(y, m - 1, 1);
        int start = (k.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = k.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (start + days + 6) / 7;
        float rh = Math.min((H - headH) / rows, 21 * dens);
        k.add(Calendar.DAY_OF_MONTH, -start);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < rows * 7; i++) {
            int dd = k.get(Calendar.DAY_OF_MONTH), mm = k.get(Calendar.MONTH) + 1, yy = k.get(Calendar.YEAR);
            boolean cur = mm == m;
            Lunar.Date L = Lunar.fromSolar(dd, mm, yy);
            boolean hol = Lunar.ngayLe(dd, mm, L) != null;
            int col = i % 7;
            float cx = cw * col + cw / 2, cy = headH + rh * (i / 7) + rh / 2;
            pt.setTypeface(cur ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            pt.setTextSize(12 * dens);
            int kd = hol ? 1 : (L.day == 1 || L.day == 15) ? 2 : col >= 5 ? 1 : 0;
            int color = kd == 1 ? p.red : kd == 2 ? p.gold : p.fg;
            if (cur && dd == today) {
                Paint circ = new Paint(Paint.ANTI_ALIAS_FLAG);
                circ.setColor(kd == 1 ? (p.darkText ? p.red : 0xFFFF5A4E) : kd == 2 ? 0xFFFFB020 : p.todayBg);
                circ.setShadowLayer(3 * dens, 0, dens, 0x40000000);
                cv.drawCircle(cx, cy, Math.min(cw, rh) / 2 - 0.5f * dens, circ);
                color = kd == 1 ? 0xFFFFFFFF : kd == 2 ? 0xFF3A2500 : p.todayFg;
            }
            pt.setColor(color);
            Paint.FontMetrics fm = pt.getFontMetrics();
            cv.drawText(String.valueOf(dd), cx, cy - (fm.ascent + fm.descent) / 2, pt);
            if (!(cur && dd == today)) {
                int key = CalendarEvents.key(yy, mm, dd);
                int dc = 0;
                if (personal.contains(key)) dc = p.acc;
                if (dc != 0) {
                    dot.setColor(dc);
                    cv.drawCircle(cx, cy + 7.5f * dens, 1.7f * dens, dot);
                }
            }
            k.add(Calendar.DAY_OF_MONTH, 1);
        }
        return b;
    }

    /* ---------------- Ảnh: ánh sáng loang của kính ---------------- */
    static Bitmap gloss(boolean light) {
        float s = 1.5f;
        int W = Math.round(400 * s), H = Math.round(300 * s);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, W, H), 26 * s, 26 * s, Path.Direction.CW);
        cv.clipPath(clip);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        int a1 = light ? 0x5AFFFFFF : 0x2EFFFFFF, a2 = light ? 0x2EFFFFFF : 0x17FFFFFF;
        pt.setShader(new RadialGradient(70 * s, 0, 170 * s, a1, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, pt);
        pt.setShader(new RadialGradient(360 * s, 300 * s, 130 * s, a2, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, pt);
        // vệt sáng mảnh ở mép trên
        pt.setShader(new LinearGradient(0, 0, 0, 14 * s, light ? 0x66FFFFFF : 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, 14 * s, pt);
        return b;
    }
}
