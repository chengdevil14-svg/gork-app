/**
 * 存钱记录 → Google Sheet 同步脚本
 * 「记录」表：日期 | 进出 | 金额 | 分类 | 照片 | 备注 | 目标 | 编号
 * 「目标」表：每个存款目标 / 欠款的进度
 * 照片直接嵌入格子里（不经过 Google Drive）。
 */

const SHEET_NAME = '记录';
const HEADERS = ['日期', '进出', '金额', '分类', '照片', '备注', '目标', '编号'];
const COL = { date: 1, type: 2, amount: 3, tag: 4, photo: 5, note: 6, goal: 7, id: 8 };
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
  if (props.getProperty('EMAIL_ON') !== 'true') return;
  const goals = JSON.parse(props.getProperty('GOALS') || '[]');
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
  if (!lines.length) return;
  const email = Session.getEffectiveUser().getEmail();
  const ss = getSpreadsheet_();
  MailApp.sendEmail(email, '💳 存钱记录：还款 / 存款日提醒', lines.join('\n\n') + '\n\n打开表格：' + ss.getUrl());
}

function nextDue_(today, day) {
  const y = today.getFullYear(), m = today.getMonth();
  const clamp = (yy, mm) => Math.min(day, new Date(yy, mm + 1, 0).getDate());
  let d = new Date(y, m, clamp(y, m));
  if (d < today) d = new Date(y, m + 1, clamp(y, m + 1));
  return d;
}

function doGet() {
  return json_({ ok: true, msg: '存钱记录同步脚本运行中' });
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

    if (d.action === 'goals') {
      writeGoals_(ss, d.goals || []);
      const props = PropertiesService.getScriptProperties();
      props.setProperty('GOALS', JSON.stringify(d.goals || []));
      props.setProperty('EMAIL_ON', d.emailOn ? 'true' : 'false');
      if (d.emailOn) {
        try { ensureDailyTrigger_(); } catch (e) { /* 需要先在编辑器运行 setup 授权 */ }
      }
      return json_({ ok: true });
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
      sh.getRange(row, COL.type).setValue(amount >= 0 ? '存入' : '取出');
      sh.getRange(row, COL.amount).setValue(amount);
      sh.getRange(row, COL.note).setValue(d.note || '');
      sh.getRange(row, COL.tag).setValue(d.tag || '');
      sh.getRange(row, COL.goal).setValue(d.goal || '');
      sh.getRange(row, COL.id).setValue(id);
      sh.getRange(row, 1, 1, 7).setFontColor(amount >= 0 ? '#16A34A' : '#DC2626');

      const photoCell = sh.getRange(row, COL.photo);
      if (d.photo) {
        const img = SpreadsheetApp.newCellImage()
          .setSourceUrl('data:image/jpeg;base64,' + d.photo)
          .setAltTextTitle(d.date + ' ' + (d.note || ''))
          .build();
        photoCell.setValue(img);
        sh.setRowHeight(row, 120);
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
  const ss = SpreadsheetApp.create('存钱记录');
  props.setProperty('SHEET_ID', ss.getId());
  return ss;
}

function getSheet_(ss) {
  let sh = ss.getSheetByName(SHEET_NAME);
  if (sh) return sh;
  sh = ss.getSheets()[0];
  sh.setName(SHEET_NAME);
  sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS])
    .setFontWeight('bold').setBackground('#6610F2').setFontColor('#FFFFFF');
  sh.setFrozenRows(1);
  sh.setColumnWidth(COL.date, 110);
  sh.setColumnWidth(COL.type, 60);
  sh.setColumnWidth(COL.amount, 110);
  sh.setColumnWidth(COL.tag, 90);
  sh.setColumnWidth(COL.photo, 160);
  sh.setColumnWidth(COL.note, 200);
  sh.setColumnWidth(COL.goal, 110);
  sh.getRange('A:A').setNumberFormat('yyyy-mm-dd');
  sh.getRange('C:C').setNumberFormat('+$#,##0.00;-$#,##0.00');
  sh.getRange('H:H').setNumberFormat('@');
  sh.hideColumns(COL.id);
  // 右侧汇总
  sh.getRange('J1:J3').setValues([['总存款'], ['总存入'], ['总取出']]).setFontWeight('bold');
  sh.getRange('K1:K3').setFormulas([['=SUM(C2:C)'], ['=SUMIF(C2:C,">0")'], ['=-SUMIF(C2:C,"<0")']])
    .setNumberFormat('$#,##0.00');
  sh.setColumnWidth(11, 120);
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
    sh.getRange(2, 1, last - 1, HEADERS.length).sort([{ column: COL.date, ascending: true }, { column: COL.id, ascending: true }]);
  }
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
