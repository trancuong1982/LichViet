package vn.cuong.lichviet;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Gộp sự kiện từ lịch của máy với ngày lễ Việt Nam, mùng 1 và rằm. */
public final class CalendarEvents {
    private CalendarEvents() {}

    public static final int PERSONAL = 0, HOLIDAY = 1, LUNAR = 2;

    public static final class Ev implements Comparable<Ev> {
        public long start;          // ms, với sự kiện cả ngày: 0h giờ địa phương
        public boolean allDay;
        public String title;
        public int kind;
        public int y, m, d;          // ngày (giờ địa phương)
        @Override public int compareTo(Ev o) {
            if (start != o.start) return Long.compare(start, o.start);
            return Integer.compare(kind, o.kind);
        }
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static int key(int y, int m, int d) { return y * 10000 + m * 100 + d; }

    /** Sự kiện từ hôm nay (0h) trong {@code days} ngày tới, đã sắp xếp. */
    public static List<Ev> upcoming(Context c, int days) {
        List<Ev> out = new ArrayList<>();
        Calendar t0 = Calendar.getInstance();
        t0.set(Calendar.HOUR_OF_DAY, 0); t0.set(Calendar.MINUTE, 0); t0.set(Calendar.SECOND, 0); t0.set(Calendar.MILLISECOND, 0);
        long from = t0.getTimeInMillis();
        long to = from + days * 86_400_000L;
        Set<Integer> holidayDays = new HashSet<>();
        long now = System.currentTimeMillis();

        // 1) Lịch của máy
        if (hasPermission(c)) {
            Cursor cur = null;
            try {
                Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(b, from - 86_400_000L);
                ContentUris.appendId(b, to);
                String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Instances.OWNER_ACCOUNT, CalendarContract.Instances.VISIBLE};
                cur = c.getContentResolver().query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC");
                if (cur != null) {
                    while (cur.moveToNext()) {
                        if (cur.getInt(6) == 0) continue;
                        String title = cur.getString(0);
                        if (title == null || title.trim().isEmpty()) continue;
                        long begin = cur.getLong(1), end = cur.getLong(2);
                        boolean allDay = cur.getInt(3) == 1;
                        Ev e = new Ev();
                        e.title = title.trim();
                        e.allDay = allDay;
                        Calendar k;
                        if (allDay) {
                            // Sự kiện cả ngày lưu theo UTC
                            Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                            u.setTimeInMillis(begin);
                            k = Calendar.getInstance();
                            k.clear();
                            k.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
                        } else {
                            if (end < now) continue;
                            k = Calendar.getInstance();
                            k.setTimeInMillis(begin);
                        }
                        e.start = k.getTimeInMillis();
                        e.y = k.get(Calendar.YEAR); e.m = k.get(Calendar.MONTH) + 1; e.d = k.get(Calendar.DAY_OF_MONTH);
                        if (key(e.y, e.m, e.d) < key(t0.get(Calendar.YEAR), t0.get(Calendar.MONTH) + 1, t0.get(Calendar.DAY_OF_MONTH))) continue;
                        String cal = ((cur.getString(4) == null ? "" : cur.getString(4)) + " " + (cur.getString(5) == null ? "" : cur.getString(5))).toLowerCase(Locale.ROOT);
                        boolean holidayCal = cal.contains("holiday") || cal.contains("ngày lễ") || cal.contains("ngay le") || cal.contains("#holiday");
                        e.kind = holidayCal ? HOLIDAY : PERSONAL;
                        if (holidayCal) holidayDays.add(key(e.y, e.m, e.d));
                        out.add(e);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cur != null) cur.close();
            }
        }

        // 2) Ngày lễ Việt Nam + mùng 1, rằm (có sẵn trong app)
        Calendar k = (Calendar) t0.clone();
        for (int i = 0; i < days; i++) {
            int y = k.get(Calendar.YEAR), m = k.get(Calendar.MONTH) + 1, d = k.get(Calendar.DAY_OF_MONTH);
            Lunar.Date L = Lunar.fromSolar(d, m, y);
            String le = Lunar.ngayLe(d, m, L);
            if (le != null && !holidayDays.contains(key(y, m, d))) out.add(make(k, le, HOLIDAY));
            else if (le == null && (L.day == 1 || L.day == 15))
                out.add(make(k, (L.day == 1 ? "Mùng 1 tháng " : "Rằm tháng ") + L.month + " ÂL", LUNAR));
            k.add(Calendar.DAY_OF_MONTH, 1);
        }
        Collections.sort(out);
        return out;
    }

    private static Ev make(Calendar k, String title, int kind) {
        Ev e = new Ev();
        e.start = k.getTimeInMillis(); e.allDay = true; e.title = title; e.kind = kind;
        e.y = k.get(Calendar.YEAR); e.m = k.get(Calendar.MONTH) + 1; e.d = k.get(Calendar.DAY_OF_MONTH);
        return e;
    }

    /** Ngày trong tháng có sự kiện cá nhân (để đánh dấu trên lịch). */
    public static Set<Integer> personalDays(List<Ev> evs) {
        Set<Integer> s = new HashSet<>();
        for (Ev e : evs) if (e.kind == PERSONAL) s.add(key(e.y, e.m, e.d));
        return s;
    }
}
