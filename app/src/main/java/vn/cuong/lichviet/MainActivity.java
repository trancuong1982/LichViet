package vn.cuong.lichviet;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_LOC = 101;
    private static final int REQ_CAL = 102;
    private static final int REQ_BG = 103;
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        applyBars();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private class Bridge {
        /** Lấy vị trí hiện tại rồi tải thời tiết (kết quả trả về window.onNativeWeather). */
        @JavascriptInterface
        public void requestWeather() { main.post(MainActivity.this::startLocation); }

        @JavascriptInterface
        public String getCachedWeather() { return cachedPayload(); }

        @JavascriptInterface
        public boolean hasBgLocation() {
            return android.os.Build.VERSION.SDK_INT < 29
                    || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void requestBgLocation() {
            if (android.os.Build.VERSION.SDK_INT < 29) return;
            main.post(() -> requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG));
        }

        @JavascriptInterface
        public boolean hasCalendar() { return CalendarEvents.hasPermission(MainActivity.this); }

        @JavascriptInterface
        public void requestCalendar() {
            main.post(() -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL));
        }

        /** Sự kiện lịch máy + ngày lễ VN + mùng 1/rằm trong {@code days} ngày tới. */
        @JavascriptInterface
        public String getEvents(int days) {
            JSONArray a = new JSONArray();
            try {
                for (CalendarEvents.Ev e : CalendarEvents.upcoming(MainActivity.this, days)) {
                    java.util.Calendar t = java.util.Calendar.getInstance();
                    t.setTimeInMillis(e.start);
                    a.put(new JSONObject().put("y", e.y).put("m", e.m).put("d", e.d).put("title", e.title).put("kind", e.kind)
                            .put("time", e.allDay ? "" : WidgetRenderer.two(t.get(java.util.Calendar.HOUR_OF_DAY)) + ":" + WidgetRenderer.two(t.get(java.util.Calendar.MINUTE))));
                }
            } catch (Exception ignored) {}
            return a.toString();
        }

        @JavascriptInterface
        public boolean isDark() { return WidgetRenderer.isNight(MainActivity.this); }

        @JavascriptInterface
        public String getBirth() { return Weather.birth(MainActivity.this); }

        @JavascriptInterface
        public void setBirth(String b) {
            Weather.setBirth(MainActivity.this, b);
            main.post(() -> WidgetRenderer.updateAll(MainActivity.this));
        }

        @JavascriptInterface
        public int getInterval() { return Weather.interval(MainActivity.this); }

        @JavascriptInterface
        public void setInterval(int m) {
            Weather.setInterval(MainActivity.this, m);
            WidgetRenderer.schedulePeriodic(MainActivity.this);
        }

        @JavascriptInterface
        public String getModel() { return Weather.model(MainActivity.this); }

        @JavascriptInterface
        public void setModel(String m) {
            Weather.setModel(MainActivity.this, m);
            refreshInBackground();
        }

        @JavascriptInterface
        public String listWidgets() {
            JSONArray a = new JSONArray();
            AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
            for (Class<? extends BaseWidget> cls : WidgetRenderer.PROVIDERS) {
                for (int id : m.getAppWidgetIds(new ComponentName(MainActivity.this, cls))) {
                    try { a.put(new JSONObject().put("id", id).put("type", WidgetRenderer.typeOf(cls))); }
                    catch (Exception ignored) {}
                }
            }
            return a.toString();
        }

        @JavascriptInterface
        public void configureWidget(int id) {
            Intent i = new Intent(MainActivity.this, WidgetConfigActivity.class);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(WidgetConfigActivity.EXTRA_FROM_APP, true);
            main.post(() -> startActivity(i));
        }

        @JavascriptInterface
        public boolean pinWidget(String type) {
            AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
            if (!m.isRequestPinAppWidgetSupported()) return false;
            return m.requestPinAppWidget(new ComponentName(MainActivity.this, WidgetRenderer.classOf(type)), null, null);
        }
    }

    private String cachedPayload() {
        String raw = Weather.cachedRaw(this);
        if (raw == null) return "";
        try {
            return new JSONObject().put("w", new JSONObject(raw)).put("place", Weather.place(this))
                    .put("ts", Weather.cachedTs(this)).put("model", Weather.model(this)).toString();
        } catch (Exception e) { return ""; }
    }

    private void refreshInBackground() {
        new Thread(() -> {
            boolean ok = Weather.refresh(getApplicationContext());
            WidgetRenderer.updateAll(getApplicationContext());
            final String payload = cachedPayload();
            final String err = ok ? "null" : JSONObject.quote(Weather.hasLocation(this) ? "Không tải được thời tiết (kiểm tra mạng)" : "Chưa có vị trí");
            main.post(() -> web.evaluateJavascript("window.onNativeWeather(" + JSONObject.quote(payload) + "," + err + ")", null));
        }).start();
    }

    /** Màu thanh trạng thái / điều hướng theo sáng – tối của máy. */
    @SuppressWarnings("deprecation")
    private void applyBars() {
        boolean dark = WidgetRenderer.isNight(this);
        int bg = dark ? Color.parseColor("#11141B") : Color.parseColor("#F6F8FD");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!dark) flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (web != null) web.setBackgroundColor(bg);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyBars();
        if (web != null) web.evaluateJavascript("window.setDark && window.setDark(" + WidgetRenderer.isNight(this) + ")", null);
        WidgetRenderer.updateAll(this);
    }

    private boolean hasLocPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocation() {
        if (!hasLocPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        fetchLocation();
        maybeAskCalendar();
    }

    /** Hỏi quyền đọc lịch máy một lần (để widget hiện sự kiện & ngày lễ chính thức). */
    private void maybeAskCalendar() {
        if (CalendarEvents.hasPermission(this) || Weather.prefs(this).getBoolean("askedCal", false)) return;
        Weather.prefs(this).edit().putBoolean("askedCal", true).apply();
        main.postDelayed(() -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL), 600);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            if (hasLocPermission()) fetchLocation();
            else sendError();
            maybeAskCalendar();
        } else if (requestCode == REQ_BG) {
            web.evaluateJavascript("window.onCalendarChanged && window.onCalendarChanged()", null);
        } else if (requestCode == REQ_CAL) {
            WidgetRenderer.updateAll(this);
            web.evaluateJavascript("window.onCalendarChanged && window.onCalendarChanged()", null);
        }
    }

    /** Lấy vị trí chính xác: ưu tiên GPS, đồng thời mạng; nhận điểm đủ chính xác (≤ 60 m) hoặc tốt nhất sau 15 giây. */
    @SuppressWarnings({"MissingPermission", "deprecation"})
    private void fetchLocation() {
        try {
            final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location last = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
            }
            // Vị trí rất mới (< 3 phút) và chính xác thì dùng luôn
            if (last != null && System.currentTimeMillis() - last.getTime() < 3 * 60 * 1000 && last.getAccuracy() <= 100) {
                sendLocation(last);
                return;
            }
            final Location fallback = last;
            final java.util.List<String> providers = new java.util.ArrayList<>();
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
            if (providers.isEmpty()) {
                if (fallback != null) sendLocation(fallback); else sendError();
                return;
            }
            final boolean[] done = {false};
            final Location[] best = {null};
            final LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (done[0]) return;
                    if (best[0] == null || location.getAccuracy() < best[0].getAccuracy()) best[0] = location;
                    if (location.getAccuracy() <= 60) {
                        done[0] = true;
                        lm.removeUpdates(this);
                        sendLocation(location);
                    }
                }
                @Override public void onStatusChanged(String p, int s, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            for (String p : providers) lm.requestLocationUpdates(p, 0, 0, listener, Looper.getMainLooper());
            main.postDelayed(() -> {
                if (done[0]) return;
                done[0] = true;
                lm.removeUpdates(listener);
                if (best[0] != null) sendLocation(best[0]);
                else if (fallback != null) sendLocation(fallback);
                else sendError();
            }, 15000);
        } catch (Exception e) {
            sendError();
        }
    }

    private void sendLocation(Location l) {
        Weather.saveLocation(this, l.getLatitude(), l.getLongitude());
        Weather.prefs(this).edit().putLong("loc_ts", l.getTime()).apply();
        refreshInBackground();
    }

    private void sendError() {
        // Không lấy được vị trí mới: dùng vị trí đã lưu (nếu có)
        if (Weather.hasLocation(this)) { refreshInBackground(); return; }
        main.post(() -> web.evaluateJavascript("window.onNativeWeather(\"\"," + JSONObject.quote("Chưa có quyền vị trí / GPS đang tắt") + ")", null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetRenderer.updateAll(this);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
