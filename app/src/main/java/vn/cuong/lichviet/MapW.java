package vn.cuong.lichviet;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Widget 5×3 "Bản đồ Việt Nam": ngày 3D, thời tiết trong ngày, bản đồ có định vị GPS. */
public final class MapW {
    private MapW() {}

    /* ---------------- Dữ liệu bản đồ (đã chiếu sẵn) ---------------- */
    static final class MapData {
        float W, H, lon0, lat1, k, sx;
        List<float[]> vn = new ArrayList<>(), nb = new ArrayList<>();
        float[] hs, ts;
    }
    private static MapData MAP;

    static synchronized MapData map(Context c) {
        if (MAP != null) return MAP;
        MapData m = new MapData();
        try (InputStream in = c.getAssets().open("vnmap.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            m.W = (float) j.getDouble("W"); m.H = (float) j.getDouble("H");
            m.lon0 = (float) j.getDouble("LON0"); m.lat1 = (float) j.getDouble("LAT1");
            m.k = (float) j.getDouble("k"); m.sx = (float) j.getDouble("sx");
            readPolys(j.getJSONArray("vn"), m.vn);
            readPolys(j.getJSONArray("nb"), m.nb);
            JSONObject isl = j.getJSONObject("islands");
            m.hs = flat(isl.getJSONArray("hs"));
            m.ts = flat(isl.getJSONArray("ts"));
        } catch (Exception ignored) {}
        MAP = m;
        return m;
    }
    static void readPolys(JSONArray a, List<float[]> out) throws Exception {
        for (int i = 0; i < a.length(); i++) out.add(flat(a.getJSONArray(i)));
    }
    static float[] flat(JSONArray pts) throws Exception {
        float[] f = new float[pts.length() * 2];
        for (int i = 0; i < pts.length(); i++) {
            JSONArray p = pts.getJSONArray(i);
            f[i * 2] = (float) p.getDouble(0); f[i * 2 + 1] = (float) p.getDouble(1);
        }
        return f;
    }

    /** Vẽ bản đồ VN, sáng tại vị trí GPS. */
    static Bitmap drawMap(Context c, Summary.P p, float dens, double lat, double lon, boolean hasLoc) {
        MapData m = map(c);
        float wDp = 100;
        float s = wDp * dens / m.W;
        int Wp = Math.round(m.W * s), Hp = Math.round(m.H * s);
        Bitmap b = Bitmap.createBitmap(Math.max(1, Wp), Math.max(1, Hp), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean dt = p.darkText;
        // nước láng giềng: viền mờ
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(0.8f * dens);
        pt.setColor(dt ? 0x2616203A : 0x2EFFFFFF);
        for (float[] poly : m.nb) cv.drawPath(toPath(poly, s), pt);
        // Việt Nam: tô chuyển màu + viền sáng
        Path vn = new Path();
        for (float[] poly : m.vn) vn.addPath(toPath(poly, s));
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 0, Wp * 0.3f, Hp,
                dt ? 0x995BA8D6 : 0x8C7FD3C7, dt ? 0x993F66F0 : 0x8C5B8CFF, Shader.TileMode.CLAMP));
        cv.drawPath(vn, fill);
        pt.setStrokeWidth(1.1f * dens);
        pt.setStrokeJoin(Paint.Join.ROUND);
        pt.setColor(dt ? 0xCC2D4BA0 : 0xD9FFFFFF);
        cv.drawPath(vn, pt);
        // Hoàng Sa, Trường Sa
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(dt ? 0xCC2D4BA0 : 0xD9FFFFFF);
        for (float[] arr : new float[][]{m.hs, m.ts})
            if (arr != null) for (int i = 0; i < arr.length; i += 2) cv.drawCircle(arr[i] * s, arr[i + 1] * s, 1.1f * dens, dot);
        // vị trí GPS: vùng tỏa sáng + vòng + điểm
        if (hasLoc) {
            float x = (float) ((lon - m.lon0) * m.k * m.sx) * s, y = (float) ((m.lat1 - lat) * m.sx) * s;
            if (x >= 0 && x <= Wp && y >= 0 && y <= Hp) {
                Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
                float r = 30 * s;
                g.setShader(new RadialGradient(x, y, r, new int[]{0xF2FFE27A, 0x8CFFB23A, 0x00FF8A00}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
                cv.drawCircle(x, y, r, g);
                Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(1.6f * dens);
                ring.setColor(0xCCFFD25E);
                cv.drawCircle(x, y, 10 * s, ring);
                Paint d1 = new Paint(Paint.ANTI_ALIAS_FLAG);
                d1.setColor(0xFFFF8A00);
                cv.drawCircle(x, y, 4.6f * s, d1);
                d1.setColor(0xFFFFFFFF);
                cv.drawCircle(x, y, 2.6f * s, d1);
            }
        }
        return b;
    }
    static Path toPath(float[] poly, float s) {
        Path pa = new Path();
        for (int i = 0; i < poly.length; i += 2) {
            if (i == 0) pa.moveTo(poly[0] * s, poly[1] * s); else pa.lineTo(poly[i] * s, poly[i + 1] * s);
        }
        pa.close();
        return pa;
    }
    static float[] center(float[] a) { float x = 0, y = 0; for (int i = 0; i < a.length; i += 2) { x += a[i]; y += a[i + 1]; } return new float[]{x / (a.length / 2f), y / (a.length / 2f)}; }
    static float minY(float[] a) { float v = Float.MAX_VALUE; for (int i = 1; i < a.length; i += 2) v = Math.min(v, a[i]); return v; }
    static float maxY(float[] a) { float v = -Float.MAX_VALUE; for (int i = 1; i < a.length; i += 2) v = Math.max(v, a[i]); return v; }

    /* ---------------- Dự báo trong ngày: 3 buổi tới ---------------- */
    static final String[] BUOI = {"Sáng", "Trưa", "Chiều", "Tối", "Đêm"};
    static final int[][] KHUNG = {{6, 11, 9}, {11, 14, 12}, {14, 18, 16}, {18, 22, 20}, {22, 30, 24}};

    static int buoiOf(int h) {
        if (h >= 6 && h < 11) return 0;
        if (h >= 11 && h < 14) return 1;
        if (h >= 14 && h < 18) return 2;
        if (h >= 18 && h < 22) return 3;
        return 4;
    }

    /* ---------------- Vẽ widget ---------------- */
    static final int[][] FC = {{R.id.f0_lb, R.id.f0_ic, R.id.f0_t}, {R.id.f1_lb, R.id.f1_ic, R.id.f1_t}, {R.id.f2_lb, R.id.f2_ic, R.id.f2_t}};

    static void render(Context c, AppWidgetManager m, int id, WidgetRenderer.Cfg f) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_map);
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
        v.setInt(R.id.panel, "setColorFilter", light ? 0xFF2D4BA0 : 0xFFFFFFFF);
        v.setInt(R.id.panel, "setImageAlpha", light ? 20 : 26);

        Calendar now = Calendar.getInstance();
        int d = now.get(Calendar.DAY_OF_MONTH), mo = now.get(Calendar.MONTH) + 1, y = now.get(Calendar.YEAR);
        int wd = now.get(Calendar.DAY_OF_WEEK) - 1, hour = now.get(Calendar.HOUR_OF_DAY);
        Lunar.Date L = Lunar.fromSolar(d, mo, y);

        // ----- Lời chào + đồng hồ -----
        v.setTextViewText(R.id.greet, Summary.greeting(hour));
        v.setTextColor(R.id.greet, p.fg);
        String fmt = f.h24 ? "HH:mm" : "h:mm";
        v.setCharSequence(R.id.clock, "setFormat24Hour", fmt);
        v.setCharSequence(R.id.clock, "setFormat12Hour", fmt);
        v.setTextColor(R.id.clock, p.fg);
        v.setViewVisibility(R.id.ampm, f.h24 ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.ampm, hour < 12 ? "AM" : "PM");
        v.setTextColor(R.id.ampm, p.fg);

        // ----- Ngày 3D + nhắc nhở -----
        String le = Lunar.ngayLe(d, mo, L);
        boolean lunarMark = L.day == 1 || L.day == 15;
        int kind = WidgetRenderer.dayKind(d, mo, wd, L);
        int kc = kind == 1 ? p.red : kind == 2 ? p.gold : p.fg;
        v.setImageViewBitmap(R.id.big3d, Summary.draw3D(String.valueOf(d), kind, p, dens, 82, 68));
        v.setTextViewText(R.id.d_weekday, WidgetRenderer.THU[wd]);
        v.setTextColor(R.id.d_weekday, kc);
        v.setTextViewText(R.id.d_month, "Tháng " + mo + " · " + y);
        v.setTextColor(R.id.d_month, p.fg);
        v.setTextViewText(R.id.d_lunar, "ÂL " + WidgetRenderer.lunarDM(L) + " · " + Lunar.canChiYear(L.year));
        v.setTextColor(R.id.d_lunar, p.fg2);

        List<CharSequence> rem = new ArrayList<>();
        for (CalendarEvents.Ev e : CalendarEvents.upcoming(c, 1)) {
            if (e.d != d || e.m != mo) continue;
            int col = e.kind == CalendarEvents.PERSONAL ? (light ? p.acc : 0xFF8FC2FF) : e.kind == CalendarEvents.HOLIDAY ? p.red : p.gold;
            SpannableStringBuilder sb = new SpannableStringBuilder();
            Summary.append(sb, "● ", col, true);
            Summary.append(sb, e.title, e.kind == CalendarEvents.PERSONAL ? p.fg : col, true);
            if (e.kind == CalendarEvents.PERSONAL) rem.add(0, sb); else rem.add(sb);
        }
        int[] remIds = {R.id.rem1, R.id.rem2};
        for (int i = 0; i < 2; i++) {
            if (i < rem.size()) { v.setViewVisibility(remIds[i], View.VISIBLE); v.setTextViewText(remIds[i], rem.get(i)); }
            else v.setViewVisibility(remIds[i], View.GONE);
        }
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

        // ----- Thời tiết -----
        JSONObject w = Weather.cached(c);
        v.setViewVisibility(R.id.wx_col, f.weather ? View.VISIBLE : View.GONE);
        for (int t : new int[]{R.id.wx_temp, R.id.wx_desc}) v.setTextColor(t, p.fg);
        v.setTextColor(R.id.wx_hilo, p.fg2);
        v.setTextColor(R.id.rain, p.fg2);
        if (w != null) {
            try {
                JSONObject cur = w.getJSONObject("current"), dl = w.getJSONObject("daily"), h = w.getJSONObject("hourly");
                int code = cur.optInt("weather_code", 3);
                v.setImageViewResource(R.id.wx_icon, Weather.iconVivid(code, cur.optInt("is_day", 1) == 1));
                v.setTextViewText(R.id.wx_temp, Math.round(cur.optDouble("temperature_2m", 0)) + "°");
                v.setTextViewText(R.id.wx_desc, Weather.desc(code));
                v.setTextViewText(R.id.wx_hilo, Math.round(dl.getJSONArray("temperature_2m_max").optDouble(0, 0)) + "°/"
                        + Math.round(dl.getJSONArray("temperature_2m_min").optDouble(0, 0)) + "° · ẩm " + cur.optInt("relative_humidity_2m", 0) + "%");
                int i0 = Weather.currentHourIndex(w), dayBase = i0 - hour;
                int b = buoiOf(hour), base = dayBase + (hour < 6 ? -24 : 0);
                JSONArray hc = h.getJSONArray("weather_code"), ht = h.getJSONArray("temperature_2m"), hd = h.getJSONArray("is_day");
                for (int k = 0; k < 3; k++) {
                    b++;
                    if (b > 4) { b = 0; base += 24; }
                    int[] kh = KHUNG[b];
                    int maxCode = 0, rep = Math.min(base + kh[2], ht.length() - 1);
                    for (int i = base + kh[0]; i < base + kh[1] && i < hc.length(); i++) if (i >= 0) maxCode = Math.max(maxCode, hc.optInt(i, 0));
                    v.setTextViewText(FC[k][0], BUOI[b]);
                    v.setTextColor(FC[k][0], p.fg2);
                    v.setImageViewResource(FC[k][1], Weather.iconVivid(maxCode, rep >= 0 && hd.optInt(rep, 1) == 1));
                    v.setTextViewText(FC[k][2], rep >= 0 ? Math.round(ht.optDouble(rep, 0)) + "°" : "--");
                    v.setTextColor(FC[k][2], p.fg);
                }
                v.setTextViewText(R.id.rain, Weather.rainHintShort(w));
            } catch (Exception ignored) {}
        } else {
            v.setTextViewText(R.id.wx_temp, "--°");
            v.setTextViewText(R.id.wx_desc, "Chưa có dữ liệu");
            v.setTextViewText(R.id.wx_hilo, "Mở app để lấy vị trí");
            v.setTextViewText(R.id.rain, "");
        }
        Intent r = new Intent(c, MapWidget.class).setAction(BaseWidget.ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.wx_col, PendingIntent.getBroadcast(c, 1000 + id, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (WidgetRenderer.refreshing) v.setTextViewText(R.id.rain, "Đang cập nhật…");

        // ----- Bản đồ + địa điểm -----
        double lat = 0, lon = 0;
        boolean hasLoc = Weather.hasLocation(c);
        try {
            lat = Double.parseDouble(Weather.prefs(c).getString("lat", "0"));
            lon = Double.parseDouble(Weather.prefs(c).getString("lon", "0"));
        } catch (Exception e) { hasLoc = false; }
        v.setImageViewBitmap(R.id.map, drawMap(c, p, dens, lat, lon, hasLoc));
        String[] pl = {WidgetRenderer.shortPlace(Weather.place(c)), WidgetRenderer.province(Weather.place(c))};
        if (pl[1].equals(pl[0])) pl[1] = "";
        SpannableStringBuilder ps = new SpannableStringBuilder();
        if (!pl[0].isEmpty()) { Summary.append(ps, "● ", 0xFFFF8A00, true); Summary.append(ps, pl[0], p.fg, true); }
        v.setTextViewText(R.id.place, f.place ? ps : "");
        v.setTextViewText(R.id.prov, f.place ? pl[1] : "");
        v.setTextColor(R.id.prov, p.fg2);
        m.updateAppWidget(id, v);
    }

    /** "Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh" → {"Bến Nghé", "TP. Hồ Chí Minh"} */
    static String[] placeParts(String place) {
        if (place == null || place.trim().isEmpty()) return new String[]{"", ""};
        String[] parts = place.split(",");
        String first = WidgetRenderer.shortPlace(place);
        String prov = parts.length > 1 ? parts[parts.length - 1].trim() : "";
        if (prov.startsWith("Thành phố ")) prov = "TP. " + prov.substring(10);
        else if (prov.startsWith("Tỉnh ")) prov = prov.substring(5);
        if (prov.equals(first)) prov = "";
        return new String[]{first, prov};
    }
}
