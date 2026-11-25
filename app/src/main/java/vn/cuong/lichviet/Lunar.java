package vn.cuong.lichviet;

/** Âm lịch Việt Nam – thuật toán thiên văn (Hồ Ngọc Đức), múi giờ +7. Bản Java khớp với lunar.js */
public final class Lunar {
    private Lunar() {}
    private static final double TZ = 7.0;

    public static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    public static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};
    public static final String[] THANG = {"", "Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười một", "Chạp"};

    public static final class Date {
        public int day, month, year, jd;
        public boolean leap;
    }

    private static int INT(double d) { return (int) Math.floor(d); }

    public static int jdFromDate(int dd, int mm, int yy) {
        int a = INT((14 - mm) / 12.0), y = yy + 4800 - a, m = mm + 12 * a - 3;
        int jd = dd + INT((153 * m + 2) / 5.0) + 365 * y + INT(y / 4.0) - INT(y / 100.0) + INT(y / 400.0) - 32045;
        if (jd < 2299161) jd = dd + INT((153 * m + 2) / 5.0) + 365 * y + INT(y / 4.0) - 32083;
        return jd;
    }

    private static double newMoon(int k) {
        double T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = Math.PI / 180;
        double Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
        C1 = C1 - 0.0004 * Math.sin(dr * 3 * Mpr);
        C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
        C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
        C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
        C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
        double deltat = T < -11
                ? 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
                : -0.000278 + 0.000265 * T + 0.000262 * T2;
        return Jd1 + C1 - deltat;
    }

    private static double sunLongitudeRad(double jdn) {
        double T = (jdn - 2451545.5 - TZ / 24) / 36525, T2 = T * T, dr = Math.PI / 180;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
        double L = (L0 + DL) * dr;
        L = L - Math.PI * 2 * INT(L / (Math.PI * 2));
        return L;
    }

    private static int getSunLongitude(int jdn) { return INT(sunLongitudeRad(jdn) / Math.PI * 6); }
    private static int getNewMoonDay(int k) { return INT(newMoon(k) + 0.5 + TZ / 24); }

    private static int getLunarMonth11(int yy) {
        double off = jdFromDate(31, 12, yy) - 2415021;
        int k = INT(off / 29.530588853);
        int nm = getNewMoonDay(k);
        if (getSunLongitude(nm) >= 9) nm = getNewMoonDay(k - 1);
        return nm;
    }

    private static int getLeapMonthOffset(int a11) {
        int k = INT((a11 - 2415021.076998695) / 29.530588853 + 0.5), last, i = 1;
        int arc = getSunLongitude(getNewMoonDay(k + i));
        do { last = arc; i++; arc = getSunLongitude(getNewMoonDay(k + i)); } while (arc != last && i < 14);
        return i - 1;
    }

    public static Date fromSolar(int dd, int mm, int yy) {
        int dayNumber = jdFromDate(dd, mm, yy);
        int k = INT((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = getNewMoonDay(k + 1);
        if (monthStart > dayNumber) monthStart = getNewMoonDay(k);
        int a11 = getLunarMonth11(yy), b11 = a11, lunarYear;
        if (a11 >= monthStart) { lunarYear = yy; a11 = getLunarMonth11(yy - 1); }
        else { lunarYear = yy + 1; b11 = getLunarMonth11(yy + 1); }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = INT((monthStart - a11) / 29.0);
        boolean leap = false;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = getLeapMonthOffset(a11);
            if (diff >= leapMonthDiff) { lunarMonth = diff + 10; if (diff == leapMonthDiff) leap = true; }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        Date r = new Date();
        r.day = lunarDay; r.month = lunarMonth; r.year = lunarYear; r.leap = leap; r.jd = dayNumber;
        return r;
    }

    public static String canChiYear(int y) { return CAN[(y + 6) % 10] + " " + CHI[(y + 8) % 12]; }
    public static String canChiDay(int jd) { return CAN[(jd + 9) % 10] + " " + CHI[(jd + 1) % 12]; }
    public static String canChiMonth(int m, int y) { return CAN[(y * 12 + m + 3) % 10] + " " + CHI[(m + 1) % 12]; }

    /* ---------------- Xem ngày theo tuổi ---------------- */
    static final String[] NAP_AM = {"Hải Trung Kim", "Lư Trung Hỏa", "Đại Lâm Mộc", "Lộ Bàng Thổ", "Kiếm Phong Kim", "Sơn Đầu Hỏa",
            "Giản Hạ Thủy", "Thành Đầu Thổ", "Bạch Lạp Kim", "Dương Liễu Mộc", "Tuyền Trung Thủy", "Ốc Thượng Thổ",
            "Tích Lịch Hỏa", "Tùng Bách Mộc", "Trường Lưu Thủy", "Sa Trung Kim", "Sơn Hạ Hỏa", "Bình Địa Mộc",
            "Bích Thượng Thổ", "Kim Bạch Kim", "Phú Đăng Hỏa", "Thiên Hà Thủy", "Đại Trạch Thổ", "Thoa Xuyến Kim",
            "Tang Đố Mộc", "Đại Khê Thủy", "Sa Trung Thổ", "Thiên Thượng Hỏa", "Thạch Lựu Mộc", "Đại Hải Thủy"};
    static final String[] THAN = {"Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước", "Kim Quỹ", "Kim Đường",
            "Bạch Hổ", "Ngọc Đường", "Thiên Lao", "Huyền Vũ", "Tư Mệnh", "Câu Trần"};
    static final boolean[] THAN_TOT = {true, true, false, false, true, true, false, true, false, false, true, false};
    static final String[] TRUC = {"Kiến", "Trừ", "Mãn", "Bình", "Định", "Chấp", "Phá", "Nguy", "Thành", "Thâu", "Khai", "Bế"};
    static final int[] TRUC_DIEM = {0, 1, 1, 0, 1, 1, -1, -1, 1, 0, 1, -1};
    public static final String[] MUC = {"", "Xấu", "Kém", "Bình thường", "Khá", "Tốt"};
    static final String[] GIO_HD = {"110100101100", "001101001011", "110011010010", "101100110100", "001011001101", "010010110011"};

    private static int thanStart(int mchi) {
        switch (mchi) {
            case 2: case 8: return 0;
            case 3: case 9: return 2;
            case 4: case 10: return 4;
            case 5: case 11: return 6;
            case 6: case 0: return 8;
            default: return 10; // Mùi, Sửu
        }
    }

    static int tietMonthChi(int jd) {
        double deg = sunLongitudeRad(jd + 0.5) * 180 / Math.PI;
        return (2 + INT((((deg - 315) % 360) + 360) % 360 / 30)) % 12;
    }

    /** Chi của tuổi (theo năm âm lịch của ngày sinh dương lịch). */
    public static int chiTuoi(int dd, int mm, int yy) { return (fromSolar(dd, mm, yy).year + 8) % 12; }
    public static int namAmTuoi(int dd, int mm, int yy) { return fromSolar(dd, mm, yy).year; }
    public static String menh(int lunarYear) { return NAP_AM[((((lunarYear - 4) % 60) + 60) % 60) / 2]; }

    public static final class DanhGia {
        public int diem; public String muc; public boolean hoangDao, xung; public String than, truc;
    }

    /** chiTuoi < 0: không xét tuổi. */
    public static DanhGia danhGia(int dd, int mm, int yy, int chiTuoi) {
        Date L = fromSolar(dd, mm, yy);
        int jd = L.jd, dchi = (jd + 1) % 12, mchi = (L.month + 1) % 12;
        int ti = (dchi - thanStart(mchi) + 12) % 12, tr = (dchi - tietMonthChi(jd) + 12) % 12;
        DanhGia r = new DanhGia();
        r.than = THAN[ti]; r.hoangDao = THAN_TOT[ti]; r.truc = TRUC[tr];
        int s = 3 + (r.hoangDao ? 1 : -1) + TRUC_DIEM[tr];
        if (chiTuoi >= 0) {
            int diff = (dchi - chiTuoi + 12) % 12;
            if (diff == 6) { s -= 2; r.xung = true; }
            else if ((dchi + chiTuoi) % 12 == 1) s += 1;
            else if (dchi % 4 == chiTuoi % 4 && dchi != chiTuoi) s += 1;
        }
        if (r.xung) s = Math.min(s, 2);
        r.diem = Math.max(1, Math.min(5, s));
        r.muc = MUC[r.diem];
        return r;
    }

    /** Giờ hoàng đạo trong ngày, bỏ giờ xung tuổi (chiTuoi < 0: không lọc). */
    public static String gioTot(int jd, int chiTuoi, int max) {
        String s = GIO_HD[((jd + 1) % 12) % 6];
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int i = 0; i < 12 && n < max; i++) {
            if (s.charAt(i) != '1') continue;
            if (chiTuoi >= 0 && (i - chiTuoi + 12) % 12 == 6) continue;
            if (b.length() > 0) b.append(", ");
            b.append(CHI[i]); n++;
        }
        return b.toString();
    }

    public static int isoWeek(int dd, int mm, int yy) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        c.setMinimalDaysInFirstWeek(4);
        c.set(yy, mm - 1, dd);
        return c.get(java.util.Calendar.WEEK_OF_YEAR);
    }

    /* ---------------- Ngày lễ & tiết khí ---------------- */
    static final String[] TIETKHI = {"Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng",
            "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ", "Thu phân", "Hàn lộ", "Sương giáng",
            "Lập đông", "Tiểu tuyết", "Đại tuyết", "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập"};
    public static String tietKhi(int jd) { return TIETKHI[INT(sunLongitudeRad(jd + 1) / Math.PI * 12)]; }

    static final java.util.Map<String, String> LE_DUONG = new java.util.HashMap<>();
    static final java.util.Map<String, String> LE_AM = new java.util.HashMap<>();
    static {
        String[] dg = {"1/1", "Tết Dương lịch", "3/2", "Thành lập Đảng", "14/2", "Lễ Tình nhân", "27/2", "Thầy thuốc VN",
                "8/3", "Quốc tế Phụ nữ", "26/3", "Thành lập Đoàn", "30/4", "Giải phóng miền Nam", "1/5", "Quốc tế Lao động",
                "19/5", "Sinh nhật Bác Hồ", "1/6", "Quốc tế Thiếu nhi", "28/6", "Gia đình VN", "27/7", "Thương binh Liệt sĩ",
                "19/8", "Cách mạng tháng Tám", "2/9", "Quốc khánh", "13/10", "Doanh nhân VN", "20/10", "Phụ nữ VN",
                "20/11", "Nhà giáo VN", "22/12", "Thành lập QĐND", "24/12", "Giáng sinh"};
        String[] am = {"1/1", "Tết Nguyên đán", "2/1", "Tết Nguyên đán", "3/1", "Tết Nguyên đán", "15/1", "Tết Nguyên tiêu",
                "3/3", "Tết Hàn thực", "10/3", "Giỗ Tổ Hùng Vương", "15/4", "Lễ Phật đản", "5/5", "Tết Đoan ngọ",
                "7/7", "Thất tịch", "15/7", "Lễ Vu Lan", "15/8", "Tết Trung thu", "9/9", "Tết Trùng cửu",
                "15/10", "Tết Hạ nguyên", "23/12", "Ông Táo chầu trời"};
        for (int i = 0; i < dg.length; i += 2) LE_DUONG.put(dg[i], dg[i + 1]);
        for (int i = 0; i < am.length; i += 2) LE_AM.put(am[i], am[i + 1]);
    }

    /** Ngày lễ / mùng 1 / rằm của ngày (null nếu không có). */
    public static String suKien(int dd, int mm, Date L) {
        String s = LE_DUONG.get(dd + "/" + mm);
        if (s != null) return s;
        if (!L.leap) { s = LE_AM.get(L.day + "/" + L.month); if (s != null) return s; }
        if (L.day == 1) return "Mùng 1 tháng " + THANG[L.month];
        if (L.day == 15) return "Rằm tháng " + THANG[L.month];
        return null;
    }
}
