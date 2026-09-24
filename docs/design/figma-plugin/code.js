// Nungil UI builder (owner I).
// Builds the team Figma file described in docs/design/ui-guide.md: colour tokens as variables with
// Light, Dark and High contrast modes, the text styles, six screens in each theme, and the components.
// Run it once in a NEW, empty Figma design file (Figma desktop: Plugins > Development > Nungil UI builder).
// Plain ES2017 on purpose: the plugin sandbox does not need a build step.

var THEMES = ['Light', 'Dark', 'High contrast'];

// The same values as app/src/main/res-i/values/colors.xml, values-night/colors.xml and the ng_hc_* set.
var TOKENS = {
  ngBackground: ['#F4F6F8', '#0F1115', '#000000'],
  ngCard: ['#FFFFFF', '#1C1F26', '#000000'],
  ngText: ['#191F28', '#F2F4F6', '#FFFFFF'],
  ngTextSub: ['#4E5968', '#B0B8C1', '#FFFFFF'],
  ngPrimary: ['#1B64DA', '#2F6FDB', '#FFD400'],
  ngOnPrimary: ['#FFFFFF', '#FFFFFF', '#000000'],
  ngPrimarySoft: ['#E8F1FF', '#1A2A45', '#1F1A00'],
  ngAccentText: ['#1B64DA', '#7EB0FF', '#FFD400'],
  ngDanger: ['#C62828', '#C62828', '#FF6B6B'],
  ngOnDanger: ['#FFFFFF', '#FFFFFF', '#000000'],
  ngSuccess: ['#0B7A4B', '#2EB67D', '#3DFF8B'],
  ngLine: ['#E5E8EB', '#2C313A', '#FFFFFF'],
  ngFocus: ['#1B64DA', '#FFD400', '#FFD400']
};

// Card and tonal-button outline: only the high-contrast theme draws one.
var STROKE = [0, 0, 2];

// name, size, line height, weight, letter spacing (%)
var TYPE = [
  ['Display', 30, 40, 'bold', -2],
  ['Title', 24, 32, 'bold', -2],
  ['Headline', 20, 28, 'semibold', 0],
  ['Body', 18, 27, 'regular', 0],
  ['BodyStrong', 18, 27, 'bold', 0],
  ['Label', 18, 24, 'semibold', 0],
  ['Caption', 15, 21, 'regular', 0]
];

var W = 360;
var H = 800;
var GUTTER = 20;
var GAP = 12;
var CAMERA_BG = { r: 0.169, g: 0.184, b: 0.212 };

var FONT = {
  regular: { family: 'Pretendard', style: 'Regular' },
  semibold: { family: 'Pretendard', style: 'SemiBold' },
  bold: { family: 'Pretendard', style: 'Bold' }
};

var collection = null;
var modeIds = {};
var perTheme = false; // true when the Figma plan allows only one mode per collection
var vars = {}; // vars[theme][token] = Variable
var strokeVar = null;
var styles = {}; // styles[name] = TextStyle
var CUR = 'Light'; // theme being drawn

function hex(h) {
  var n = parseInt(h.slice(1), 16);
  return { r: ((n >> 16) & 255) / 255, g: ((n >> 8) & 255) / 255, b: (n & 255) / 255 };
}

function themeIndex() {
  return THEMES.indexOf(CUR);
}

async function loadFonts() {
  await figma.loadFontAsync({ family: 'Inter', style: 'Regular' });
  try {
    await figma.loadFontAsync(FONT.regular);
    await figma.loadFontAsync(FONT.semibold);
    await figma.loadFontAsync(FONT.bold);
  } catch (e) {
    FONT = {
      regular: { family: 'Inter', style: 'Regular' },
      semibold: { family: 'Inter', style: 'Semi Bold' },
      bold: { family: 'Inter', style: 'Bold' }
    };
    await figma.loadFontAsync(FONT.semibold);
    await figma.loadFontAsync(FONT.bold);
    figma.notify('Pretendard is not installed, so Inter is used. Install Pretendard and run again for the real look.', { timeout: 8000 });
  }
}

function makeVariables() {
  collection = figma.variables.createVariableCollection('Nungil tokens');
  modeIds.Light = collection.modes[0].modeId;
  collection.renameMode(modeIds.Light, 'Light');
  try {
    var dark = collection.addMode('Dark');
    try {
      modeIds['High contrast'] = collection.addMode('High contrast');
      modeIds.Dark = dark;
    } catch (inner) {
      collection.removeMode(dark);
      perTheme = true;
    }
  } catch (e) {
    perTheme = true;
  }
  THEMES.forEach(function (t) { vars[t] = {}; });
  Object.keys(TOKENS).forEach(function (name) {
    var values = TOKENS[name];
    if (!perTheme) {
      var v = figma.variables.createVariable(name, collection, 'COLOR');
      THEMES.forEach(function (t, i) {
        v.setValueForMode(modeIds[t], hex(values[i]));
        vars[t][name] = v;
      });
    } else {
      THEMES.forEach(function (t, i) {
        var tv = figma.variables.createVariable(t + '/' + name, collection, 'COLOR');
        tv.setValueForMode(modeIds.Light, hex(values[i]));
        vars[t][name] = tv;
      });
    }
  });
  if (!perTheme) {
    strokeVar = figma.variables.createVariable('ngCardStrokeWidth', collection, 'FLOAT');
    THEMES.forEach(function (t, i) { strokeVar.setValueForMode(modeIds[t], STROKE[i]); });
  }
  if (perTheme) {
    figma.notify('Your Figma plan allows one variable mode: tokens are named Light/…, Dark/… and High contrast/… instead.', { timeout: 8000 });
  }
}

function makeTextStyles() {
  TYPE.forEach(function (t) {
    var s = figma.createTextStyle();
    s.name = t[0];
    s.fontName = FONT[t[3]];
    s.fontSize = t[1];
    s.lineHeight = { value: t[2], unit: 'PIXELS' };
    s.letterSpacing = { value: t[4], unit: 'PERCENT' };
    s.description = t[1] + '/' + t[2] + ' ' + t[3];
    styles[t[0]] = s;
  });
}

// ---- Paint helpers ------------------------------------------------------------------------------

function paint(token) {
  var p = { type: 'SOLID', color: hex(TOKENS[token][themeIndex()]) };
  return figma.variables.setBoundVariableForPaint(p, 'color', vars[CUR][token]);
}

function fill(token) {
  return [paint(token)];
}

/** High-contrast outline: bound to ngCardStrokeWidth when modes exist, else the theme's own width. */
function outline(node) {
  node.strokes = fill('ngLine');
  node.strokeAlign = 'INSIDE';
  if (!perTheme && strokeVar) {
    try {
      node.setBoundVariable('strokeWeight', strokeVar);
      return;
    } catch (e) {
      // Older Figma versions cannot bind stroke weight; fall through to a fixed width.
    }
  }
  node.strokeWeight = STROKE[themeIndex()];
}

/** Top-level frames show their theme: one explicit variable mode per screen. */
function applyTheme(frame) {
  if (!perTheme) frame.setExplicitVariableModeForCollection(collection, modeIds[CUR]);
}

// ---- Node helpers -------------------------------------------------------------------------------

function box(name, dir, opt) {
  opt = opt || {};
  var f = figma.createFrame();
  f.name = name;
  f.layoutMode = dir;
  f.primaryAxisSizingMode = 'AUTO';
  f.counterAxisSizingMode = 'AUTO';
  f.itemSpacing = opt.gap || 0;
  f.paddingLeft = opt.px !== undefined ? opt.px : (opt.p || 0);
  f.paddingRight = opt.px !== undefined ? opt.px : (opt.p || 0);
  f.paddingTop = opt.py !== undefined ? opt.py : (opt.p || 0);
  f.paddingBottom = opt.py !== undefined ? opt.py : (opt.p || 0);
  f.fills = opt.fill ? fill(opt.fill) : [];
  if (opt.radius) f.cornerRadius = opt.radius;
  if (opt.center) {
    f.primaryAxisAlignItems = 'CENTER';
    f.counterAxisAlignItems = 'CENTER';
  } else if (opt.middle) {
    f.counterAxisAlignItems = 'CENTER';
  }
  return f;
}

/** Appends [child]; 'fill' stretches it along the parent's width (or grows it in a row). */
function add(parent, child, sizing) {
  parent.appendChild(child);
  if (sizing === 'fill') {
    child.layoutSizingHorizontal = 'FILL';
    if (child.type === 'TEXT') child.textAutoResize = 'HEIGHT';
  } else if (sizing === 'grow') {
    child.layoutSizingVertical = 'FILL';
  }
  return child;
}

async function text(content, style, token, opt) {
  opt = opt || {};
  var t = figma.createText();
  t.name = opt.name || content.slice(0, 24);
  t.fontName = FONT.regular;
  t.characters = content;
  await t.setTextStyleIdAsync(styles[style].id);
  t.fills = fill(token);
  if (opt.align) t.textAlignHorizontal = opt.align;
  return t;
}

function spacer(h) {
  var s = figma.createFrame();
  s.name = 'space';
  s.resize(1, h);
  s.fills = [];
  return s;
}

// ---- Components ---------------------------------------------------------------------------------

async function button(kind, label, icon) {
  var bg = { primary: 'ngPrimary', tonal: 'ngPrimarySoft', danger: 'ngDanger' }[kind];
  var fg = { primary: 'ngOnPrimary', tonal: 'ngText', danger: 'ngOnDanger' }[kind];
  var b = box('Button / ' + kind, 'HORIZONTAL', { px: 24, py: 24, gap: 10, fill: bg, radius: 20, center: true });
  if (kind === 'tonal') outline(b);
  if (icon) b.appendChild(await text(icon, 'Label', fg, { name: 'icon' }));
  b.appendChild(await text(label, 'Label', fg, { name: 'label' }));
  return b;
}

async function iconBadge(glyph, strong) {
  var c = box('icon', 'HORIZONTAL', { fill: strong ? 'ngPrimary' : 'ngPrimarySoft', radius: 20, center: true });
  c.primaryAxisSizingMode = 'FIXED';
  c.counterAxisSizingMode = 'FIXED';
  c.resize(40, 40);
  c.appendChild(await text(glyph, 'Headline', strong ? 'ngOnPrimary' : 'ngPrimary', { name: 'glyph' }));
  return c;
}

async function bigCard(glyph, title, subtitle, primary) {
  var card = box(primary ? 'BigCard / primary' : 'BigCard', 'HORIZONTAL', {
    p: GUTTER, gap: 16, fill: primary ? 'ngPrimarySoft' : 'ngCard', radius: 24, middle: true
  });
  outline(card);
  try { card.minHeight = 88; } catch (e) { /* minHeight needs a recent Figma */ }
  card.appendChild(await iconBadge(glyph, primary));
  var col = box('text', 'VERTICAL', { gap: 2 });
  add(card, col, 'fill');
  add(col, await text(title, 'Headline', 'ngText', { name: 'title' }), 'fill');
  if (subtitle) add(col, await text(subtitle, 'Body', 'ngTextSub', { name: 'subtitle' }), 'fill');
  card.appendChild(await text('›', 'Title', 'ngTextSub', { name: 'chevron' }));
  return card;
}

async function segmented(options, selected) {
  var s = box('Segmented', 'HORIZONTAL', { p: 4, gap: 4, fill: 'ngCard', radius: 16 });
  outline(s);
  for (var i = 0; i < options.length; i++) {
    var on = i === selected;
    var seg = box(on ? 'segment / selected' : 'segment', 'HORIZONTAL', {
      py: 12, px: 8, fill: on ? 'ngPrimary' : null, radius: 12, center: true
    });
    add(s, seg, 'fill');
    seg.appendChild(await text(options[i], 'Label', on ? 'ngOnPrimary' : 'ngText'));
  }
  return s;
}

async function switchRow(label, on) {
  var row = box('SwitchRow', 'HORIZONTAL', { py: 16, gap: 12, middle: true });
  add(row, await text(label, 'Body', 'ngText', { name: 'label' }), 'fill');
  var track = box('switch', 'HORIZONTAL', { p: 4, fill: on ? 'ngPrimary' : 'ngLine', radius: 16, middle: true });
  track.primaryAxisSizingMode = 'FIXED';
  track.counterAxisSizingMode = 'FIXED';
  track.resize(56, 32);
  track.primaryAxisAlignItems = on ? 'MAX' : 'MIN';
  var knob = figma.createEllipse();
  knob.name = 'knob';
  knob.resize(24, 24);
  knob.fills = fill(on ? 'ngOnPrimary' : 'ngCard');
  track.appendChild(knob);
  row.appendChild(track);
  return row;
}

async function pill(label, bg, fg) {
  var p = box('Pill', 'HORIZONTAL', { px: 12, py: 4, fill: bg, radius: 12, center: true });
  p.appendChild(await text(label, 'Label', fg));
  return p;
}

async function captionBar(sentence) {
  var c = box('Caption bar', 'HORIZONTAL', { px: GUTTER, py: 12, fill: 'ngCard' });
  add(c, await text(sentence, 'BodyStrong', 'ngText', { name: 'caption' }), 'fill');
  return c;
}

async function ring(percent, size) {
  var r = figma.createFrame();
  r.name = 'Coverage ring';
  r.resize(size, size);
  r.fills = [];
  var seen = Math.round(percent * 36 / 100);
  var deg = Math.PI / 180;
  for (var i = 0; i < 36; i++) {
    var e = figma.createEllipse();
    e.name = 'bin ' + i;
    e.resize(size, size);
    var start = -90 * deg + (i * 10 + 1) * deg;
    e.arcData = { startingAngle: start, endingAngle: start + 8 * deg, innerRadius: 0.82 };
    e.fills = fill(i < seen ? 'ngPrimary' : 'ngLine');
    r.appendChild(e);
  }
  var t = await text(percent + '%', 'Title', 'ngText', { name: 'percent', align: 'CENTER' });
  r.appendChild(t);
  t.x = (size - t.width) / 2;
  t.y = (size - t.height) / 2;
  return r;
}

function camera(height) {
  var c = figma.createFrame();
  c.name = 'Camera preview';
  c.resize(W - 2 * GUTTER, height);
  c.cornerRadius = 24;
  c.clipsContent = true;
  c.fills = [{ type: 'SOLID', color: CAMERA_BG }];
  return c;
}

async function markBox(parent, x, y, w, h, label, token, weight) {
  var r = figma.createRectangle();
  r.name = 'box';
  r.resize(w, h);
  r.cornerRadius = 12;
  r.fills = [];
  r.strokes = fill(token);
  r.strokeWeight = weight;
  parent.appendChild(r);
  r.x = x;
  r.y = y;
  var p = await pill(label, token, token === 'ngFocus' && CUR !== 'Light' ? 'ngBackground' : 'ngOnPrimary');
  parent.appendChild(p);
  p.x = x;
  p.y = y - 36;
}

// ---- Screens ------------------------------------------------------------------------------------

async function screen(name, title, caption) {
  var s = figma.createFrame();
  s.name = name;
  s.layoutMode = 'VERTICAL';
  s.primaryAxisSizingMode = 'FIXED';
  s.counterAxisSizingMode = 'FIXED';
  s.resize(W, H);
  s.fills = fill('ngBackground');
  s.clipsContent = true;
  applyTheme(s);

  var bar = box('Toolbar', 'HORIZONTAL', { px: 16, py: 14, gap: 16, middle: true });
  add(s, bar, 'fill');
  if (title) {
    bar.appendChild(await text('←', 'Title', 'ngText', { name: 'back' }));
    bar.appendChild(await text(title, 'Headline', 'ngText', { name: 'title' }));
  } else {
    bar.appendChild(await text('눈길', 'Headline', 'ngText', { name: 'title' }));
  }

  var body = box('Content', 'VERTICAL', { px: GUTTER, py: 8, gap: GAP });
  add(s, body, 'fill');
  body.layoutSizingVertical = 'FILL';
  body.clipsContent = true;

  var bottom = box('Bottom', 'VERTICAL', { px: GUTTER, py: 12, gap: GAP });
  add(s, bottom, 'fill');
  add(s, await captionBar(caption), 'fill');
  return { frame: s, body: body, bottom: bottom };
}

async function heading(body, headline, sub, style) {
  add(body, await text(headline, style || 'Title', 'ngText', { name: 'headline' }), 'fill');
  if (sub) add(body, await text(sub, 'Body', 'ngTextSub', { name: 'subtitle' }), 'fill');
  body.appendChild(spacer(GAP));
}

async function homeScreen() {
  var s = await screen('Home', null, '안녕하세요, 눈길이에요.');
  add(s.body, await text('안녕하세요', 'Caption', 'ngTextSub'), 'fill');
  await heading(s.body, '무엇을 도와드릴까요?', null, 'Display');
  add(s.body, await bigCard('◎', '주변 둘러보기', '한 바퀴 돌면 주변을 알려드려요', true), 'fill');
  add(s.body, await bigCard('○', '물건 찾기', '가까워질수록 소리가 빨라져요'), 'fill');
  add(s.body, await bigCard('★', '저장한 것', '알려 준 사람과 물건'), 'fill');
  var row = box('Secondary', 'HORIZONTAL', { gap: GAP });
  add(s.body, row, 'fill');
  add(row, await button('tonal', '실시간 안내'), 'fill');
  add(row, await button('tonal', '걷기 모드'), 'fill');
  add(s.bottom, await text('“눈길”이라고 불러 주세요', 'Caption', 'ngTextSub', { align: 'CENTER' }), 'fill');
  add(s.bottom, await button('primary', '말하기', '●'), 'fill');
  return s.frame;
}

async function scanScreen() {
  var s = await screen('Scan · full scan 60%', '주변 둘러보기', '앞에 파란 의자 세 개가 있어요.');
  await heading(s.body, '주변을 둘러볼게요', '휴대폰을 세우고 천천히 한 바퀴 돌아 주세요');
  var cam = add(s.body, camera(380), 'fill');
  await markBox(cam, 36, 190, 120, 140, '의자', 'ngPrimary', 3);
  var r = await ring(60, 104);
  cam.appendChild(r);
  r.x = cam.width - 104 - 12;
  r.y = 12;
  var row = box('Actions', 'HORIZONTAL', { gap: GAP });
  add(s.bottom, row, 'fill');
  add(row, await button('tonal', '텍스트 보기'), 'fill');
  add(row, await button('tonal', '카메라 전환'), 'fill');
  add(s.bottom, await button('danger', '멈춤'), 'fill');
  return s.frame;
}

async function searchScreen() {
  var s = await screen('Search camera · found', '찾는 중', '배낭이 정면에 있어요.');
  await heading(s.body, '배낭을 찾고 있어요', '가까워질수록 소리가 빨라져요');
  var cam = add(s.body, camera(360), 'fill');
  var target = figma.createRectangle();
  target.name = 'target fill';
  target.resize(130, 160);
  target.cornerRadius = 12;
  target.fills = [paint('ngFocus')];
  target.opacity = 0.18;
  cam.appendChild(target);
  target.x = 95;
  target.y = 110;
  await markBox(cam, 95, 110, 130, 160, '배낭', 'ngFocus', 4);
  var found = await pill('찾았어요 · 정면', 'ngSuccess', 'ngBackground');
  cam.appendChild(found);
  found.x = 12;
  found.y = 12;
  var zones = box('Zones', 'HORIZONTAL', { gap: 6 });
  add(s.body, zones, 'fill');
  var names = ['왼쪽 끝', '왼쪽', '정면', '오른쪽', '오른쪽 끝'];
  for (var i = 0; i < names.length; i++) {
    var z = box('zone', 'HORIZONTAL', { py: 8, px: 2, fill: i === 2 ? 'ngSuccess' : 'ngCard', radius: 10, center: true });
    add(zones, z, 'fill');
    z.appendChild(await text(names[i], 'Caption', i === 2 ? 'ngBackground' : 'ngTextSub'));
  }
  add(s.bottom, await button('danger', '멈춤'), 'fill');
  return s.frame;
}

async function savedScreen() {
  var s = await screen('Saved · people', '저장한 것', '사람 세 명을 알고 있어요.');
  await heading(s.body, '알려 준 사람과 물건', '사람을 누르면 이름을 바꾸거나 지울 수 있어요');
  add(s.body, await segmented(['사람', '자동차', '물건'], 0), 'fill');
  add(s.body, await bigCard('민', '민준', '얼굴 20장 등록'), 'fill');
  add(s.body, await bigCard('A', 'Ali', '얼굴 20장 등록'), 'fill');
  add(s.body, await bigCard('엄', '엄마', '얼굴 20장 등록'), 'fill');
  add(s.bottom, await button('primary', '사람 추가', '+'), 'fill');
  return s.frame;
}

async function enrollScreen() {
  var s = await screen('Enroll · pose 2 of 5', '얼굴 등록', '왼쪽을 봐 주세요.');
  await heading(s.body, '왼쪽을 봐 주세요', '2 / 5 단계 · 천천히 고개를 돌려 주세요');
  var cam = add(s.body, camera(320), 'fill');
  var oval = figma.createEllipse();
  oval.name = 'face guide';
  oval.resize(170, 220);
  oval.fills = [];
  oval.strokes = fill('ngFocus');
  oval.strokeWeight = 3;
  oval.dashPattern = [10, 8];
  cam.appendChild(oval);
  oval.x = (cam.width - 170) / 2;
  oval.y = 50;
  var progress = box('Progress', 'HORIZONTAL', { gap: GAP, middle: true });
  add(s.body, progress, 'fill');
  var track = figma.createFrame();
  track.name = 'track';
  track.resize(240, 12);
  track.cornerRadius = 6;
  track.fills = fill('ngLine');
  add(progress, track, 'fill');
  var bar = figma.createRectangle();
  bar.name = 'done 40%';
  bar.resize(96, 12);
  bar.cornerRadius = 6;
  bar.fills = fill('ngPrimary');
  track.appendChild(bar);
  progress.appendChild(await text('40%', 'Label', 'ngText'));
  var dots = box('Steps', 'HORIZONTAL', { gap: 10, center: true });
  add(s.body, dots, 'fill');
  for (var i = 0; i < 5; i++) {
    var d = figma.createEllipse();
    d.name = 'step ' + (i + 1);
    d.resize(12, 12);
    d.fills = fill(i < 2 ? 'ngPrimary' : 'ngLine');
    dots.appendChild(d);
  }
  add(s.bottom, await button('danger', '멈춤'), 'fill');
  return s.frame;
}

async function settingsScreen() {
  var s = await screen('Settings', '설정', '설정을 바꿨어요.');
  add(s.body, await text('스캔', 'Headline', 'ngText'), 'fill');
  var rows = [['카메라', ['후면', '전면'], 0], ['처리', ['GPU', 'CPU'], 0], ['모델', ['가벼움', '빠름', '정확'], 2]];
  for (var i = 0; i < rows.length; i++) {
    add(s.body, await text(rows[i][0], 'Caption', 'ngTextSub'), 'fill');
    add(s.body, await segmented(rows[i][1], rows[i][2]), 'fill');
  }
  add(s.body, await switchRow('음성 안내', true), 'fill');
  add(s.body, await switchRow('색깔 말하기', true), 'fill');
  add(s.body, await text('앱', 'Headline', 'ngText'), 'fill');
  add(s.body, await text('언어', 'Caption', 'ngTextSub'), 'fill');
  add(s.body, await segmented(['시스템', 'English', '한국어'], 2), 'fill');
  add(s.body, await switchRow('고대비', CUR === 'High contrast'), 'fill');
  return s.frame;
}

// ---- Pages --------------------------------------------------------------------------------------

async function tokensPage(page) {
  var x = 0;
  for (var ti = 0; ti < THEMES.length; ti++) {
    CUR = THEMES[ti];
    var col = box('Tokens · ' + CUR, 'VERTICAL', { p: 24, gap: 10, fill: 'ngBackground', radius: 24 });
    applyTheme(col);
    page.appendChild(col);
    col.x = x;
    col.y = 0;
    col.appendChild(await text(CUR, 'Title', 'ngText'));
    var names = Object.keys(TOKENS);
    for (var i = 0; i < names.length; i++) {
      var row = box(names[i], 'HORIZONTAL', { gap: 12, middle: true });
      col.appendChild(row);
      var sw = figma.createRectangle();
      sw.name = 'swatch';
      sw.resize(56, 56);
      sw.cornerRadius = 12;
      sw.fills = fill(names[i]);
      sw.strokes = fill('ngLine');
      sw.strokeWeight = 1;
      row.appendChild(sw);
      row.appendChild(await text(names[i] + '  ' + TOKENS[names[i]][ti], 'Body', 'ngText'));
    }
    col.appendChild(spacer(12));
    for (var k = 0; k < TYPE.length; k++) {
      col.appendChild(await text(TYPE[k][0] + ' ' + TYPE[k][1] + '/' + TYPE[k][2] + ' · 주변을 둘러볼게요', TYPE[k][0], 'ngText'));
    }
    x += col.width + 80;
  }
}

async function screensPage(page, theme) {
  CUR = theme;
  var builders = [homeScreen, scanScreen, searchScreen, savedScreen, enrollScreen, settingsScreen];
  for (var i = 0; i < builders.length; i++) {
    var f = await builders[i]();
    page.appendChild(f);
    f.x = i * (W + 80);
    f.y = 0;
  }
}

async function componentsPage(page) {
  CUR = 'Light';
  var made = [
    await button('primary', '시작하기', '●'),
    await button('tonal', '카메라 전환'),
    await button('danger', '멈춤'),
    await bigCard('◎', '주변 둘러보기', '한 바퀴 돌면 주변을 알려드려요', true),
    await bigCard('○', '물건 찾기', '가까워질수록 소리가 빨라져요'),
    await segmented(['사람', '자동차', '물건'], 0),
    await switchRow('음성 안내', true),
    await captionBar('앞에 파란 의자 세 개가 있어요.'),
    await pill('찾았어요 · 정면', 'ngSuccess', 'ngBackground'),
    await ring(60, 120)
  ];
  var y = 0;
  for (var i = 0; i < made.length; i++) {
    var node = made[i];
    var component = figma.createComponentFromNode(node);
    page.appendChild(component);
    if (component.layoutMode !== 'NONE') component.resize(W - 2 * GUTTER, component.height);
    component.x = 0;
    component.y = y;
    y += component.height + 40;
  }
}

async function main() {
  await loadFonts();
  makeVariables();
  makeTextStyles();

  var first = figma.root.children[0];
  first.name = '0 Tokens';
  var pages = [first];
  var names = ['1 Screens light', '2 Screens dark', '3 Screens high contrast', '4 Components'];
  for (var i = 0; i < names.length; i++) {
    var p = figma.createPage();
    p.name = names[i];
    await p.loadAsync();
    pages.push(p);
  }

  await tokensPage(pages[0]);
  await screensPage(pages[1], 'Light');
  await screensPage(pages[2], 'Dark');
  await screensPage(pages[3], 'High contrast');
  await componentsPage(pages[4]);

  await figma.setCurrentPageAsync(pages[1]);
  figma.viewport.scrollAndZoomIntoView(pages[1].children);
  figma.closePlugin('Nungil UI is ready: 6 screens × 3 themes, tokens, text styles and components.');
}

main().catch(function (e) {
  figma.closePlugin('Nungil UI builder stopped: ' + (e && e.message ? e.message : e));
});
