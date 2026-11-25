package vn.cuong.lichviet;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicBoolean;

/** Lớp cha cho các widget: vẽ lại, hẹn giờ 0h, tự làm mới thời tiết khi dữ liệu cũ. */
public abstract class BaseWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "vn.cuong.lichviet.REFRESH";
    private static final long STALE_MS = 25 * 60 * 1000L;
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    protected abstract String type();

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) WidgetRenderer.render(c, m, id, type());
        WidgetRenderer.scheduleMidnight(c);
        if (System.currentTimeMillis() - Weather.cachedTs(c) > STALE_MS) refreshAsync(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        WidgetRenderer.render(c, m, id, type());
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_REFRESH.equals(i.getAction())) refreshAsync(c);
        else super.onReceive(c, i);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Weather.prefs(c).edit().remove("wcfg_" + id).apply();
    }

    private void refreshAsync(final Context c) {
        if (!Weather.hasLocation(c) || !busy.compareAndSet(false, true)) return;
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                Weather.refresh(app);
                WidgetRenderer.updateAll(app);
            } finally {
                busy.set(false);
                pr.finish();
            }
        }).start();
    }
}
