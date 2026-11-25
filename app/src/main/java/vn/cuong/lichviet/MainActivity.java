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
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0f1b2d"));
        getWindow().setNavigationBarColor(Color.parseColor("#0b1524"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f1b2d"));
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            if (hasLocPermission()) fetchLocation();
            else sendError();
        }
    }

    @SuppressWarnings({"MissingPermission", "deprecation"})
    private void fetchLocation() {
        try {
            final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            // Vị trí gần đây (< 15 phút) thì dùng luôn
            if (best != null && System.currentTimeMillis() - best.getTime() < 15 * 60 * 1000) {
                sendLocation(best);
                return;
            }
            final Location fallback = best;
            String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? LocationManager.NETWORK_PROVIDER
                    : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null);
            if (provider == null) {
                if (fallback != null) sendLocation(fallback); else sendError();
                return;
            }
            final boolean[] done = {false};
            final LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (done[0]) return;
                    done[0] = true;
                    lm.removeUpdates(this);
                    sendLocation(location);
                }
                @Override public void onStatusChanged(String p, int s, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            lm.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
            // Hết 20 giây chưa có thì dùng vị trí cũ
            main.postDelayed(() -> {
                if (done[0]) return;
                done[0] = true;
                lm.removeUpdates(listener);
                if (fallback != null) sendLocation(fallback); else sendError();
            }, 20000);
        } catch (Exception e) {
            sendError();
        }
    }

    private void sendLocation(Location l) {
        Weather.saveLocation(this, l.getLatitude(), l.getLongitude());
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
