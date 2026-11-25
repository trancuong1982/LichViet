package vn.cuong.lichviet;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/** Lấy & lưu dữ liệu thời tiết (Open-Meteo) dùng chung cho app và widget. */
public final class Weather {
    private Weather() {}

    public static final String DEFAULT_MODEL = "ecmwf_ifs";

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("lichviet", Context.MODE_PRIVATE);
    }

    public static String model(Context c) { return prefs(c).getString("model", DEFAULT_MODEL); }
    public static void setModel(Context c, String m) { prefs(c).edit().putString("model", m).apply(); }

    /** Chu kỳ tự cập nhật thời tiết (phút): 15 | 30 | 60. */
    public static int interval(Context c) { return prefs(c).getInt("interval", 30); }
    public static void setInterval(Context c, int m) { prefs(c).edit().putInt("interval", m).apply(); }
    public static String birth(Context c) { return prefs(c).getString("birth", ""); }
    public static void setBirth(Context c, String b) { prefs(c).edit().putString("birth", b == null ? "" : b).apply(); }

    public static void saveLocation(Context c, double lat, double lon) {
        prefs(c).edit().putString("lat", String.valueOf(lat)).putString("lon", String.valueOf(lon)).apply();
    }
    public static boolean hasLocation(Context c) { return prefs(c).contains("lat"); }

    public static JSONObject cached(Context c) {
        try {
            String s = prefs(c).getString("w_json", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) { return null; }
    }
    public static String cachedRaw(Context c) { return prefs(c).getString("w_json", null); }
    public static long cachedTs(Context c) { return prefs(c).getLong("w_ts", 0); }
    public static String place(Context c) { return prefs(c).getString("place", ""); }

    public static String buildUrl(String lat, String lon, String model) {
        String u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day,precipitation"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max"
                + "&timezone=auto&forecast_days=7";
        if (model != null && !model.isEmpty() && !"best_match".equals(model)) u += "&models=" + model;
        return u;
    }

    /** Gọi mạng (chạy ở luồng nền). Trả về true nếu thành công. */
    public static boolean refresh(Context c) {
        SharedPreferences p = prefs(c);
        String lat = p.getString("lat", null), lon = p.getString("lon", null);
        if (lat == null || lon == null) return false;
        try {
            String body = httpGet(buildUrl(lat, lon, model(c)));
            JSONObject j = new JSONObject(body);
            if (!j.has("current")) return false;
            String place = p.getString("place", "");
            String placeKey = p.getString("place_key", "");
            String key = String.format(Locale.US, "%.2f,%.2f", Double.parseDouble(lat), Double.parseDouble(lon));
            if (place.isEmpty() || !key.equals(placeKey)) {
                String np = reverseGeocode(c, Double.parseDouble(lat), Double.parseDouble(lon));
                if (np != null && !np.isEmpty()) { place = np; placeKey = key; }
            }
            p.edit().putString("w_json", body).putLong("w_ts", System.currentTimeMillis())
                    .putString("place", place).putString("place_key", placeKey).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String httpGet(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("User-Agent", "LichViet-Android/1.0");
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    static String reverseGeocode(Context c, double lat, double lon) {
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(c, new Locale("vi", "VN")).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    String l1 = firstNonEmpty(a.getSubLocality(), a.getLocality(), a.getSubAdminArea());
                    String l2 = a.getAdminArea();
                    String s = join(l1, l2);
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Exception ignored) {}
        try {
            JSONObject g = new JSONObject(httpGet("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude="
                    + lat + "&longitude=" + lon + "&localityLanguage=vi"));
            return join(firstNonEmpty(g.optString("locality"), g.optString("city")), g.optString("principalSubdivision"));
        } catch (Exception e) { return null; }
    }

    private static String firstNonEmpty(String... s) {
        for (String x : s) if (x != null && !x.trim().isEmpty()) return x.trim();
        return null;
    }
    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        if (b == null || b.isEmpty() || b.equals(a)) return a;
        return a + ", " + b;
    }

    /* ------------ diễn giải dữ liệu ------------ */

    public static int iconRes(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code == 1 || code == 2) return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 51 && code <= 57) return R.drawable.wx_drizzle;
        if (code >= 95) return R.drawable.wx_thunder;
        if (code >= 61) return R.drawable.wx_rain;
        return R.drawable.wx_cloud;
    }

    public static String desc(int code) {
        switch (code) {
            case 0: return "Trời quang";
            case 1: return "Ít mây";
            case 2: return "Có mây";
            case 3: return "Nhiều mây";
            case 45: case 48: return "Sương mù";
            case 51: case 53: case 55: case 56: case 57: return "Mưa phùn";
            case 61: return "Mưa nhỏ";
            case 63: return "Mưa vừa";
            case 65: return "Mưa to";
            case 66: case 67: return "Mưa lạnh";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Mưa tuyết";
            case 80: return "Mưa rào nhẹ";
            case 81: return "Mưa rào";
            case 82: return "Mưa rào rất to";
            case 95: return "Dông";
            case 96: case 99: return "Dông, mưa đá";
            default: return "--";
        }
    }

    /** Vị trí giờ hiện tại trong mảng hourly. */
    public static int currentHourIndex(JSONObject w) {
        try {
            String now = w.getJSONObject("current").getString("time").substring(0, 13);
            JSONArray t = w.getJSONObject("hourly").getJSONArray("time");
            for (int i = 0; i < t.length(); i++) if (t.getString(i).substring(0, 13).compareTo(now) >= 0) return i;
        } catch (Exception ignored) {}
        return 0;
    }

    private static boolean rainy(JSONObject h, int i) {
        int code = h.optJSONArray("weather_code").optInt(i, 0);
        double prob = h.optJSONArray("precipitation_probability").optDouble(i, Double.NaN);
        double mm = h.optJSONArray("precipitation").optDouble(i, 0);
        return code >= 51 || (!Double.isNaN(prob) && prob >= 50) || mm >= 0.5;
    }

    /** Câu dự báo mưa nắng ngắn cho 12 giờ tới (gọn để vừa widget). */
    public static String rainHint(JSONObject w) {
        try {
            JSONObject h = w.getJSONObject("hourly"), cur = w.getJSONObject("current");
            JSONArray t = h.getJSONArray("time");
            int i0 = currentHourIndex(w), end = Math.min(i0 + 12, t.length());
            boolean rainingNow = cur.optInt("weather_code", 0) >= 51 || cur.optDouble("precipitation", 0) >= 0.1;
            if (rainingNow) {
                for (int i = i0 + 1; i < end; i++)
                    if (!rainy(h, i)) return "Đang mưa · tạnh khoảng " + hourOf(t.getString(i)) + "h";
                return "Đang mưa · kéo dài nhiều giờ";
            }
            for (int i = i0; i < end; i++) {
                if (rainy(h, i)) {
                    int code = h.getJSONArray("weather_code").optInt(i, 0);
                    double prob = h.getJSONArray("precipitation_probability").optDouble(i, Double.NaN);
                    String kind = code >= 95 ? "Có dông" : "Có thể mưa";
                    String when = i == i0 ? " trong giờ này" : " khoảng " + hourOf(t.getString(i)) + "h";
                    return kind + when + (Double.isNaN(prob) ? "" : " (" + Math.round(prob) + "%)");
                }
            }
            return "Không mưa trong 12 giờ tới";
        } catch (Exception e) { return ""; }
    }

    static int hourOf(String isoTime) { return Integer.parseInt(isoTime.substring(11, 13)); }
}
