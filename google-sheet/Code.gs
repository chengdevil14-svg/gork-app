/**
 * 存钱记录 → Google Sheet 同步脚本
 * 表格列：日期 | 进出 | 金额 | 照片 | 备注 | 编号
 * 照片直接嵌入格子里（不经过 Google Drive）。
 */

const SHEET_NAME = '记录';
const HEADERS = ['日期', '进出', '金额', '照片', '备注', '编号'];
const COL = { date: 1, type: 2, amount: 3, photo: 4, note: 5, id: 6 };

// 第一次先在编辑器里运行这个函数，完成授权并创建表格
function setup() {
  const ss = getSpreadsheet_();
  getSheet_(ss);
  Logger.log('表格已准备好：' + ss.getUrl());
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

    if (d.action === 'delete') {
      if (row) sh.deleteRow(row);
      return json_({ ok: true });
    }

    if (d.action === 'upsert') {
      const amount = Number(d.amount);
      if (!row) {
        sh.appendRow(['', '', '', '', '', '']);
        row = sh.getLastRow();
      }
      sh.getRange(row, COL.date).setValue(d.date);
      sh.getRange(row, COL.type).setValue(amount >= 0 ? '存入' : '取出');
      sh.getRange(row, COL.amount).setValue(amount);
      sh.getRange(row, COL.note).setValue(d.note || '');
      sh.getRange(row, COL.id).setValue(id);
      sh.getRange(row, 1, 1, 5).setFontColor(amount >= 0 ? '#16A34A' : '#DC2626');

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
  sh.setColumnWidth(COL.photo, 160);
  sh.setColumnWidth(COL.note, 200);
  sh.getRange('A:A').setNumberFormat('yyyy-mm-dd');
  sh.getRange('C:C').setNumberFormat('+$#,##0.00;-$#,##0.00');
  sh.getRange('F:F').setNumberFormat('@');
  sh.hideColumns(COL.id);
  // 右侧汇总
  sh.getRange('H1:H3').setValues([['总存款'], ['总存入'], ['总取出']]).setFontWeight('bold');
  sh.getRange('I1:I3').setFormulas([['=SUM(C2:C)'], ['=SUMIF(C2:C,">0")'], ['=-SUMIF(C2:C,"<0")']])
    .setNumberFormat('$#,##0.00');
  sh.setColumnWidth(9, 120);
  return sh;
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
