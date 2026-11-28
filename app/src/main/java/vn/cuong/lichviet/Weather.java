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
            String key = String.format(Locale.US, "%.3f,%.3f", Double.parseDouble(lat), Double.parseDouble(lon));
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

    static final String[] WARD = {"Phường ", "Xã ", "Thị trấn ", "Đặc khu "};
    static final String[] DIST = {"Quận ", "Huyện ", "Thị xã ", "Thành phố "};

    static String startsWithAny(String s, String[] pre) {
        if (s == null) return null;
        s = s.trim();
        for (String p : pre) if (s.startsWith(p)) return s;
        return null;
    }

    /** Tên địa điểm chi tiết: "Phường …, Quận …, Tỉnh/TP …". */
    @SuppressWarnings("deprecation")
    static String reverseGeocode(Context c, double lat, double lon) {
        // 1) Bộ chuyển đổi địa chỉ của máy
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(c, new Locale("vi", "VN")).getFromLocation(lat, lon, 3);
                if (list != null) {
                    for (Address a : list) {
                        String ward = startsWithAny(a.getSubLocality(), WARD), dist = startsWithAny(a.getSubAdminArea(), DIST);
                        String prov = a.getAdminArea();
                        String line = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : null;
                        if (line != null) {
                            for (String part : line.split(",")) {
                                String x = part.trim();
                                if (ward == null) ward = startsWithAny(x, WARD);
                                if (dist == null && !x.equals(prov)) dist = startsWithAny(x, DIST);
                            }
                        }
                        if (ward != null) return join(join(ward, dist), prov);
                    }
                }
            }
        } catch (Exception ignored) {}
        // 2) OpenStreetMap (Nominatim) – thường có phường/xã
        try {
            JSONObject g = new JSONObject(httpGet("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16&accept-language=vi&lat="
                    + lat + "&lon=" + lon));
            JSONObject ad = g.optJSONObject("address");
            if (ad != null) {
                String ward = firstNonEmpty(ad.optString("quarter"), ad.optString("suburb"), ad.optString("village"), ad.optString("town"));
                String dist = firstNonEmpty(ad.optString("city_district"), ad.optString("county"));
                String prov = firstNonEmpty(ad.optString("city"), ad.optString("state"));
                String s = join(join(ward, dist), prov);
                if (ward != null && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) {}
        // 3) BigDataCloud
        try {
            JSONObject g = new JSONObject(httpGet("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude="
                    + lat + "&longitude=" + lon + "&localityLanguage=vi"));
            return join(firstNonEmpty(g.optString("locality"), g.optString("city")), g.optString("principalSubdivision"));
        } catch (Exception e) { return null; }
    }

    /** Khi chạy nền: lấy vị trí mới nhất máy đã có (nếu được phép) để widget đúng nơi đang đứng. */
    @SuppressWarnings("MissingPermission")
    /** Lấy vị trí chính xác cho widget: ưu tiên định vị mới (GPS) ≤ 100 m, sau đó vị trí gần nhất ≤ 150 m. */
    static void tryUpdateLocation(Context c) {
        try {
            boolean fine = c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (!fine && c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            android.location.Location best = null;
            // 1) vị trí gần nhất của các nguồn
            for (String p : lm.getProviders(true)) {
                android.location.Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime() + 60_000 || (Math.abs(l.getTime() - best.getTime()) <= 60_000 && l.getAccuracy() < best.getAccuracy())) best = l;
            }
            boolean fresh = best != null && System.currentTimeMillis() - best.getTime() < 5 * 60_000 && best.getAccuracy() <= 100;
            // 2) nếu chưa đủ mới/chính xác: xin định vị mới (Android 11+), chờ tối đa 8 giây
            if (!fresh && android.os.Build.VERSION.SDK_INT >= 30) {
                String prov = fine && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ? android.location.LocationManager.GPS_PROVIDER
                        : lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) ? android.location.LocationManager.NETWORK_PROVIDER : null;
                if (prov != null) {
                    final android.location.Location[] got = new android.location.Location[1];
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    android.os.CancellationSignal cs = new android.os.CancellationSignal();
                    lm.getCurrentLocation(prov, cs, java.util.concurrent.Executors.newSingleThreadExecutor(), l -> { got[0] = l; latch.countDown(); });
                    if (!latch.await(8, java.util.concurrent.TimeUnit.SECONDS)) cs.cancel();
                    android.location.Location l = got[0];
                    if (l != null && (best == null || l.getAccuracy() <= Math.max(best.getAccuracy(), 100) || l.getTime() > best.getTime() + 5 * 60_000)) best = l;
                }
            }
            long saved = prefs(c).getLong("loc_ts", 0);
            if (best != null && best.getTime() > saved && best.getAccuracy() <= 150) {
                saveLocation(c, best.getLatitude(), best.getLongitude());
                prefs(c).edit().putLong("loc_ts", best.getTime()).putFloat("loc_acc", best.getAccuracy()).apply();
            }
        } catch (Exception ignored) {}
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

    /** Biểu tượng sinh động (có chuyển màu) cho widget bản đồ. */
    public static int iconVivid(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wxv_sun : R.drawable.wxv_moon;
        if (code == 1 || code == 2) return day ? R.drawable.wxv_partly_day : R.drawable.wxv_partly_night;
        if (code == 3) return R.drawable.wxv_cloud;
        if (code == 45 || code == 48) return R.drawable.wxv_fog;
        if (code >= 51 && code <= 57) return R.drawable.wxv_drizzle;
        if (code >= 95) return R.drawable.wxv_thunder;
        if (code >= 61) return R.drawable.wxv_rain;
        return R.drawable.wxv_cloud;
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

    /** Bản rất ngắn cho widget 2×2. */
    public static String rainHintShort(JSONObject w) {
        try {
            JSONObject h = w.getJSONObject("hourly"), cur = w.getJSONObject("current");
            JSONArray t = h.getJSONArray("time");
            int i0 = currentHourIndex(w), end = Math.min(i0 + 12, t.length());
            if (cur.optInt("weather_code", 0) >= 51 || cur.optDouble("precipitation", 0) >= 0.1) {
                for (int i = i0 + 1; i < end; i++) if (!rainy(h, i)) return "Đang mưa · tạnh ~" + hourOf(t.getString(i)) + "h";
                return "Đang mưa";
            }
            for (int i = i0; i < end; i++) {
                if (rainy(h, i)) {
                    double prob = h.getJSONArray("precipitation_probability").optDouble(i, Double.NaN);
                    String kind = h.getJSONArray("weather_code").optInt(i, 0) >= 95 ? "Dông" : "Mưa";
                    return kind + " ~" + hourOf(t.getString(i)) + "h" + (Double.isNaN(prob) ? "" : " · " + Math.round(prob) + "%");
                }
            }
            return "Không mưa 12h tới";
        } catch (Exception e) { return ""; }
    }

    static int hourOf(String isoTime) { return Integer.parseInt(isoTime.substring(11, 13)); }
}
