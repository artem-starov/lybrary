'use strict';
/* НМТ Навчання — версія для комп'ютера. Той самий зміст і логіка, що й в Android-додатку. */

const API = window.api;
const view = document.getElementById('view');
const backBtn = document.getElementById('back');
const banner = document.getElementById('banner');

const ERA = ['#2DD4BF', '#FFC94D', '#FB923C', '#F472B6', '#A78BFA', '#60A5FA', '#34D399'];
const C_OK = '#2DD4A0';
const C_BAD = '#FF6B6B';
const C_WARN = '#FFA94D';
const C_AC = '#FFC94D';
const VOW = 'аеєиіїоуюя';
const LOCK_RX = /усі|всі|жодн|обидв|вище|правильн/i;

let DATA = [];
let UKR = null;
let backFn = null;
let keyFn = null;

/* ───────── допоміжні ───────── */

function el(tag, props, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'style') e.style.cssText = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else e.setAttribute(k, v);
  }
  for (const c of kids.flat()) {
    if (c === null || c === undefined || c === false) continue;
    e.append(c.nodeType ? c : document.createTextNode(String(c)));
  }
  return e;
}

const LS = {
  get(k, d) {
    try {
      const v = localStorage.getItem('nmt_' + k);
      return v === null ? d : JSON.parse(v);
    } catch (e) {
      return d;
    }
  },
  set(k, v) {
    try {
      localStorage.setItem('nmt_' + k, JSON.stringify(v));
    } catch (e) {}
  },
};

function shuffle(a) {
  const r = a.slice();
  for (let i = r.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [r[i], r[j]] = [r[j], r[i]];
  }
  return r;
}
const sample = (a, n) => shuffle(a).slice(0, n);
const tint = (c, a) => c + a;
const dec = (b) => new TextDecoder().decode(b);

function hashStr(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return String(h);
}

/* ───────── статистика ───────── */

const qid = (q) => hashStr(q.q);
const stats = () => LS.get('st', {});
const st = (s, q) => s[qid(q)] || [0, 0, 0, 0];

function record(q, good) {
  const s = stats();
  const x = st(s, q);
  s[qid(q)] = [x[0] + 1, x[1] + (good ? 1 : 0), good ? x[2] + 1 : 0, Date.now()];
  LS.set('st', s);
}

function addHistory(title, ok, total) {
  const h = LS.get('hs', []);
  h.push([Date.now(), title, ok, total]);
  LS.set('hs', h.slice(-30));
}

function allQ() {
  const l = [];
  for (const g of DATA) for (const q of g.q) l.push({ n: g.n, q });
  return l;
}

function overall() {
  const s = stats();
  let seen = 0, mastered = 0, total = 0, answers = 0, right = 0;
  for (const { q } of allQ()) {
    const x = st(s, q);
    total++;
    answers += x[0];
    right += x[1];
    if (x[0] > 0) seen++;
    if (x[2] >= 3) mastered++;
  }
  return { seen, mastered, total, answers, right };
}

function dueIn(pool) {
  const s = stats();
  let n = 0;
  for (const q of pool) {
    const x = st(s, q);
    if (x[0] > 0 && x[2] < 3) n++;
  }
  return n;
}
const dueCount = () => dueIn(allQ().map((x) => x.q));

/* ───────── компоненти ───────── */

const h1 = (t) => el('h1', { class: 'h1' }, t);
const h2 = (t) => el('h2', { class: 'h2' }, t);
const muted = (t) => el('p', { class: 'muted' }, t);
const card = (...k) => el('div', { class: 'card' }, ...k);
const btn = (t, fn, primary = true) => el('button', { class: 'btn ' + (primary ? 'primary' : 'ghost'), onclick: fn }, t);
const badge = (icon, color) => el('span', { class: 'badge', style: 'background:' + tint(color, '33') }, icon);

function navRow(icon, color, title, sub, fn) {
  return el(
    'div',
    { class: 'card nav' + (fn ? '' : ' dim'), onclick: fn || null },
    badge(icon, color),
    el('div', { class: 'nav-body' }, el('div', { class: 'nav-title' }, title), el('div', { class: 'nav-sub' }, sub)),
    fn ? el('span', { class: 'chev' }, '›') : null
  );
}

function tile(icon, color, title, sub, fn) {
  return el('div', { class: 'tile', onclick: fn }, badge(icon, color), el('div', { class: 't' }, title), el('div', { class: 's' }, sub));
}

function bar(p, color) {
  const i = el('i', { style: 'width:0%;background:' + color });
  setTimeout(() => {
    i.style.width = Math.max(0, Math.min(1, p)) * 100 + '%';
  }, 30);
  return el('div', { class: 'bar' }, i);
}

function ring(p, color, size, stroke, mid) {
  const NS = 'http://www.w3.org/2000/svg';
  const r = (size - stroke) / 2;
  const len = 2 * Math.PI * r;
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('width', size);
  svg.setAttribute('height', size);
  const mk = (cls) => {
    const c = document.createElementNS(NS, 'circle');
    c.setAttribute('class', cls);
    c.setAttribute('cx', size / 2);
    c.setAttribute('cy', size / 2);
    c.setAttribute('r', r);
    c.setAttribute('stroke-width', stroke);
    return c;
  };
  const trk = mk('trk');
  const val = mk('val');
  val.setAttribute('stroke', color);
  val.style.strokeDasharray = String(len);
  val.style.strokeDashoffset = String(len);
  svg.append(trk, val);
  setTimeout(() => {
    val.style.strokeDashoffset = String(len * (1 - Math.max(0, Math.min(1, p))));
  }, 40);
  const m = el('div', { class: 'mid', style: 'font-size:' + Math.round(size / 5) + 'px' }, mid);
  const box = el('div', { class: 'ring', style: 'width:' + size + 'px;height:' + size + 'px' }, svg, m);
  box.mid = m;
  return box;
}

function show(b, ...nodes) {
  backFn = b;
  keyFn = null;
  view.replaceChildren(...nodes);
  backBtn.style.visibility = b ? 'visible' : 'hidden';
  window.scrollTo(0, 0);
  view.classList.remove('in');
  void view.offsetWidth;
  view.classList.add('in');
}

backBtn.addEventListener('click', () => {
  if (backFn) backFn();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && backFn) {
    backFn();
    return;
  }
  if (keyFn) keyFn(e);
});

const openUrl = (u) => API.open(u);

/* ───────── зображення (кеш + вшиті картинки) ───────── */

const wikiUrl = (q) =>
  'https://uk.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=' + encodeURIComponent(q) +
  '&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=500&format=json';
const commonsUrl = (q) =>
  'https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=5&gsrsearch=' +
  encodeURIComponent(q) + '&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=960&format=json';

async function getBytes(u) {
  try {
    const b = await API.http(u);
    return b || null;
  } catch (e) {
    return null;
  }
}
const blobUrl = (b) => URL.createObjectURL(new Blob([b]));

async function wikiImg(img, query, used, cap) {
  const b = await getBytes(wikiUrl(query));
  if (!b) return;
  try {
    const pages = JSON.parse(dec(b)).query.pages;
    const p = Object.values(pages)[0];
    const src = p && p.thumbnail && p.thumbnail.source;
    if (!src || used.has(src)) return;
    used.add(src);
    const ib = await getBytes(src);
    if (!ib) return;
    img.src = blobUrl(ib);
    img.style.display = 'block';
    if (cap) cap.style.display = 'block';
    img.onclick = () => openUrl('https://uk.wikipedia.org/wiki/' + encodeURIComponent(p.title.replace(/ /g, '_')));
  } catch (e) {}
}

async function mapImg(img, cap, query, used) {
  let done = false;
  try {
    const b = await getBytes(commonsUrl(query));
    if (b) {
      const pages = Object.values(JSON.parse(dec(b)).query.pages);
      pages.sort((a, c) => (a.index || 99) - (c.index || 99));
      for (const p of pages) {
        const ii = p.imageinfo && p.imageinfo[0];
        if (!ii || !String(ii.mime || '').startsWith('image/')) continue;
        const th = ii.thumburl || ii.url;
        if (!th || used.has(th)) continue;
        used.add(th);
        const ib = await getBytes(th);
        if (!ib) continue;
        img.src = blobUrl(ib);
        img.style.display = 'block';
        cap.style.display = 'block';
        const page = ii.descriptionurl;
        if (page) img.onclick = () => openUrl(page);
        done = true;
        break;
      }
    }
  } catch (e) {}
  if (!done) await wikiImg(img, query, used, cap);
}

async function portrait(img, name, cap) {
  const b = await getBytes(wikiUrl(name));
  if (!b) return;
  try {
    const pages = JSON.parse(dec(b)).query.pages;
    for (const p of Object.values(pages)) {
      if (p.thumbnail) {
        const ib = await getBytes(p.thumbnail.source);
        if (!ib) return;
        img.src = blobUrl(ib);
        img.style.display = 'block';
        cap.style.display = 'block';
        img.onclick = () => openUrl('https://uk.wikipedia.org/wiki/' + encodeURIComponent(p.title.replace(/ /g, '_')));
        break;
      }
    }
  } catch (e) {}
}

/* ───────── головний екран ───────── */

function home() {
  const o = overall();
  const due = dueCount();
  const frac = o.total ? o.mastered / o.total : 0;
  const hero = el(
    'div',
    { class: 'hero' },
    el(
      'div',
      { class: 'hero-top' },
      ring(frac, C_AC, 110, 10, Math.round(frac * 100) + '%'),
      el(
        'div',
        { class: 'hero-txt' },
        el('div', { class: 'muted-w' }, 'Засвоєно'),
        el('div', { class: 'big' }, o.mastered + ' з ' + o.total + ' питань'),
        el('div', { class: 'muted-w sm' }, o.seen === 0 ? 'Почни з діагностики — вона покаже, що ти вже знаєш.' : 'Питання вважається засвоєним після трьох правильних відповідей поспіль.')
      )
    ),
    btn(due > 0 ? 'Повторити (' + due + ')' : 'Пройти діагностику', () => (due > 0 ? review() : diag()))
  );
  show(
    null,
    h1('Навчання до НМТ'),
    muted('Конспект, повторення й тести — все в одному місці'),
    hero,
    h2('Предмети'),
    navRow('📜', ERA[1], 'Історія України', '5–11 класи: конспект, діячі, тести', hist),
    navRow('🇺🇦', ERA[0], 'Українська мова', 'правила, практика письма, тести, наголоси, фразеологізми', ukrHome),
    navRow('➗', '#98A2B3', 'Математика', 'скоро', null),
    navRow('🇬🇧', '#98A2B3', 'Англійська', 'скоро', null)
  );
}

function site() {
  return card(
    el('div', { class: 'muted sm' }, 'ℹ️ Тести в цьому додатку — лише для закріплення матеріалу. Для пробного проходження НМТ користуйся сайтом zno.osvita.ua:'),
    btn('Тренувальні варіанти НМТ', () => openUrl('https://zno.osvita.ua/ukraine-history/nmt.html')),
    btn('Завдання за темами', () => openUrl('https://zno.osvita.ua/ukraine-history/tema.html'), false)
  );
}

function siteU() {
  return card(
    el('div', { class: 'muted sm' }, 'ℹ️ Для пробного проходження НМТ з української мови користуйся сайтом zno.osvita.ua:'),
    btn('Тренувальні варіанти НМТ', () => openUrl('https://zno.osvita.ua/ukrmova/nmt.html')),
    btn('Завдання за темами', () => openUrl('https://zno.osvita.ua/ukrainian/tema.html'), false)
  );
}

/* ───────── Історія України ───────── */

function eraRow(i, first, last, s) {
  const g = DATA[i];
  const col = ERA[i % ERA.length];
  let mastered = 0;
  for (const q of g.q) if (st(s, q)[2] >= 3) mastered++;
  const c = card(
    el('div', { class: 'nav-title' }, g.t),
    el('div', { class: 'nav-sub' }, g.n + ' клас · засвоєно ' + mastered + ' з ' + g.q.length),
    bar(g.q.length ? mastered / g.q.length : 0, col)
  );
  c.classList.add('nav');
  c.style.display = 'block';
  c.addEventListener('click', () => grade(i));
  return el(
    'div',
    { class: 'era' + (first ? ' first' : '') + (last ? ' last' : '') },
    el('div', { class: 'rail' }, el('div', { class: 'node', style: 'background:' + col }, String(g.n))),
    c
  );
}

function hist() {
  const s = stats();
  const d = LS.get('res_d', -1);
  const dsub = d < 0 ? '30 питань з усіх епох' : '≈ ' + Math.round(100 + d * 100) + ' балів';
  const due = dueCount();
  show(
    home,
    h1('Історія України'),
    muted('Обери, з чого почати'),
    el(
      'div',
      { class: 'tiles' },
      tile('🎯', C_AC, 'Діагностика', dsub, diag),
      tile('🔁', C_OK, 'Повторення', due === 0 ? 'помилки повертаються частіше' : 'до повторення: ' + due, review),
      tile('📊', ERA[5], 'Статистика', 'прогрес і слабкі місця', statsScreen),
      tile('📌', ERA[3], 'Пам’ятки', 'ключові дати епох', memos)
    ),
    h2('Епохи'),
    ...DATA.map((_, i) => eraRow(i, i === 0, i === DATA.length - 1, s)),
    site()
  );
}

function memoCard(g) {
  const c = card(el('div', { class: 'nav-title' }, '📌 Пам’ятка'), el('ul', {}, ...g.memo.map((m) => el('li', {}, m))));
  c.classList.add('memo');
  return c;
}

function memos() {
  show(hist, h1('📌 Усі пам’ятки'), ...DATA.map((g) => {
    const c = memoCard(g);
    c.insertBefore(el('div', { class: 'muted sm' }, g.n + ' клас: ' + g.t), c.firstChild);
    return c;
  }));
}

function grade(i) {
  const g = DATA[i];
  const era = ERA[i % ERA.length];
  const used = new Set();
  const nodes = [];
  nodes.push(el('span', { class: 'chip', style: 'background:' + era }, g.n + ' клас'));
  nodes.push(h1(g.t));
  nodes.push(memoCard(g));
  const c = card();
  g.s.forEach((full, j) => {
    const img = el('img', { class: 'thumb', alt: '' });
    c.append(el('div', { class: 'srow' }, el('div', { class: 'tx' }, '• ' + full), img));
    const idx = full.indexOf('Тема в підручнику: ');
    if (idx >= 0) {
      const tp = full.slice(idx + 'Тема в підручнику: '.length).trim();
      if (tp) wikiImg(img, tp, used, null);
    }
    for (const m of g.maps || []) {
      if (m.length > 2 && m[2] === j) {
        const cap = el('div', { class: 'cap' }, '🗺 ' + m[0]);
        const mv = el('img', { class: 'mapimg', alt: '' });
        c.append(cap, mv);
        mapImg(mv, cap, m[1], used);
      }
    }
  });
  nodes.push(c);
  if (g.terms && g.terms.length) {
    nodes.push(h2('Поняття і терміни'));
    nodes.push(card(...g.terms.map((t) => el('div', { class: 'row-line' }, t[0] + ' — ' + t[1]))));
  }
  if (g.p && g.p.length) {
    nodes.push(h2('Діячі епохи'));
    for (const o of g.p) {
      const pim = el('img', { class: 'portrait', alt: '' });
      const pcap = el('div', { class: 'cap' }, 'Фото: Вікіпедія');
      nodes.push(card(pim, pcap, el('div', { class: 'nav-title' }, o.name), el('div', { class: 'nav-sub' }, o.years + ' · ' + o.role), el('p', {}, o.desc)));
      portrait(pim, o.name, pcap);
    }
  }
  nodes.push(btn('Здати тест', () => quiz(g.q, 'g' + g.n, g.n + ' клас', () => grade(i))));
  show(hist, ...nodes);
}

function diag() {
  const l = [];
  const rest = [];
  for (const g of DATA) {
    const s = shuffle(g.q);
    l.push(...s.slice(0, 4));
    rest.push(...s.slice(4));
  }
  l.push(...sample(rest, 2));
  quiz(shuffle(l), 'd', 'Діагностика', hist);
}

function review() {
  reviewOf(allQ().map((x) => x.q), hist);
}

function reviewOf(pool, ret) {
  const s = stats();
  let due = 0;
  const cand = pool.map((q) => {
    const x = st(s, q);
    if (x[0] > 0 && x[2] < 3) due++;
    const w = x[0] === 0 ? 1.5 : x[2] === 0 ? 10 : Math.max(0.5, 6 - x[2] * 2);
    return { q, w };
  });
  if (due === 0) {
    show(ret, h1('🔁 Повторення'), card(el('p', {}, 'Поки що нічого повторювати. Пройди тест — питання, на яких помилишся, потрапляють сюди й повертаються частіше.')), btn('Назад', ret));
    return;
  }
  const picked = [];
  const n = Math.min(15, cand.length);
  for (let i = 0; i < n; i++) {
    const total = cand.reduce((a, c) => a + c.w, 0);
    let r = Math.random() * total;
    let idx = cand.length - 1;
    for (let k = 0; k < cand.length; k++) {
      r -= cand[k].w;
      if (r <= 0) {
        idx = k;
        break;
      }
    }
    picked.push(cand.splice(idx, 1)[0].q);
  }
  quiz(picked, 'r', 'Повторення', ret);
}

function statsScreen() {
  const s = stats();
  const o = overall();
  const nodes = [h1('📊 Статистика')];
  const top = card();
  if (o.answers === 0) top.append(el('p', {}, 'Даних ще немає. Пройди будь-який тест — і тут з’явиться твій прогрес.'));
  else {
    top.append(
      el('div', { class: 'big' }, Math.round((o.right * 100) / o.answers) + '% правильних відповідей'),
      el('div', { class: 'muted sm' }, 'Усього відповідей: ' + o.answers + ' (правильно: ' + o.right + ')'),
      el('div', { class: 'muted sm' }, 'Питань пройдено: ' + o.seen + ' з ' + o.total),
      el('div', { class: 'muted sm' }, 'Засвоєно (3 правильні поспіль): ' + o.mastered)
    );
  }
  nodes.push(top, h2('За класами'));
  const gc = card();
  for (const g of DATA) {
    let a = 0, rr = 0;
    for (const q of g.q) {
      const x = st(s, q);
      a += x[0];
      rr += x[1];
    }
    const p = a === 0 ? 0 : rr / a;
    gc.append(el('div', {}, g.n + ' клас · ' + g.t + ' — ' + (a === 0 ? '—' : Math.round(p * 100) + '%')), bar(p, p < 0.5 ? C_BAD : p < 0.7 ? C_WARN : C_OK));
  }
  nodes.push(gc);
  const hard = [];
  for (const { q } of allQ()) {
    const x = st(s, q);
    if (x[0] - x[1] > 0) hard.push([q, x]);
  }
  hard.sort((a, b) => b[1][0] - b[1][1] - (a[1][0] - a[1][1]));
  if (hard.length) {
    nodes.push(h2('Найскладніші питання'));
    nodes.push(card(...hard.slice(0, 5).map(([q, x]) => el('div', { class: 'row-line' }, '• ' + q.q + '  (помилок: ' + (x[0] - x[1]) + ' з ' + x[0] + ')'))));
  }
  const hs = LS.get('hs', []);
  if (hs.length) {
    nodes.push(h2('Останні тести'));
    nodes.push(
      card(
        ...hs.slice(-8).reverse().map((r) => el('div', { class: 'row-line' }, new Date(r[0]).toLocaleString('uk-UA', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) + ' · ' + r[1] + ' — ' + r[2] + '/' + r[3]))
      )
    );
  }
  nodes.push(
    btn('Скинути статистику', () => {
      if (confirm('Скинути всю статистику й історію тестів?')) {
        for (const k of Object.keys(localStorage)) if (k.startsWith('nmt_')) localStorage.removeItem(k);
        statsScreen();
      }
    }, false),
    btn('Назад', hist)
  );
  show(hist, ...nodes);
}

/* ───────── Українська мова: дані ───────── */

const ukrSections = () => UKR.sections;

function typed(arr, t) {
  return (arr || []).map((o) => Object.assign({ t }, o));
}
function secQ(i) {
  const s = ukrSections()[i];
  return [...typed(s.q, 'c'), ...typed(s.m, 'm'), ...typed(s.w, 'w')];
}

function stressWords() {
  return UKR.stress.map((w) => {
    let idx = -1;
    for (let i = 0; i < w.length; i++) {
      if (w[i] !== w[i].toLowerCase()) {
        idx = i;
        break;
      }
    }
    return [w.toLowerCase(), idx, w];
  });
}

function stressQ(w, i) {
  return { t: 's', q: 'Постав наголос: ' + w, w, i, e: 'Правильно: ' + w.slice(0, i) + w[i].toUpperCase() + w.slice(i + 1) };
}

function phrQ(i) {
  const all = UKR.phr;
  const p = all[i];
  const others = shuffle(all.filter((_, k) => k !== i).map((x) => x[1])).slice(0, 3);
  const opts = shuffle([...others, p[1]]);
  return { q: 'Що означає фразеологізм «' + p[0] + '»?', o: opts, a: opts.indexOf(p[1]), e: '«' + p[0] + '» — ' + p[1] + '.' };
}

function phrMatch() {
  const sh = shuffle(UKR.phr);
  const pick = sh.slice(0, 4);
  const opts = shuffle([...pick.map((p) => p[1]), sh[4][1]]);
  return {
    t: 'm', nr: true,
    q: 'Установіть відповідність між фразеологізмом (1–4) та його значенням (А–Д).',
    l: pick.map((p) => p[0]), r: opts, a: pick.map((p) => opts.indexOf(p[1])), e: '',
  };
}

function practiceQs(ci) {
  const c = UKR.practice[ci];
  return c.items.map((it) => ({ t: 'w', pr: true, q: it[0], ans: it[1], e: it[2] || '', h: c.hint || '' }));
}

function ukrPool() {
  const l = [];
  ukrSections().forEach((_, i) => l.push(...secQ(i)));
  UKR.practice.forEach((_, i) => l.push(...practiceQs(i)));
  for (const [w, i] of stressWords()) l.push(stressQ(w, i));
  UKR.phr.forEach((_, i) => l.push(phrQ(i)));
  return l;
}

/* ───────── Українська мова: екрани ───────── */

function ukrHome() {
  const due = dueIn(ukrPool());
  const secs = ukrSections();
  show(
    home,
    h1('Українська мова'),
    muted('Правила, практика письма, тести як на НМТ, наголоси й фразеологізми'),
    el(
      'div',
      { class: 'tiles' },
      tile('🎯', C_AC, 'Змішаний тест', '20 завдань, як на НМТ', ukrMixed),
      tile('🔁', C_OK, 'Повторення', due === 0 ? 'помилки повертаються частіше' : 'до повторення: ' + due, () => reviewOf(ukrPool(), ukrHome)),
      tile('🔊', ERA[5], 'Наголоси', 'словник і тренажер', ukrStress),
      tile('💬', ERA[3], 'Фразеологізми', 'значення й тренажер', ukrPhr)
    ),
    navRow('✍️', C_AC, 'Практика письма', 'е/и, апостроф, відмінки, ступені — пиши й закріплюй', ukrPractice),
    h2('Розділи програми'),
    ...secs.map((sec, i) => {
      const v = LS.get('res_u_' + sec.id, -1);
      return navRow(sec.ic, ERA[i % ERA.length], sec.t, v < 0 ? 'правила, тест, вправи' : 'останній тест: ' + Math.round(v * 100) + '%', () => ukrSection(i));
    }),
    siteU()
  );
}

function ukrMixed() {
  const pool = ukrPool();
  const c = sample(pool.filter((q) => (q.t || 'c') === 'c'), 12);
  const sx = sample(pool.filter((q) => q.t === 's'), 3);
  const w = sample(pool.filter((q) => q.t === 'w' && !q.pr), 2);
  const m = sample(pool.filter((q) => q.t === 'm'), 3);
  quiz([...c, ...sx, ...w, ...m], 'u', 'Змішаний тест', ukrHome);
}

function ukrPractice() {
  const pr = UKR.practice;
  show(
    ukrHome,
    h1('✍️ Практика письма'),
    card(el('p', {}, 'Тут не тест, а навчання. Пиши відповідь сам: є підказка з правилом, а після помилки можна спробувати ще раз.')),
    btn('Змішана практика (15 завдань)', () => {
      const all = [];
      pr.forEach((_, i) => all.push(...practiceQs(i)));
      quiz(sample(all, 15), 'p', 'Практика письма', ukrPractice);
    }),
    h2('Теми'),
    ...pr.map((c, i) => {
      const v = LS.get('res_p_' + c.id, -1);
      const n = c.items.length;
      return navRow(c.ic, ERA[i % ERA.length], c.t, v < 0 ? n + ' завдань' : n + ' завдань · останній результат ' + Math.round(v * 100) + '%', () =>
        quiz(sample(practiceQs(i), 10), 'p_' + c.id, c.t, ukrPractice)
      );
    }),
    btn('Назад', ukrHome, false)
  );
}

function ukrSection(i) {
  const sec = ukrSections()[i];
  const qs = secQ(i);
  const tests = qs.filter((q) => q.t !== 'w');
  const writes = qs.filter((q) => q.t === 'w');
  const rules = card(el('ul', {}, ...sec.rules.map((r) => el('li', {}, r))));
  rules.classList.add('rules');
  show(
    ukrHome,
    el('span', { class: 'chip', style: 'background:' + ERA[i % ERA.length] }, 'Розділ ' + (i + 1)),
    h1(sec.t),
    memoCard(sec),
    h2('Правила'),
    rules,
    btn('Тест розділу (' + tests.length + ' завдань)', () => quiz(shuffle(tests), 'u_' + sec.id, sec.t, () => ukrSection(i))),
    btn('Вправи: впиши відповідь (' + writes.length + ')', () => quiz(shuffle(writes), 'uw', 'Вправи: ' + sec.t, () => ukrSection(i)), false),
    btn('Назад', ukrHome, false)
  );
}

function ukrStress() {
  const words = stressWords();
  const dc = card();
  const d = el('div', { class: 'words' });
  UKR.stress.forEach((w, j) => {
    let ui = -1;
    for (let k = 0; k < w.length; k++) {
      if (w[k] !== w[k].toLowerCase()) {
        ui = k;
        break;
      }
    }
    if (ui < 0) d.append(w);
    else d.append(w.slice(0, ui), el('b', {}, w[ui]), w.slice(ui + 1));
    if (j < UKR.stress.length - 1) d.append('   ·   ');
  });
  dc.append(d);
  show(
    ukrHome,
    h1('Наголоси'),
    card(el('p', {}, words.length + ' слів, у яких найчастіше помиляються. У тренажері натисни наголошену голосну.')),
    btn('Тренувати наголоси (15)', () => quiz(sample(words, 15).map((x) => stressQ(x[0], x[1])), 'us', 'Наголоси', ukrStress)),
    h2('Словник'),
    dc,
    el('div', { class: 'muted sm' }, 'Золота велика літера — наголошена голосна.'),
    btn('Назад', ukrHome, false)
  );
}

function ukrPhr() {
  const ph = UKR.phr;
  show(
    ukrHome,
    h1('Фразеологізми'),
    btn('Тренувати (10 завдань)', () => {
      const idx = sample(ph.map((_, i) => i), 8);
      quiz([...idx.map(phrQ), phrMatch(), phrMatch()], 'up', 'Фразеологізми', ukrPhr);
    }),
    h2('Словник'),
    ...ph.map((p) => card(el('div', { class: 'nav-title' }, p[0]), el('div', { class: 'nav-sub' }, p[1]))),
    btn('Назад', ukrHome, false)
  );
}

/* ───────── Тест ───────── */

const norm = (s) => s.trim().toLowerCase().replace(/[’ʼ`]/g, "'").replace(/\s+/g, ' ');

function quiz(qs, key, title, ret) {
  if (!qs.length) return;
  let k = 0;
  let ok = 0;
  const wrong = [];
  const isUkr = key.startsWith('u') || key.startsWith('p');

  function end() {
    const p = ok / qs.length;
    if (key !== 'r' && key !== 'w') LS.set('res_' + key, p);
    addHistory(title, ok, qs.length);
    const col = p >= 0.7 ? C_OK : p >= 0.5 ? C_WARN : C_BAD;
    const r = ring(p, col, 190, 14, '0 / ' + qs.length);
    r.mid.style.fontSize = '34px';
    let n = 0;
    const t = setInterval(() => {
      n++;
      r.mid.textContent = Math.min(n, ok) + ' / ' + qs.length;
      if (n >= ok) clearInterval(t);
    }, 900 / Math.max(ok, 1));
    const msg = p >= 0.9 ? 'Блискуче!' : p >= 0.7 ? 'Гарний результат' : p >= 0.5 ? 'Непогано, є що підтягнути' : 'Варто повторити правила';
    const noPts = key === 'r' || key === 'w' || isUkr;
    const pts = noPts ? '' : ' Орієнтовно ≈ ' + Math.round(100 + p * 100) + ' балів зі 200.';
    const nodes = [
      el('div', { class: 'muted' }, title),
      h1('Результат'),
      card(
        el('div', { class: 'center', style: 'display:flex;flex-direction:column;align-items:center;gap:8px' }, r, el('div', { class: 'big' }, msg), el('div', { class: 'muted' }, 'Правильно: ' + Math.round(p * 100) + '%.' + pts))
      ),
    ];
    if (wrong.length) nodes.push(btn('Розібрати помилки (' + wrong.length + ')', () => quiz(wrong.slice(), 'w', 'Помилки', ret)));
    nodes.push(btn('Готово', ret, wrong.length === 0));
    if (isUkr) nodes.push(siteU());
    else if (key !== 'r' && key !== 'w') nodes.push(site());
    show(ret, ...nodes);
    keyFn = (e) => {
      if (e.key === 'Enter') ret();
    };
  }

  function ask() {
    const q = qs[k];
    const t = q.t || 'c';
    const holder = el('div', {});
    let nextBtn = null;

    function after(good, extra) {
      if (good) ok++;
      else wrong.push(q);
      if (!q.nr) record(q, good);
      const ex = ((q.e || '') + (extra ? '\n' + extra : '')).trim();
      if (ex) {
        const ec = card(el('div', { class: 'expl' }, '💡 ' + ex));
        if (!good) holder.append(ec);
        else {
          const eb = btn('Чому так?', () => {
            eb.replaceWith(ec);
          }, false);
          holder.append(eb);
        }
      }
      nextBtn = btn(k + 1 < qs.length ? 'Далі (Enter)' : 'Результат (Enter)', () => {
        k++;
        if (k < qs.length) ask();
        else end();
      });
      holder.append(nextBtn);
      keyFn = (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          nextBtn.click();
        }
      };
    }

    const nodes = [
      el('div', { class: 'qhead' }, el('span', {}, title), el('b', {}, k + 1 + ' / ' + qs.length)),
      bar((k + 1) / qs.length, C_AC),
      card(el('div', { class: 'qtext' }, q.q)),
    ];
    if (q.h) nodes.push(el('div', { class: 'hint' }, '💡 Підказка: ' + q.h));
    nodes.push(holder);
    show(ret, ...nodes);
    if (t === 'm') renderMatch(q, holder, after);
    else if (t === 'w') renderWrite(q, holder, after);
    else if (t === 's') renderStress(q, holder, after);
    else renderMcq(q, holder, after);
  }

  ask();
}

function renderMcq(q, holder, after) {
  const o = q.o;
  const order = o.map((_, i) => i);
  const locked = o.some((x) => LOCK_RX.test(x));
  const ord = locked ? order : shuffle(order);
  const a = ord.indexOf(q.a);
  const letters = 'АБВГДЕ';
  let answered = false;
  const opts = ord.map((oi, j) =>
    el(
      'button',
      {
        class: 'opt',
        onclick: () => {
          if (answered) return;
          answered = true;
          const good = j === a;
          opts.forEach((b, m) => {
            b.disabled = true;
            if (m === a) {
              b.classList.add('ok');
              b.firstChild.textContent = '✓';
            } else if (m === j) {
              b.classList.add('bad');
              b.firstChild.textContent = '✕';
            } else b.classList.add('faded');
          });
          after(good, '');
        },
      },
      el('span', { class: 'lt' }, letters[j]),
      el('span', {}, o[oi])
    )
  );
  opts.forEach((b) => holder.append(b));
  keyFn = (e) => {
    const n = parseInt(e.key, 10);
    if (n >= 1 && n <= opts.length) opts[n - 1].click();
  };
}

function renderMatch(q, holder, after) {
  const letters = 'АБВГД';
  const oc = card(...q.r.map((r, j) => el('div', { class: 'row-line' }, letters[j] + '   ' + r)));
  holder.append(oc);
  const sel = q.l.map(() => -1);
  const chips = [];
  let done = false;
  q.l.forEach((txt, i) => {
    const mine = q.r.map((_, j) =>
      el('button', {
        class: 'lt-btn',
        onclick: () => {
          if (done) return;
          sel[i] = j;
          mine.forEach((c, m) => c.classList.toggle('sel', m === j));
        },
      }, letters[j])
    );
    chips.push(mine);
    holder.append(card(el('div', {}, (i + 1) + '. ' + txt), el('div', { class: 'letters' }, ...mine)));
  });
  const check = btn('Перевірити', () => {
    if (done) return;
    if (sel.some((x) => x < 0)) {
      alert('Обери відповідь у кожному рядку');
      return;
    }
    done = true;
    let good = true;
    const keyTxt = [];
    sel.forEach((s, i) => {
      const c = q.a[i];
      chips[i].forEach((b) => b.classList.remove('sel'));
      if (s === c) chips[i][c].classList.add('ok');
      else {
        good = false;
        chips[i][s].classList.add('bad');
        chips[i][c].classList.add('ok');
      }
      keyTxt.push((i + 1) + '–' + letters[c]);
    });
    check.remove();
    after(good, good ? '' : 'Правильно: ' + keyTxt.join('   '));
  });
  holder.append(check);
  keyFn = (e) => {
    if (e.key === 'Enter') check.click();
  };
}

function renderWrite(q, holder, after) {
  const inp = el('input', { class: 'answer', type: 'text', placeholder: 'Твоя відповідь', autocomplete: 'off', spellcheck: 'false' });
  const retry = el('div', { class: 'retry' }, '');
  retry.style.display = 'none';
  let done = false;
  let tries = 0;
  const check = btn('Перевірити', () => {
    if (done) return;
    const u = norm(inp.value || '');
    if (!u) {
      inp.focus();
      return;
    }
    tries++;
    const good = q.ans.some((a) => norm(a) === u);
    if (!good && q.pr && tries < 2) {
      inp.classList.add('bad');
      retry.textContent = 'Майже! Спробуй ще раз — подивись на підказку.';
      retry.style.display = 'block';
      inp.focus();
      return;
    }
    done = true;
    inp.disabled = true;
    inp.classList.remove('bad');
    inp.classList.add(good ? 'ok' : 'bad');
    retry.style.display = 'none';
    check.remove();
    after(good && tries === 1, good ? '' : 'Правильна відповідь: ' + q.ans[0]);
  });
  inp.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.stopPropagation();
      if (!done) check.click();
    }
  });
  holder.append(inp, retry, check);
  setTimeout(() => inp.focus(), 50);
}

function renderStress(q, holder, after) {
  const w = q.w;
  let done = false;
  const word = el('div', { class: 'word' });
  for (let i = 0; i < w.length; i++) {
    if (VOW.includes(w[i])) {
      const v = el('span', {
        class: 'vow',
        onclick: () => {
          if (done) return;
          done = true;
          const good = i === q.i;
          word.className = 'word ' + (good ? 'ok' : 'bad');
          word.textContent = w.slice(0, q.i + 1) + '\u0301' + w.slice(q.i + 1);
          after(good, '');
        },
      }, w[i]);
      word.append(v);
    } else word.append(w[i]);
  }
  holder.append(card(el('div', { class: 'center muted sm' }, 'Натисни наголошену голосну'), word));
}

/* ───────── запуск ───────── */

async function init() {
  try {
    DATA = JSON.parse(await API.data('data.json'));
    UKR = JSON.parse(await API.data('data_ukr.json'));
  } catch (e) {
    view.textContent = 'Не вдалося завантажити дані: ' + e;
    return;
  }
  home();
  try {
    const up = await API.checkUpdate();
    if (up && up.url) {
      banner.hidden = false;
      banner.append('Доступна нова версія. ', el('a', { onclick: () => openUrl(up.url) }, 'Завантажити'));
    }
  } catch (e) {}
}

init();
