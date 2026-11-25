(function () {
  var $ = function (id) { return document.getElementById(id); };
  var THU = ["Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"];
  var THANG_AM = ["", "Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Mười một", "Chạp"];
  var nz = function (v, d) { return v == null ? d : v; };
  var pad = function (n) { return (n < 10 ? "0" : "") + n; };

  var LE_DUONG = {
    "1/1": "Tết Dương lịch", "9/1": "Ngày HS-SV VN", "3/2": "Thành lập Đảng CSVN", "14/2": "Lễ Tình nhân",
    "27/2": "Thầy thuốc VN", "8/3": "Quốc tế Phụ nữ", "26/3": "Thành lập Đoàn TNCS", "30/4": "Giải phóng miền Nam",
    "1/5": "Quốc tế Lao động", "19/5": "Sinh nhật Bác Hồ", "1/6": "Quốc tế Thiếu nhi", "28/6": "Gia đình VN",
    "27/7": "Thương binh Liệt sĩ", "19/8": "Cách mạng tháng Tám", "2/9": "Quốc khánh", "13/10": "Doanh nhân VN",
    "20/10": "Phụ nữ VN", "31/10": "Halloween", "20/11": "Nhà giáo VN", "22/12": "Thành lập QĐND", "24/12": "Giáng sinh"
  };
  var LE_AM = {
    "1/1": "Tết Nguyên đán", "2/1": "Tết Nguyên đán", "3/1": "Tết Nguyên đán", "15/1": "Tết Nguyên tiêu",
    "3/3": "Tết Hàn thực", "10/3": "Giỗ Tổ Hùng Vương", "15/4": "Lễ Phật đản", "5/5": "Tết Đoan ngọ",
    "7/7": "Thất tịch", "15/7": "Lễ Vu Lan", "15/8": "Tết Trung thu", "9/9": "Tết Trùng cửu",
    "10/10": "Tết Thường tân", "23/12": "Ông Táo chầu trời"
  };

  function info(d, m, y) {
    var L = Lunar.solar2Lunar(d, m, y);
    var hol = [];
    if (LE_DUONG[d + "/" + m]) hol.push(LE_DUONG[d + "/" + m]);
    if (!L.leap && LE_AM[L.day + "/" + L.month]) hol.push(LE_AM[L.day + "/" + L.month] + " (ÂL)");
    if (L.day == 1 && !hol.length) hol.push("Mùng 1 âm lịch");
    if (L.day == 15 && !hol.length) hol.push("Rằm (15 âm lịch)");
    return { L: L, hol: hol };
  }

  function rows(list) {
    return list.map(function (r) { return '<div class="row"><span>' + r[0] + '</span><span class="' + (r[2] || "") + '">' + r[1] + "</span></div>"; }).join("");
  }

  function dayDetailHtml(d, m, y, title) {
    var x = info(d, m, y), L = x.L, wd = new Date(y, m - 1, d).getDay();
    var list = [
      ["Dương lịch", THU[wd] + ", " + pad(d) + "/" + pad(m) + "/" + y],
      ["Âm lịch", L.day + " tháng " + THANG_AM[L.month] + (L.leap ? " (nhuận)" : "") + " năm " + Lunar.canChiYear(L.year)],
      ["Ngày", Lunar.canChiDay(L.jd)],
      ["Tháng", Lunar.canChiMonth(L.month, L.year) + (L.leap ? " (nhuận)" : "")],
      ["Năm", Lunar.canChiYear(L.year)],
      ["Tiết khí", Lunar.tietKhi(L.jd)],
      ["Giờ hoàng đạo", Lunar.gioHoangDao(L.jd).join("<br>"), "hd"]
    ];
    if (x.hol.length) list.splice(1, 0, ["Sự kiện", x.hol.join("<br>"), "hol"]);
    return "<h3>" + title + "</h3>" + rows(list);
  }

  /* ---------- ĐỒNG HỒ & HÔM NAY ---------- */
  var lastDayKey = "";
  function tick() {
    var n = new Date(), d = n.getDate(), m = n.getMonth() + 1, y = n.getFullYear();
    $("clock").textContent = pad(n.getHours()) + ":" + pad(n.getMinutes());
    $("sec").textContent = pad(n.getSeconds());
    var key = d + "/" + m + "/" + y + "/" + n.getHours();
    if (key !== lastDayKey) {
      lastDayKey = key;
      var L = Lunar.solar2Lunar(d, m, y);
      $("solarText").textContent = THU[n.getDay()] + ", ngày " + d + " tháng " + m + " năm " + y;
      $("lunarDay").textContent = L.day;
      $("lunarMonthText").textContent = "Tháng " + THANG_AM[L.month] + (L.leap ? " (nhuận)" : "") + " âm lịch";
      $("lunarYearText").textContent = "Năm " + Lunar.canChiYear(L.year) + " · Giờ " + Lunar.canChiHour(L.jd, n.getHours());
      $("dayInfo").innerHTML = dayDetailHtml(d, m, y, "Thông tin ngày");
      if (viewY === null) { viewY = y; viewM = m; selD = [d, m, y]; renderCal(); }
    }
  }

  /* ---------- LỊCH THÁNG ---------- */
  var viewY = null, viewM = null, selD = null;
  function renderCal() {
    var first = new Date(viewY, viewM - 1, 1), start = (first.getDay() + 6) % 7;
    var t = new Date(), html = "";
    var L1 = Lunar.solar2Lunar(1, viewM, viewY);
    $("calTitle").innerHTML = "Tháng " + viewM + " / " + viewY + "<small>Năm " + Lunar.canChiYear(L1.year) + "</small>";
    for (var i = 0; i < 42; i++) {
      var dt = new Date(viewY, viewM - 1, 1 - start + i);
      var d = dt.getDate(), m = dt.getMonth() + 1, y = dt.getFullYear();
      if (i >= 35 && m !== viewM) break;
      var x = info(d, m, y), L = x.L, col = i % 7;
      var cls = "cell c" + (col + 1);
      if (m !== viewM) cls += " other";
      if (d === t.getDate() && m === t.getMonth() + 1 && y === t.getFullYear()) cls += " today";
      if (selD && selD[0] === d && selD[1] === m && selD[2] === y) cls += " sel";
      if (x.hol.length && !(x.hol.length === 1 && /^(Mùng 1|Rằm)/.test(x.hol[0]))) cls += " hasHol";
      var lt = L.day === 1 ? L.day + "/" + L.month + (L.leap ? "N" : "") : L.day;
      html += '<div class="' + cls + '" data-d="' + d + "/" + m + "/" + y + '"><div class="s">' + d +
        '</div><div class="l' + (L.day === 1 || L.day === 15 ? " m1" : "") + '">' + lt + "</div></div>";
    }
    $("calGrid").innerHTML = html;
    if (selD) $("selInfo").innerHTML = dayDetailHtml(selD[0], selD[1], selD[2], "Ngày đã chọn");
  }
  $("calGrid").addEventListener("click", function (e) {
    var c = e.target.closest(".cell"); if (!c) return;
    var p = c.getAttribute("data-d").split("/").map(Number);
    selD = p;
    if (p[1] !== viewM) { viewM = p[1]; viewY = p[2]; }
    renderCal();
  });
  $("prevM").onclick = function () { viewM--; if (viewM < 1) { viewM = 12; viewY--; } renderCal(); };
  $("nextM").onclick = function () { viewM++; if (viewM > 12) { viewM = 1; viewY++; } renderCal(); };
  $("btnToday").onclick = function () { var t = new Date(); viewY = t.getFullYear(); viewM = t.getMonth() + 1; selD = [t.getDate(), viewM, viewY]; renderCal(); };
  // vuốt trái/phải để đổi tháng
  var sx = null;
  $("calGrid").addEventListener("touchstart", function (e) { sx = e.touches[0].clientX; }, { passive: true });
  $("calGrid").addEventListener("touchend", function (e) {
    if (sx === null) return; var dx = e.changedTouches[0].clientX - sx; sx = null;
    if (Math.abs(dx) > 60) (dx < 0 ? $("nextM") : $("prevM")).onclick();
  });

  /* ---------- TAB ---------- */
  document.querySelectorAll("nav.bottom button").forEach(function (b) {
    b.onclick = function () {
      document.querySelectorAll("nav.bottom button").forEach(function (x) { x.classList.remove("active"); });
      document.querySelectorAll(".tab").forEach(function (x) { x.classList.remove("active"); });
      b.classList.add("active"); $(b.getAttribute("data-tab")).classList.add("active");
      window.scrollTo(0, 0);
    };
  });

  /* ---------- THỜI TIẾT ---------- */
  var NB = window.AndroidBridge || null;
  var ic = function (code, isDay, cls) { return '<span class="ico ' + (cls || "") + '">' + WX.icon(code, isDay) + "</span>"; };

  function renderWeather(w, placeName, ts) {
    var c = w.current, dly = w.daily, nz2 = WX.num;
    $("place").textContent = placeName || "Vị trí hiện tại";
    $("wIcon").innerHTML = WX.icon(c.weather_code, c.is_day);
    $("wTemp").textContent = Math.round(c.temperature_2m) + "°C";
    $("wDesc").textContent = WX.desc(c.weather_code) + " · Cảm giác " + Math.round(c.apparent_temperature) + "°";
    var hint = WX.rainHint(w);
    $("wHint").innerHTML = hint ? '<span class="ico sm">' + (WX_ICONS.umbrella || "") + "</span>" + hint : "";
    var sr = (dly.sunrise[0] || "").slice(11), ss = (dly.sunset[0] || "").slice(11);
    var pmax = dly.precipitation_probability_max ? dly.precipitation_probability_max[0] : null;
    var meta = [
      ["Độ ẩm", c.relative_humidity_2m + "%"],
      ["Gió", Math.round(c.wind_speed_10m) + " km/h"],
      ["Cao / Thấp", Math.round(dly.temperature_2m_max[0]) + "° / " + Math.round(dly.temperature_2m_min[0]) + "°"],
      ["Khả năng mưa", pmax != null ? pmax + "%" : nz2(dly.precipitation_sum[0], 0).toFixed(1) + " mm"],
      ["Mặt trời mọc", sr], ["Mặt trời lặn", ss]
    ];
    if (dly.uv_index_max && dly.uv_index_max[0] != null) meta.push(["Chỉ số UV", dly.uv_index_max[0].toFixed(1)]);
    $("wMeta").innerHTML = meta.map(function (m) { return "<div><span>" + m[0] + "</span><b>" + m[1] + "</b></div>"; }).join("");
    var t = new Date(ts), mdl = (WX.MODELS.filter(function (x) { return x.id === currentModel(); })[0] || {}).name || "";
    $("wUpdated").textContent = "Cập nhật " + pad(t.getHours()) + ":" + pad(t.getMinutes()) + " " + pad(t.getDate()) + "/" + pad(t.getMonth() + 1) + " · Open-Meteo · " + mdl;

    // 24 giờ
    var h = w.hourly, idx = WX.hourIndex(w), hh = "";
    for (var i = idx; i < Math.min(idx + 24, h.time.length); i++) {
      hh += '<div class="h"><div>' + (i === idx ? "Bây giờ" : WX.hourOf(h.time[i]) + "h") + "</div>" + ic(h.weather_code[i], h.is_day[i]) +
        "<div><b>" + Math.round(h.temperature_2m[i]) + '°</b></div><div class="p">' + WX.popText(h, i) + "</div></div>";
    }
    $("hourly").innerHTML = hh;
    $("hint2").innerHTML = hint ? '<span class="ico sm">' + (WX_ICONS.umbrella || "") + "</span>" + hint : "";

    // 7 ngày
    var dd = "", allMax = Math.max.apply(null, dly.temperature_2m_max), allMin = Math.min.apply(null, dly.temperature_2m_min);
    for (var j = 0; j < dly.time.length; j++) {
      var p = dly.time[j].split("-").map(Number), dt = new Date(p[0], p[1] - 1, p[2]);
      var L = Lunar.solar2Lunar(p[2], p[1], p[0]);
      var mn = dly.temperature_2m_min[j], mx = dly.temperature_2m_max[j], span = Math.max(1, allMax - allMin);
      var pr = dly.precipitation_probability_max ? dly.precipitation_probability_max[j] : null;
      var prT = pr != null ? pr + "%" : (nz2(dly.precipitation_sum[j], 0) >= 0.1 ? nz2(dly.precipitation_sum[j], 0).toFixed(1) + "mm" : "");
      dd += '<div class="d"><div>' + (j === 0 ? "Hôm nay" : THU[dt.getDay()]) + '<div class="sub">' + pad(p[2]) + "/" + pad(p[1]) +
        " · ÂL " + L.day + "/" + L.month + "</div></div>" + ic(dly.weather_code[j], 1) +
        "<div>" + WX.desc(dly.weather_code[j]) + '<div class="p">' + prT + "</div></div>" +
        '<div class="t"><span class="lo">' + Math.round(mn) + '°</span><span class="bar"><i style="left:' + ((mn - allMin) / span * 100) +
        "%;right:" + ((allMax - mx) / span * 100) + '%"></i></span><b>' + Math.round(mx) + "°</b></div></div>";
    }
    $("daily").innerHTML = dd;
  }

  function currentModel() {
    try { return NB ? NB.getModel() : (localStorage.getItem("model") || "ecmwf_ifs"); } catch (e) { return "ecmwf_ifs"; }
  }

  // Android trả kết quả về đây: payload = {"w":..,"place":..,"ts":..} (chuỗi JSON)
  window.onNativeWeather = function (payload, err) {
    $("btnRefresh").classList.remove("spin");
    if (payload) {
      try { var o = JSON.parse(payload); renderWeather(o.w, o.place, o.ts); } catch (e) {}
    }
    if (err) toast(err);
  };

  function toast(msg) {
    var t = $("toast"); t.textContent = msg; t.classList.add("show");
    clearTimeout(toast._t); toast._t = setTimeout(function () { t.classList.remove("show"); }, 3500);
  }

  // Chế độ trình duyệt (không có Android): tự gọi API
  function webRefresh() {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(function (p) {
      var lat = p.coords.latitude.toFixed(4), lon = p.coords.longitude.toFixed(4);
      Promise.all([
        fetch(WX.url(lat, lon, currentModel())).then(function (r) { return r.json(); }),
        fetch("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat + "&longitude=" + lon + "&localityLanguage=vi")
          .then(function (r) { return r.json(); }).catch(function () { return null; })
      ]).then(function (res) {
        var g = res[1], name = g ? [g.locality || g.city, g.principalSubdivision].filter(Boolean).join(", ") : "";
        var o = { w: res[0], place: name, ts: Date.now() };
        try { localStorage.setItem("wcache", JSON.stringify(o)); } catch (e) {}
        window.onNativeWeather(JSON.stringify(o), null);
      }).catch(function () { window.onNativeWeather("", "Không tải được thời tiết"); });
    }, function () { window.onNativeWeather("", "Chưa có quyền vị trí"); }, { timeout: 15000, maximumAge: 600000 });
  }

  function refresh() {
    $("btnRefresh").classList.add("spin");
    if (NB) NB.requestWeather(); else webRefresh();
  }

  // Hiện dữ liệu đã lưu ngay khi mở app
  try {
    var cached = NB ? NB.getCachedWeather() : localStorage.getItem("wcache");
    if (cached) window.onNativeWeather(cached, null);
  } catch (e) {}
  $("btnRefresh").onclick = refresh;

  /* ---------- CÀI ĐẶT ---------- */
  var WTYPES = [
    { id: "clock", name: "Đồng hồ & Lịch", size: "4×2" },
    { id: "compact", name: "Thanh gọn", size: "4×1" },
    { id: "weather", name: "Thời tiết", size: "4×2" },
    { id: "calendar", name: "Lịch ngày", size: "2×2" }
  ];
  function renderSettings() {
    var cm = currentModel();
    $("models").innerHTML = WX.MODELS.map(function (m) {
      return '<label class="opt"><input type="radio" name="model" value="' + m.id + '"' + (m.id === cm ? " checked" : "") +
        "><span><b>" + m.name + "</b><small>" + m.note + "</small></span></label>";
    }).join("");
    $("models").querySelectorAll("input").forEach(function (r) {
      r.onchange = function () {
        if (NB) NB.setModel(r.value); else { localStorage.setItem("model", r.value); webRefresh(); }
        toast("Đã đổi nguồn dự báo: " + r.parentNode.querySelector("b").textContent);
      };
    });
    $("addWidgets").innerHTML = WTYPES.map(function (t) {
      return '<button class="wbtn" data-t="' + t.id + '"><b>' + t.name + "</b><small>" + t.size + "</small></button>";
    }).join("");
    $("addWidgets").querySelectorAll("button").forEach(function (b) {
      b.onclick = function () {
        var ok = NB && NB.pinWidget(b.getAttribute("data-t"));
        if (!ok) toast("Hãy giữ lâu màn hình chính → Widget → Lịch Việt");
      };
    });
    var list = [];
    try { list = NB ? JSON.parse(NB.listWidgets()) : []; } catch (e) {}
    $("myWidgets").innerHTML = list.length ? list.map(function (x) {
      var t = WTYPES.filter(function (y) { return y.id === x.type; })[0] || WTYPES[0];
      return '<div class="row"><span>' + t.name + ' <small>#' + x.id + '</small></span><button class="small" data-id="' + x.id + '">Tùy chỉnh</button></div>';
    }).join("") : '<div class="muted">Chưa có widget nào trên màn hình chính.</div>';
    $("myWidgets").querySelectorAll("button").forEach(function (b) {
      b.onclick = function () { NB.configureWidget(parseInt(b.getAttribute("data-id"), 10)); };
    });
  }
  document.querySelector('[data-tab="tab-set"]').addEventListener("click", renderSettings);
  document.addEventListener("visibilitychange", function () {
    if (!document.hidden && $("tab-set").classList.contains("active")) renderSettings();
  });

  tick(); setInterval(tick, 1000);
  refresh();
  setInterval(refresh, 30 * 60 * 1000); // tự cập nhật 30 phút/lần khi app đang mở
})();
