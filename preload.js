'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  http: (u) => ipcRenderer.invoke('http', u),
  data: (n) => ipcRenderer.invoke('data', n),
  open: (u) => ipcRenderer.invoke('open', u),
  checkUpdate: () => ipcRenderer.invoke('update'),
});
