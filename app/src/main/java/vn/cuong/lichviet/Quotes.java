package vn.cuong.lichviet;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/** Châm ngôn mỗi ngày: đổi 1 câu mỗi ngày, không lặp lại cho đến khi hết vòng; chạm để đổi câu khác. */
public final class Quotes {
    private Quotes() {}
    private static List<String> cache;

    static synchronized List<String> all(Context c) {
        if (cache != null) return cache;
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("quotes.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) if (!line.trim().isEmpty()) out.add(line.trim());
        } catch (Exception ignored) {}
        if (out.isEmpty()) out.add("Có công mài sắt, có ngày nên kim.");
        cache = out;
        return out;
    }

    static int dayIndex() {
        Calendar k = Calendar.getInstance();
        return Lunar.jdFromDate(k.get(Calendar.DAY_OF_MONTH), k.get(Calendar.MONTH) + 1, k.get(Calendar.YEAR));
    }

    public static String today(Context c) {
        List<String> q = all(c);
        int n = q.size(), day = dayIndex();
        int cycle = day / n, pos = day % n;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        Random rnd = new Random(cycle * 7919L + 2026L);
        for (int i = n - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
        int off = 0;
        if (Weather.prefs(c).getInt("q_day", -1) == day) off = Weather.prefs(c).getInt("q_off", 0);
        return q.get(perm[(pos + off) % n]);
    }

    /** Chạm vào câu: đổi sang câu khác (trong ngày). */
    public static void next(Context c) {
        int day = dayIndex();
        int off = Weather.prefs(c).getInt("q_day", -1) == day ? Weather.prefs(c).getInt("q_off", 0) : 0;
        Weather.prefs(c).edit().putInt("q_day", day).putInt("q_off", off + 1).apply();
    }
}
