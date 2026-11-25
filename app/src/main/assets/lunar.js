/* Âm lịch Việt Nam - thuật toán thiên văn (theo Hồ Ngọc Đức), múi giờ +7 */
(function (root) {
  var PI = Math.PI, TZ = 7.0;
  var INT = Math.floor;

  function jdFromDate(dd, mm, yy) {
    var a = INT((14 - mm) / 12), y = yy + 4800 - a, m = mm + 12 * a - 3;
    var jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - INT(y / 100) + INT(y / 400) - 32045;
    if (jd < 2299161) jd = dd + INT((153 * m + 2) / 5) + 365 * y + INT(y / 4) - 32083;
    return jd;
  }

  function jdToDate(jd) {
    var a, b, c;
    if (jd > 2299160) { a = jd + 32044; b = INT((4 * a + 3) / 146097); c = a - INT((b * 146097) / 4); }
    else { b = 0; c = jd + 32082; }
    var d = INT((4 * c + 3) / 1461), e = c - INT((1461 * d) / 4), m = INT((5 * e + 2) / 153);
    return [e - INT((153 * m + 2) / 5) + 1, m + 3 - 12 * INT(m / 10), b * 100 + d - 4800 + INT(m / 10)];
  }

  function newMoon(k) {
    var T = k / 1236.85, T2 = T * T, T3 = T2 * T, dr = PI / 180;
    var Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
    Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
    var M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
    var Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
    var F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
    var C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2 * dr * M);
    C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(dr * 2 * Mpr);
    C1 = C1 - 0.0004 * Math.sin(dr * 3 * Mpr);
    C1 = C1 + 0.0104 * Math.sin(dr * 2 * F) - 0.0051 * Math.sin(dr * (M + Mpr));
    C1 = C1 - 0.0074 * Math.sin(dr * (M - Mpr)) + 0.0004 * Math.sin(dr * (2 * F + M));
    C1 = C1 - 0.0004 * Math.sin(dr * (2 * F - M)) - 0.0006 * Math.sin(dr * (2 * F + Mpr));
    C1 = C1 + 0.0010 * Math.sin(dr * (2 * F - Mpr)) + 0.0005 * Math.sin(dr * (2 * Mpr + M));
    var deltat = T < -11 ? 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.000000081 * T * T3
                         : -0.000278 + 0.000265 * T + 0.000262 * T2;
    return Jd1 + C1 - deltat;
  }

  /* Kinh độ mặt trời (radian, 0..2PI) tại ngày Julius jdn (thời điểm 0h giờ địa phương) */
  function sunLongitudeRad(jdn) {
    var T = (jdn - 2451545.5 - TZ / 24) / 36525, T2 = T * T, dr = PI / 180;
    var M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
    var L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
    var DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
    DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2 * M) + 0.000290 * Math.sin(dr * 3 * M);
    var L = (L0 + DL) * dr;
    L = L - PI * 2 * INT(L / (PI * 2));
    return L;
  }
  function getSunLongitude(jdn) { return INT(sunLongitudeRad(jdn) / PI * 6); }
  function getNewMoonDay(k) { return INT(newMoon(k) + 0.5 + TZ / 24); }

  function getLunarMonth11(yy) {
    var off = jdFromDate(31, 12, yy) - 2415021;
    var k = INT(off / 29.530588853);
    var nm = getNewMoonDay(k);
    if (getSunLongitude(nm) >= 9) nm = getNewMoonDay(k - 1);
    return nm;
  }

  function getLeapMonthOffset(a11) {
    var k = INT((a11 - 2415021.076998695) / 29.530588853 + 0.5), last, i = 1;
    var arc = getSunLongitude(getNewMoonDay(k + i));
    do { last = arc; i++; arc = getSunLongitude(getNewMoonDay(k + i)); } while (arc != last && i < 14);
    return i - 1;
  }

  function solar2Lunar(dd, mm, yy) {
    var dayNumber = jdFromDate(dd, mm, yy);
    var k = INT((dayNumber - 2415021.076998695) / 29.530588853);
    var monthStart = getNewMoonDay(k + 1);
    if (monthStart > dayNumber) monthStart = getNewMoonDay(k);
    var a11 = getLunarMonth11(yy), b11 = a11, lunarYear;
    if (a11 >= monthStart) { lunarYear = yy; a11 = getLunarMonth11(yy - 1); }
    else { lunarYear = yy + 1; b11 = getLunarMonth11(yy + 1); }
    var lunarDay = dayNumber - monthStart + 1;
    var diff = INT((monthStart - a11) / 29);
    var lunarLeap = 0, lunarMonth = diff + 11;
    if (b11 - a11 > 365) {
      var leapMonthDiff = getLeapMonthOffset(a11);
      if (diff >= leapMonthDiff) { lunarMonth = diff + 10; if (diff == leapMonthDiff) lunarLeap = 1; }
    }
    if (lunarMonth > 12) lunarMonth -= 12;
    if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
    return { day: lunarDay, month: lunarMonth, year: lunarYear, leap: lunarLeap, jd: dayNumber };
  }

  var CAN = ["Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"];
  var CHI = ["Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"];
  var TIETKHI = ["Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng",
    "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ",
    "Thu phân", "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết", "Đại tuyết",
    "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập"];
  var GIO_HD = ["110100101100", "001101001011", "110011010010", "101100110100", "001011001101", "010010110011"];

  function canChiYear(y) { return CAN[(y + 6) % 10] + " " + CHI[(y + 8) % 12]; }
  function canChiMonth(m, y) { return CAN[(y * 12 + m + 3) % 10] + " " + CHI[(m + 1) % 12]; }
  function canChiDay(jd) { return CAN[(jd + 9) % 10] + " " + CHI[(jd + 1) % 12]; }
  function tietKhi(jd) { return TIETKHI[INT(sunLongitudeRad(jd + 1) / PI * 12)]; }
  function gioHoangDao(jd) {
    var chiDay = (jd + 1) % 12, s = GIO_HD[chiDay % 6], out = [];
    for (var i = 0; i < 12; i++) if (s.charAt(i) == "1") {
      out.push(CHI[i] + " (" + ((i * 2 + 23) % 24) + "-" + ((i * 2 + 1) % 24) + "h)");
    }
    return out;
  }
  function canChiHour(jd, hour) { // giờ Tý bắt đầu 23h
    var chiIdx = INT(((hour + 1) % 24) / 2);
    var d = hour >= 23 ? jd + 1 : jd; // 23h thuộc giờ Tý của ngày hôm sau
    var canIdx = (2 * ((d + 9) % 10) + chiIdx) % 10;
    return CAN[canIdx] + " " + CHI[chiIdx];
  }

  root.Lunar = {
    jdFromDate: jdFromDate, jdToDate: jdToDate, solar2Lunar: solar2Lunar,
    canChiYear: canChiYear, canChiMonth: canChiMonth, canChiDay: canChiDay,
    canChiHour: canChiHour, tietKhi: tietKhi, gioHoangDao: gioHoangDao
  };
  if (typeof module !== "undefined") module.exports = root.Lunar;
})(typeof window !== "undefined" ? window : globalThis);
