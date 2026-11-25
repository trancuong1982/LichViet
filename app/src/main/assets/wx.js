/* Hàm dùng chung về thời tiết – khớp logic với Weather.java */
(function () {
  var DESC = { 0: "Trời quang", 1: "Ít mây", 2: "Có mây", 3: "Nhiều mây", 45: "Sương mù", 48: "Sương mù",
    51: "Mưa phùn", 53: "Mưa phùn", 55: "Mưa phùn", 56: "Mưa phùn", 57: "Mưa phùn",
    61: "Mưa nhỏ", 63: "Mưa vừa", 65: "Mưa to", 66: "Mưa lạnh", 67: "Mưa lạnh",
    71: "Mưa tuyết", 73: "Mưa tuyết", 75: "Mưa tuyết", 77: "Mưa tuyết", 85: "Mưa tuyết", 86: "Mưa tuyết",
    80: "Mưa rào nhẹ", 81: "Mưa rào", 82: "Mưa rào rất to", 95: "Dông", 96: "Dông, mưa đá", 99: "Dông, mưa đá" };

  function iconKey(code, isDay) {
    var day = isDay !== 0;
    if (code === 0) return day ? "sun" : "moon";
    if (code === 1 || code === 2) return day ? "partly_day" : "partly_night";
    if (code === 3) return "cloud";
    if (code === 45 || code === 48) return "fog";
    if (code >= 51 && code <= 57) return "drizzle";
    if (code >= 95) return "thunder";
    if (code >= 61) return "rain";
    return "cloud";
  }
  function num(v, d) { return v == null || isNaN(v) ? d : v; }

  function hourIndex(w) {
    var now = w.current.time.slice(0, 13), t = w.hourly.time;
    for (var i = 0; i < t.length; i++) if (t[i].slice(0, 13) >= now) return i;
    return 0;
  }
  function rainy(h, i) {
    var p = h.precipitation_probability ? h.precipitation_probability[i] : null;
    var mm = h.precipitation ? num(h.precipitation[i], 0) : 0;
    return num(h.weather_code[i], 0) >= 51 || (p != null && p >= 50) || mm >= 0.5;
  }
  function hourOf(s) { return parseInt(s.slice(11, 13), 10); }
  function rainHint(w) {
    try {
      var h = w.hourly, t = h.time, i0 = hourIndex(w), end = Math.min(i0 + 12, t.length), i;
      if (rainy(h, i0)) {
        for (i = i0 + 1; i < end; i++) if (!rainy(h, i)) return "Đang mưa · dự kiến tạnh khoảng " + hourOf(t[i]) + "h";
        return "Mưa kéo dài trong 12 giờ tới";
      }
      for (i = i0 + 1; i < end; i++) if (rainy(h, i)) {
        var p = h.precipitation_probability ? h.precipitation_probability[i] : null;
        return (h.weather_code[i] >= 95 ? "Có dông" : "Có thể mưa") + " khoảng " + hourOf(t[i]) + "h" + (p != null ? " (" + p + "%)" : "");
      }
      return "Không mưa trong 12 giờ tới";
    } catch (e) { return ""; }
  }
  function popText(h, i) {
    var p = h.precipitation_probability ? h.precipitation_probability[i] : null;
    if (p != null) return p + "%";
    var mm = h.precipitation ? num(h.precipitation[i], 0) : 0;
    return mm >= 0.1 ? mm.toFixed(1) + "mm" : "";
  }

  window.WX = {
    MODELS: [
      { id: "ecmwf_ifs", name: "ECMWF IFS 9 km", note: "Trung tâm Dự báo Châu Âu – mô hình toàn cầu được đánh giá chính xác nhất (khuyên dùng)" },
      { id: "best_match", name: "Tự động", note: "Open-Meteo tự chọn mô hình phù hợp nhất cho vị trí" },
      { id: "gfs_seamless", name: "GFS (NOAA – Mỹ)", note: "Mô hình toàn cầu của Mỹ, cập nhật mỗi 6 giờ" },
      { id: "icon_seamless", name: "ICON (DWD – Đức)", note: "Mô hình toàn cầu của Cơ quan Khí tượng Đức" }
    ],
    desc: function (c) { return DESC[c] || "--"; },
    iconKey: iconKey,
    icon: function (code, isDay) { return (window.WX_ICONS || {})[iconKey(code, isDay)] || ""; },
    hourIndex: hourIndex, rainHint: rainHint, popText: popText, num: num, hourOf: hourOf,
    url: function (lat, lon, model) {
      var u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day,precipitation" +
        "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max" +
        "&timezone=auto&forecast_days=7";
      if (model && model !== "best_match") u += "&models=" + model;
      return u;
    }
  };
})();
