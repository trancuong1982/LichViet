(function () {
  var $ = function (id) { return document.getElementById(id); };
  var NB = window.AndroidBridge || null;
  var THU = ["Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"];
  var THU_N = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
  var THANG_AM = ["", "Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Một", "Chạp"];
  var pad = function (n) { return (n < 10 ? "0" : "") + n; };
  var esc = function (s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); };

  /* ---------- Sáng / tối theo máy ---------- */
  window.setDark = function (d) { document.body.classList.toggle("dark", !!d); document.documentElement.className = d ? "dark-pre" : ""; };
  window.setDark(window.__dark);
  if (!NB && window.matchMedia) matchMedia("(prefers-color-scheme: dark)").addEventListener("change", function (e) { window.setDark(e.matches); });

  /* ---------- Ngày lễ ---------- */
  var LE_DUONG = {
    "1/1": "Tết Dương lịch", "9/1": "Ngày HS-SV Việt Nam", "3/2": "Thành lập Đảng CSVN", "14/2": "Lễ Tình nhân",
    "27/2": "Thầy thuốc Việt Nam", "8/3": "Quốc tế Phụ nữ", "26/3": "Thành lập Đoàn TNCS", "30/4": "Giải phóng miền Nam",
    "1/5": "Quốc tế Lao động", "19/5": "Sinh nhật Bác Hồ", "1/6": "Quốc tế Thiếu nhi", "28/6": "Gia đình Việt Nam",
    "27/7": "Thương binh Liệt sĩ", "19/8": "Cách mạng tháng Tám", "2/9": "Quốc khánh", "13/10": "Doanh nhân Việt Nam",
    "20/10": "Phụ nữ Việt Nam", "20/11": "Nhà giáo Việt Nam", "22/12": "Thành lập QĐND Việt Nam", "24/12": "Giáng sinh"
  };
  var LE_AM = {
    "1/1": "Tết Nguyên đán", "2/1": "Tết Nguyên đán", "3/1": "Tết Nguyên đán", "15/1": "Tết Nguyên tiêu",
    "3/3": "Tết Hàn thực", "10/3": "Giỗ Tổ Hùng Vương", "15/4": "Lễ Phật đản", "5/5": "Tết Đoan ngọ",
    "7/7": "Thất tịch", "15/7": "Lễ Vu Lan", "15/8": "Tết Trung thu", "9/9": "Tết Trùng cửu",
    "15/10": "Tết Hạ nguyên", "23/12": "Ông Táo chầu trời"
  };
  function holidays(d, m, y, L) {
    L = L || Lunar.solar2Lunar(d, m, y);
    var h = [];
    if (LE_DUONG[d + "/" + m]) h.push(LE_DUONG[d + "/" + m]);
    if (!L.leap && LE_AM[L.day + "/" + L.month]) h.push(LE_AM[L.day + "/" + L.month] + " (ÂL)");
    return h;
  }
  function amText(L) { return L.day + "/" + L.month + (L.leap ? " nhuận" : ""); }
  function thangAm(L) { return "tháng " + THANG_AM[L.month] + (L.leap ? " (nhuận)" : ""); }

  /* ---------- Ngày sinh & tuổi ---------- */
  function getBirth() {
    try { return (NB ? NB.getBirth() : localStorage.getItem("birth")) || ""; } catch (e) { return ""; }
  }
  function setBirth(v) {
    try { if (NB) NB.setBirth(v); else localStorage.setItem("birth", v); } catch (e) {}
  }
  function tuoiInfo() {
    var b = getBirth(); if (!/^\d{4}-\d{2}-\d{2}$/.test(b)) return null;
    var p = b.split("-").map(Number), t = Lunar.tuoi(p[2], p[1], p[0]);
    t.birth = pad(p[2]) + "/" + pad(p[1]) + "/" + p[0];
    t.birthLunar = amText(t.lunar) + " năm " + t.canChi;
    return t;
  }
  function dots(n) { var s = '<span class="dots">'; for (var i = 1; i <= 5; i++) s += '<i class="' + (i <= n ? "f" : "") + '"></i>'; return s + "</span>"; }

  /* ---------- Đồng hồ & Hôm nay ---------- */
  var lastKey = "";
  function tick() {
    var n = new Date(), d = n.getDate(), m = n.getMonth() + 1, y = n.getFullYear();
    $("clock").textContent = pad(n.getHours()) + ":" + pad(n.getMinutes());
    var key = d + "/" + m + "/" + y + "/" + n.getHours();
    if (key === lastKey) return;
    lastKey = key;
    var L = Lunar.solar2Lunar(d, m, y);
    $("hWeek").textContent = THU[n.getDay()] + " · Tuần " + Lunar.isoWeek(d, m, y);
    $("hDate").textContent = d + " tháng " + m + ", " + y;
    $("gioCC").textContent = "Giờ " + Lunar.canChiHour(L.jd, n.getHours());
    $("lunarBig").innerHTML = L.day + "<small>/" + L.month + (L.leap ? "N" : "") + "</small>";
    $("lunarYear").textContent = "Năm " + Lunar.canChiYear(L.year);
    var hol = holidays(d, m, y, L);
    var rows = [
      ["Ngày", Lunar.canChiDay(L.jd) + " · tháng " + Lunar.canChiMonth(L.month, L.year)],
      ["Tiết khí", Lunar.tietKhi(L.jd)],
      ["Giờ hoàng đạo", Lunar.gioHoangDao(L.jd).map(function (g) { return g.split(" ")[0]; }).join(", ")]
    ];
    if (hol.length) rows.unshift(["Sự kiện", hol.join(", "), "hol"]);
    $("todayInfo").innerHTML = rows.map(function (r) { return '<div class="r"><span>' + r[0] + '</span><b class="' + (r[2] || "") + '">' + esc(r[1]) + "</b></div>"; }).join("");
    renderAgeCard();
    if (viewY === null) { viewY = y; viewM = m; selD = [d, m, y]; }
    renderCal();
  }

  function renderAgeCard() {
    var t = tuoiInfo(), n = new Date();
    if (!t) {
      $("ageCard").innerHTML = '<div class="age"><div class="av">?</div><div class="m"><b>Xem ngày tốt xấu theo tuổi</b><div class="s">Nhập ngày sinh một lần trong Cài đặt</div></div><button class="small">Nhập</button></div>';
      $("ageCard").onclick = function () { openTab("tab-set"); setTimeout(function () { $("birth").focus(); }, 200); };
      return;
    }
    var r = Lunar.danhGia(n.getDate(), n.getMonth() + 1, n.getFullYear(), t.chi);
    $("ageCard").innerHTML = '<div class="age"><div class="av">' + t.chiName + '</div><div class="m"><div class="s">Hôm nay với tuổi ' + t.canChi +
      '</div><b>' + r.muc + "</b> " + dots(r.diem) + '<div class="s">Giờ tốt: ' + r.gioTot.slice(0, 4).map(function (g) { return g.split(" ")[0]; }).join(", ") +
      '</div></div><span class="chip c' + r.diem + '">Chi tiết ›</span></div>';
    $("ageCard").onclick = openAge;
  }

  /* ---------- Lịch tháng ---------- */
  var viewY = null, viewM = null, selD = null;
  function renderCal() {
    var first = new Date(viewY, viewM - 1, 1), start = (first.getDay() + 6) % 7, t = new Date(), html = "";
    var last = new Date(viewY, viewM, 0).getDate();
    var La = Lunar.solar2Lunar(1, viewM, viewY), Lb = Lunar.solar2Lunar(last, viewM, viewY);
    $("calTitle").textContent = "Tháng " + viewM + ", " + viewY;
    var w1 = Lunar.isoWeek(1, viewM, viewY), w2 = Lunar.isoWeek(last, viewM, viewY);
    $("calSub").textContent = "Tuần " + w1 + " – " + w2 + " · ÂL " + (La.month === Lb.month ? thangAm(La) : "tháng " + THANG_AM[La.month] + " – " + THANG_AM[Lb.month]) + " " + Lunar.canChiYear(Lb.year);
    var tw = Lunar.isoWeek(t.getDate(), t.getMonth() + 1, t.getFullYear());
    for (var i = 0; i < 42; i++) {
      var dt = new Date(viewY, viewM - 1, 1 - start + i), d = dt.getDate(), m = dt.getMonth() + 1, y = dt.getFullYear();
      if (i % 7 === 0) {
        if (i >= 35 && m !== viewM) break;
        var wk = Lunar.isoWeek(d, m, y), cur = wk === tw && Math.abs(dt - t) < 7 * 864e5;
        html += '<div class="wk' + (cur ? " cur" : "") + '">' + wk + "</div>";
      }
      var L = Lunar.solar2Lunar(d, m, y), cls = "cell";
      if (i % 7 === 6) cls += " sun";
      if (m !== viewM) cls += " other";
      if (d === t.getDate() && m === t.getMonth() + 1 && y === t.getFullYear()) cls += " today";
      else if (selD && selD[0] === d && selD[1] === m && selD[2] === y) cls += " sel";
      if (holidays(d, m, y, L).length) cls += " dot";
      var lt = L.day === 1 ? L.day + "/" + L.month + (L.leap ? "N" : "") : L.day;
      html += '<div class="' + cls + '" data-d="' + d + "/" + m + "/" + y + '"><span class="s">' + d + '</span><span class="l' + (L.day === 1 || L.day === 15 ? " m" : "") + '">' + lt + "</span></div>";
    }
    $("calGrid").innerHTML = html;
    renderSel();
    renderEvents();
  }
  function renderSel() {
    if (!selD) return;
    var d = selD[0], m = selD[1], y = selD[2], dt = new Date(y, m - 1, d), L = Lunar.solar2Lunar(d, m, y), hol = holidays(d, m, y, L);
    $("selDay").innerHTML = '<div class="bx"><b>' + d + "</b><span>" + THU_N[dt.getDay()] + " · Tuần " + Lunar.isoWeek(d, m, y) + '</span></div><div class="tx"><b>Âm lịch ' +
      amText(L) + " " + Lunar.canChiYear(L.year) + "</b><br>Ngày " + Lunar.canChiDay(L.jd) + " · Tiết " + Lunar.tietKhi(L.jd) +
      "<br>Hoàng đạo: " + Lunar.gioHoangDao(L.jd).map(function (g) { return g.split(" ")[0]; }).join(", ") +
      (hol.length ? '<br><b class="hol">' + esc(hol.join(", ")) + "</b>" : "") + "</div>";
    var t = tuoiInfo();
    if (!t) { $("selAge").style.display = "none"; return; }
    var r = Lunar.danhGia(d, m, y, t.chi);
    $("selAge").style.display = "";
    $("selAge").innerHTML = '<div class="age"><div class="av">' + t.chiName + '</div><div class="m"><div class="s">Với tuổi ' + t.canChi + " · " + r.than +
      " · trực " + r.truc + "</div><b>" + r.muc + "</b> " + dots(r.diem) + '<div class="s">Giờ tốt: ' + r.gioTot.map(function (g) { return g.split(" ")[0]; }).join(", ") + "</div></div></div>";
  }
  function deviceEvents() {
    try { return NB && NB.getEvents ? JSON.parse(NB.getEvents(60)) : null; } catch (e) { return null; }
  }
  window.onCalendarChanged = function () { renderCal(); if ($("tab-set").classList.contains("active")) renderSettings(); };
  function renderEvents() {
    var dev = deviceEvents();
    if (dev) {
      var t0 = new Date(), html = dev.slice(0, 5).map(function (e) {
        var dt = new Date(e.y, e.m - 1, e.d), today = e.y === t0.getFullYear() && e.m === t0.getMonth() + 1 && e.d === t0.getDate();
        var col = e.kind === 1 ? "var(--red)" : e.kind === 2 ? "var(--orange)" : "var(--acc)";
        return '<div class="ev"><span><i style="display:inline-block;width:7px;height:7px;border-radius:4px;background:' + col + ';margin-right:8px"></i>' +
          (e.kind === 1 ? '<span class="hol" style="color:var(--red)!important">' + esc(e.title) + "</span>" : esc(e.title)) + "</span><b>" +
          (today ? "Hôm nay" : THU_N[dt.getDay()] + " · " + pad(e.d) + "/" + pad(e.m)) + (e.time ? " " + e.time : "") + "</b></div>";
      }).join("");
      $("events").innerHTML = html || '<div class="muted">Không có sự kiện.</div>';
      return;
    }
    var out = [], t = new Date(); t.setHours(0, 0, 0, 0);
    for (var k = 1; k <= 120 && out.length < 4; k++) {
      var dt = new Date(t.getTime() + k * 864e5), d = dt.getDate(), m = dt.getMonth() + 1, y = dt.getFullYear();
      var L = Lunar.solar2Lunar(d, m, y), h = holidays(d, m, y, L);
      if (L.day === 1 && !h.length) h = ["Mùng 1 " + thangAm(L)];
      if (L.day === 15 && !h.length) h = ["Rằm " + thangAm(L)];
      h.forEach(function (x) { if (out.length < 4) out.push('<div class="ev"><span>' + esc(x) + "</span><b>" + THU_N[dt.getDay()] + " · " + pad(d) + "/" + pad(m) + "</b></div>"); });
    }
    $("events").innerHTML = out.join("");
  }
  $("calGrid").addEventListener("click", function (e) {
    var c = e.target.closest(".cell"); if (!c) return;
    var p = c.getAttribute("data-d").split("/").map(Number);
    selD = p; if (p[1] !== viewM) { viewM = p[1]; viewY = p[2]; }
    renderCal();
  });
  function shiftM(k) { viewM += k; if (viewM < 1) { viewM = 12; viewY--; } if (viewM > 12) { viewM = 1; viewY++; } renderCal(); }
  $("prevM").onclick = function () { shiftM(-1); };
  $("nextM").onclick = function () { shiftM(1); };
  $("btnToday").onclick = function () { var t = new Date(); viewY = t.getFullYear(); viewM = t.getMonth() + 1; selD = [t.getDate(), viewM, viewY]; renderCal(); };
  var sx = null;
  $("calGrid").addEventListener("touchstart", function (e) { sx = e.touches[0].clientX; }, { passive: true });
  $("calGrid").addEventListener("touchend", function (e) {
    if (sx === null) return; var dx = e.changedTouches[0].clientX - sx; sx = null;
    if (Math.abs(dx) > 60) shiftM(dx < 0 ? 1 : -1);
  });

  /* ---------- Màn xem ngày theo tuổi ---------- */
  function openAge() {
    var t = tuoiInfo(); if (!t) return;
    var n = new Date(), r = Lunar.danhGia(n.getDate(), n.getMonth() + 1, n.getFullYear(), t.chi);
    $("ageProfile").innerHTML = '<div class="av">' + t.chiName + '</div><div class="m"><div class="s">Ngày sinh ' + t.birth + " · ÂL " + t.birthLunar +
      "</div><b>Tuổi " + t.canChi + '</b><div class="s">Mệnh ' + t.menh + '</div></div><button class="small" id="ageEdit">Sửa</button>';
    $("ageEdit").onclick = function () { closeAge(); openTab("tab-set"); };
    var L = r.L;
    $("ageHero").innerHTML = '<div class="s">Hôm nay · ' + THU_N[n.getDay()] + " " + pad(n.getDate()) + "/" + pad(n.getMonth() + 1) + " · ÂL " + amText(L) +
      " · ngày " + r.canChiNgay + '</div><div class="m"><b>' + r.muc + "</b>" + dots(r.diem) + '</div><div class="g">Giờ tốt cho bạn: ' + r.gioTot.join(", ") + "</div>";
    $("ageWhy").innerHTML = r.lyDo.map(function (x) {
      return '<div class="why"><span class="k ' + (x.ok > 0 ? "p" : x.ok < 0 ? "n" : "z") + '">' + (x.ok > 0 ? "✓" : x.ok < 0 ? "✕" : "–") + "</span><div><b>" + esc(x.t) + "</b><small>" + esc(x.n) + "</small></div></div>";
    }).join("");
    $("ageUpTitle").textContent = "14 NGÀY TỚI CHO TUỔI " + t.canChi.toUpperCase();
    var up = "";
    for (var k = 1; k <= 14; k++) {
      var dt = new Date(n.getFullYear(), n.getMonth(), n.getDate() + k), d = dt.getDate(), m = dt.getMonth() + 1, y = dt.getFullYear();
      var e = Lunar.danhGia(d, m, y, t.chi), why = e.xung ? "Xung tuổi " + t.chiName : (e.hoangDao ? "Hoàng đạo " : "Hắc đạo ") + e.than + " · trực " + e.truc;
      up += '<div class="up"><div class="d">' + THU_N[dt.getDay()] + " · " + pad(d) + "/" + pad(m) + "<small>" + amText(e.L) + " · " + e.canChiNgay +
        '</small></div><div class="w">' + why + '</div><span class="chip c' + e.diem + '">' + e.muc + "</span></div>";
    }
    $("ageUp").innerHTML = up;
    $("scrAge").classList.add("show"); $("scrAge").scrollTop = 0;
    history.pushState({ age: 1 }, "");
  }
  function closeAge() { $("scrAge").classList.remove("show"); }
  $("ageBack").onclick = function () { history.back(); };
  window.addEventListener("popstate", closeAge);

  /* ---------- Tab ---------- */
  function openTab(id) {
    document.querySelectorAll("nav.bottom button").forEach(function (x) { x.classList.toggle("active", x.getAttribute("data-tab") === id); });
    document.querySelectorAll(".tab").forEach(function (x) { x.classList.toggle("active", x.id === id); });
    if (id === "tab-set") renderSettings();
    window.scrollTo(0, 0);
  }
  document.querySelectorAll("nav.bottom button").forEach(function (b) { b.onclick = function () { openTab(b.getAttribute("data-tab")); }; });

  /* ---------- Thời tiết ---------- */
  function icon(code, isDay, cls) { return '<div class="ic ' + (cls || "") + '">' + WX.icon(code, isDay) + "</div>"; }
  function currentModel() { try { return NB ? NB.getModel() : (localStorage.getItem("model") || "ecmwf_ifs"); } catch (e) { return "ecmwf_ifs"; } }

  function renderWeather(w, placeName, ts) {
    var c = w.current, dly = w.daily, h = w.hourly, num = WX.num;
    var place = placeName || "Vị trí hiện tại", t = new Date(ts), hint = WX.rainHint(w);
    $("place").textContent = place; $("place2").textContent = place;
    $("upd").textContent = "Cập nhật " + pad(t.getHours()) + ":" + pad(t.getMinutes());
    $("wIcon").innerHTML = WX.icon(c.weather_code, c.is_day);
    $("wTemp").textContent = Math.round(c.temperature_2m) + "°";
    $("wDesc").textContent = WX.desc(c.weather_code) + " · cảm giác " + Math.round(c.apparent_temperature) + "°";
    $("wHL").innerHTML = "Cao " + Math.round(dly.temperature_2m_max[0]) + "°<br>Thấp " + Math.round(dly.temperature_2m_min[0]) + "°<br>Ẩm " + c.relative_humidity_2m + "%";
    var hintHtml = hint ? '<div class="ic">' + (WX_ICONS.umbrella || "") + "</div><span>" + esc(hint) + "</span>" : "";
    $("hint1").innerHTML = hintHtml; $("hint2").innerHTML = hintHtml;

    var i0 = WX.hourIndex(w), five = "", firstRain = -1;
    for (var i = i0 + 1; i < Math.min(i0 + 6, h.time.length); i++) {
      var pr = h.precipitation_probability ? h.precipitation_probability[i] : null;
      var rainy = h.weather_code[i] >= 51 || (pr != null && pr >= 50);
      if (rainy && firstRain < 0) firstRain = i;
      five += '<div class="' + (i === firstRain ? "on" : "") + '">' + WX.hourOf(h.time[i]) + "h" + icon(h.weather_code[i], h.is_day[i]) +
        "<b>" + Math.round(h.temperature_2m[i]) + '°</b><div class="p">' + WX.popText(h, i) + "</div></div>";
    }
    $("hours5").innerHTML = five;

    $("hIcon").innerHTML = WX.icon(c.weather_code, c.is_day);
    $("hTemp").textContent = Math.round(c.temperature_2m) + "°";
    $("hDesc").textContent = WX.desc(c.weather_code) + " · cảm giác " + Math.round(c.apparent_temperature) + "°";
    var uv = dly.uv_index_max && dly.uv_index_max[0] != null ? Math.round(dly.uv_index_max[0]) : null;
    var uvT = uv == null ? "–" : uv + (uv >= 8 ? " · rất cao" : uv >= 6 ? " · cao" : uv >= 3 ? " · TB" : " · thấp");
    $("hMet").innerHTML = "<div>Ẩm<b>" + c.relative_humidity_2m + "%</b></div><div>Gió<b>" + Math.round(c.wind_speed_10m) + " km/h</b></div><div>UV<b>" + uvT +
      "</b></div><div>Lặn<b>" + (dly.sunset[0] || "").slice(11) + "</b></div>";

    var hh = "";
    for (var j = i0; j < Math.min(i0 + 24, h.time.length); j++) {
      hh += "<div>" + (j === i0 ? "Giờ này" : WX.hourOf(h.time[j]) + "h") + icon(h.weather_code[j], h.is_day[j]) + "<b>" + Math.round(h.temperature_2m[j]) +
        '°</b><div class="p">' + WX.popText(h, j) + "</div></div>";
    }
    $("h24").innerHTML = hh;

    var lo = Math.min.apply(null, dly.temperature_2m_min), hi = Math.max.apply(null, dly.temperature_2m_max), span = Math.max(1, hi - lo), dd = "";
    for (var k = 0; k < dly.time.length; k++) {
      var p = dly.time[k].split("-").map(Number), dt = new Date(p[0], p[1] - 1, p[2]), L = Lunar.solar2Lunar(p[2], p[1], p[0]);
      var mn = dly.temperature_2m_min[k], mx = dly.temperature_2m_max[k];
      var pp = dly.precipitation_probability_max ? dly.precipitation_probability_max[k] : null;
      var ppT = pp != null ? pp + "%" : (num(dly.precipitation_sum[k], 0) >= 0.1 ? num(dly.precipitation_sum[k], 0).toFixed(1) + "mm" : "");
      dd += '<div class="d7"><div class="n"><b>' + (k === 0 ? "Hôm nay" : THU[dt.getDay()]) + "</b><div>" + pad(p[2]) + "/" + pad(p[1]) + " · ÂL " + L.day + "/" + L.month +
        "</div></div>" + icon(dly.weather_code[k], 1) + '<span class="pp">' + ppT + '</span><div class="bar"><span class="lo">' + Math.round(mn) +
        '°</span><span class="tr"><i style="left:' + ((mn - lo) / span * 100).toFixed(0) + "%;right:" + ((hi - mx) / span * 100).toFixed(0) + '%"></i></span><b>' + Math.round(mx) + "°</b></div></div>";
    }
    $("d7").innerHTML = dd;
    var mdl = (WX.MODELS.filter(function (x) { return x.id === currentModel(); })[0] || {}).name || "";
    $("src").textContent = "Nguồn: Open-Meteo · " + mdl;
  }

  function setSpin(on) { document.querySelectorAll(".btnRefresh").forEach(function (b) { b.classList.toggle("spin", on); }); }
  window.onNativeWeather = function (payload, err) {
    setSpin(false);
    if (payload) { try { var o = JSON.parse(payload); renderWeather(o.w, o.place, o.ts); } catch (e) {} }
    if (err) toast(err);
  };
  function toast(msg) {
    var t = $("toast"); t.textContent = msg; t.classList.add("show");
    clearTimeout(toast._t); toast._t = setTimeout(function () { t.classList.remove("show"); }, 3500);
  }
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
  function refresh() { setSpin(true); if (NB) NB.requestWeather(); else webRefresh(); }
  try { var cached = NB ? NB.getCachedWeather() : localStorage.getItem("wcache"); if (cached) window.onNativeWeather(cached, null); } catch (e) {}
  document.querySelectorAll(".btnRefresh").forEach(function (b) { b.onclick = refresh; });

  /* ---------- Cài đặt ---------- */
  var WTYPES = [
    { id: "clock", name: "Đồng hồ & Lịch", size: "4×2" }, { id: "date", name: "Ngày · Tuần · Âm lịch", size: "4×2" },
    { id: "weather", name: "Thời tiết theo giờ", size: "4×3" }, { id: "weather2", name: "Thời tiết", size: "2×2" },
    { id: "calendar3", name: "Lịch ngày đầy đủ", size: "3×2" }, { id: "calendar", name: "Lịch ngày", size: "2×2" },
    { id: "compact", name: "Thanh gọn", size: "4×1" }, { id: "summary", name: "Tổng hợp", size: "5×3" }
  ];
  function renderBirthInfo() {
    var t = tuoiInfo();
    $("birthInfo").innerHTML = t ? "Âm lịch: <b>" + t.birthLunar + "</b><br>Tuổi <b>" + t.canChi + "</b> · mệnh " + t.menh : "Chưa nhập ngày sinh.";
  }
  $("birth").addEventListener("change", function () {
    setBirth(this.value); renderBirthInfo(); renderAgeCard(); renderSel();
    if (this.value) toast("Đã lưu ngày sinh");
  });
  function renderSettings() {
    $("birth").value = getBirth(); renderBirthInfo();
    var hasCal = false; try { hasCal = NB ? NB.hasCalendar() : false; } catch (e) {}
    $("calPerm").innerHTML = hasCal ? '<div class="muted">Đã kết nối – sự kiện và ngày lễ từ lịch của máy hiện trên app và widget.</div>'
      : '<div class="muted" style="margin-bottom:10px">Cho phép đọc lịch của máy để hiện lịch hẹn và ngày lễ chính thức (bật lịch “Ngày lễ ở Việt Nam” trong Google Calendar).</div><button class="small" id="btnCal">Cho phép đọc lịch</button>';
    if (!hasCal && $("btnCal")) $("btnCal").onclick = function () { if (NB) NB.requestCalendar(); };
    var cm = currentModel();
    $("models").innerHTML = WX.MODELS.map(function (m) {
      return '<label class="opt"><input type="radio" name="model" value="' + m.id + '"' + (m.id === cm ? " checked" : "") + "><span><b>" + m.name + "</b><small>" + m.note + "</small></span></label>";
    }).join("");
    $("models").querySelectorAll("input").forEach(function (r) {
      r.onchange = function () {
        if (NB) NB.setModel(r.value); else { localStorage.setItem("model", r.value); webRefresh(); }
        setSpin(true); toast("Đã đổi nguồn dự báo: " + r.parentNode.querySelector("b").textContent);
      };
    });
    var iv = 30; try { iv = NB ? NB.getInterval() : 30; } catch (e) {}
    $("interval").innerHTML = [15, 30, 60].map(function (v) { return '<button class="chp' + (v === iv ? " on" : "") + '" data-v="' + v + '">' + v + " phút</button>"; }).join("");
    $("interval").querySelectorAll("button").forEach(function (b) {
      b.onclick = function () { if (NB) NB.setInterval(parseInt(b.getAttribute("data-v"), 10)); renderSettings(); };
    });
    $("addWidgets").innerHTML = WTYPES.map(function (t) { return '<button class="wbtn" data-t="' + t.id + '"><b>' + t.name + "</b><small>" + t.size + "</small></button>"; }).join("");
    $("addWidgets").querySelectorAll("button").forEach(function (b) {
      b.onclick = function () { var ok = NB && NB.pinWidget(b.getAttribute("data-t")); if (!ok) toast("Giữ lâu màn hình chính → Tiện ích → Lịch Việt"); };
    });
    var list = []; try { list = NB ? JSON.parse(NB.listWidgets()) : []; } catch (e) {}
    $("myWidgets").innerHTML = list.length ? list.map(function (x) {
      var t = WTYPES.filter(function (y) { return y.id === x.type; })[0] || WTYPES[0];
      return '<div class="ev" style="align-items:center"><span>' + t.name + ' <small style="color:var(--tx3)">' + t.size + '</small></span><button class="small" data-id="' + x.id + '">Tùy chỉnh</button></div>';
    }).join("") : '<div class="muted">Chưa có widget nào trên màn hình chính.</div>';
    $("myWidgets").querySelectorAll("button").forEach(function (b) { b.onclick = function () { NB.configureWidget(parseInt(b.getAttribute("data-id"), 10)); }; });
  }
  document.addEventListener("visibilitychange", function () {
    if (document.hidden) return;
    lastKey = ""; tick();
    if ($("tab-set").classList.contains("active")) renderSettings();
  });

  tick(); setInterval(tick, 1000);
  refresh();
  setInterval(refresh, 30 * 60 * 1000);
})();
