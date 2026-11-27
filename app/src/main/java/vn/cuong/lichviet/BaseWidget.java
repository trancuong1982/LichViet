package vn.cuong.lichviet;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;

/** Lớp cha cho các widget: vẽ lại, hẹn giờ 0h & chu kỳ cập nhật, nút làm mới thời tiết. */
public abstract class BaseWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "vn.cuong.lichviet.REFRESH";
    public static final String ACTION_NEXT_QUOTE = "vn.cuong.lichviet.NEXT_QUOTE";
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    protected abstract String type();

    @Override
    public void onEnabled(Context c) {
        WidgetRenderer.schedulePeriodic(c);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            try { WidgetRenderer.render(c, m, id, type()); } catch (Exception ignored) {}
        }
        WidgetRenderer.scheduleMidnight(c);
        WidgetRenderer.schedulePeriodic(c);
        long stale = Weather.interval(c) * 60_000L - 60_000L;
        if (System.currentTimeMillis() - Weather.cachedTs(c) > stale) refreshAsync(c, this);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        WidgetRenderer.render(c, m, id, type());
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) refreshAsync(c, this);
        else if (ACTION_NEXT_QUOTE.equals(i.getAction())) { Quotes.next(c); WidgetRenderer.updateAll(c); }
        else super.onReceive(c, i);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Weather.prefs(c).edit().remove("wcfg_" + id).apply();
    }

    /** Tải thời tiết ở luồng nền; widget hiện "Đang cập nhật…" trong lúc chờ. */
    static void refreshAsync(final Context c, android.content.BroadcastReceiver r) {
        if (!Weather.hasLocation(c) || !busy.compareAndSet(false, true)) return;
        final android.content.BroadcastReceiver.PendingResult pr = r.goAsync();
        final Context app = c.getApplicationContext();
        WidgetRenderer.refreshing = true;
        WidgetRenderer.updateAll(app);
        new Thread(() -> {
            try {
                Weather.tryUpdateLocation(app);
                Weather.refresh(app);
            } finally {
                WidgetRenderer.refreshing = false;
                WidgetRenderer.updateAll(app);
                busy.set(false);
                pr.finish();
            }
        }).start();
    }
}
