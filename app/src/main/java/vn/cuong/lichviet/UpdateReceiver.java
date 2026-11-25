package vn.cuong.lichviet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Báo thức 0h (đổi ngày), chu kỳ tự cập nhật thời tiết, và khi đổi giờ/múi giờ. */
public class UpdateReceiver extends BroadcastReceiver {
    public static final String ACTION_MIDNIGHT = "vn.cuong.lichviet.MIDNIGHT";
    public static final String ACTION_PERIODIC = "vn.cuong.lichviet.PERIODIC";

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_PERIODIC.equals(i.getAction())) {
            BaseWidget.refreshAsync(c, this);
            return;
        }
        WidgetRenderer.updateAll(c);
        WidgetRenderer.scheduleMidnight(c);
        WidgetRenderer.schedulePeriodic(c);
    }
}
