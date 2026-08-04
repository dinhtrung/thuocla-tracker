    // ========== DATA LAYER ==========
    const DB_KEY = 'smoking_data';
    const CFG_KEY = 'smoking_config';
    const TRIGGERS = ['🍜 Sau ăn','☕ Cà phê','😤 Stress','🍻 Nhậu','😞 Buồn','🌀 Thói quen','🚬 Thèm','🤷 Khác'];

    function getToday() {
      const d = new Date();
      return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    }

    function getWeekBounds() {
      const now = new Date();
      const dayOfWeek = now.getDay();
      const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
      const monday = new Date(now);
      monday.setDate(now.getDate() + mondayOffset);
      const sunday = new Date(monday);
      sunday.setDate(monday.getDate() + 6);
      const fmt = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
      return { start: fmt(monday), end: fmt(sunday) };
    }

    function getMonthBounds() {
      const now = new Date();
      const start = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-01`;
      const endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
      const end = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(endDate).padStart(2,'0')}`;
      return { start, end };
    }

    function loadData() {
      try { return JSON.parse(localStorage.getItem(DB_KEY)) || {}; }
      catch { return {}; }
    }

    function saveData(data) {
      localStorage.setItem(DB_KEY, JSON.stringify(data));
    }

    function loadConfig() {
      try {
        const cfg = JSON.parse(localStorage.getItem(CFG_KEY));
        return cfg || { goal: 10, intervalGoal: 60, pricePerPack: 25000, cigsPerPack: 20 };
      } catch { return { goal: 10, intervalGoal: 60, pricePerPack: 25000, cigsPerPack: 20 }; }
    }

    function saveConfig(cfg) {
      localStorage.setItem(CFG_KEY, JSON.stringify(cfg));
    }

    function getTodayData() {
      const data = loadData();
      return (data[getToday()] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
    }

    function setTodayData(records) {
      const data = loadData();
      const today = getToday();
      // Sort by time for consistency
      records.sort((a, b) => new Date(a.time) - new Date(b.time));
      data[today] = records;
      saveData(data);
    }

    function addRecord(isoTime) {
      const records = getTodayData();
      if (isoTime) {
        records.push({ time: isoTime });
      } else {
        // Round to nearest 5 min (0,5,10...) to match the time picker
        const now = new Date();
        now.setMinutes(Math.round(now.getMinutes() / 5) * 5, 0, 0);
        records.push({ time: now.toISOString() });
      }
      setTodayData(records);
      return records;
    }

    function removeLastRecord() {
      const records = getTodayData();
      if (records.length === 0) return records;
      records.pop();
      setTodayData(records);
      return records;
    }

    function getDayCount(dateStr) {
      const data = loadData();
      return (data[dateStr] || []).length;
    }

    function getLast7Days() {
      return getLastNDays(7);
    }

    function getLastNDays(n) {
      const days = [];
      for (let i = n - 1; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const key = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
        days.push({ key, dayName: getDayName(d), count: getDayCount(key) });
      }
      return days;
    }

    function getDayName(date) {
      return ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'][date.getDay()];
    }

    function getLowestDay() {
      const last31 = getLastNDays(31);
      const today = getToday();
      let min = { key: '', count: Infinity, dayName: '' };
      let hasData = false;
      for (const d of last31) {
        if (d.key !== today && d.count > 0 && d.count < min.count) {
          min = d;
          hasData = true;
        }
      }
      if (!hasData) return null;
      return min;
    }

    function getYesterdayKey() {
      const d = new Date();
      d.setDate(d.getDate() - 1);
      return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    }

    // ========== MONEY ==========
    function calcCost(count) {
      const cfg = loadConfig();
      if (!cfg.pricePerPack || !cfg.cigsPerPack) return 0;
      return (count / cfg.cigsPerPack) * cfg.pricePerPack;
    }

    function getWeekCount() {
      const bounds = getWeekBounds();
      const data = loadData();
      let total = 0;
      for (const [key, records] of Object.entries(data)) {
        if (key >= bounds.start && key <= bounds.end) total += records.length;
      }
      return total;
    }

    function getMonthCount() {
      const bounds = getMonthBounds();
      const data = loadData();
      let total = 0;
      for (const [key, records] of Object.entries(data)) {
        if (key >= bounds.start && key <= bounds.end) total += records.length;
      }
      return total;
    }

    function formatMoney(n) {
      if (n < 1000) return n + '₫';
      return Math.round(n).toLocaleString('vi-VN') + '₫';
    }

    // ========== TIMER ==========
    let timerInterval = null;

    function startTimer() {
      if (timerInterval) clearInterval(timerInterval);
      timerInterval = setInterval(updateTimer, 1000);
      updateTimer();
    }

    function updateTimer() {
      const records = getTodayData();
      const cfg = loadConfig();
      const intervalGoal = cfg.intervalGoal || 0;
      const el = document.getElementById('timerDisplay');
      const ring = document.getElementById('timerRing');
      const target = document.getElementById('timerTarget');
      const estimate = document.getElementById('timerEstimate');

      if (records.length === 0) {
        el.textContent = '--:--';
        el.className = 'timer-value';
        ring.textContent = '--';
        ring.className = 'timer-ring';
        target.textContent = intervalGoal > 0 ? `Mục tiêu: ≥${intervalGoal}ph` : '';
        estimate.style.display = 'none';
        return;
      }

      const last = new Date(records[records.length - 1].time);
      const now = new Date();
      const diffMs = now - last;
      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const secPart = diffSec % 60;

      // Format as H:MM:SS or MM:SS
      let display;
      if (diffMin >= 60) {
        const h = Math.floor(diffMin / 60);
        const m = diffMin % 60;
        display = `${h}:${String(m).padStart(2,'0')}:${String(secPart).padStart(2,'0')}`;
      } else {
        display = `${String(diffMin).padStart(2,'0')}:${String(secPart).padStart(2,'0')}`;
      }

      el.textContent = display;

      // Color coding and estimate based on interval goal
      if (intervalGoal > 0) {
        const pct = Math.min(100, (diffMin / intervalGoal) * 100);

        if (diffMin >= intervalGoal) {
          el.className = 'timer-value good';
          ring.className = 'timer-ring good';
          ring.textContent = '✅';
          target.textContent = `Mục tiêu: ≥${intervalGoal}ph ✅ Đạt`;
          estimate.style.display = 'none';
        } else {
          const remaining = intervalGoal - diffMin;
          const nextTime = new Date(last.getTime() + intervalGoal * 60000);
          const estStr = `${String(nextTime.getHours()).padStart(2,'0')}:${String(nextTime.getMinutes()).padStart(2,'0')}`;
          target.textContent = `Mục tiêu: ≥${intervalGoal}ph • ${Math.round(pct)}%`;
          estimate.style.display = 'block';
          estimate.textContent = `🔜 Dự kiến: ${estStr} (còn ${remaining}ph)`;

          if (diffMin >= intervalGoal * 0.7) {
            el.className = 'timer-value warn';
            ring.className = 'timer-ring warn';
            ring.textContent = diffMin + "'";
          } else {
            el.className = 'timer-value bad';
            ring.className = 'timer-ring bad';
            ring.textContent = diffMin + "'";
          }
        }
      } else {
        target.textContent = '';
        estimate.style.display = 'none';
        el.className = 'timer-value';
        ring.className = 'timer-ring';
        ring.textContent = diffMin + "'";
      }
    }

    // ========== UI ==========
    function formatTime(isoStr) {
      const d = new Date(isoStr);
      return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    }

    function updateDisplay() {
      const records = getTodayData();
      const count = records.length;
      const cfg = loadConfig();

      // Today number
      document.getElementById('todayCount').textContent = count;

      // Goal
      document.getElementById('goalDisplay').textContent = cfg.goal;
      const pct = Math.min(100, (count / cfg.goal) * 100);
      document.getElementById('progressFill').style.width = pct + '%';
      document.getElementById('progressText').textContent = `${count} / ${cfg.goal}`;

      // Lowest day in last 7
      const lowest = getLowestDay();
      if (lowest && lowest.count > 0) {
        document.getElementById('lowestDayCount').textContent = lowest.count;
        const dayNamesShort = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
        const lowDate = new Date(lowest.key + 'T00:00:00');
        const lowLabel = lowest.key === getToday() ? 'Hôm nay' : (dayNamesShort[lowDate.getDay()] || '');
        document.getElementById('lowestDayLabel').textContent = `Ít nhất • ${lowLabel}`;
      } else {
        document.getElementById('lowestDayCount').textContent = '-';
        document.getElementById('lowestDayLabel').textContent = 'Ít nhất';
      }

      // Yesterday
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const yKey = `${yesterday.getFullYear()}-${String(yesterday.getMonth()+1).padStart(2,'0')}-${String(yesterday.getDate()).padStart(2,'0')}`;
      document.getElementById('yesterdayCount').textContent = getDayCount(yKey);

      // 7-day average
      const last7 = getLast7Days();
      const total7 = last7.reduce((s, d) => s + d.count, 0);
      document.getElementById('avg7').textContent = (total7 / 7).toFixed(1);

      // This month
      document.getElementById('thisMonth').textContent = getMonthCount();

      // Timeline
      renderComparisonTimeline();

      // Date
      const now = new Date();
      const dayNames = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
      document.getElementById('dateDisplay').textContent = `${dayNames[now.getDay()]}, ${now.getDate()}/${now.getMonth()+1}/${now.getFullYear()}`;

      // Chart
      renderChart();

      // Money
      updateMoneyStats();

      // Timer
      updateTimer();
    }

    function renderComparisonTimeline() {
      const todayRecords = getTodayData();
      const todayCount = todayRecords.length;
      const cfg = loadConfig();
      const intervalGoal = cfg.intervalGoal || 0;
      const rows = document.getElementById('comparisonRows');
      const todayCountEl = document.getElementById('comparisonTodayCount');
      const lowestCountEl = document.getElementById('comparisonLowestCount');

      // Get lowest day
      const lowest = getLowestDay();
      let lowestRecords = [];
      let lowestCount = 0;
      let lowestLabel = '';
      if (lowest && lowest.count > 0) {
        const data = loadData();
        lowestRecords = (data[lowest.key] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
        lowestCount = lowestRecords.length;
        const dayNamesShort = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
        const lowDate = new Date(lowest.key + 'T00:00:00');
        lowestLabel = `${dayNamesShort[lowDate.getDay()]} ${lowest.key.slice(5)}`;
      }

      // Average gap helper
      function calcAvgGap(records) {
        if (records.length < 2) return null;
        let total = 0;
        for (let i = 1; i < records.length; i++) {
          const prev = new Date(records[i-1].time);
          const curr = new Date(records[i].time);
          total += Math.round((curr - prev) / 60000);
        }
        return Math.round(total / (records.length - 1));
      }

      const todayAvg = calcAvgGap(todayRecords);
      const lowestAvg = calcAvgGap(lowestRecords);

      todayCountEl.textContent = `(${todayCount})`;
      lowestCountEl.textContent = lowestLabel ? `(${lowestCount} • ${lowestLabel})` : '(-)';
      document.getElementById('comparisonTodayAvg').textContent = todayAvg !== null ? `TB ${todayAvg}ph` : '—';
      document.getElementById('comparisonLowestAvg').textContent = lowestAvg !== null ? `TB ${lowestAvg}ph` : '—';

      const maxRows = Math.max(todayCount, lowestCount, 1);

      if (maxRows === 0 || todayCount === 0) {
        rows.innerHTML = '<div class="empty-state">Chưa hút điếu nào hôm nay</div>';
        return;
      }

      if (!lowest || lowestCount === 0) {
        rows.innerHTML = '<div class=\"empty-state\">Chưa có ngày tham chiếu</div>';
        return;
      }

      let html = '';

      function gapInfo(records, i, intervalGoal) {
        let gapStr = '';
        let gapBad = false;
        let gapGood = false;
        if (i > 0) {
          const prev = new Date(records[i-1].time);
          const curr = new Date(records[i].time);
          const diffMin = Math.round((curr - prev) / 60000);
          if (diffMin > 0) {
            gapStr = `${diffMin}ph`;
            if (intervalGoal > 0) {
              if (diffMin < intervalGoal) {
                gapBad = true;
                const earlyBy = intervalGoal - diffMin;
                gapStr += ` ⚠️ -${earlyBy}`;
              } else {
                gapGood = true;
                const extra = diffMin - intervalGoal;
                gapStr += ` ✅ +${extra}`;
              }
            }
          }
        }
        return { str: gapStr, bad: gapBad, good: gapGood };
      }

      for (let i = 0; i < maxRows; i++) {
        const todayR = todayRecords[i];
        const lowestR = lowestRecords[i];

        // Today side
        let todayHtml = '';
        if (todayR) {
          const isHalf = todayR.note === '½';
          const num = isHalf ? '½' : `#${i + 1}`;
          const tgap = gapInfo(todayRecords, i, intervalGoal);
          const gapClass = tgap.bad ? 'bad' : tgap.good ? 'good' : '';
          const tIcon = triggerIcon(todayR.trigger);
          todayHtml = `
            <div class="comp-num">${num}</div>
            <div class="comp-time today">${formatTime(todayR.time)}${tIcon ? `<span class="cig-trigger">${tIcon}</span>` : ''}</div>
            ${tgap.str ? `<div class="comp-gap ${gapClass}">${tgap.str}</div>` : ''}
          `;
        } else {
          todayHtml = '<div class="comp-num" style="color:transparent;">-</div>';
        }

        // Lowest side
        let lowestHtml = '';
        if (lowestR) {
          const isHalf = lowestR.note === '½';
          const num = isHalf ? '½' : `#${i + 1}`;
          const lgap = gapInfo(lowestRecords, i, intervalGoal);
          const gapClass = lgap.bad ? 'bad' : lgap.good ? 'good' : '';
          const lIcon = triggerIcon(lowestR.trigger);
          lowestHtml = `
            ${lgap.str ? `<div class="comp-gap ${gapClass}">${lgap.str}</div>` : ''}
            <div class="comp-time lowest">${lIcon ? `<span class="cig-trigger">${lIcon}</span>` : ''}${formatTime(lowestR.time)}</div>
            <div class="comp-num">${num}</div>
          `;
        } else {
          lowestHtml = '<div class="comp-num" style="color:transparent;">-</div>';
        }

        const clickable = todayR ? 'clickable' : '';
        const onclick = todayR ? `onclick="openTimeEditor(${i})"` : '';

        html += `
          <div class="comparison-row ${clickable}" ${onclick}>
            <div class="comp-today">${todayHtml}</div>
            <div class="comp-divider"></div>
            <div class="comp-lowest">${lowestHtml}</div>
          </div>
        `;
      }

      rows.innerHTML = html;
    }

    let chartPage = 0; // 0 = latest 7 days, 1 = previous week, etc.

    function getPageDays() {
      const days = [];
      const startOffset = chartPage * 7;
      for (let i = 0; i < 7; i++) {
        const d = new Date();
        d.setDate(d.getDate() - startOffset - (6 - i));
        const key = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
        days.push({ key, dayName: getDayName(d), count: getDayCount(key) });
      }
      return days;
    }

    function shiftChart(delta) {
      chartPage = Math.max(0, chartPage + delta);
      // Update the date range label
      const days = getPageDays();
      const startLabel = days[0].key.slice(5);
      const endLabel = days[6].key.slice(5);
      const label = chartPage === 0 ? '7 ngày qua' : `${startLabel} – ${endLabel}`;
      document.getElementById('chartDateRange').textContent = label;
      renderChart();
    }

    function renderChart() {
      const days = getPageDays();
      const maxCount = Math.max(1, ...days.map(d => d.count));
      const svg = document.getElementById('chartSvg');
      const today = getToday();
      const cfg = loadConfig();

      const W = 640, H = 260;
      const MT = 22, MB = 70, ML = 2, MR = 44;
      const chartX = ML;
      const chartY = MT;
      const chartW = W - ML - MR;
      const chartH = H - MT - MB;
      const gap = 6;
      const slotW = chartW / 7;
      const barW = Math.max(8, slotW - gap);

      let els = '';

      // Grid lines
      for (let g = 0; g <= 4; g++) {
        const gy = chartY + (g / 4) * chartH;
        els += `<line x1="${chartX}" y1="${gy}" x2="${chartX + chartW}" y2="${gy}" stroke="rgba(255,255,255,0.04)" stroke-width="1"/>`;
      }

      // Bars
      days.forEach((d, i) => {
        const barH = (d.count / maxCount) * chartH;
        const x = chartX + i * slotW;
        const y = chartY + chartH - barH;
        const isToday = d.key === today;
        const isSel = d.key === selectedDate;
        const color = isToday ? '#ff6b81' : '#e94560';

        if (barH > 0) {
          els += `<rect x="${x}" y="${y}" width="${barW}" height="${barH}" rx="3" ry="3" fill="${color}" class="chart-svg-bar${isSel ? ' selected' : ''}" data-key="${d.key}" onclick="selectDay('${d.key}')" style="cursor:pointer"/>`;
        }

        // Count label — larger
        if (d.count > 0) {
          els += `<text x="${x + barW/2}" y="${y - 10}" text-anchor="middle" fill="#fff" font-size="20" font-weight="800">${d.count}</text>`;
        }

        // Day name label below
        els += `<text x="${x + barW/2}" y="${chartY + chartH + 20}" text-anchor="middle" fill="#ccc" font-size="13" font-weight="600">${d.count > 0 ? d.dayName : d.key.slice(5)}</text>`;

        // Average gap label (only if >= 2 cigarettes)
        if (d.count >= 2) {
          const data = loadData();
          const records = (data[d.key] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
          let totalGap = 0;
          for (let gi = 1; gi < records.length; gi++) {
            totalGap += Math.round((new Date(records[gi].time) - new Date(records[gi-1].time)) / 60000);
          }
          const avgGap = Math.round(totalGap / (records.length - 1));
          els += `<text x="${x + barW/2}" y="${chartY + chartH + 38}" text-anchor="middle" fill="#aaa" font-size="10" font-weight="600">≃${avgGap}ph</text>`;
        }
      });

      // Goal line
      if (cfg.goal > 0 && maxCount > 0) {
        const goalRatio = Math.min(1, cfg.goal / maxCount);
        const goalY = chartY + chartH - goalRatio * chartH;
        els += `<line x1="${chartX}" y1="${goalY}" x2="${chartX + chartW}" y2="${goalY}" stroke="#f1c40f" stroke-width="1.5" stroke-dasharray="6,3" opacity="0.7"/>`;
        els += `<text x="${chartX + chartW + 4}" y="${goalY + 4}" fill="#f1c40f" font-size="11" font-weight="700">🎯${cfg.goal}</text>`;
      }

      svg.innerHTML = els;

      // Update summary stats below chart
      updateChartGapStats(days);
    }

    function updateChartGapStats(days) {
      const data = loadData();
      const cfg = loadConfig();
      const today = getToday();

      // Today's average gap
      const todayRecords = (data[today] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
      let todayGap = null;
      if (todayRecords.length >= 2) {
        let total = 0;
        for (let i = 1; i < todayRecords.length; i++) {
          total += Math.round((new Date(todayRecords[i].time) - new Date(todayRecords[i-1].time)) / 60000);
        }
        todayGap = Math.round(total / (todayRecords.length - 1));
      }
      document.getElementById('chartTodayGap').textContent = todayGap !== null ? `≃${todayGap}ph` : '—';
      document.getElementById('chartTodayGap').parentElement.className = 'stat-card ' + (todayGap !== null ? (todayGap >= cfg.intervalGoal ? 'green' : 'accent') : '');

      // Week average gap (across all days with ≥2 cigs in the visible range)
      let totalGapAll = 0;
      let countDays = 0;
      for (const d of days) {
        if (d.count >= 2) {
          const records = (data[d.key] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
          let total = 0;
          for (let i = 1; i < records.length; i++) {
            total += Math.round((new Date(records[i].time) - new Date(records[i-1].time)) / 60000);
          }
          totalGapAll += Math.round(total / (records.length - 1));
          countDays++;
        }
      }
      const weekAvg = countDays > 0 ? Math.round(totalGapAll / countDays) : null;
      document.getElementById('chartWeekGap').textContent = weekAvg !== null ? `≃${weekAvg}ph` : '—';
    }


    // ========== DAY DETAIL (STATS TAB) ==========
    let selectedDate = '';

    function selectDay(dateKey) {
      if (selectedDate === dateKey) {
        closeDayDetail();
        return;
      }
      selectedDate = dateKey;
      renderDayDetail();
      renderChart();
    }

    function closeDayDetail() {
      selectedDate = '';
      document.getElementById('dayDetail').classList.remove('active');
      document.getElementById('dayDetailActions').style.display = 'none';
      renderChart();
    }

    function renderDayDetail() {
      if (!selectedDate) return;
      const data = loadData();
      const records = (data[selectedDate] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
      const el = document.getElementById('dayDetail');
      const title = document.getElementById('dayDetailTitle');
      const content = document.getElementById('dayDetailContent');
      const actions = document.getElementById('dayDetailActions');

      const now = new Date();
      const today = getToday();
      const parts = selectedDate.split('-');
      const d = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
      const dayNames = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
      const dayLabel = selectedDate === today ? 'Hôm nay' : `${dayNames[d.getDay()]} ${parts[2]}/${parts[1]}`;
      title.textContent = `📋 ${dayLabel} (${records.length} điếu)`;

      if (records.length === 0) {
        content.innerHTML = '<div class="day-detail-empty">Không có điếu nào trong ngày này</div>';
      } else {
        const cfg = loadConfig();
        const intervalGoal = cfg.intervalGoal || 0;
        let html = '';
        for (let i = 0; i < records.length; i++) {
          const r = records[i];
          const isHalf = r.note === '½';
          let label = isHalf ? '½ điếu' : `Điếu #${i + 1}`;
          const tIcon = triggerIcon(r.trigger);

          // Gap since previous
          let gapStr = '';
          let gapBad = false;
          let gapGood = false;
          if (i > 0) {
            const prev = new Date(records[i-1].time);
            const curr = new Date(r.time);
            const diffMin = Math.round((curr - prev) / 60000);
            if (diffMin > 0) {
              gapStr = `${diffMin}ph sau`;
              if (intervalGoal > 0) {
                if (diffMin < intervalGoal) {
                  gapBad = true;
                  const earlyBy = intervalGoal - diffMin;
                  gapStr += ` ⚠️ ${earlyBy}ph sớm`;
                } else {
                  gapGood = true;
                  const extra = diffMin - intervalGoal;
                  gapStr += ` ✅ +${extra}ph`;
                }
              }
            }
          }

          html += `
            <div class="timeline-item" data-index="${i}"
                 ontouchstart="longPressStart(event, '${selectedDate}', ${i})"
                 ontouchend="longPressEnd(event)"
                 ontouchmove="longPressEnd(event)"
                 oncontextmenu="event.preventDefault();confirmDelete('${selectedDate}', ${i})">
              <div class="timeline-time">${formatTime(r.time)}${tIcon ? `<span class="cig-trigger">${tIcon}</span>` : ''}</div>
              <div class="timeline-dot"></div>
              <div class="timeline-label">
                ${label}
                ${gapStr ? `<span class="timeline-gap ${gapBad ? 'bad' : gapGood ? 'good' : ''}"> — ${gapStr}</span>` : ''}
              </div>
              <div class="delete-hint">✕</div>
            </div>
          `;
        }
        content.innerHTML = html;
      }

      // Wire action buttons
      const timeBtn = document.getElementById('dayDetailTimeBtn');

      timeBtn.onclick = () => openPastTimePicker(selectedDate, records);

      actions.style.display = 'flex';
      actions.style.flexDirection = 'column';

      el.classList.add('active');
    }

    // Long-press to delete cigarette
    let longPressTimer = null;

    function longPressStart(e, dateStr, index) {
      longPressTimer = setTimeout(() => {
        const el = e.currentTarget;
        el.classList.add('long-pressing');
        navigator.vibrate?.(20);
        if (confirm('Xoá điếu này?')) {
          deleteCigaretteFromDay(dateStr, index);
        }
        el.classList.remove('long-pressing');
        longPressTimer = null;
      }, 500);
    }

    function longPressEnd(e) {
      if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
      }
      e.currentTarget?.classList.remove('long-pressing');
    }

    function confirmDelete(dateStr, index) {
      if (confirm('Xoá điếu này?')) {
        deleteCigaretteFromDay(dateStr, index);
      }
    }

    function updateMoneyStats() {
      const cfg = loadConfig();
      const todayCount = getTodayData().length;
      const weekCount = getWeekCount();
      const monthCount = getMonthCount();

      document.getElementById('moneyToday').textContent = formatMoney(calcCost(todayCount));
      document.getElementById('moneyWeek').textContent = formatMoney(calcCost(weekCount));
      document.getElementById('moneyMonth').textContent = formatMoney(calcCost(monthCount));

      const actualCost = calcCost(monthCount);
      const goalCost = calcCost(loadConfig().goal * 30);
      document.getElementById('moneySaved').textContent = formatMoney(Math.max(0, goalCost - actualCost));
    }

    // ========== ACTIONS ==========
    function addCig() {
      const records = addRecord();
      updateDisplay();
      const el = document.getElementById('todayCount');
      el.classList.remove('pop');
      void el.offsetWidth;
      el.classList.add('pop');
      if (navigator.vibrate) navigator.vibrate(15);
    }

    function undoLast() {
      const records = removeLastRecord();
      updateDisplay();
      if (navigator.vibrate) navigator.vibrate(10);
    }

    // ========== PAST DAY ACTIONS (STATS TAB) ==========
    function getDayData(dateStr) {
      const data = loadData();
      return (data[dateStr] || []).slice().sort((a, b) => new Date(a.time) - new Date(b.time));
    }

    function setDayData(dateStr, records) {
      const data = loadData();
      data[dateStr] = records;
      saveData(data);
    }

    function addToDay(dateStr) {
      const records = getDayData(dateStr);
      const time = records.length > 0
        ? new Date(new Date(records[records.length - 1].time).getTime() + 3600000)
        : new Date(dateStr + 'T12:00:00');
      records.push({ time: time.toISOString() });
      records.sort((a, b) => new Date(a.time) - new Date(b.time));
      setDayData(dateStr, records);
      afterDayEdit();
    }

    function undoFromDay(dateStr) {
      const records = getDayData(dateStr);
      if (records.length === 0) return;
      records.pop();
      setDayData(dateStr, records);
      afterDayEdit();
    }

    function afterDayEdit() {
      renderDayDetail();
      renderChart();
      updateDisplay();
      if (navigator.vibrate) navigator.vibrate(10);
    }

    function deleteCigaretteFromDay(dateStr, index) {
      const records = getDayData(dateStr);
      if (index < 0 || index >= records.length) return;
      records.splice(index, 1);
      setDayData(dateStr, records);
      afterDayEdit();
    }

    function openPastTimePicker(dateStr, records) {
      modalMode = 'past-add';
      window._pastDate = dateStr;
      modalTrigger = -1;
      document.getElementById('modalTitle').textContent = '⏪ Thêm cho ' + dateStr.slice(5);
      document.getElementById('modalDesc').textContent = 'Chọn giờ đã hút nhưng chưa note';
      document.getElementById('modalDeleteRow').style.display = 'none';
      document.getElementById('modalConfirmBtn').textContent = '➕ Thêm';

      const defaultTime = records.length > 0
        ? new Date(new Date(records[records.length - 1].time).getTime() + 3600000)
        : new Date(dateStr + 'T12:00:00');
      setModalTime(defaultTime);
      document.getElementById('modalTriggerName').textContent = '';
      renderModalTriggers();
      document.getElementById('timePickerModal').classList.add('active');
    }

    // ========== TIME PICKER MODAL ==========
    let modalMode = 'add'; // 'add' or 'edit'
    let editingIndex = -1;

    function openTimePicker() {
      modalMode = 'add';
      editingIndex = -1;
      modalTrigger = -1;
      document.getElementById('modalTitle').textContent = '⏪ Thêm điếu quên note';
      document.getElementById('modalDesc').textContent = 'Chọn giờ đã hút nhưng chưa note';
      document.getElementById('modalDeleteRow').style.display = 'none';
      document.getElementById('modalConfirmBtn').textContent = '➕ Thêm';
      setModalTime(new Date());
      document.getElementById('modalTriggerName').textContent = '';
      renderModalTriggers();
      document.getElementById('timePickerModal').classList.add('active');
    }

    function openTimeEditor(index) {
      const records = getTodayData();
      if (!records[index]) return;
      modalMode = 'edit';
      editingIndex = index;
      modalTrigger = records[index].trigger !== undefined ? records[index].trigger : -1;
      document.getElementById('modalTitle').textContent = '✏️ Sửa giờ điếu thuốc';
      document.getElementById('modalDesc').textContent = `Điếu #${index + 1}`;
      document.getElementById('modalDeleteRow').style.display = 'block';
      document.getElementById('modalConfirmBtn').textContent = '💾 Lưu';
      setModalTime(new Date(records[index].time));
      document.getElementById('modalTriggerName').textContent = modalTrigger >= 0 ? TRIGGERS[modalTrigger] : '';
      renderModalTriggers();
      document.getElementById('timePickerModal').classList.add('active');
    }

    function closeModal() {
      document.getElementById('timePickerModal').classList.remove('active');
    }

    function goToDay(dateKey) {
      switchTab('stats');
      setTimeout(() => selectDay(dateKey), 100);
    }

    function setModalTime(date) {
      document.getElementById('modalHour').value = date.getHours();
      // Round to nearest 5 min
      const min = Math.round(date.getMinutes() / 5) * 5;
      document.getElementById('modalMinute').value = Math.min(55, min);
    }

    function confirmModal() {
      const h = parseInt(document.getElementById('modalHour').value);
      const m = parseInt(document.getElementById('modalMinute').value);

      if (modalMode === 'past-add') {
        const dateStr = window._pastDate;
        const parts = dateStr.split('-');
        const d = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]), h, m, 0);
        const iso = d.toISOString();
        const records = getDayData(dateStr);
        const rec = { time: iso };
        if (modalTrigger >= 0) rec.trigger = modalTrigger;
        records.push(rec);
        records.sort((a, b) => new Date(a.time) - new Date(b.time));
        setDayData(dateStr, records);
        closeModal();
        afterDayEdit();
      } else {
        const d = new Date();
        d.setHours(h, m, 0, 0);
        const iso = d.toISOString();
        const records = getTodayData();
        if (modalMode === 'add') {
          const rec = { time: iso };
          if (modalTrigger >= 0) rec.trigger = modalTrigger;
          records.push(rec);
        } else if (modalMode === 'edit' && editingIndex >= 0) {
          records[editingIndex].time = iso;
          if (modalTrigger >= 0) records[editingIndex].trigger = modalTrigger;
          else delete records[editingIndex].trigger;
        }
        records.sort((a, b) => new Date(a.time) - new Date(b.time));
        setTodayData(records);
        closeModal();
        updateDisplay();
        if (navigator.vibrate) navigator.vibrate(10);
      }
    }

    function deleteModalItem() {
      if (editingIndex < 0) return;
      if (!confirm('Xoá điếu này?')) return;
      const records = getTodayData();
      records.splice(editingIndex, 1);
      setTodayData(records);
      closeModal();
      updateDisplay();
    }

    // ========== SETTINGS ==========
    function loadSettingsUI() {
      const cfg = loadConfig();
      document.getElementById('dailyGoalInput').value = cfg.goal || 10;
      document.getElementById('intervalGoalInput').value = cfg.intervalGoal || 0;
      document.getElementById('pricePerPack').value = cfg.pricePerPack || 25000;
      document.getElementById('cigsPerPack').value = cfg.cigsPerPack || 20;
    }

    function saveSettings() {
      const goal = parseInt(document.getElementById('dailyGoalInput').value) || 10;
      const intervalGoal = parseInt(document.getElementById('intervalGoalInput').value) || 0;
      const pricePerPack = parseInt(document.getElementById('pricePerPack').value) || 25000;
      const cigsPerPack = parseInt(document.getElementById('cigsPerPack').value) || 20;
      saveConfig({ goal, intervalGoal, pricePerPack, cigsPerPack });
      updateDisplay();
      const btn = document.querySelector('.btn-save');
      const orig = btn.textContent;
      btn.textContent = '✅ Đã lưu!';
      btn.style.background = '#2ecc71';
      setTimeout(() => {
        btn.textContent = orig;
        btn.style.background = '';
      }, 1500);
    }

    function exportCSV() {
      const data = loadData();
      const cfg = loadConfig();
      const dates = Object.keys(data).sort();

      if (dates.length === 0) {
        alert('Chưa có dữ liệu để xuất!');
        return;
      }

      // Build CSV rows
      let rows = [
        ['Ngày', 'Giờ', 'Ghi chú', 'Khoảng cách (phút)', 'STT trong ngày']
      ];
      const header = rows[0].join(',');

      for (const dateKey of dates) {
        const records = data[dateKey] || [];
        records.sort((a, b) => new Date(a.time) - new Date(b.time));
        for (let i = 0; i < records.length; i++) {
          const r = records[i];
          const d = new Date(r.time);
          const timeStr = `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
          const note = r.note || '';

          // Gap from previous cigarette
          let gap = '';
          if (i > 0) {
            const prev = new Date(records[i-1].time);
            const curr = new Date(r.time);
            gap = Math.round((curr - prev) / 60000);
          }

          rows.push(`"${dateKey}","${timeStr}","${note}","${gap}","${i + 1}"`);
        }
      }

      const csv = rows.join('\n');

      // Download
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const now = new Date();
      const filename = `thuocla_${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}.csv`;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      // Feedback
      if (navigator.vibrate) navigator.vibrate(15);
      const btn = document.querySelector('[onclick="exportCSV()"]');
      if (btn) {
        const orig = btn.textContent;
        btn.textContent = '✅ Đã xuất!';
        btn.style.background = '#2ecc71';
        setTimeout(() => {
          btn.textContent = orig;
          btn.style.background = '';
        }, 2000);
      }

      // Show success with record count
      const totalRecords = rows.length - 1; // minus header
      if (totalRecords > 1) {
        // Brief notification
        const notification = document.createElement('div');
        notification.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:var(--green);color:white;padding:12px 20px;border-radius:12px;font-size:14px;font-weight:600;z-index:9999;animation:fadeIn 0.3s ease;';
        notification.textContent = `📥 Đã xuất ${totalRecords} dòng → ${filename}`;
        document.body.appendChild(notification);
        setTimeout(() => {
          notification.style.opacity = '0';
          notification.style.transition = 'opacity 0.3s';
          setTimeout(() => document.body.removeChild(notification), 300);
        }, 3000);
      }
    }

    function clearAllData() {
      if (confirm('Xoá toàn bộ lịch sử hút thuốc?')) {
        localStorage.removeItem(DB_KEY);
        updateDisplay();
      }
    }

    // ========== TABS ==========
    function switchTab(name) {
      document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.getElementById('tab-' + name).classList.add('active');
      document.querySelector(`.tab[data-tab="${name}"]`)?.classList.add('active');
      if (name === 'main' || name === 'stats' || name === 'settings') updateDisplay();
      if (name === 'settings') loadSettingsUI();
    }

    // Close modal on overlay click
    document.getElementById('timePickerModal')?.addEventListener('click', (e) => {
      if (e.target === document.getElementById('timePickerModal')) closeModal();
    });

    // ========== CLEANUP ==========
    function cleanupOldData() {
      const data = loadData();
      const cutoff = new Date();
      cutoff.setDate(cutoff.getDate() - 31);
      const cutoffStr = `${cutoff.getFullYear()}-${String(cutoff.getMonth()+1).padStart(2,'0')}-${String(cutoff.getDate()).padStart(2,'0')}`;
      let changed = false;
      for (const key of Object.keys(data)) {
        if (key < cutoffStr) {
          delete data[key];
          changed = true;
        }
      }
      if (changed) saveData(data);
    }

    // ========== INIT ==========
    // Check URL hash for tab
    const hash = window.location.hash.slice(1);
    if (['stats', 'settings'].includes(hash)) switchTab(hash);
    cleanupOldData();
    updateDisplay();
    loadSettingsUI();
    startTimer();

    // Auto-refresh every 30s, timer every 1s
    setInterval(() => { updateDisplay(); loadSettingsUI(); }, 30000);

    // ========== FEATURES: Chain-Smoke Alert ==========
    function checkChainSmoke() {
      const records = getTodayData();
      if (records.length < 2) return null;
      const prev = new Date(records[records.length - 2].time);
      const curr = new Date(records[records.length - 1].time);
      const gapMin = Math.round((curr - prev) / 60000);
      if (gapMin > 0 && gapMin <= 30) return gapMin;
      return null;
    }

    function showChainAlert(gapMin) {
      const el = document.getElementById('todayCount');
      el.style.transition = 'background 0.2s';
      el.style.background = 'rgba(233,69,96,0.3)';
      setTimeout(() => { el.style.background = ''; }, 1500);
      const n = document.createElement('div');
      n.style.cssText = 'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(233,69,96,0.95);color:white;padding:20px 28px;border-radius:16px;font-size:16px;font-weight:700;z-index:9999;text-align:center;animation:fadeIn 0.3s ease;max-width:85vw;box-shadow:0 8px 40px rgba(0,0,0,0.4);';
      n.innerHTML = `🔥 <b>Chain-Smoke!</b><br><span style="font-size:13px;font-weight:400;">Chỉ cách điếu trước ${gapMin}ph!<br>Hãy chờ thêm ${Math.max(1,30-gapMin)}ph nữa.</span>`;
      document.body.appendChild(n);
      if (navigator.vibrate) navigator.vibrate([100,50,100,50,200]);
      setTimeout(() => { n.style.opacity='0'; n.style.transition='opacity 0.3s'; setTimeout(()=>n.remove(),300); }, 2500);
    }

    // ========== FEATURES: Peak Hours Warning ==========
    function checkPeakHour() {
      const h = new Date().getHours();
      if (h >= 6 && h < 12) {
        const n = document.createElement('div');
        n.style.cssText = 'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);background:rgba(241,196,15,0.95);color:#1a1a2e;padding:20px 28px;border-radius:16px;font-size:15px;font-weight:600;z-index:9999;text-align:center;animation:fadeIn 0.3s ease;max-width:85vw;box-shadow:0 8px 40px rgba(0,0,0,0.4);';
        n.innerHTML = `🌅 <b>Khung giờ đỉnh!</b><br><span style="font-size:13px;font-weight:400;">Sáng nay đã hút ${getTodayData().length} điếu.<br>36% tổng số rơi vào khung này — cố gắng giảm nhé!</span>`;
        document.body.appendChild(n);
        setTimeout(() => { n.style.opacity='0'; n.style.transition='opacity 0.3s'; setTimeout(()=>n.remove(),300); }, 3000);
      }
    }

    // ========== FEATURES: Slow-Down Alert ==========
    function checkSlowDown() {
      const records = getTodayData();
      if (records.length < 3) return;
      const now = new Date();
      const recent = records.filter(r => new Date(r.time) >= new Date(now-7200000));
      if (recent.length >= 3) {
        const n = document.createElement('div');
        n.style.cssText = 'position:fixed;bottom:100px;left:50%;transform:translateX(-50%);background:rgba(230,126,34,0.95);color:white;padding:14px 20px;border-radius:14px;font-size:14px;font-weight:600;z-index:9999;text-align:center;animation:fadeIn 0.3s ease;max-width:90vw;';
        n.innerHTML = `🐌 <b>Chậm lại!</b> ${recent.length} điếu trong 2h — uống nước & thư giãn 15ph!`;
        document.body.appendChild(n);
        setTimeout(() => { n.style.opacity='0'; n.style.transition='opacity 0.3s'; setTimeout(()=>n.remove(),300); }, 4000);
      }
    }

    // ========== FEATURES: Trigger Notes ==========

    // Trigger icon helpers (used by timeline + modal)
    let modalTrigger = -1; // -1 = not set

    function triggerIcon(idx) {
      if (idx === undefined || idx === null || idx < 0 || idx >= TRIGGERS.length) return '';
      return TRIGGERS[idx].split(' ')[0];
    }

    function renderModalTriggers() {
      const grid = document.getElementById('modalTriggerGrid');
      if (!grid) return;
      let h = '';
      TRIGGERS.forEach((t, i) => {
        const emoji = t.split(' ')[0];
        const sel = modalTrigger === i ? ' selected' : '';
        h += `<button type="button" class="trigger-chip${sel}" data-idx="${i}" onclick="selectModalTrigger(${i})" title="${t}">${emoji}</button>`;
      });
      grid.innerHTML = h;
    }

    function selectModalTrigger(idx) {
      modalTrigger = (modalTrigger === idx) ? -1 : idx; // tap again to unset
      renderModalTriggers();
      const nameEl = document.getElementById('modalTriggerName');
      if (nameEl) nameEl.textContent = modalTrigger >= 0 ? TRIGGERS[modalTrigger] : '';
    }

    function showTriggerPicker() {
      const c = document.createElement('div'); c.id='triggerPicker';
      c.style.cssText = 'position:fixed;bottom:0;left:0;right:0;background:var(--surface);border-radius:20px 20px 0 0;padding:16px 20px;z-index:9998;box-shadow:0 -4px 30px rgba(0,0,0,0.5);animation:fadeIn 0.25s ease;';
      let h = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;"><span style="font-size:14px;font-weight:700;">☕ Lý do hút?</span><button onclick="dismissTriggerPicker()" style="background:none;border:none;color:var(--text-dim);font-size:18px;cursor:pointer;">✕</button></div><div style="display:flex;flex-wrap:wrap;gap:6px;">';
      TRIGGERS.forEach((t,i) => { h += `<button class="preset-btn" onclick="setTrigger(${i})" style="font-size:14px;padding:10px 16px;">${t}</button>`; });
      h += '</div>'; c.innerHTML = h; document.body.appendChild(c);
    }
    function dismissTriggerPicker() { const e=document.getElementById('triggerPicker'); if(e){e.style.opacity='0'; e.style.transition='opacity 0.2s'; setTimeout(()=>e.remove(),200);} }
    function setTrigger(idx) {
      const r = getTodayData();
      if (r.length > 0) { r[r.length-1].trigger = idx; setTodayData(r); }
      dismissTriggerPicker();
    }

    // ========== FEATURES: Insights Rendering ==========
    function renderInsights() {
      const data = loadData(), cfg = loadConfig(), today = getToday();
      const MAX_CHAIN = 30;
      let chainToday = 0, chainWeek = 0;
      const sortedDays = Object.keys(data).sort();
      const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate()-7);
      let bestStreak=0, curStreak=0;
      for (const ds of sortedDays) {
        const recs = (data[ds]||[]).slice().sort((a,b)=>new Date(a.time)-new Date(b.time));
        let dc=0;
        for(let i=1;i<recs.length;i++) { const g=Math.round((new Date(recs[i].time)-new Date(recs[i-1].time))/60000); if(g>0&&g<=MAX_CHAIN) dc++; }
        if(ds===today) chainToday=dc;
        if(new Date(ds)>=weekAgo) chainWeek+=dc;
        if(dc===0){curStreak++;if(curStreak>bestStreak)bestStreak=curStreak;}else curStreak=0;
      }
      gid('chainToday').textContent=chainToday;
      gid('chainWeek').textContent=chainWeek;
      gid('chainBest').textContent=bestStreak+' ngày';
      gid('chainStreak').textContent=curStreak;

      // Week comparison
      const now=new Date(), dow=now.getDay();
      const monOff=dow===0?-6:1-dow;
      function getWeek(off) {
        const s=new Date(now); s.setDate(s.getDate()+monOff+off*7);
        const e=new Date(s); e.setDate(e.getDate()+6);
        const fmt=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
        const ss=fmt(s), ee=fmt(e);
        let tot=0, tg=0, cg=0, dd=0;
        for(const[k,v]of Object.entries(data)){if(k>=ss&&k<=ee){dd++;tot+=v.length;for(let i=1;i<v.length;i++){const g=Math.round((new Date(v[i].time)-new Date(v[i-1].time))/60000);if(g>0){tg+=g;cg++;}}}}
        return {tot, avg:cg>0?Math.round(tg/cg):null, dd};
      }
      const tw=getWeek(0), lw=getWeek(-1);
      const m=new Date(now); m.setDate(m.getDate()+monOff);
      const su=new Date(m); su.setDate(m.getDate()+6);
      const fs=d=>`${String(d.getDate()).padStart(2,'0')}/${String(d.getMonth()+1).padStart(2,'0')}`;
      gid('weekCompareLabel').textContent=`(${fs(m)}-${fs(su)})`;
      gid('wcThisWeek').textContent=`${tw.tot} đ (${tw.dd} ngày)`;
      gid('wcLastWeek').textContent=`${lw.tot} đ (${lw.dd} ngày)`;
      const diff=tw.tot-lw.tot;
      const dcEl=gid('wcDiff'), dcCard=gid('wcDiffCard');
      if(diff<0){dcEl.textContent=`${diff} ✅`;dcCard.className='stat-card green';}else if(diff>0){dcEl.textContent=`+${diff}`;dcCard.className='stat-card accent';}else{dcEl.textContent='0';dcCard.className='stat-card';}
      gid('wcGap').textContent=tw.avg?`${tw.avg}ph`:'—';
      gid('wcGapCard').className='stat-card '+(tw.avg&&tw.avg>=cfg.intervalGoal?'green':'accent');

      // Sleep analysis
      let allF=[], allL=[], ear=1440;
      for(const[ds,recs]of Object.entries(data)){if(!recs.length)continue;
        const s=recs.slice().sort((a,b)=>new Date(a.time)-new Date(b.time));
        const f=new Date(s[0].time), l=new Date(s[s.length-1].time);
        const fm=f.getHours()*60+f.getMinutes(), lm=l.getHours()*60+l.getMinutes();
        allF.push(fm); allL.push(lm); if(fm<ear) ear=fm;
      }
      if(allF.length){
        const af=Math.round(allF.reduce((a,b)=>a+b,0)/allF.length);
        const al=Math.round(allL.reduce((a,b)=>a+b,0)/allL.length);
        gid('sleepFirst').textContent=`${String(Math.floor(af/60)).padStart(2,'0')}h${String(af%60).padStart(2,'0')}`;
        gid('sleepLast').textContent=`${String(Math.floor(al/60)).padStart(2,'0')}h${String(al%60).padStart(2,'0')}`;
        gid('sleepEarliest').textContent=`${String(Math.floor(ear/60)).padStart(2,'0')}h${String(ear%60).padStart(2,'0')}`;
        const slp=(1440-al)+af; gid('sleepSpan').textContent=`${Math.floor(slp/60)}h${String(slp%60).padStart(2,'0')}`;
      }

      // Challenges
      const tc=getTodayData().length, allC=sortedDays.map(ds=>(data[ds]||[]).length);
      const avgC=allC.length?Math.round(allC.reduce((a,b)=>a+b,0)/allC.length):0;
      let gapOk=true;
      if(tc>=2&&cfg.intervalGoal>0){for(let i=1;i<tc;i++){const g=Math.round((new Date(getTodayData()[i].time)-new Date(getTodayData()[i-1].time))/60000);if(g>0&&g<cfg.intervalGoal){gapOk=false;break;}}}
      const chals=[
        {ic:chainToday===0?'✅':'⏳',lb:'Không chain hôm nay',ok:chainToday===0},
        {ic:tc<=avgC?'✅':'⏳',lb:`Hút ≤ TB (${avgC} đ/ngày)`,ok:tc<=avgC},
        {ic:curStreak>=3?'✅':'⏳',lb:`Chuỗi ${curStreak} ngày không chain`,ok:curStreak>=3},
        {ic:gapOk?'✅':'⏳',lb:`Giữ gap ≥${cfg.intervalGoal}ph`,ok:gapOk},
        {ic:tc<=cfg.goal?'✅':'⏳',lb:`Dưới mục tiêu (≤${cfg.goal} đ)`,ok:tc<=cfg.goal},
      ];
      gid('challengeContainer').innerHTML=chals.map(c=>`<div style="display:flex;align-items:center;gap:10px;background:var(--surface);border-radius:10px;padding:10px 14px;margin-bottom:6px;"><span style="font-size:18px;">${c.ic}</span><span style="font-size:13px;color:${c.ok?'var(--green)':'var(--text-dim)'};">${c.lb}</span></div>`).join('');

      // Trigger stats
      const tcArr=new Array(TRIGGERS.length).fill(0); let totalT=0;
      for(const[,recs]of Object.entries(data)){for(const r of recs){if(r.trigger!==undefined&&r.trigger<TRIGGERS.length){tcArr[r.trigger]++;totalT++;}}}
      const te=gid('triggerStats');
      if(totalT>0){
        const maxTc=Math.max(...tcArr);
        te.innerHTML=tcArr.map((c,i)=>c>0?`<div style="grid-column:span 2;background:var(--surface);border-radius:8px;padding:8px 12px;"><div style="display:flex;justify-content:space-between;font-size:12px;"><span>${TRIGGERS[i]}</span><span style="color:var(--text-dim);">${c} (${Math.round(c/totalT*100)}%)</span></div><div style="height:4px;background:rgba(255,255,255,0.08);border-radius:2px;margin-top:4px;"><div style="height:100%;width:${Math.round(c/maxTc*100)}%;background:var(--accent);border-radius:2px;"></div></div></div>`:'').join('');
      } else te.innerHTML='<div class="empty-state">Chưa ghi nhận trigger. Khi +1 chọn lý do hút nhé!</div>';
    }
    function gid(id){return document.getElementById(id);}

    // ========== FEATURES: After-Add Hooks ==========
    function afterAddCig() {
      const cg=checkChainSmoke(); if(cg) setTimeout(()=>showChainAlert(cg),600);
      if(getTodayData().length<=3) setTimeout(checkPeakHour,1200);
      setTimeout(checkSlowDown,1800);
      setTimeout(showTriggerPicker,2000);
    }

    // Patch addCig
    const _origAddCig = window.addCig || function(){};
    window.addCig = function() {
      const records = addRecord(); updateDisplay();
      const el = document.getElementById('todayCount');
      el.classList.remove('pop'); void el.offsetWidth; el.classList.add('pop');
      if (navigator.vibrate) navigator.vibrate(15);
      afterAddCig();
    };

    // Patch switchTab
    const _origSwitchTab = window.switchTab;
    window.switchTab = function(name) {
      document.querySelectorAll('.tab-content').forEach(t=>t.classList.remove('active'));
      document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
      document.getElementById('tab-'+name).classList.add('active');
      document.querySelector(`.tab[data-tab="${name}"]`)?.classList.add('active');
      if(name==='main'||name==='stats'||name==='settings') updateDisplay();
      if(name==='settings') loadSettingsUI();
      if(name==='insights') renderInsights();
    };
