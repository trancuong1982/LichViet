package vn.cuong.lichviet;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/** Widget 5×3 "Nghệ thuật": icon thời tiết như thật, số ngày 3D cao, châm ngôn, lịch sắp tới, đồng hồ kim lồng số. */
public final class ArtW {
    private ArtW() {}

    static int iconReal(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wxr_sun : R.drawable.wxr_moon;
        if (code == 1 || code == 2) return day ? R.drawable.wxr_partly : R.drawable.wxr_partly_night;
        if (code >= 95) return R.drawable.wxr_thunder;
        if (code >= 51) return R.drawable.wxr_rain;
        return R.drawable.wxr_cloud;
    }

    /** Số ngày 3D cao, chữ hẹp (2 chữ số). kind: 0 thường, 1 đỏ, 2 vàng. */
    static Bitmap tall3D(String num, int kind, Summary.P p, float dens, int wDp, int hDp) {
        int W = Math.round(wDp * dens), H = Math.round(hDp * dens);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setTypeface(Summary.heavy());
        pt.setTextScaleX(0.62f);
        float pad = 6 * dens, exX = 3.6f * dens, exY = 5.4f * dens;
        pt.setTextSize(100 * dens);
        Rect bd = new Rect();
        pt.getTextBounds(num, 0, num.length(), bd);
        float sc = Math.min((H - exY - 2 * pad) / bd.height(), (W - exX - 2 * pad) / pt.measureText(num));
        pt.setTextSize(100 * dens * sc);
        pt.getTextBounds(num, 0, num.length(), bd);
        float tw = pt.measureText(num);
        float x = W - pad - exX - tw, yb = pad - bd.top;           // canh phải, sát trên
        boolean red = kind == 1, gold = kind == 2;
        int ex, fTop, fBot;
        if (red) { ex = p.exRed; fTop = p.faceRedTop; fBot = p.faceRedBot; }
        else if (gold) { ex = p.exGold; fTop = p.faceGoldTop; fBot = p.faceGoldBot; }
        else if (p.darkText) { ex = 0xFF8C97B8; fTop = 0xFF3A4566; fBot = 0xFF0E1426; }
        else { ex = 0xFF3F6BE8; fTop = 0xFFFFFFFF; fBot = 0xFFE4ECFF; }
        // bóng mềm (nằm gọn trong lề, không tạo khung)
        pt.setColor(ex);
        pt.setShadowLayer(3.5f * dens, 1.5f * dens, 2.5f * dens, 0x59000000);
        cv.drawText(num, x + exX, yb + exY, pt);
        pt.clearShadowLayer();
        // khối nổi xiên
        int steps = 6;
        for (int i = steps; i >= 1; i--) cv.drawText(num, x + exX * i / steps, yb + exY * i / steps, pt);
        float top = yb + bd.top, bot = yb + bd.bottom;
        pt.setShader(new LinearGradient(0, top, 0, bot, fTop, fBot, Shader.TileMode.CLAMP));
        cv.drawText(num, x, yb, pt);
        pt.setShader(new LinearGradient(0, top, 0, top + (bot - top) * 0.32f, 0x8CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawText(num, x, yb, pt);
        return b;
    }

    static void render(Context c, AppWidgetManager m, int id, WidgetRenderer.Cfg f) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_art);
        boolean glass = "glass".equals(f.style), solid = "solid".equals(f.style);
        boolean light = "light".equals(f.tone) || ("auto".equals(f.tone) && Summary.wallpaperLight(c));
        Summary.P p = Summary.palette(light);
        float dens = c.getResources().getDisplayMetrics().density;

        if (glass) {
            v.setViewVisibility(R.id.bg, View.VISIBLE); v.setViewVisibility(R.id.gloss, View.VISIBLE); v.setViewVisibility(R.id.rim, View.VISIBLE);
            v.setInt(R.id.bg, "setColorFilter", light ? 0xFFFFFFFF : 0xFF0E1428);
            v.setInt(R.id.bg, "setImageAlpha", light ? 140 : 105);
            v.setImageViewBitmap(R.id.gloss, Summary.gloss(light));
            v.setInt(R.id.rim, "setImageAlpha", light ? 230 : 95);
        } else if (solid) {
            v.setViewVisibility(R.id.bg, View.VISIBLE); v.setViewVisibility(R.id.gloss, View.GONE); v.setViewVisibility(R.id.rim, View.GONE);
            v.setInt(R.id.bg, "setColorFilter", light ? 0xFFF6F8FD : 0xFF1C212B);
            v.setInt(R.id.bg, "setImageAlpha", Math.round(f.opacity * 2.55f));
        } else {
            v.setViewVisibility(R.id.bg, View.GONE); v.setViewVisibility(R.id.gloss, View.GONE); v.setViewVisibility(R.id.rim, View.GONE);
        }
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Calendar now = Calendar.getInstance();
        int d = now.get(Calendar.DAY_OF_MONTH), mo = now.get(Calendar.MONTH) + 1, y = now.get(Calendar.YEAR);
        int wd = now.get(Calendar.DAY_OF_WEEK) - 1, hour = now.get(Calendar.HOUR_OF_DAY);
        Lunar.Date L = Lunar.fromSolar(d, mo, y);
        int kind = WidgetRenderer.dayKind(d, mo, wd, L);
        int kc = kind == 1 ? p.red : kind == 2 ? p.gold : p.fg;

        // ----- Châm ngôn (thay lời chào), chạm để đổi -----
        v.setTextViewText(R.id.quote, "“" + Quotes.today(c));
        v.setTextColor(R.id.quote, p.fg2);
        Intent nq = new Intent(c, ArtWidget.class).setAction(BaseWidget.ACTION_NEXT_QUOTE);
        v.setOnClickPendingIntent(R.id.quote_box, PendingIntent.getBroadcast(c, 4000 + id, nq, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // ----- Số ngày 3D + thứ, tháng, âm lịch -----
        v.setImageViewBitmap(R.id.big3d, tall3D((d < 10 ? "0" : "") + d, kind, p, dens, 150, 92));
        v.setTextViewText(R.id.d_weekday, WidgetRenderer.THU[wd].toUpperCase(new java.util.Locale("vi")));
        v.setTextColor(R.id.d_weekday, kc);
        SpannableStringBuilder ms = new SpannableStringBuilder();
        Summary.append(ms, "Tháng " + mo, p.fg, true);
        Summary.append(ms, " " + y, p.fg, false);
        v.setTextViewText(R.id.d_month, ms);
        v.setTextViewText(R.id.d_lunar, "ÂL " + WidgetRenderer.lunarDM(L) + " · " + Lunar.canChiYear(L.year));
        v.setTextColor(R.id.d_lunar, p.fg2);

        // ----- Tuổi tốt / xấu -----
        int ct = f.tuoi ? WidgetRenderer.chiTuoi(c) : -1;
        if (ct >= 0) {
            Lunar.DanhGia g = Lunar.danhGia(d, mo, y, ct);
            int mc = g.diem >= 4 ? p.good : (g.diem == 3 ? p.mid : p.red);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            Summary.append(sb, "● ", mc, true);
            Summary.append(sb, "Tuổi " + Lunar.CHI[ct] + " · ", p.fg2, false);
            Summary.append(sb, g.diem == 3 ? "Bình thường" : g.muc, mc, true);
            v.setViewVisibility(R.id.tuoi, View.VISIBLE);
            v.setTextViewText(R.id.tuoi, sb);
        } else v.setViewVisibility(R.id.tuoi, View.GONE);

        // ----- Lịch sắp tới: 3 mục gần nhất -----
        int[] evIds = {R.id.ev0, R.id.ev1, R.id.ev2};
        List<CalendarEvents.Ev> evs = CalendarEvents.upcoming(c, 60);
        int n = 0;
        for (CalendarEvents.Ev e : evs) {
            if (n >= 3) break;
            int col = e.kind == CalendarEvents.PERSONAL ? (light ? p.acc : 0xFF8FC2FF) : e.kind == CalendarEvents.HOLIDAY ? p.red : p.gold;
            boolean today = e.d == d && e.m == mo && e.y == y;
            String when = today ? "Hôm nay" : WidgetRenderer.two(e.d) + "/" + WidgetRenderer.two(e.m) + (e.y != y ? "/" + e.y : "");
            if (!e.allDay && e.kind == CalendarEvents.PERSONAL) {
                Calendar t = Calendar.getInstance(); t.setTimeInMillis(e.start);
                when += " " + WidgetRenderer.two(t.get(Calendar.HOUR_OF_DAY)) + ":" + WidgetRenderer.two(t.get(Calendar.MINUTE));
            }
            SpannableStringBuilder sb = new SpannableStringBuilder();
            Summary.append(sb, "● ", col, true);
            Summary.append(sb, e.title, e.kind == CalendarEvents.PERSONAL ? p.fg : col, true);
            Summary.append(sb, "  · " + when, p.fg2, false);
            v.setViewVisibility(evIds[n], View.VISIBLE);
            v.setTextViewText(evIds[n], sb);
            n++;
        }
        for (; n < 3; n++) v.setViewVisibility(evIds[n], View.GONE);

        // ----- Đồng hồ kim lồng số -----
        String fmt = f.h24 ? "HH:mm" : "h:mm";
        v.setCharSequence(R.id.clock, "setFormat24Hour", fmt);
        v.setCharSequence(R.id.clock, "setFormat12Hour", fmt);
        v.setTextColor(R.id.clock, (p.fg & 0x00FFFFFF) | 0x8C000000);
        v.setViewVisibility(R.id.ampm, f.h24 ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.ampm, hour < 12 ? "AM" : "PM");
        v.setTextColor(R.id.ampm, p.fg2);
        if (Build.VERSION.SDK_INT >= 31)
            v.setColorStateList(R.id.aclock, "setMinuteHandTintList", ColorStateList.valueOf(light ? 0xFF16203A : 0xFFFFFFFF));

        // ----- Thời tiết -----
        JSONObject w = Weather.cached(c);
        v.setViewVisibility(R.id.wx_col, f.weather ? View.VISIBLE : View.INVISIBLE);
        v.setViewVisibility(R.id.wx_icon, f.weather ? View.VISIBLE : View.INVISIBLE);
        v.setTextColor(R.id.wx_temp, p.fg);
        v.setTextColor(R.id.wx_desc, p.fg);
        v.setTextColor(R.id.wx_hilo, p.fg2);
        if (w != null) {
            try {
                JSONObject cur = w.getJSONObject("current"), dl = w.getJSONObject("daily");
                int code = cur.optInt("weather_code", 3);
                v.setImageViewResource(R.id.wx_icon, iconReal(code, cur.optInt("is_day", 1) == 1));
                v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
                v.setTextViewText(R.id.wx_desc, Weather.desc(code));
                v.setTextViewText(R.id.wx_hilo, Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0)) + "°/"
                        + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "° · ẩm " + cur.optInt("relative_humidity_2m", 0) + "%");
            } catch (Exception ignored) {}
        } else {
            v.setTextViewText(R.id.wx_temp, "--°");
            v.setTextViewText(R.id.wx_desc, "Chưa có dữ liệu");
            v.setTextViewText(R.id.wx_hilo, "Mở app để lấy vị trí");
        }
        if (WidgetRenderer.refreshing) v.setTextViewText(R.id.wx_hilo, "Đang cập nhật…");
        Intent r = new Intent(c, ArtWidget.class).setAction(BaseWidget.ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.wx_col, PendingIntent.getBroadcast(c, 5000 + id, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.wx_icon, PendingIntent.getBroadcast(c, 6000 + id, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        String place = Weather.place(c);
        String s = WidgetRenderer.shortPlace(place), pv = WidgetRenderer.province(place);
        SpannableStringBuilder ps = new SpannableStringBuilder();
        if (f.place && !s.isEmpty()) {
            Summary.append(ps, "● ", 0xFFFF8A00, true);
            Summary.append(ps, s, p.fg, true);
            if (!pv.isEmpty() && !pv.equals(s)) Summary.append(ps, " · " + pv, p.fg2, false);
        }
        v.setTextViewText(R.id.place, ps);
        m.updateAppWidget(id, v);
    }
}
