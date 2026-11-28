'use strict';
const { app, BrowserWindow, ipcMain, shell, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

let UA = 'NMTStudyApp/1.1 (student study app) Electron';

function readText(name) {
  try {
    return fs.readFileSync(path.join(__dirname, name), 'utf8').trim();
  } catch (e) {
    return '';
  }
}

const REPO = readText('repo.txt');
if (REPO.includes('/')) UA = 'NMTStudyApp/1.1 (https://github.com/' + REPO + '; student study app) Electron';

// Ім'я файлу = MD5 від URL (так само, як у Android-версії та в fetch_images.py)
const nameOf = (u) => crypto.createHash('md5').update(u, 'utf8').digest('hex');

function cacheDir() {
  const d = path.join(app.getPath('userData'), 'cache');
  fs.mkdirSync(d, { recursive: true });
  return d;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Не більше 4 одночасних запитів у мережу
let active = 0;
const waiting = [];
async function slot() {
  if (active >= 4) await new Promise((r) => waiting.push(r));
  active++;
}
function release() {
  active--;
  const n = waiting.shift();
  if (n) n();
}

async function getBytes(u) {
  const key = nameOf(u);
  const cp = path.join(cacheDir(), key);
  try {
    if (fs.existsSync(cp) && fs.statSync(cp).size > 0) return fs.readFileSync(cp);
  } catch (e) {}
  // Картинки, вшиті в програму під час збірки
  try {
    const bp = path.join(__dirname, 'img', key);
    if (fs.existsSync(bp) && fs.statSync(bp).size > 0) return fs.readFileSync(bp);
  } catch (e) {}
  await slot();
  try {
    for (let t = 0; t < 3; t++) {
      try {
        const r = await fetch(u, { headers: { 'User-Agent': UA, Accept: '*/*' } });
        if (r.status === 200) {
          const buf = Buffer.from(await r.arrayBuffer());
          if (buf.length > 0) {
            try {
              fs.writeFileSync(cp + '.tmp', buf);
              fs.renameSync(cp + '.tmp', cp);
            } catch (e) {}
            return buf;
          }
        } else if (r.status !== 429 && r.status < 500) {
          return null;
        }
      } catch (e) {}
      await sleep(1500 * (t + 1));
    }
  } finally {
    release();
  }
  return null;
}

ipcMain.handle('http', async (_e, u) => {
  if (typeof u !== 'string' || !/^https:\/\//.test(u)) return null;
  return await getBytes(u);
});

const DATA_FILES = new Set(['data.json', 'data_ukr.json']);
ipcMain.handle('data', (_e, name) => {
  if (!DATA_FILES.has(name)) return '';
  return fs.readFileSync(path.join(__dirname, name), 'utf8');
});

ipcMain.handle('open', (_e, u) => {
  if (typeof u === 'string' && /^https:\/\//.test(u)) shell.openExternal(u);
});

// Перевірка нової версії: файл version.json у GitHub Releases
ipcMain.handle('update', async () => {
  try {
    const local = parseInt(readText('build.txt'), 10);
    if (!REPO.includes('/') || isNaN(local)) return null;
    const r = await fetch('https://github.com/' + REPO + '/releases/download/desktop-latest/version.json', {
      headers: { 'User-Agent': UA, 'Cache-Control': 'no-cache' },
    });
    if (r.status !== 200) return null;
    const j = await r.json();
    if (typeof j.build === 'number' && j.build > local) {
      return { url: 'https://github.com/' + REPO + '/releases/download/desktop-latest/NMT-Desktop.exe' };
    }
  } catch (e) {}
  return null;
});

function createWindow() {
  const w = new BrowserWindow({
    width: 1100,
    height: 820,
    minWidth: 720,
    minHeight: 600,
    backgroundColor: '#07122B',
    title: 'НМТ Навчання',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  Menu.setApplicationMenu(null);
  w.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  w.webContents.on('will-navigate', (e) => e.preventDefault());
  w.loadFile('index.html');
}

app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
