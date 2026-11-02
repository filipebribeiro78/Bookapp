/**
 * Estante: receives books and photos from the Estante Android app, stores the photos in
 * Google Drive and catalogues them in this Sheet. Setup steps are in SETUP.md.
 *
 * Columns are found by their header text, so you can reorder columns or add your own.
 */

const VERSION = '2';
const BOOKS = 'Books';
const PHOTOS = 'Photos';
const BOOK_HEADERS = [
  'Book ID', 'Book name', 'Authors', 'Date of publication', 'Original year', 'Publisher', 'ISBN',
  'Language', 'Pages', 'Condition', 'Cover thumbnail', 'Photo links', 'Asking price (EUR)', 'Price status',
  'Description (EN)', 'Description (PT)', 'Drive folder', 'Status', 'OLX status', 'OLX link',
  'WordPress status', 'WordPress link', 'Created', 'Updated',
];
const PHOTO_HEADERS = ['Photo ID', 'Book ID', 'Book name', 'File name', 'Type of page', 'Thumbnail', 'Link', 'Taken at'];
const THUMBNAIL_ROW_HEIGHT = 90;
// Kept as plain text so Sheets doesn't turn an ISBN into a number or "1982-05" into a date.
const TEXT_COLUMNS = ['ISBN', 'Date of publication'];

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Estante')
    .addItem('1. Set up this sheet', 'setup')
    .addItem('2. Show connection details', 'showConnectionDetails')
    .addToUi();
}

function setup() {
  const spreadsheet = SpreadsheetApp.getActive();
  ensureSheet_(spreadsheet, BOOKS, BOOK_HEADERS, 'Cover thumbnail');
  ensureSheet_(spreadsheet, PHOTOS, PHOTO_HEADERS, 'Thumbnail');
  spreadsheet.getSheets().forEach((sheet) => {
    const name = sheet.getName();
    if (name !== BOOKS && name !== PHOTOS && sheet.getLastRow() === 0) spreadsheet.deleteSheet(sheet);
  });

  const props = PropertiesService.getScriptProperties();
  if (!props.getProperty('TOKEN')) {
    props.setProperty('TOKEN', Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, ''));
  }
  rootFolder_();
  notify_(
    'Estante is set up. Next, in Apps Script: Deploy > New deployment > Web app, with ' +
      '"Execute as: Me" and "Who has access: Anyone". Then use Estante > Show connection details.',
  );
}

function showConnectionDetails() {
  const token = PropertiesService.getScriptProperties().getProperty('TOKEN');
  if (!token) {
    notify_('Run Estante > Set up this sheet first.');
    return;
  }
  const url = ScriptApp.getService().getUrl();
  const urlLine = url && /\/exec$/.test(url)
    ? `<code style="word-break:break-all">${url}</code>`
    : 'Copy it from Apps Script: Deploy > Manage deployments. It ends in /exec.';
  const html = HtmlService.createHtmlOutput(
    '<div style="font-family:sans-serif;font-size:14px;line-height:1.4">' +
      `<p><b>Web app URL</b><br>${urlLine}</p>` +
      `<p><b>Token</b><br><code style="word-break:break-all">${token}</code></p>` +
      '<p>Paste both into Estante &gt; Settings on your phone. Keep the token private: ' +
      'anyone with the URL and the token can add books to this Sheet.</p></div>',
  ).setWidth(480).setHeight(300);
  SpreadsheetApp.getUi().showModalDialog(html, 'Estante connection details');
}

function doGet() {
  return ContentService.createTextOutput('Estante is running. Send books here from the Estante app.');
}

function doPost(e) {
  try {
    const request = JSON.parse(e.postData.contents);
    const token = PropertiesService.getScriptProperties().getProperty('TOKEN');
    if (!token || request.token !== token) {
      return reply_({ ok: false, error: 'The token does not match. Copy it again from Estante > Show connection details.' });
    }
    switch (request.action) {
      case 'ping':
        return reply_({ ok: true, version: VERSION, sheetUrl: SpreadsheetApp.getActive().getUrl() });
      case 'upsertBook':
        return reply_(withLock_(() => upsertBook_(request.book)));
      case 'uploadPhoto':
        return reply_(withLock_(() => uploadPhoto_(request.bookId, request.photo)));
      case 'finishBook':
        return reply_(withLock_(() => finishBook_(request.bookId)));
      case 'listBooks':
        return reply_(listBooks_(request.status));
      case 'updateBook':
        return reply_(withLock_(() => updateBook_(request.bookId, request.fields)));
      default:
        return reply_({ ok: false, error: `Unknown action: ${request.action}` });
    }
  } catch (err) {
    return reply_({ ok: false, error: String(err && err.message ? err.message : err) });
  }
}

function upsertBook_(book) {
  const sheet = sheet_(BOOKS);
  let row = findRow_(sheet, book.id);
  let folder = row ? folderFromUrl_(getCell_(sheet, row, 'Drive folder')) : null;
  if (!folder) folder = rootFolder_().createFolder(folderName_(book));
  else if (folder.getName() !== folderName_(book)) folder.setName(folderName_(book));

  if (!row) {
    sheet.appendRow(rowValues_(sheet, {
      'Book ID': book.id,
      'Book name': book.title,
      Condition: book.condition,
      'Drive folder': folder.getUrl(),
      Status: 'Photos uploading',
      Created: new Date(book.createdAt),
      Updated: new Date(),
    }));
    row = sheet.getLastRow();
    sheet.setRowHeight(row, THUMBNAIL_ROW_HEIGHT);
  } else {
    setCell_(sheet, row, 'Book name', book.title);
    setCell_(sheet, row, 'Condition', book.condition);
    setCell_(sheet, row, 'Drive folder', folder.getUrl());
    setCell_(sheet, row, 'Status', 'Photos uploading');
    setCell_(sheet, row, 'Updated', new Date());
  }
  return { ok: true, folderUrl: folder.getUrl() };
}

function uploadPhoto_(bookId, photo) {
  const photos = sheet_(PHOTOS);
  const existing = findRow_(photos, photo.id);
  if (existing) return { ok: true, fileUrl: getCell_(photos, existing, 'Link') };

  const books = sheet_(BOOKS);
  const bookRow = findRow_(books, bookId);
  if (!bookRow) throw new Error('This book is not in the Sheet yet. Upload it again from the app.');
  const folder = folderFromUrl_(getCell_(books, bookRow, 'Drive folder'));
  if (!folder) throw new Error("The book's Drive folder is missing. Upload the book again from the app.");

  const blob = Utilities.newBlob(Utilities.base64Decode(photo.dataBase64), photo.mimeType || 'image/jpeg', photo.fileName);
  const file = folder.createFile(blob);
  // Thumbnails in the Sheet, OLX and WordPress all need a link that works without signing in.
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  const thumbnail = thumbnailFormula_(file.getId());

  photos.appendRow(rowValues_(photos, {
    'Photo ID': photo.id,
    'Book ID': bookId,
    'Book name': getCell_(books, bookRow, 'Book name'),
    'File name': photo.fileName,
    'Type of page': photo.pageType,
    Thumbnail: thumbnail,
    Link: file.getUrl(),
    'Taken at': new Date(photo.takenAt),
  }));
  photos.setRowHeight(photos.getLastRow(), THUMBNAIL_ROW_HEIGHT);

  const coverCell = books.getRange(bookRow, column_(books, 'Cover thumbnail'));
  if (photo.pageType === 'cover' && !coverCell.getFormula()) coverCell.setFormula(thumbnail);
  const links = String(getCell_(books, bookRow, 'Photo links') || '');
  setCell_(books, bookRow, 'Photo links', links ? `${links}\n${file.getUrl()}` : file.getUrl());
  setCell_(books, bookRow, 'Updated', new Date());
  return { ok: true, fileUrl: file.getUrl() };
}

function finishBook_(bookId) {
  const books = sheet_(BOOKS);
  const row = findRow_(books, bookId);
  if (!row) throw new Error('This book is not in the Sheet yet. Upload it again from the app.');
  setCell_(books, row, 'Status', 'Photos uploaded');
  setCell_(books, row, 'Updated', new Date());
  return { ok: true };
}

/** Every book as {header: value as displayed}, optionally only those with the given Status. */
function listBooks_(status) {
  const sheet = sheet_(BOOKS);
  if (sheet.getLastRow() < 2) return { ok: true, books: [] };
  const headers = headers_(sheet);
  const rows = sheet.getRange(2, 1, sheet.getLastRow() - 1, headers.length).getDisplayValues();
  const books = rows
    .map((row) => Object.fromEntries(headers.map((header, i) => [header, row[i]])))
    .filter((book) => book['Book ID'] && (!status || book.Status === status));
  return { ok: true, books };
}

/** Writes the given {header: value} fields for one book. */
function updateBook_(bookId, fields) {
  const sheet = sheet_(BOOKS);
  const row = findRow_(sheet, bookId);
  if (!row) throw new Error(`Book ${bookId} is not in the Sheet.`);
  Object.keys(fields || {}).forEach((header) => {
    if (header === 'Book ID') throw new Error('The Book ID column cannot be changed.');
    setCell_(sheet, row, header, fields[header]);
  });
  setCell_(sheet, row, 'Updated', new Date());
  return { ok: true };
}

// ---- helpers ----

function ensureSheet_(spreadsheet, name, headers, thumbnailHeader) {
  const sheet = spreadsheet.getSheetByName(name) || spreadsheet.insertSheet(name);
  if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
    sheet.setFrozenRows(1);
    sheet.setColumnWidth(headers.indexOf(thumbnailHeader) + 1, 120);
    TEXT_COLUMNS.filter((header) => headers.includes(header)).forEach((header) => {
      sheet.getRange(2, headers.indexOf(header) + 1, sheet.getMaxRows() - 1, 1).setNumberFormat('@');
    });
  }
  return sheet;
}

function sheet_(name) {
  const sheet = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sheet) throw new Error(`The "${name}" tab is missing. Run Estante > Set up this sheet.`);
  return sheet;
}

const headerCache_ = {};

function headers_(sheet) {
  const name = sheet.getName();
  if (!headerCache_[name]) {
    headerCache_[name] = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0].map(String);
  }
  return headerCache_[name];
}

function column_(sheet, header) {
  const index = headers_(sheet).indexOf(header);
  if (index < 0) throw new Error(`The column "${header}" is missing from the ${sheet.getName()} tab.`);
  return index + 1;
}

function getCell_(sheet, row, header) {
  return sheet.getRange(row, column_(sheet, header)).getValue();
}

function setCell_(sheet, row, header, value) {
  const range = sheet.getRange(row, column_(sheet, header));
  if (TEXT_COLUMNS.includes(header)) range.setNumberFormat('@');
  range.setValue(value);
}

function rowValues_(sheet, fields) {
  return headers_(sheet).map((header) => (Object.prototype.hasOwnProperty.call(fields, header) ? fields[header] : ''));
}

function findRow_(sheet, id) {
  if (!id || sheet.getLastRow() < 2) return 0;
  const match = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1)
    .createTextFinder(String(id))
    .matchEntireCell(true)
    .findNext();
  return match ? match.getRow() : 0;
}

function rootFolder_() {
  const props = PropertiesService.getScriptProperties();
  const id = props.getProperty('ROOT_FOLDER_ID');
  if (id) {
    try {
      const folder = DriveApp.getFolderById(id);
      if (!folder.isTrashed()) return folder;
    } catch (err) {
      // Deleted or inaccessible: fall through and create a new one.
    }
  }
  const folder = DriveApp.createFolder('Estante photos');
  props.setProperty('ROOT_FOLDER_ID', folder.getId());
  return folder;
}

function folderFromUrl_(url) {
  const match = /\/folders\/([-\w]+)/.exec(String(url || ''));
  if (!match) return null;
  try {
    const folder = DriveApp.getFolderById(match[1]);
    return folder.isTrashed() ? null : folder;
  } catch (err) {
    return null;
  }
}

function folderName_(book) {
  return `${book.title} (${String(book.id).slice(0, 8)})`;
}

function thumbnailFormula_(fileId) {
  return `=IMAGE("https://drive.google.com/thumbnail?id=${fileId}&sz=w400")`;
}

function withLock_(work) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    return work();
  } finally {
    lock.releaseLock();
  }
}

function reply_(body) {
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

function notify_(message) {
  try {
    SpreadsheetApp.getUi().alert(message);
  } catch (err) {
    console.log(message);
  }
}
