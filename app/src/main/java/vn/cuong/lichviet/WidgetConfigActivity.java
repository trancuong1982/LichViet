package vn.cuong.lichviet;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/** Màn hình tùy chỉnh widget (mở khi thêm widget hoặc khi chọn "Tùy chỉnh"). */
public class WidgetConfigActivity extends Activity {
    public static final String EXTRA_FROM_APP = "from_app";
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String type = "clock";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        if (in != null && in.getExtras() != null)
            widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // Mặc định: hủy (nếu người dùng thoát ngang khi đang thêm widget)
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        type = WidgetRenderer.typeOfId(this, widgetId);

        boolean dark = WidgetRenderer.isNight(this);
        int bg = dark ? Color.parseColor("#11141B") : Color.parseColor("#F6F8FD");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        WebView web = new WebView(this);
        web.setBackgroundColor(bg);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "CfgBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/widget-config.html");
    }

    private class Bridge {
        @JavascriptInterface public String getType() { return type; }

        @JavascriptInterface public boolean isDark() { return WidgetRenderer.isNight(WidgetConfigActivity.this); }

        @JavascriptInterface public String getBirth() { return Weather.birth(WidgetConfigActivity.this); }

        @JavascriptInterface public boolean isWallLight() { return Summary.wallpaperLight(WidgetConfigActivity.this); }

        @JavascriptInterface public String getEvents(int days) {
            org.json.JSONArray a = new org.json.JSONArray();
            try {
                for (CalendarEvents.Ev e : CalendarEvents.upcoming(WidgetConfigActivity.this, days)) {
                    java.util.Calendar t = java.util.Calendar.getInstance();
                    t.setTimeInMillis(e.start);
                    a.put(new JSONObject().put("y", e.y).put("m", e.m).put("d", e.d).put("title", e.title).put("kind", e.kind)
                            .put("time", e.allDay ? "" : WidgetRenderer.two(t.get(java.util.Calendar.HOUR_OF_DAY)) + ":" + WidgetRenderer.two(t.get(java.util.Calendar.MINUTE))));
                }
            } catch (Exception ignored) {}
            return a.toString();
        }

        @JavascriptInterface public String getConfig() { return WidgetRenderer.cfgJson(WidgetConfigActivity.this, widgetId); }

        @JavascriptInterface public String getWeather() {
            String raw = Weather.cachedRaw(WidgetConfigActivity.this);
            if (raw == null) return "";
            try {
                return new JSONObject().put("w", new JSONObject(raw))
                        .put("place", Weather.place(WidgetConfigActivity.this)).toString();
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public void save(String json) {
            runOnUiThread(() -> {
                WidgetRenderer.saveCfg(WidgetConfigActivity.this, widgetId, json);
                WidgetRenderer.render(WidgetConfigActivity.this, AppWidgetManager.getInstance(WidgetConfigActivity.this), widgetId, type);
                setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
                finish();
            });
        }

        @JavascriptInterface public void cancel() { runOnUiThread(WidgetConfigActivity.this::finish); }
    }
}
