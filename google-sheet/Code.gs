/**
 * 成长账本 → Google Sheet 同步脚本
 * 「记录」表：日期 | 进出 | 金额 | 货币 | 结算金额 | 分类 | 账户 | 照片 | 备注 | 目标 | 编号
 * 「订阅」表：所有订阅支出
 * 「目标」表：每个存款目标 / 欠款的进度
 * 照片直接嵌入格子里（不经过 Google Drive）。
 */

const SHEET_NAME = '记录';
const HEADERS = ['日期', '进出', '金额', '货币', '结算金额', '分类', '账户', '照片', '备注', '目标', '编号', '计入收支'];
const COL = { date: 1, type: 2, amount: 3, cur: 4, base: 5, tag: 6, acc: 7, photo: 8, note: 9, goal: 10, id: 11, stat: 12 };
const SYM = { SGD: 'SGD ', MYR: 'MYR ', USD: 'USD ', CNY: '¥', EUR: '€', GBP: '£', JPY: '¥', HKD: 'HK$', AUD: 'A$', CAD: 'C$' };
const fmt_ = code => { const s = SYM[code] || ''; return '+"' + s + '"#,##0.00;-"' + s + '"#,##0.00'; };
const GOAL_SHEET = '目标';

// 第一次先在编辑器里运行这个函数，完成授权并创建表格（也会开启每天 9 点的 Email 还款提醒检查）
function setup() {
  const ss = getSpreadsheet_();
  getSheet_(ss);
  ensureDailyTrigger_();
  Logger.log('表格已准备好：' + ss.getUrl());
}

function ensureDailyTrigger_() {
  const exists = ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'dailyCheck');
  if (!exists) ScriptApp.newTrigger('dailyCheck').timeBased().everyDays(1).atHour(9).create();
}

// 每天自动运行：到了还款日（或提前 N 天）就发 Email
function dailyCheck() {
  const props = PropertiesService.getScriptProperties();
  const emailOn = props.getProperty('EMAIL_ON') === 'true';
  const subEmail = props.getProperty('SUB_EMAIL') === 'true';
  if (!emailOn && !subEmail) return;
  const goals = emailOn ? JSON.parse(props.getProperty('GOALS') || '[]') : [];
  const subs = subEmail ? JSON.parse(props.getProperty('SUBS') || '[]') : [];
  const tz = Session.getScriptTimeZone();
  const today = new Date(Utilities.formatDate(new Date(), tz, 'yyyy/MM/dd'));
  const lines = [];
  goals.forEach(g => {
    if (!g.dueDay || g.remaining <= 0) return;
    const due = nextDue_(today, g.dueDay);
    const days = Math.round((due - today) / 86400000);
    if (days !== (g.remindBefore || 0)) return;
    const word = g.type === '欠款' ? '还款' : '存款';
    const v = g.type === '欠款' ? '还' : '存';
    let t = `【${g.name}】${days === 0 ? '今天' : days + ' 天后'}（${due.getMonth() + 1}月${due.getDate()}日）是${word}日。`;
    if (g.monthly > 0) {
      const left = g.monthly - g.thisMonth;
      t += left > 0 ? `本月计划${v} $${g.monthly.toFixed(2)}，还差 $${left.toFixed(2)}。` : '本月计划已完成。';
    }
    t += `${g.type === '欠款' ? '还欠' : '还差'} $${Number(g.remaining).toFixed(2)}。`;
    lines.push(t);
  });
  subs.forEach(x => {
    if (!x.active || !x.next) return;
    const p = x.next.split('-').map(Number);
    const due = new Date(p[0], p[1] - 1, p[2]);
    const days = Math.round((due - today) / 86400000);
    if (days !== (x.remindBefore || 0)) return;
    const amt = (SYM[x.currency] || '') + Number(x.amount).toFixed(2);
    lines.push(x.wantCancel
      ? `✂️【${x.name}】${days === 0 ? '今天' : days + ' 天后'}会自动续费 ${amt}。你标记了想取消，记得在扣款前去取消！`
      : `🔁【${x.name}】${days === 0 ? '今天' : days + ' 天后'}会扣款 ${amt}（${x.cycle}）。还在用吗？`);
  });
  if (!lines.length) return;
  const email = Session.getEffectiveUser().getEmail();
  const ss = getSpreadsheet_();
  MailApp.sendEmail(email, '💳 成长账本：还款 / 订阅提醒', lines.join('\n\n') + '\n\n打开表格：' + ss.getUrl() + '\n\n（此邮件由成长账本自动发送）');
}

function nextDue_(today, day) {
  const y = today.getFullYear(), m = today.getMonth();
  const clamp = (yy, mm) => Math.min(day, new Date(yy, mm + 1, 0).getDate());
  let d = new Date(y, m, clamp(y, m));
  if (d < today) d = new Date(y, m + 1, clamp(y, m + 1));
  return d;
}

function doGet() {
  return json_({ ok: true, msg: '成长账本同步脚本运行中' });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const d = JSON.parse(e.postData.contents);
    const ss = getSpreadsheet_();
    const sh = getSheet_(ss);

    if (d.action === 'ping') return json_({ ok: true, sheet: ss.getUrl() });

    const id = String(d.id);
    let row = findRow_(sh, id);

    if (d.action === 'rebuild') {
      const props = PropertiesService.getScriptProperties();
      const base = props.getProperty('BASE') || 'SGD';
      const sums = rebuildSummaries_(ss, base);
      splitMonths_(ss);
      // Rebuild dashboard from stored props if available
      const goals = JSON.parse(props.getProperty('GOALS') || '[]');
      const subs = JSON.parse(props.getProperty('SUBS') || '[]');
      buildDashboard_(ss, { baseCode: base, goals: goals, subs: subs, accounts: [], deposits: [] }, sums || { day: {}, mon: {} });
      arrangeTabs_(ss);
      return json_({ ok: true, msg: '已重建汇总与首页' });
    }

    if (d.action === 'goals') {
      writeGoals_(ss, d.goals || []);
      const props = PropertiesService.getScriptProperties();
      props.setProperty('GOALS', JSON.stringify(d.goals || []));
      props.setProperty('EMAIL_ON', d.emailOn ? 'true' : 'false');
      props.setProperty('SUBS', JSON.stringify(d.subs || []));
      props.setProperty('SUB_EMAIL', d.subEmail ? 'true' : 'false');
      props.setProperty('BASE', d.baseCode || 'SGD');
      props.setProperty('LAST_SYNC', new Date().toISOString());
      writeSubs_(ss, d.subs || [], d.baseCode || 'SGD');
      const sums = rebuildSummaries_(ss, d.baseCode || 'SGD');
      splitMonths_(ss);
      buildDashboard_(ss, d, sums || { day: {}, mon: {} });
      arrangeTabs_(ss);
      if (d.emailOn || d.subEmail) {
        try { ensureDailyTrigger_(); } catch (e) { /* 需要先在编辑器运行 setup 授权 */ }
      }
      return json_({ ok: true, count: Math.max(0, sh.getLastRow() - 1) });
    }

    if (d.action === 'delete') {
      if (row) sh.deleteRow(row);
      return json_({ ok: true });
    }

    if (d.action === 'upsert') {
      const amount = Number(d.amount);
      if (!row) {
        sh.appendRow(HEADERS.map(() => ''));
        row = sh.getLastRow();
      }
      sh.getRange(row, COL.date).setValue(d.date);
      sh.getRange(row, COL.type).setValue(d.transfer ? '转账' : (amount >= 0 ? '存入' : '取出'));
      sh.getRange(row, COL.acc).setValue(d.transfer ? (d.account || '') + ' → ' + (d.toAccount || '') : (d.account || ''));
      sh.getRange(row, COL.amount).setValue(amount).setNumberFormat(fmt_(d.currency || 'SGD'));
      sh.getRange(row, COL.cur).setValue(d.currency || 'SGD');
      sh.getRange(row, COL.base).setValue(Number(d.base != null ? d.base : amount)).setNumberFormat(fmt_(d.baseCode || 'SGD'));
      sh.getRange(row, COL.note).setValue(d.note || '');
      sh.getRange(row, COL.tag).setValue(d.tag || '');
      sh.getRange(row, COL.goal).setValue(d.goal || '');
      sh.getRange(row, COL.id).setValue(id);
      sh.getRange(row, COL.stat).setValue(d.stat != null ? Number(d.stat) : (d.transfer ? 0 : Number(d.base)));
      sh.getRange(row, 1, 1, 10).setFontColor(d.transfer ? '#6B7280' : (amount >= 0 ? '#16A34A' : '#DC2626'));

      const photoCell = sh.getRange(row, COL.photo);
      if (d.photo) {
        // 限制照片大小，避免单元格图片过大导致表格卡顿（约 150KB base64）
        if (d.photo.length > 200000) {
          photoCell.setValue('📷 照片过大已跳过');
        } else {
          const img = SpreadsheetApp.newCellImage()
            .setSourceUrl('data:image/jpeg;base64,' + d.photo)
            .setAltTextTitle(d.date + ' ' + (d.note || ''))
            .build();
          photoCell.setValue(img);
          sh.setRowHeight(row, 120);
        }
      } else if (d.hasPhoto === false) {
        photoCell.clearContent();
        sh.setRowHeight(row, 28);
      }

      sortByDate_(sh);
      return json_({ ok: true });
    }

    return json_({ ok: false, error: '未知操作：' + d.action });
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message ? err.message : err) });
  } finally {
    lock.releaseLock();
  }
}

function getSpreadsheet_() {
  const props = PropertiesService.getScriptProperties();
  const id = props.getProperty('SHEET_ID');
  if (id) {
    try { return SpreadsheetApp.openById(id); } catch (e) { /* 被删了就重建 */ }
  }
  const ss = SpreadsheetApp.create('成长账本');
  props.setProperty('SHEET_ID', ss.getId());
  return ss;
}

function getSheet_(ss) {
  let sh = ss.getSheetByName(SHEET_NAME);
  if (sh) {
    // 旧版表格没有「账户」列：在「分类」后面插入一列（其他列自动右移）
    const head = sh.getRange(1, 1, 1, Math.max(1, sh.getLastColumn())).getDisplayValues()[0];
    if (head.indexOf('账户') < 0 && head[5] === '分类') {
      sh.insertColumnAfter(6);
      sh.getRange(1, 7).setValue('账户').setFontWeight('bold').setBackground('#6610F2').setFontColor('#FFFFFF');
      sh.setColumnWidth(7, 120);
    }
    if (!sh.getFilter()) sh.getRange(1, 1, sh.getMaxRows(), HEADERS.length).createFilter();
    if (sh.getRange(1, COL.stat).getDisplayValue() !== '计入收支') {
      sh.getRange(1, COL.stat).setValue('计入收支').setFontWeight('bold');
      sh.hideColumns(COL.id, 2);
    }
    return sh;
  }
  sh = ss.getSheets()[0];
  sh.setName(SHEET_NAME);
  sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS])
    .setFontWeight('bold').setBackground('#6610F2').setFontColor('#FFFFFF');
  sh.setFrozenRows(1);
  sh.getRange(1, 1, sh.getMaxRows(), HEADERS.length).createFilter();
  sh.setColumnWidth(COL.date, 110);
  sh.setColumnWidth(COL.type, 60);
  sh.setColumnWidth(COL.amount, 110);
  sh.setColumnWidth(COL.tag, 90);
  sh.setColumnWidth(COL.photo, 160);
  sh.setColumnWidth(COL.note, 200);
  sh.setColumnWidth(COL.goal, 110);
  sh.getRange('A:A').setNumberFormat('yyyy-mm-dd');
  sh.getRange('K:K').setNumberFormat('@');
  sh.setColumnWidth(COL.cur, 60);
  sh.setColumnWidth(COL.base, 110);
  sh.hideColumns(COL.id, 2);
  // 右侧汇总
  sh.getRange('M1:M3').setValues([['总收支（结算）'], ['总存入'], ['总取出']]).setFontWeight('bold');
  sh.getRange('N1:N3').setFormulas([['=SUMIF(B2:B,"<>转账",E2:E)'], ['=SUMIFS(E2:E,E2:E,">0",B2:B,"<>转账")'], ['=-SUMIFS(E2:E,E2:E,"<0",B2:B,"<>转账")']])
    .setNumberFormat('#,##0.00');
  sh.setColumnWidth(13, 120);
  sh.setColumnWidth(14, 120);
  return sh;
}

function writeGoals_(ss, goals) {
  let gs = ss.getSheetByName(GOAL_SHEET);
  if (!gs) gs = ss.insertSheet(GOAL_SHEET);
  gs.clear();
  const head = ['名称', '类型', '总额', '已完成', '剩余', '进度', '每月计划', '本月已完成', '预计完成', '期限', '之后每月需', '每月几号', '提前几天提醒'];
  const rows = goals.map(g => [
    g.name, g.type, g.target, g.done, g.remaining, g.pct, g.monthly || '', g.thisMonth,
    g.finish || '', g.deadline || '', g.need || '', g.dueDay || '', g.dueDay ? (g.remindBefore || 0) : '',
  ]);
  gs.getRange(1, 1, 1, head.length).setValues([head])
    .setFontWeight('bold').setBackground('#6610F2').setFontColor('#FFFFFF');
  if (rows.length) {
    gs.getRange(2, 1, rows.length, head.length).setValues(rows);
    gs.getRange(2, 3, rows.length, 3).setNumberFormat('$#,##0.00');
    gs.getRange(2, 6, rows.length, 1).setNumberFormat('0%');
    gs.getRange(2, 7, rows.length, 2).setNumberFormat('$#,##0.00');
    gs.getRange(2, 11, rows.length, 1).setNumberFormat('$#,##0.00');
  }
  gs.setFrozenRows(1);
  gs.autoResizeColumns(1, head.length);
}

function writeSubs_(ss, subs, base) {
  let gs = ss.getSheetByName('订阅');
  if (!gs) gs = ss.insertSheet('订阅');
  gs.clear();
  const head = ['名称', '金额', '货币', '周期', '下次扣款', '每月约（' + base + '）', '想取消', '状态'];
  gs.getRange(1, 1, 1, head.length).setValues([head])
    .setFontWeight('bold').setBackground('#6610F2').setFontColor('#FFFFFF');
  const rows = subs.map(x => [x.name, x.amount, x.currency, x.cycle, x.next, x.monthlyBase,
    x.wantCancel ? '✂️ 是' : '', x.active ? '进行中' : '已取消']);
  if (rows.length) {
    gs.getRange(2, 1, rows.length, head.length).setValues(rows);
    gs.getRange(2, 2, rows.length, 1).setNumberFormat('#,##0.00');
    gs.getRange(2, 6, rows.length, 1).setNumberFormat('#,##0.00');
    const n = rows.length + 2;
    gs.getRange(n, 5).setValue('每月合计').setFontWeight('bold');
    gs.getRange(n, 6).setFormula('=SUMIF(H2:H' + (n - 1) + ',"进行中",F2:F' + (n - 1) + ')').setNumberFormat('#,##0.00').setFontWeight('bold');
  }
  gs.setFrozenRows(1);
  gs.autoResizeColumns(1, head.length);
}

// ---------- 每日 / 每月 / 每年 / 分类 汇总 ----------
function rebuildSummaries_(ss, base) {
  const sh = getSheet_(ss);
  const last = sh.getLastRow();
  if (last < 2) return;
  const tz = ss.getSpreadsheetTimeZone();
  const vals = sh.getRange(2, 1, last - 1, HEADERS.length).getValues();
  const WEEK = ['日', '一', '二', '三', '四', '五', '六'];
  const day = {}, mon = {}, yr = {}, catMon = {};
  const add = (m, k) => (m[k] = m[k] || { in: 0, out: 0, n: 0, cats: {} });
  vals.forEach(r => {
    let d = r[COL.date - 1];
    if (!(d instanceof Date)) d = new Date(String(d).replace(/-/g, '/'));
    if (isNaN(d)) return;
    const type = r[COL.type - 1];
    let v = r[COL.stat - 1];
    if (v === '' || v === null) v = type === '转账' ? (r[COL.tag - 1] === '交通卡充值' ? Number(r[COL.base - 1]) : 0) : Number(r[COL.base - 1]);
    v = Number(v) || 0;
    if (v === 0) return;
    const dk = Utilities.formatDate(d, tz, 'yyyy-MM-dd');
    const mk = dk.slice(0, 7), yk = dk.slice(0, 4);
    const cat = r[COL.tag - 1] || '未分类';
    [add(day, dk), add(mon, mk), add(yr, yk)].forEach(o => {
      o.n++;
      if (v > 0) o.in += v; else { o.out += -v; o.cats[cat] = (o.cats[cat] || 0) - v; }
    });
    if (v < 0) { catMon[cat] = catMon[cat] || {}; catMon[cat][mk] = (catMon[cat][mk] || 0) - v; }
  });
  const top = (cats, n) => Object.keys(cats).sort((a, b) => cats[b] - cats[a]).slice(0, n)
    .map(k => k + ' ' + cats[k].toFixed(2)).join('、');
  const money = '#,##0.00';

  const dRows = Object.keys(day).sort().reverse().map(k => {
    const o = day[k], dt = new Date(k.replace(/-/g, '/'));
    return [dt, '星期' + WEEK[dt.getDay()], o.in, o.out, o.in - o.out, o.n, top(o.cats, 1)];
  });
  writeTable_(ss, '每日汇总', ['日期', '星期', '存入', '取出', '结余', '笔数', '花最多'], dRows, [[1, 'yyyy-mm-dd'], [3, money], [4, money], [5, money]], '#3E95D6');

  const mRows = Object.keys(mon).sort().reverse().map(k => {
    const o = mon[k], p = k.split('-').map(Number);
    const now = new Date();
    const days = (p[0] === now.getFullYear() && p[1] === now.getMonth() + 1) ? now.getDate() : new Date(p[0], p[1], 0).getDate();
    return [k, o.in, o.out, o.in - o.out, o.in > 0 ? (o.in - o.out) / o.in : '', o.out / days, o.n, top(o.cats, 3)];
  });
  writeTable_(ss, '每月汇总', ['月份', '存入', '取出', '结余', '储蓄率', '日均支出', '笔数', '支出前 3 分类'], mRows, [[2, money], [3, money], [4, money], [5, '0%'], [6, money]], '#2E9D5B');

  const yRows = Object.keys(yr).sort().reverse().map(k => {
    const o = yr[k], now = new Date();
    const months = Number(k) === now.getFullYear() ? now.getMonth() + 1 : 12;
    return [k, o.in, o.out, o.in - o.out, o.in > 0 ? (o.in - o.out) / o.in : '', o.out / months, o.n, top(o.cats, 3)];
  });
  writeTable_(ss, '每年汇总', ['年份', '存入', '取出', '结余', '储蓄率', '月均支出', '笔数', '支出前 3 分类'], yRows, [[2, money], [3, money], [4, money], [5, '0%'], [6, money]], '#9B4DDB');

  const months = [];
  const now = new Date();
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    months.push(Utilities.formatDate(d, tz, 'yyyy-MM'));
  }
  const cats = Object.keys(catMon).sort((a, b) => {
    const s = c => months.reduce((t, m) => t + (catMon[c][m] || 0), 0);
    return s(b) - s(a);
  });
  const cRows = cats.map(c => [c].concat(months.map(m => catMon[c][m] || '')).concat([months.reduce((t, m) => t + (catMon[c][m] || 0), 0)]));
  const fm = months.map((m, i) => [i + 2, money]).concat([[months.length + 2, money]]);
  writeTable_(ss, '分类汇总', ['分类（支出）'].concat(months).concat(['12 个月合计']), cRows, fm, '#E0538A');

  return { day: day, mon: mon };
}

// ---------- 明细按月分开（隐藏） ----------
function splitMonths_(ss) {
  const sh = getSheet_(ss);
  const last = sh.getLastRow();
  const cols = HEADERS.length;
  const keep = {};
  if (last >= 2) {
    const dates = sh.getRange(2, COL.date, last - 1, 1).getDisplayValues().map(r => String(r[0]).slice(0, 7));
    let i = 0;
    while (i < dates.length) {
      const mk = dates[i];
      let j = i;
      while (j + 1 < dates.length && dates[j + 1] === mk) j++;
      if (/^\d{4}-\d{2}$/.test(mk)) {
        const name = '明细 ' + mk;
        keep[name] = true;
        let dest = ss.getSheetByName(name);
        if (!dest) dest = ss.insertSheet(name);
        dest.clear();
        const n = j - i + 1;
        sh.getRange(1, 1, 1, cols).copyTo(dest.getRange(1, 1));
        sh.getRange(i + 2, 1, n, cols).copyTo(dest.getRange(2, 1));
        for (let c = 1; c <= cols; c++) dest.setColumnWidth(c, sh.getColumnWidth(c));
        for (let r = 0; r < n; r++) {
          const h = sh.getRowHeight(i + 2 + r);
          if (h > 30) dest.setRowHeight(r + 2, h);
        }
        dest.setFrozenRows(1);
        dest.hideColumns(COL.id, 2);
        dest.setTabColor('#94A3B8');
      }
      i = j + 1;
    }
  }
  ss.getSheets().forEach(s => {
    const n = s.getName();
    if (n.indexOf('明细 ') === 0 && !keep[n]) ss.deleteSheet(s);
  });
}

// ---------- 首页仪表板 ----------
function buildDashboard_(ss, d, sums) {
  let s = ss.getSheetByName('首页');
  if (!s) s = ss.insertSheet('首页', 0);
  s.getRange(1, 1, s.getMaxRows(), s.getMaxColumns()).breakApart();
  s.clear();
  s.setHiddenGridlines(true);
  s.setTabColor('#3E95D6');
  const base = d.baseCode || 'SGD';
  const tz = ss.getSpreadsheetTimeZone();
  const now = new Date();
  const y = now.getFullYear(), m = now.getMonth() + 1;
  const mk = y + '-' + ('0' + m).slice(-2);
  const days = new Date(y, m, 0).getDate();
  const money = '#,##0.00';
  const BLUE = '#3E95D6', GREEN = '#16A34A', RED = '#DC2626';

  // title + month totals
  s.getRange('A1:G1').merge().setValue('📒 成长账本 · ' + y + '年' + m + '月（' + base + '）')
    .setFontSize(15).setFontWeight('bold').setBackground(BLUE).setFontColor('#FFFFFF').setVerticalAlignment('middle');
  s.setRowHeight(1, 36);
  const mo = (sums.mon && sums.mon[mk]) || { in: 0, out: 0 };
  s.getRange('A2:G2').setValues([['存入', mo.in, '', '支出', mo.out, '结余', mo.in - mo.out]]);
  s.getRange('B2').setNumberFormat(money).setFontColor(GREEN).setFontWeight('bold');
  s.getRange('E2').setNumberFormat(money).setFontColor(RED).setFontWeight('bold');
  s.getRange('G2').setNumberFormat(money).setFontWeight('bold').setFontColor(mo.in - mo.out >= 0 ? GREEN : RED);
  s.getRange('B2:C2').merge();
  s.setRowHeight(2, 28);

  // calendar
  const WEEK = ['一', '二', '三', '四', '五', '六', '日'];
  s.getRange(3, 1, 1, 7).setValues([WEEK]).setFontWeight('bold').setHorizontalAlignment('center').setBackground('#E8F3FC');
  const first = new Date(y, m - 1, 1);
  const offset = (first.getDay() + 6) % 7;
  const rows = Math.ceil((offset + days) / 7);
  const grid = [], bgs = [], fcs = [];
  for (let r = 0; r < rows; r++) {
    grid.push(['', '', '', '', '', '', '']);
    bgs.push(['#FFFFFF', '#FFFFFF', '#FFFFFF', '#FFFFFF', '#FFFFFF', '#FFFFFF', '#FFFFFF']);
    fcs.push(['#111827', '#111827', '#111827', '#111827', '#111827', '#111827', '#111827']);
  }
  for (let dd = 1; dd <= days; dd++) {
    const idx = offset + dd - 1, r = Math.floor(idx / 7), c = idx % 7;
    const key = mk + '-' + ('0' + dd).slice(-2);
    const o = sums.day && sums.day[key];
    let t = String(dd);
    if (o) {
      const net = o.in - o.out;
      if (o.in > 0) t += '\n+' + o.in.toFixed(2);
      if (o.out > 0) t += '\n-' + o.out.toFixed(2);
      t += '\n= ' + (net >= 0 ? '+' : '') + net.toFixed(2);
      bgs[r][c] = net > 0 ? '#DCFCE7' : (net < 0 ? '#FEE2E2' : '#FFFFFF');
      fcs[r][c] = net >= 0 ? '#166534' : '#991B1B';
    }
    if (dd === now.getDate()) t = '📍' + t;
    grid[r][c] = t;
  }
  const cal = s.getRange(4, 1, rows, 7);
  cal.setValues(grid).setBackgrounds(bgs).setFontColors(fcs).setWrap(true).setVerticalAlignment('top')
    .setFontSize(10).setBorder(true, true, true, true, true, true, '#E5E7EB', SpreadsheetApp.BorderStyle.SOLID);
  for (let r = 0; r < rows; r++) s.setRowHeight(4 + r, 72);
  for (let c = 1; c <= 7; c++) s.setColumnWidth(c, 78);

  // last 12 months under the calendar
  let r0 = 4 + rows + 1;
  s.getRange(r0, 1, 1, 7).merge().setValue('📈 最近 12 个月').setFontWeight('bold').setBackground(BLUE).setFontColor('#FFFFFF');
  s.getRange(r0 + 1, 1, 1, 4).setValues([['月份', '存入', '支出', '结余']]).setFontWeight('bold');
  const mkeys = Object.keys(sums.mon || {}).sort().reverse().slice(0, 12);
  if (mkeys.length) {
    const mr = mkeys.map(k => [k, sums.mon[k].in, sums.mon[k].out, sums.mon[k].in - sums.mon[k].out]);
    s.getRange(r0 + 2, 1, mr.length, 4).setValues(mr);
    s.getRange(r0 + 2, 2, mr.length, 3).setNumberFormat(money);
    // 简单柱状图
    try {
      const chart = s.newChart()
        .setChartType(Charts.ChartType.COLUMN)
        .addRange(s.getRange(r0 + 1, 1, mkeys.length + 1, 4))
        .setPosition(r0 + mkeys.length + 4, 1, 0, 0)
        .setOption('title', '最近月份收支')
        .setOption('legend', { position: 'bottom' })
        .setOption('width', 520)
        .setOption('height', 280)
        .build();
      s.insertChart(chart);
    } catch (e) { /* 图表可选，失败不影响 */ }
  }

  // right-hand panels
  s.setColumnWidth(8, 16);
  const W = [140, 90, 95, 95];
  for (let i = 0; i < 4; i++) s.setColumnWidth(9 + i, W[i]);
  let r = 1;
  const panel = (title, color, head, data, fmts) => {
    s.getRange(r, 9, 1, 4).merge().setValue(title).setFontWeight('bold').setBackground(color).setFontColor('#FFFFFF').setVerticalAlignment('middle');
    s.setRowHeight(r, 30);
    r++;
    s.getRange(r, 9, 1, head.length).setValues([head]).setFontWeight('bold').setFontSize(9).setFontColor('#6B7280');
    r++;
    if (data.length) {
      const padded = data.map(row => row.concat(['', '', '', '']).slice(0, 4));
      s.getRange(r, 9, padded.length, 4).setValues(padded).setWrap(true).setVerticalAlignment('middle');
      (fmts || []).forEach(f => s.getRange(r, 9 + f[0], padded.length, 1).setNumberFormat(f[1]));
      r += padded.length;
    } else {
      s.getRange(r, 9).setValue('（还没有）').setFontColor('#9CA3AF');
      r++;
    }
    r++;
  };
  const ICON = ['💵', '🏦', '💳', '📱', '👛', '🚇'];
  const TYPE = ['现金', '银行卡', '信用卡', '电子钱包', '其他', '交通卡'];
  panel('💳 账户', '#1D4ED8', ['账户', '类型', '余额', '货币'],
    (d.accounts || []).map(a => [(ICON[a.type] || '') + ' ' + a.name, TYPE[a.type] || '', a.balance, a.currency]), [[2, money]]);
  panel('🔁 固定收支 / 订阅', '#E0538A', ['名称', '金额', '周期', '下次'],
    (d.subs || []).filter(x => x.active).map(x => [(x.wantCancel ? '✂️ ' : '') + x.name, (x.isIncome ? '+' : '-') + Number(x.amount).toFixed(2) + ' ' + x.currency, x.cycle, x.next]));
  panel('🎯 欠款 / 目标', '#9B4DDB', ['名称', '剩余', '进度', '预计完成'],
    (d.goals || []).map(g => [(g.type === '欠款' ? '💸 ' : '🎯 ') + g.name, g.remaining, g.pct, g.finish]), [[1, money], [2, '0%']]);
  panel('💰 利息 / 定期', '#2E9D5B', ['名称', '本金', '年利率', '到期 / 已赚'],
    (d.deposits || []).map(x => [x.name, x.principal, x.rate / 100, x.maturity ? x.maturity : '已赚 ' + Number(x.accrued).toFixed(2)]), [[1, money], [2, '0.00%']]);
  s.getRange(r, 9, 1, 4).merge().setValue('更新于 ' + Utilities.formatDate(now, tz, 'yyyy-MM-dd HH:mm') + ' · 明细在隐藏的「明细 YYYY-MM」页')
    .setFontSize(9).setFontColor('#9CA3AF');
}

// 首页、汇总在前；原始记录、每日、目标、订阅、按月明细隐藏
function arrangeTabs_(ss) {
  const home = ss.getSheetByName('首页');
  if (home) { home.showSheet(); ss.setActiveSheet(home); ss.moveActiveSheet(1); }
  const visible = ['首页', '每月汇总', '每年汇总', '分类汇总'];
  const hidden = ['记录', '每日汇总', '目标', '订阅'];
  let pos = 2;
  visible.slice(1).concat(hidden).forEach(n => {
    const s = ss.getSheetByName(n);
    if (s) { s.showSheet(); ss.setActiveSheet(s); ss.moveActiveSheet(pos++); }
  });
  const months = ss.getSheets().map(s => s.getName()).filter(n => n.indexOf('明细 ') === 0).sort().reverse();
  months.forEach(n => { const s = ss.getSheetByName(n); ss.setActiveSheet(s); ss.moveActiveSheet(pos++); });
  if (home) ss.setActiveSheet(home);
  hidden.concat(months).forEach(n => { const s = ss.getSheetByName(n); if (s && !s.isSheetHidden()) s.hideSheet(); });
}

function writeTable_(ss, name, head, rows, formats, color) {
  let s = ss.getSheetByName(name);
  if (!s) s = ss.insertSheet(name);
  s.clear();
  s.setTabColor(color);
  s.getRange(1, 1, 1, head.length).setValues([head]).setFontWeight('bold').setBackground(color).setFontColor('#FFFFFF');
  if (rows.length) {
    s.getRange(2, 1, rows.length, head.length).setValues(rows);
    formats.forEach(f => s.getRange(2, f[0], rows.length, 1).setNumberFormat(f[1]));
  }
  s.setFrozenRows(1);
  s.autoResizeColumns(1, head.length);
  for (let c = 1; c <= head.length; c++) {
    const w = s.getColumnWidth(c);
    if (w < 80) s.setColumnWidth(c, 80);
    if (w > 260) { s.setColumnWidth(c, 260); s.getRange(1, c, Math.max(1, rows.length + 1), 1).setWrap(true); }
  }
}

function findRow_(sh, id) {
  const last = sh.getLastRow();
  if (last < 2) return 0;
  const ids = sh.getRange(2, COL.id, last - 1, 1).getDisplayValues();
  for (let i = 0; i < ids.length; i++) if (ids[i][0] === id) return i + 2;
  return 0;
}

function sortByDate_(sh) {
  const last = sh.getLastRow();
  if (last > 2) {
    sh.getRange(2, 1, last - 1, HEADERS.length).sort([{ column: COL.date, ascending: false }, { column: COL.id, ascending: false }]);
  }
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

