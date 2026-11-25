package vn.cuong.lichviet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Nhận báo thức 0h (và khi đổi giờ/múi giờ) để vẽ lại ngày dương – âm trên widget. */
public class UpdateReceiver extends BroadcastReceiver {
    public static final String ACTION_MIDNIGHT = "vn.cuong.lichviet.MIDNIGHT";

    @Override
    public void onReceive(Context c, Intent i) {
        WidgetRenderer.updateAll(c);
        WidgetRenderer.scheduleMidnight(c);
    }
}
