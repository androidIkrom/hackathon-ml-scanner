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
var BRAND = null; // the brand picture (눈길이 walking with a friend), made from BRAND_B64 in main()

// Parts of the brand picture as [x, y, width, height], fractions of the square image.
var CROP = {
  full: [0, 0, 1, 1],
  both: [0.088, 0.191, 0.702, 0.702], // 눈길이 and the friend: the launcher icon
  mascot: [0.096, 0.399, 0.319, 0.319], // 눈길이's face: stickers and small sizes
  hero: [0, 0.41, 1, 0.415] // a wide band for the home card
};

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

/** A rectangle filled with part of the brand picture. */
function pic(name, w, h, radius, crop) {
  var r = figma.createRectangle();
  r.name = name;
  r.resize(w, h);
  r.cornerRadius = radius;
  r.fills = [{
    type: 'IMAGE',
    imageHash: BRAND.hash,
    scaleMode: 'CROP',
    imageTransform: [[crop[2], 0, crop[0]], [0, crop[3], crop[1]]]
  }];
  return r;
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
  var push = box('push', 'HORIZONTAL', {});
  add(bar, push);
  push.layoutGrow = 1;
  bar.appendChild(pic('App icon', 32, 32, 10, CROP.both));

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

// ---- Proposal screens (every destination in nav_graph.xml, with the brand picture applied) ------

async function tile(glyph, title) {
  var t = box('Tile', 'HORIZONTAL', { px: 16, py: 18, gap: 12, fill: 'ngCard', radius: 24, middle: true });
  outline(t);
  t.appendChild(await iconBadge(glyph, false));
  add(t, await text(title, 'Headline', 'ngText', { name: 'title' }), 'fill');
  return t;
}

async function tiles(body, pairs) {
  for (var i = 0; i < pairs.length; i += 2) {
    var row = box('Tiles', 'HORIZONTAL', { gap: GAP });
    add(body, row, 'fill');
    add(row, await tile(pairs[i][0], pairs[i][1]), 'fill');
    if (pairs[i + 1]) add(row, await tile(pairs[i + 1][0], pairs[i + 1][1]), 'fill');
  }
}

async function titleWithMascot(body, over, headline, sub) {
  var row = box('Heading', 'HORIZONTAL', { gap: GAP, middle: true });
  add(body, row, 'fill');
  var col = box('text', 'VERTICAL', { gap: 4 });
  add(row, col, 'fill');
  if (over) add(col, await text(over, 'Label', 'ngAccentText', { name: 'over' }), 'fill');
  add(col, await text(headline, 'Title', 'ngText', { name: 'headline' }), 'fill');
  if (sub) add(col, await text(sub, 'Body', 'ngTextSub', { name: 'subtitle' }), 'fill');
  row.appendChild(pic('눈길이 sticker', 84, 84, 42, CROP.mascot));
}

async function listCard(rows) {
  var list = box('List', 'VERTICAL', { px: 20, py: 4, fill: 'ngCard', radius: 24 });
  outline(list);
  for (var i = 0; i < rows.length; i++) {
    var r = box('Row', 'HORIZONTAL', { py: 16, gap: 14, middle: true });
    add(list, r, 'fill');
    if (rows[i][0]) r.appendChild(await iconBadge(rows[i][0], false));
    add(r, await text(rows[i][1], 'Body', 'ngText'), 'fill');
    if (rows[i][2]) r.appendChild(await text(rows[i][2], rows[i][3] || 'Caption', rows[i][3] ? 'ngText' : 'ngTextSub'));
  }
  return list;
}

async function field(body, labelText, hint) {
  add(body, await text(labelText, 'Caption', 'ngTextSub'), 'fill');
  var row = box('Field', 'HORIZONTAL', { gap: 10, middle: true });
  add(body, row, 'fill');
  var f = box('input', 'HORIZONTAL', { px: 18, py: 18, fill: 'ngCard', radius: 16, middle: true });
  f.strokes = fill('ngLine');
  f.strokeWeight = 2;
  add(row, f, 'fill');
  add(f, await text(hint, 'Body', 'ngTextSub', { name: 'hint' }), 'fill');
  var mic = box('mic', 'HORIZONTAL', { fill: 'ngPrimarySoft', radius: 16, center: true });
  mic.primaryAxisSizingMode = 'FIXED';
  mic.counterAxisSizingMode = 'FIXED';
  mic.resize(60, 60);
  mic.appendChild(await text('●', 'Headline', 'ngPrimary', { name: 'glyph' }));
  row.appendChild(mic);
}

async function progressBar(body, percent) {
  var row = box('Progress', 'HORIZONTAL', { gap: GAP, middle: true });
  add(body, row, 'fill');
  var track = figma.createFrame();
  track.name = 'track';
  track.resize(240, 12);
  track.cornerRadius = 6;
  track.fills = fill('ngLine');
  track.clipsContent = true;
  add(row, track, 'fill');
  var done = figma.createRectangle();
  done.name = 'done';
  done.resize(Math.max(12, Math.round(240 * percent / 100)), 12);
  done.cornerRadius = 6;
  done.fills = fill('ngPrimary');
  track.appendChild(done);
  row.appendChild(await text(percent + '%', 'Label', 'ngText'));
}

function photoCard(height) {
  var c = camera(height);
  c.name = 'Photo';
  return c;
}

async function chips(body, labels) {
  var row = box('Chips', 'HORIZONTAL', { gap: 8 });
  add(body, row, 'fill');
  for (var i = 0; i < labels.length; i++) row.appendChild(await pill(labels[i], 'ngCard', 'ngText'));
}

async function onboardingScreen() {
  var s = await screen('Onboarding', null, '안녕하세요, 눈길이에요.');
  add(s.body, pic('Brand picture', W - 2 * GUTTER, 200, 24, CROP.hero), 'fill');
  await heading(s.body, '안녕하세요, 눈길이에요', '인터넷 없이 이 휴대폰만으로 주변에 무엇이 있는지 알려드려요.');
  var say = box('Try saying', 'VERTICAL', { p: 20, gap: 10, fill: 'ngPrimarySoft', radius: 24 });
  add(s.body, say, 'fill');
  add(say, await text('이렇게 말해 보세요', 'Caption', 'ngTextSub'), 'fill');
  var lines = ['“눈길아, 주변 둘러보기”', '“가방 찾아줘”', '“눈길아, 그만”'];
  for (var i = 0; i < lines.length; i++) add(say, await text(lines[i], 'BodyStrong', 'ngText'), 'fill');
  add(s.bottom, await button('tonal', '음성 명령 켜기', '●'), 'fill');
  add(s.bottom, await button('primary', '시작하기'), 'fill');
  return s.frame;
}

async function proposalHomeScreen() {
  var s = await screen('Home', null, '안녕하세요, 눈길이에요.');
  add(s.body, await text('안녕하세요', 'Caption', 'ngTextSub'), 'fill');
  await heading(s.body, '무엇을 도와드릴까요?', null, 'Display');
  var hero = box('Hero card', 'VERTICAL', { p: 12, gap: 8, fill: 'ngPrimary', radius: 24 });
  add(s.body, hero, 'fill');
  add(hero, pic('Brand picture', W - 2 * GUTTER - 24, 132, 16, CROP.hero), 'fill');
  var words = box('text', 'VERTICAL', { px: 12, py: 8, gap: 2 });
  add(hero, words, 'fill');
  add(words, await text('주변 둘러보기  ›', 'Title', 'ngOnPrimary', { name: 'title' }), 'fill');
  add(words, await text('한 바퀴 돌면 주변을 말로 알려드려요', 'Body', 'ngOnPrimary', { name: 'subtitle' }), 'fill');
  await tiles(s.body, [['○', '물건 찾기'], ['★', '저장한 것'], ['◉', '실시간 안내'], ['➤', '길 안내'], ['⌇', '걷기 모드'], ['◷', '기록']]);
  add(s.bottom, await button('tonal', '‘눈길’ 하고 불러 주세요', '●'), 'fill');
  return s.frame;
}

async function hubScreen() {
  var s = await screen('Scan hub', '둘러보기', '어떻게 둘러볼까요?');
  await heading(s.body, '어떻게 둘러볼까요?', '휴대폰을 세우고 천천히 돌아 주세요.');
  add(s.body, await bigCard('◎', '주변 둘러보기', '한 바퀴 돌면 주변을 알려드려요', true), 'fill');
  add(s.body, await bigCard('◉', '실시간 안내', '카메라가 찾는 대로 바로 말해요'), 'fill');
  add(s.body, await bigCard('⌇', '걷기 모드', '걷는 동안 장애물을 알려드려요'), 'fill');
  add(s.body, await bigCard('◷', '기록', '지난 결과를 다시 들어요'), 'fill');
  return s.frame;
}

var HISTORY_ROWS = [
  ['오늘 오후 3:12 · 주변 둘러보기', '앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요.'],
  ['오늘 오전 9:40 · 실시간 안내', '앞에 사람 두 명, 왼쪽에 문이 있어요.'],
  ['어제 오후 6:05 · 주변 둘러보기', '방의 80퍼센트를 살펴봤어요. 앞에 책상, 뒤에 소파가 있어요.']
];

async function historyRows(body) {
  for (var i = 0; i < HISTORY_ROWS.length; i++) {
    var c = box('History item', 'VERTICAL', { px: 20, py: 18, gap: 6, fill: 'ngCard', radius: 24 });
    outline(c);
    add(body, c, 'fill');
    add(c, await text(HISTORY_ROWS[i][0], 'Caption', 'ngTextSub', { name: 'meta' }), 'fill');
    add(c, await text(HISTORY_ROWS[i][1], 'Body', 'ngText', { name: 'summary' }), 'fill');
  }
}

async function historyScreen() {
  var s = await screen('History', '기록', '지난 결과를 다시 들어요.');
  await heading(s.body, '기록', '누르면 다시 들려드려요. 길게 누르면 지울 수 있어요.');
  await historyRows(s.body);
  return s.frame;
}

async function historyDeleteScreen() {
  var s = await screen('History · delete sheet', '기록', '이 기록을 지울까요?');
  await heading(s.body, '기록', '누르면 다시 들려드려요. 길게 누르면 지울 수 있어요.');
  await historyRows(s.body);
  s.body.opacity = 0.5;
  var sheet = box('Delete sheet', 'VERTICAL', { px: GUTTER, py: 20, gap: GAP, fill: 'ngCard' });
  sheet.topLeftRadius = 28;
  sheet.topRightRadius = 28;
  add(s.frame, sheet, 'fill');
  add(sheet, await text('이 기록을 지울까요?', 'Headline', 'ngText', { align: 'CENTER' }), 'fill');
  add(sheet, await button('danger', '삭제'), 'fill');
  add(sheet, await button('tonal', '취소'), 'fill');
  return s.frame;
}

async function scanLiveScreen() {
  var s = await screen('Scan · live', '실시간 안내', '오른쪽에 컵이 있어요.');
  await heading(s.body, '실시간 안내', '휴대폰을 천천히 움직여 보세요. 찾는 대로 알려 드릴게요.');
  var cam = add(s.body, camera(340), 'fill');
  await markBox(cam, 50, 110, 140, 190, '사람', 'ngPrimary', 3);
  await markBox(cam, 210, 230, 100, 80, '컵', 'ngPrimary', 3);
  var said = box('What I said', 'VERTICAL', { px: 16, py: 14, gap: 6, fill: 'ngCard', radius: 16 });
  outline(said);
  add(s.body, said, 'fill');
  add(said, await text('말한 내용', 'Caption', 'ngTextSub'), 'fill');
  add(said, await text('앞에 사람이 있어요', 'Body', 'ngTextSub'), 'fill');
  add(said, await text('오른쪽에 컵이 있어요', 'BodyStrong', 'ngText'), 'fill');
  add(s.bottom, await button('danger', '멈춤'), 'fill');
  return s.frame;
}

async function scanPermissionScreen() {
  var s = await screen('Scan · camera needed', '주변 둘러보기', '주변을 보려면 카메라를 허용해 주세요.');
  await heading(s.body, '주변을 둘러볼게요', '휴대폰을 세우고 천천히 한 바퀴 돌아 주세요');
  var panel = box('Permission panel', 'VERTICAL', { p: 24, gap: 12, fill: 'ngCard', radius: 24, center: true });
  outline(panel);
  panel.primaryAxisSizingMode = 'FIXED';
  add(s.body, panel, 'fill');
  panel.resize(W - 2 * GUTTER, 380);
  panel.appendChild(await text('카메라가 필요해요', 'Headline', 'ngText', { align: 'CENTER' }));
  add(panel, await text('주변을 보려면 카메라를 허용해 주세요.', 'Body', 'ngTextSub', { align: 'CENTER' }), 'fill');
  panel.appendChild(await button('tonal', '설정 열기'));
  var row = box('Actions', 'HORIZONTAL', { gap: GAP });
  add(s.bottom, row, 'fill');
  add(row, await button('tonal', '글로 보기'), 'fill');
  add(row, await button('tonal', '카메라 전환'), 'fill');
  add(s.bottom, await button('primary', '카메라 허용'), 'fill');
  return s.frame;
}

async function resultScreen() {
  var s = await screen('Result', '둘러본 결과', '앞에 파란 의자 세 개가 있어요.');
  await titleWithMascot(s.body, '✓ 방 전체를 확인했어요', '다 둘러봤어요');
  var said = box('Summary', 'VERTICAL', { p: 24, fill: 'ngCard', radius: 24 });
  outline(said);
  add(s.body, said, 'fill');
  add(said, await text('앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요.', 'Title', 'ngText'), 'fill');
  var list = box('Objects', 'VERTICAL', { px: 20, py: 8, fill: 'ngCard', radius: 24 });
  outline(list);
  add(s.body, list, 'fill');
  var found = [['앞', '파란 의자', '3개'], ['오른쪽', '검은 노트북', '1대'], ['뒤', '사람', '1명']];
  for (var i = 0; i < found.length; i++) {
    var r = box('Object', 'HORIZONTAL', { py: 16, gap: 14, middle: true });
    add(list, r, 'fill');
    r.appendChild(await pill(found[i][0], 'ngPrimarySoft', 'ngAccentText'));
    add(r, await text(found[i][1], 'Body', 'ngText'), 'fill');
    r.appendChild(await text(found[i][2], 'BodyStrong', 'ngText'));
  }
  var actions = box('Actions', 'HORIZONTAL', { gap: GAP });
  add(s.bottom, actions, 'fill');
  add(actions, await button('tonal', '다시 듣기'), 'fill');
  add(actions, await button('primary', '다시 둘러보기'), 'fill');
  return s.frame;
}

async function walkScreen() {
  var s = await screen('Walk mode', '걷기 모드', '앞에 사람이 있어요. 왼쪽으로 조금 비켜 주세요.');
  await heading(s.body, '걷기 모드', '카메라로 거리를 재고 있어요.');
  var cam = add(s.body, camera(380), 'fill');
  var go = box('Where to', 'HORIZONTAL', { px: 16, py: 14, gap: 10, fill: 'ngCard', radius: 14, middle: true });
  cam.appendChild(go);
  go.resize(cam.width - 32, 52);
  go.x = 16;
  go.y = 16;
  add(go, await text('어디로 갈까요?', 'Body', 'ngTextSub', { name: 'hint' }), 'fill');
  go.appendChild(await text('●', 'Headline', 'ngPrimary', { name: 'mic' }));
  await markBox(cam, 60, 150, 90, 170, '사람 · 2 m', 'ngDanger', 3);
  var dir = box('Direction', 'HORIZONTAL', { p: 16, gap: 14, fill: 'ngPrimarySoft', radius: 20, middle: true });
  cam.appendChild(dir);
  dir.resize(cam.width - 32, 88);
  dir.x = 16;
  dir.y = cam.height - 88 - 16;
  dir.appendChild(await iconBadge('↗', true));
  var words = box('text', 'VERTICAL', { gap: 2 });
  add(dir, words, 'fill');
  add(words, await text('오른쪽 앞으로 가세요', 'BodyStrong', 'ngText'), 'fill');
  add(words, await text('역까지 120 m', 'Body', 'ngTextSub'), 'fill');
  add(s.bottom, await button('danger', '멈춤'), 'fill');
  return s.frame;
}

async function findScreen() {
  var s = await screen('Find · text', '찾기', '무엇을 찾을까요?');
  await titleWithMascot(s.body, null, '무엇을 찾을까요?', '물건 이름이나 저장한 이름을 말하거나 입력해 주세요.');
  await field(s.body, '찾을 물건이나 사람', '예: 가방, 컵, 민준');
  add(s.body, await text('추천', 'Caption', 'ngTextSub'), 'fill');
  await chips(s.body, ['가방', '열쇠', '휴대폰', '컵', '민준']);
  add(s.bottom, await button('tonal', '글자나 코드 읽기'), 'fill');
  add(s.bottom, await button('primary', '찾기'), 'fill');
  return s.frame;
}

async function readerScreen() {
  var s = await screen('Reader', '글자 읽기', '출입구. 2층 회의실은 왼쪽 계단으로.');
  await heading(s.body, '글자 읽기', '표지판, 라벨, QR 코드에 카메라를 비춰 주세요.');
  var cam = add(s.body, camera(280), 'fill');
  var guide = figma.createRectangle();
  guide.name = 'text guide';
  guide.resize(cam.width - 80, 140);
  guide.cornerRadius = 14;
  guide.fills = [];
  guide.strokes = fill('ngPrimary');
  guide.strokeWeight = 3;
  guide.dashPattern = [10, 8];
  cam.appendChild(guide);
  guide.x = 40;
  guide.y = 70;
  var last = box('Last read', 'VERTICAL', { p: 20, gap: 8, fill: 'ngPrimarySoft', radius: 24 });
  add(s.body, last, 'fill');
  add(last, await text('마지막으로 읽은 글자', 'Caption', 'ngTextSub'), 'fill');
  add(last, await text('출입구 · 2층 회의실은 왼쪽 계단으로', 'Headline', 'ngText'), 'fill');
  add(s.bottom, await button('danger', '읽기 멈춤'), 'fill');
  return s.frame;
}

async function personScreen() {
  var s = await screen('Person', '사람', '민준이에요.');
  await heading(s.body, '민준', '사람 · 얼굴 20장 등록');
  add(s.body, photoCard(300), 'fill');
  var row = box('Actions', 'HORIZONTAL', { gap: GAP });
  add(s.bottom, row, 'fill');
  add(row, await button('tonal', '이름 바꾸기'), 'fill');
  add(row, await button('danger', '삭제'), 'fill');
  add(s.bottom, await button('primary', '이 사람 찾기'), 'fill');
  return s.frame;
}

async function addPersonScreen() {
  var s = await screen('Add person', '사람 추가', '이름이 뭐예요?');
  await heading(s.body, '누구를 등록할까요?', '이름을 말하거나 입력하고 카메라를 골라 주세요.');
  await field(s.body, '이름', '이름');
  add(s.body, await listCard([['◉', '다른 사람 (뒤 카메라)'], ['○', '나 (앞 카메라)']]), 'fill');
  add(s.bottom, await button('primary', '시작하기'), 'fill');
  return s.frame;
}

async function itemScreen() {
  var s = await screen('Item', '물건', '내 가방이에요.');
  await heading(s.body, '내 가방', '가방 · 사진 12장 등록');
  add(s.body, photoCard(300), 'fill');
  var row = box('Actions', 'HORIZONTAL', { gap: GAP });
  add(s.bottom, row, 'fill');
  add(row, await button('tonal', '이름 바꾸기'), 'fill');
  add(row, await button('danger', '삭제'), 'fill');
  add(s.bottom, await button('primary', '찾기'), 'fill');
  return s.frame;
}

async function addItemScreen() {
  var s = await screen('Add item', '물건 추가', '뭐라고 부를까요?');
  await heading(s.body, '이 물건을 뭐라고 부를까요?', '예: 내 가방, 아빠 차. 그다음 카메라로 비춰 주세요.');
  await field(s.body, '이름', '이름');
  add(s.bottom, await button('primary', '시작하기'), 'fill');
  return s.frame;
}

async function itemEnrollScreen() {
  var s = await screen('Item enroll · 4 of 12', '물건 등록', '가만히 들고 있어 주세요.');
  await heading(s.body, '내 가방 등록 중', '가만히 들고 있어 주세요 · 4 / 12');
  await progressBar(s.body, 33);
  var cam = add(s.body, camera(320), 'fill');
  await markBox(cam, 80, 70, 160, 200, '가방', 'ngPrimary', 3);
  add(s.bottom, await button('tonal', '잠시 멈춤'), 'fill');
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

async function proposalPage(page, theme) {
  CUR = theme;
  var builders = [
    onboardingScreen, proposalHomeScreen, hubScreen, settingsScreen, historyScreen, historyDeleteScreen,
    scanScreen, scanLiveScreen, scanPermissionScreen, resultScreen, walkScreen,
    findScreen, searchScreen, readerScreen,
    savedScreen, personScreen, addPersonScreen, enrollScreen, itemScreen, addItemScreen, itemEnrollScreen
  ];
  var rows = [6, 5, 3, 7];
  var i = 0;
  for (var r = 0; r < rows.length; r++) {
    for (var c = 0; c < rows[r]; c++) {
      var frame = await builders[i++]();
      page.appendChild(frame);
      frame.x = c * (W + 80);
      frame.y = r * (H + 160);
    }
  }
}

async function brandPage(page) {
  CUR = 'Light';
  var items = [
    ['Store and splash', pic('Brand picture', 512, 512, 48, CROP.full)],
    ['Launcher · rounded square', pic('Launcher icon', 192, 192, 44, CROP.both)],
    ['Launcher · circle', pic('Launcher icon round', 192, 192, 96, CROP.both)],
    ['Small sizes · 눈길이', pic('Small icon', 108, 108, 26, CROP.mascot)],
    ['48 dp', pic('Icon 48', 48, 48, 12, CROP.mascot)]
  ];
  var x = 0;
  for (var i = 0; i < items.length; i++) {
    var col = box(items[i][0], 'VERTICAL', { gap: 12 });
    page.appendChild(col);
    col.appendChild(items[i][1]);
    col.appendChild(await text(items[i][0], 'Label', 'ngText'));
    col.x = x;
    col.y = 0;
    x += col.width + 64;
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
  BRAND = figma.createImage(figma.base64Decode(BRAND_B64));

  var first = figma.root.children[0];
  first.name = '0 Tokens';
  var pages = [first];
  var names = ['1 Screens light', '2 Screens dark', '3 Screens high contrast', '4 Components', '5 Proposal light', '6 Proposal dark', '7 Proposal high contrast', '8 Brand'];
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
  await proposalPage(pages[5], 'Light');
  await proposalPage(pages[6], 'Dark');
  await proposalPage(pages[7], 'High contrast');
  await brandPage(pages[8]);

  await figma.setCurrentPageAsync(pages[1]);
  figma.viewport.scrollAndZoomIntoView(pages[1].children);
  figma.closePlugin('Nungil UI is ready: 6 screens × 3 themes, all 21 proposal screens × 3 themes, the brand page, tokens, text styles and components.');
}

// The brand picture (640 px JPEG). Embedded because the plugin has no network access (manifest.json).
var BRAND_B64 = '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAYEBQUFBAYFBQUHBgYHCQ8KCQgICRMNDgsPFhMXFxYTFRUYGyMeGBohGhUVHikfISQlJygnGB0rLismLiMmJyb/2wBDAQYHBwkICRIKChImGRUZJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJib/wAARCAKAAoADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDTMnQcdB2HpTGY47fkKrl+QPYfypxfivEcbM+auSWkJurtI8DBPPAr1jQ7YWtmihVzj+6K838LhW1AEivVIBiJQPSvmM5qPnUOh7mXQXK5E4c56L/3yKe0hCE4Xp/dFRLVPV7xLa1c7hnFeAo80kkevdRV2ee+MZg+o4G3j2FZNp5ksgjQAk+wpup3DXN28h9eK6PwbYpKfNdcmvueZYbDK/Q+VknWrOxJbaDLOis4Hr90V12kRG0iCELwP7oqcKFGAMAUV87WxM62ktj1KdGNPVFlptwxhf8AvkVUlyDnC/8AfIqVBSTDK1yrQ6HqJC59F/75FWlc46L/AN8iqEAYtWgq8c03YaRFczbIWYhen90V5X4jmEuosRjj2Fd14lnnS3YQqWOOgrza4E3ms0ylWJ71+tcD4D2cZYlyWvQ+czare0Ehqk+g/KnA+w/IUxafX6YzwLDgx9B+QoyfQflSUVIyQMfb8qXJ9vyFMpQaQD9x9vyFG4+35CmiloEPBPt+QpQx9B+VNHSlFIB4Y46D8qMn2/KkFFIQuT7flQCfb8qO1JQFhSx9vyFKCfb8qbSigLDsn2/IUoY+35CkpRUhYUE+35ClDH2/IU2loAduPt+QoyfQfkKbSikA4E+35ClDH0H5CkopAOyfb8hRuPoPyFJRSAXJ9B+Qpcn2/IUgpaBgWPt+QoDHPQfkKTGaUUgHBj6D8hTtx9B+QplLmlYY/PsPyFLn2H5CmZpaVhjwx9B+QoJ56D8hTadUgKGPoPyFOyfb8hTOlKKAH7j7fkKUMfb8hTKWpAduPt+Qoyfb8hTaWgY4E+35Cnbj6D8hTAaWpGP3H0H5CgMfQfkKaKWkMcGPoPyFODH0H5CmClFIZICfRfyFOBPoPyFRg04VNhjwx9B+QpwY+g/IUylBqbDQ/cfQfkKcrH0H5CmClFIok3H0H5CnBj6D/vkVHTlNQ0UTKx9B/wB8inBj6L+QqEGng1DRaZOrH0H/AHyKcHPoP++RUINKDUWKJg59B/3yKcGPt/3yKhBpymk0NMnDH0X/AL5FSxud68L1/uiqwNSRn5h9azaLueUDO4Z9B/Kp1ANRzYDceg/lT4Tk1+dNmxreHx5d8rdK9MguYhApZwOPWvL4ZViAYHBFR3ms3RGyN8V4uNwTxU1JOx6OFxaoRaZ6RqGt21sjYkBIrhNV1qXUJiisdtYEs883MkhNLaMBIM0YfLqdD3nqwrY2dXRaItvCTziuw8HOETa3FYUKqyA4zVq1ujaSAp0qcRJ1YOBFG0Jcx6B1FIKxtP1BpgM1rLKoXJNeJKLi7M9WMlJXROtIxUnGaoXF+iAgHmqcd07yg54pcrHzI6GJAKkNQWr74xUxqDZbETwpJ95Qawtd0SGaBmRBmuiBodQyFT3r0MDj6+CrRqUpNWMKtGFWLjJHjFzC1vcNEw6Gmit/xjaiG7Dgda54Gv6MwOJWLw0Ky6o+HrU/Z1HDsPzThTQc0orrMhaUUgpwpAKKWjFKKQB2pRRigCgQ4UtIKWkIWgUlLSADQKUUYpBcWiiloABSikFLmkAtFJmlFIBaBRmlpALRQKWkAgpaSloAWikooAWlptKKQx1LTacKQxRS0gpaQC0optLSAdmlplKKQXHUuabSikMcKcDTM0uaVhj6KbmlpDFpc02jNKwD1p4NRqaeKljQ/NKDTaKkokBpwNRrThUspD807NMFLmlYdyQGnA1GKcDUNFEgNOBqMU4GpaKTJAaeDUQNPBqGUmSqakjPzD61CDT4z8w+tQ0Vc8sbkj6D+VOjYrWj9iAHI7D+VU54vLavzHnUnY67WIpGkY8Gmqhzk9atRqCKJAAKOboKxXIq7pek3N7KNikL61FZxedconqa9S0Oxit7VSFGSK8/GYp0I2W7OzDUPavXY5+LQpYYOc5xVRdPlM+1lOBXfEAjBFV2t4y27aM14axc9bnpPDR6GPaweTGBjmpmLbSM1elhB6Cqkg25zxWN3J3ZduUypvvnJp0b4IqvezAS4BqESntXUotow5rM6/S5NyDmtE1ymmXjIQK6O1l8xQa5ZxcWdcJcyJ6cvSkwaWoNDh/GcEs7hYkLHNc2NGvtm7y69Tmt43bcyg0nkx4xsFfoWA4wngsLChCnex4lbLFVqObe55FJBNCxEqFabXpmqaRBcRMQozXnmoWrWty0Z6dq/Q8lz6hm0Xy6SW6PExeDlh3rsQCnCminivoTgHUopBTqQABS0CikIKUdKKUUgCloooEFKKBS0gEoFFLQAUUtFIAooooAUUuaSikA7NGaaKcKQC0UUtIYUUEUUALSikFLSGLQKKKQDs0UlKKQCilpKKQC0tJmlpAJTgabSigY6ikzS0hjqM02nDmkMWlpKUUgFFPFMFPFSxodmlFNpRUsseKd2pgpakY4GnA0wU6kMeKUU0U6pZQ8U4UwHinCoY0PFPFRinCpZaJAafGfmX61EKfH99frUNDOTcqcHI6D+VZGqtxwa5y212aSREGTwK2C7TKC9fF5Xk7qVXKr8KO3FydGNupJpYluJhEgJrr7Xwy00YaTvTvBulRBRMygnrXZDAGBxXyOdYlUMVKjQeiPQweHU6anPqcdF4ba3u0kTOAa7O0XbCqntSgUucdK+eq4ida3MelTpRp/CSnAFRMcmkLNRjiskW2AHFZOuP5EJcccVrisPxbxpjsOoBrqoQ56kY9zKfws4+W93yFiav6dmc57Vxdm9w8jb+gNdvoW1FXnkivrsfk9fB4X28loeZTqxlU5L6mxBCExW7psgIC1jg1oaX/rK+QnqtT1IaOyNwrxWRq2qRWON5ArYzXn/jwjzF5717vDmApY/HKjV2McfWlQoucdzqrDUIrtNwYc1cryrT9Uns2ARiV9K3ovFbCPDA5r6fMeC8TGq3hdYs8yhm1Nx/eaM7OeVYo2ZiOlebeIZ0mviV7VYv8AxBPcqVXIFYjEs2Sck19Pwzw7Uyxyq1n7zPPx+PjiEow2AU9aaBTxX2zPIuGKcKQU6pAKKSlpAKKWkFLSEL+FAoopAOzSZpKCKAFFGaaKdQA7NFIKWkAUoooApAFLiilFAxBSilApaQBSikpakYppMUUooAKKKWgAoooFIBRS5pBRSGOoooFIApc0UGgAoopaACijFKBSGKKUUAU4CkMKUUAUoFSMUU6m0VIx4paaKWkUOpwpgp4qShwp1MpwqRjhTqaKdUspDhThTRTqllDhTqYKeKljQ4U5PvD600U5PvD61LGeK6FZ8iRx2FdBgDHtUVvCIYwoHYfyqQ0YekqVNRM8VWdao5dDvfCV5GYRHkA4rqRgjINeQ2V7LaPujPFbK+KbkR7cHNfnWb8L16+JlVoPRnsYXMoQpqM+h6JvXOAQTUqLnrXLeFbua9JklPBrq14r4bG4SWDrOjN6o9ujVVaHOhHAAzUaurdDnFM1OTy7KR84wK89svFM0d/LG6sVBxmuzLsqr4+EpUVexFatGk/ePRya5vxZdILVo8jpVSbxTGY/lPNc1qN/JeyEknbX2+QcJ4mOJjXxStGPQ8fG5lTVNxpu7ZnxqFzj1rZ064KgHPIrKAp6MUOVNfpmZ5fTx+GdCWh89h68qVTnOuivQQMmul0do3TcGGa81F3IBirthrNxbHHJFfm1Tgat7NtT97oe/TzeCkrrQ9MnuEhjLMw6V5p4mvftV4QpyBUl9rlxcoVGVBrFOWJJOSa+g4Z4allknWrO83+Bx5jmKxC5IbDQKcBSgUuK+6ueKAFLigCnVIwFLQKWkMUUtIKKQBS0mKWkAZpaSlFAC0tJSikAUuaSgCkAtLikFOFIAFKKMUAUgFFLRiikAUUCloGJThSYpRSAWiiikAUtIKWgAozRRQAGikooAeKWkFKKkYtKBTcU7tSGFFFLQAUuKBS0hiYpQKKdSGKBTqQUtSxhS0lKKQwopcUAUhiilooFIY4U6milFSUOFOFMHWpB0pMYopaSlqCxwpwpgpwpDHinioxThUMaHinx/fH1qMGpI/vj61LKPOZBz+A/lURq1Iozz6D+VVJnSPkmtJO2rOJK+iENV7i4SIdarXN8BwtZdxM8p5PFeViMxhTVo6s9KhgJ1HeWiO+8Ca7Gtx5LtjBr1GKaJ0DhwQa+c9PS485Xt8qwPUV6DpuqXsVqEkY5xX5rm2CeJrusnqz341YYePKdxrE4niMEZzmsi00G2RCWQF25NRaFJJcvuc5Oa6MIa4qU6mD9ynKxm2q3vNHC+INMW2bzEGBnmsYCu/1rTZLyMqveuZvdAurdNygnHWv1/I88w9fDQhWqLnPncXg5xm5QjoZApQKTBUkMMEdqetfWHmCYpQKdRipuAlLilxQBQAAUuKUUCpuAmKXFFKaBhRQKKQBTqTFKKAFFGKBS1IxAKWlApwFK47CClwKMU7HFK4CYoxTgKXFK4EeKUU6lAouISinYpMUhi5pPeiigQGhaMUoFACilxSiipGJQaWigBKUUmKUUAFFLijFADaWloxSAVacBQop1JlCYopaXFTcAApcUopcUrjGgUuKWikMMUoFFLSuMUUtJSikMKUUUUgFFL2oFLSKEFKBRS0hhS0UopDAdacDTcU4Uih4paaKcKljFFOFNFOFSMcKcKZTxUspCipI/vj61GKkj++PrUsZ5hqd+plPldMD+VZNxLJICc07aP0FT2ljPcuFRDg96+KrY+rJe9LQ+lhhKNPVIx23ZxyTWjpWmSXTjcpC1u/8ACPNCgldfrWrpfkQpt2gNXbDLKuIwbxFJ3fQ4K+PjTqezQ2y0qG3UfKM1bNuD2qxuB6Uor4mo6kZtT3Qn72po6KVtutdHBMkvSuUiJxWrpTnfjNeZXV25HZSlZWN9AM0TwpLGVYA5FIDUgbAzXBzSjJSi9TtVmrM858S2QtrneowCax1NdF4xnDzCPHOa5wV/ReTTqVMBTlV3sfEYtRVeSiPzSikxS16pyDqBSU4CpASlpSKTFAxRS4oApcUgEop2KXbSuOwgHFGKXFOApXHYaBTsUuKUClcYgFLSilxUgJilxS44pcUhDRSj3oxTsUANxzSilxRikAUUuKMUAJikxT6TFADadSgUuKVwEFGKXFFIBKKXFGKAEpcUoFKBQAgFKRTgKXFK4xmKAKfijFK4WAUtGKMUhhinCkFLSGKKWgUlIYtFFFIApaSloAUUtIKdSGJSjmilFIYtLSUopFAKWilpDClFJSikMUUtJSg0ihwpaaKcKljQ4UopBSipGOpaSlApMaHjk09PvD60wU+P7y/WoZR5xo+jvcSK0i8YHFdlZ2ENugCqMiobXZE4AGBgfyq406IhYngV+PYupOc7dD3lVc9WM1DAtm3DtXP2EJuLkKOgNGq6wHYwhsU/w7eRC5+fHWv0TJMZSwWV2nL3tdDw8TSlWxCstDdewZVGBVSRDEcNXRm5tjGDuGMVyGuXwNztj6Zr4vEwVRur1Z6duVWNGNxjrWrpQJbNcfDeMMZNdboEhkjDYrw68XGNzooyuzfD9KmTlarRgFsZqdnWJCWNefKDbUUtWdyfVnH+MbdFIkHWuXUVv+KbwXE/lqcgdaw1Ff0HkdOrSy+nGrvY+NxsoyrycdhMUAc1IRRivYucdhAKeBSAU9RSbGNxRin4oAqbhYQCnYp22jFTcqw3FLilxSgUXAQClxSilxSuMbijFPxSYpXATFKKXFLSuISlxQKWgAxRilApQKQhMUYp2KCKQxtLRRQAUoFGKUCgQYpcUUUgENJinUYoATFLilxRRcYmKMUuDSikACilFFIYUUUUAKKWkFOFIYlKBS4opAFJS0hFAwopQKXFIAoFKBSikAClooxSKAUtNxS0DHUtIKdSGFFFFIoXFLRS0hi0UUUhi04UgpRUjHClFIKcKkYopwpAKcBUsYoqRB8y/WmKKlQfMv1qGUc43BBHoP5VnalNKRsQnmrKTCQDHoP5UvlhjkivyzlSd2ejcraP4da7bzZeSa25/DCxRboeGA7Va0a5EZEZ4FdMhDpn1rycVXrRqb6HfRpwcTyq+nvrKQxEsRVKMTzvvfiu98UabG0ZmVQCOa5AcdK/RcmyjD4/DRryl8jxMXXnQm4WNTSNGa4KluRXaWVktpb7QOQKz/DM8JhXJAOK1NRvIoIWO4dK+HzHA11mLwsIu19PQ9nDTpqj7Rs5bUtWntb8qOVFVrrXLidMLxVC+l+0XTyep4qACv2TD5XhYQg5U1zJI+ZqYmo5NKWjBizsWY5JoApwFPAr1b2OUaFpcU/FJilcY0DmnAUoFKBSbATFKKdijFTcYAUuKBS0hiYopccUUAAFLigUopAGKSnUlIQgFLilooGGKAKWjFIQtLSUooAKKKWkAlFKKXFFwEFLilApaQDaAKdiilcY3FLilooASjFOxRii4FzSNOn1S/isrcqHkzlm6KB1NanibwzLocENx9pFxDI2wnbtKtjP5VZ+HQB8QH/Zgc/qK2vigxOnWMY6NOSfwU/415NXE1FjY0Yv3ev4nq0sNTeDlVlv/wAMed0e1PC4rovBvh9NXu3mulb7HB94A43t2XP869CrWhRg5z2R59KlKrNQjuzm8UYrrPHej6fpclrJYjyvP3Boc5HGORmuVxSo1o1qaqR2Y61GVGo6ct0JSiiitTIWiiikMWkooFAh1FJSigYtFFFIYtLSUtIYlLiiloKFFOpopaljFoApBS0hi0opKUUhodRiilFIoBS4oAp2KkLAKcKAKeBUtlWEFSqKaBT14qGykhQKkjHzL9aaKkQfMPrUMuxyMMAiUD2H8qmVaW6IjYc9h/KiNgwyK/MZHciaA7ZFI9a67T5A0IJNctZwmRwa6GAFIworx8bZ2R24e61Kfii6RLVkzyRXEqM81veJo3LqxPGelYyrgV+y8PYenh8vgoO99T53HzlOu79CW1nlt2zG2Pan3V3PccO5x6VDilxmvbdODlz21OPmlblvoRbacFp4WnAVbYhgWnAU7FAFK4xMUmKdRilcBAKcBRilApXGGKXFGKXFIBKSnUhoAKKSloELRRQOlIAFLRRQMUClxSCnCkAmKXFGKWkAlAFOxRSuAmKXFFLQAYpRRRSAKXFApaQCUuKMU4A0hjQKMVJtpQmaVwI8UYqbZSFKLgbvgO6trHXDJdSiJJISgduADkHn8qv/ABD1S0u57S2tZlmMO5nZDlQTjAz68VyJB6UgHtXI8LF4hV29UdaxU1QdBLRixxySyJHGpaR2CqB3J6V7Ho1lDo+jxW5ZQIk3Sv2J6sa8ht5ZLeeOeI7ZI2DqcdCDXQ694su9T042aW62wkx5rK5O4eg9BXLj8PVxDhCPw9TowGIpYdSnL4uhleJNQbVtWlvMnyh8kKnsg6fn1/Gsulzj7xrqdB8IXOohLm8LW1qeQMfO49h2Hua7ZVKWGprmdkjkjCriaj5Vds5akru/GPh7S7DRxd2kZgkjdVwXJ8zPbnv3rhaMPiI4iHPAMRh5YefJLcBRRQK6DAKKXFAoAKUUUUgFpaQUtIYtFIKWkULS0gpaRSFooFLigYAU4UgopALilpKWkUgFPHSminDpUsaHCnCminipZQoFPFNHWnjpUMpCgU8CmipFqGWkKBUiD5l+tIBUkY+ZfrWbZdjhrtm3Dd6D+VRRzOjetXpog/Xrgfyp9jZxl9zkcV8jmeT4mOIcqUbxexpQrQlFJvU29EUmIM4rWyoGScAVzlxfGEhIDwOtQzanO6bQcZ71EeEa1VxnOe+5s8wpwukh3iC4WWTYpzg81kKKkfLEknJpAK/R8NQjh6MaUdkeFVqOpNyY00AU7FGK6LmYmKcKXFGKVxgBS4oFLSGMIpRS0YoAMUoFFKKQCUUGkoEFFLiigYlLS0UAJilxSilxSuIbSgUuKKLjAUtGKWkAlKKBTsUgCiiikAUuKKAKACl7UUopAAFOxSDrTwKTYCAVIFrO1LV7LT4y80m5h0RepPpXLa342ksUO5FikcfJAv3h7se30rzq2Po09L3fkerh8qxNZc1uVd3p/wAE7svbxKWuLmKBR3kbH/16y7vxd4SsGK3GpyTMOogQY/M14brOtX+pzNJPO20/wKeBWUz+prxa2Prz+F8q8v8ANn0FDKcLTXvrmfnovuX+bPoO28e+CJXC77kZ7s+P6VvQ6n4Vvo99rqbx8Z+bDCvlyGK4uJAkETMCcbgOB9TXZaRBPbRi0tOh5dycbj/hXA8ViYaqq/nZnoxy/B1NHRXyuj22CSwml2rehlzwyp1/OtUaMkse62vEY9lkG3P4814jLql3olubu4Y+UvQK2Sx9BVC3+LXicyg2M9rBEp4iEQfP1J5NV/aOOcrxmmvREyyfLlHlcWn6u/8Ake13FvLbStFNGUdex/nURBNN8H+MbTxx4ama4gS21jTcNKiH5XQnG5fb27H61O688V9NgsV9ZpczVmtH6nxmPwjwlZ073W69DX8Fpp662smoFAAh8oyfdD++fbOK7DWvFen6dmKBheXHZY2yq/Vv6V5vg45phXHalWwUK9VVJt+gUcbOhSdOCXqWtZ1O+1W4867nLAH5IxwifQf1qhinYpK74RjCKjFWRxSlKb5pO7GkUAU7FLiruSNxQAadiikAmKMUoopDEpRQKUUDAClopaRQUtFFIYuKXFFFIYUtFFIoKWgUopAAp4popwpMocKcKaKcKhlIcBTxTQKeoqWUkOFSLTBUiis2aIcKlj+8PrUYqSP7y/Ws2Wcieo+g/lRkgcGhuo+g/lSV7J5I0jNMIqQ001SER4opxppqhWCkFBozTHYcKKQGlpDEpRRRQIKWiikAUtJinYoASkxzTsUUAJS0UuKQCAUvSlxRSGJSilxS0CEpMU6jFIAooxS4oASlFGKXFIAopcUYpDCiloANAABTgOaAO1JNNHbx75TgfzrOpUjTi5SeiNKVOVWahBXbJCAFLHgDqa5LxL4rt9ODwqwZyMBQf51m+N/Gq20bWNqR5pHJH8NeZefJdzebcuW5ycnrXyuIxtXEabR7d/X/ACPtsHltHBpNrmqd+i9P89zop9XmRv7SuG3zP/x7xnog/vY/lXOXc73ErSysXdjkk0txK9xJvbtwPYVEy4Ga5LWPQlJshOSKngihT55hvbsnb8aiLYNN3GoYLQ0zqMhTywdqqOAOBTI9RmUttkILe9ZxamZOajlRfOzQuL2a8dVdyyRDCAn8zWDq1sbG4jvI/lSUkcdAw6j8a1LPBlx7V0dr4SvvFmj3VhprQi8iZLiITMVDAZDDODjIb9KmUlBXexajKptuVvh54nfRNTuNRVA6tZSRmInAcnGB+dekaL8UtKllEeq6NJEp/wCWkEpyPwNeM6hpGs+FmWDXdMuLNmOEZhlH+jjg/nSW15BL0bafRquFWSV4Sav2ZhUoU6jtVgm13Wp9WaWdL1+2+06DqC3A7wy4Vx7VBPC8UjRSoyOpwVYYIr5/8La/faDqKXdpKQAfnXPDCvpDw7q9h4r0aO4kbD4wsg+9E3ofUe1d1DNalGSjiNYvr1XqeVi8jp1YOeF0kvs9/QySuKYRV28tZbWZoZlw6+nQjsR7VUIr6mElJXWx8c04uz3GUUpoqxBg0lLRigApKWjFAwpaKAKQwFOoAoIpFBQKMUoBoGLS4pBThUjQYoApaXFIoQCnAUAU6lcaEApcUuKcBU3KEAp6ilC09VqGykgA4pwFKoqQLWbZokNUVIooC08CobKSACpEHzr9aQCpEHzr9ahsuxxp6j6D+VJTmHI+g/lTTXtnjiGmGpMZOAOtbOkaHLcsrMvFROrGmryZpTpSqO0TIt7Sa4YLGhNb9h4WmnAMmRXY6Xo0FsillGa24wiDCqBXhYjNJXtTPeoZZFa1Dk7TwbbgDeuavr4QssYKLXRrJUquDXkzxuIbvzHqRwlGK0icbd+C7Z1PlgA+1czq3ha5tFLRgsB2r1qo5oY5kKuoOa1o5nXpvV3RlVwFCotrHgjo0blWBDDsaTFd/wCL/DYAa5gXB68VwZUqSpGCK+rw+JhiIc0T5nE4aVCfKxuKKdijFdFzksIKUUYooAXFGKKWkAlLRSikAgpcU6kNACUtFFIBaKKKAEpaSloAKWiikAtFApaACnAUAU9R7VLARVrjPG+ptbSTgN8lvH09WIz/AIVt6x4itNOWQKVkdOpJwqn6968m8S66usPdhHLSz9kXC5H/AOqvDzDEQnH2cXfXU+myfCVaVT29SNlbTv8A1Y4+4upLm9YuS7u2WNaCIQoyfwpumWccUBkuGId2IGOtWW8hRgEn6mvGSPoSLnpUMjknA6U6SQdBwKgZs0mMCaaTSE80Cs2MUmjBNQSyASJGOrGraKSMVLKRNp8e+cCvdPgNZmTxBeT4/dw2hB/FgB/WvFLJPL+c9RX0/wDBXQZdK8K/brldtxqREmD1EYHy/nkn8q5az0OmHuxOp13QdO1fT5rK8tIZoZhh45EDI31B/nXy58W/hPd+FFl1fQ0kn0pfmlgJLPbj1B/iT9R79a+uwOcGvPvF/wATvAGkanLoWsag8s8Z2TLBAZViPoxHGfUDNclNTi7wLc4yXLU/4J8iaTqBIEUpzn7rV6v8I9efT9bWxlkxb3Xy4J6HtXK/F3wtpOkyQ+KPCN3FfeGtRkKq8ByLabqYyOq56gHGOR6VieHL+Uz2k6E+Yrjkeua7pJVIMypydOaPrrVNl3Yq4IMtuMg+qdx+HX86wHGKsaLcFvKSU/621YkH6f8A16ryGvockqOWH5X0PkOIKEaeLcl1/r+vQjxSU6kr3z58SilpKACloopDAUtJS0DFFFApaQxKWgClxQMBThSU4CpY0LilAopRUlgBTgKAKeBUtlIQA09VpQKeBUNlpCAU8CgCngVDZSQKKkApAKeBUNlpCqKcFoApw6Vm2aJAoqVB86/WmrUkf31+tQ2WcU/X8B/Ko6e55H0H8qks4TPOqAd+a929ldnipczsjV0DS2uZVd14r0Kxto7eIKFGaz9DtFgtlJHOK1N1fL4zESqzt0PqsJh40oJ9SYNmnA1ADUitXntHemTCnKcGogaeprNotFlH9akqopwasRvkc1nJFDLyFZ7d0YZGK8o8QaPcJqDmGIlWPavXeKgktIJG3MgJrsweMlhm2cuJw0cRHlZ4rLYXEK5kjI/CquDmvZtQ0i3uIWAQZx6V5f4g09rC7YbflNfR4PHxxD5dmeBi8C6C5lqjJxRigUtemeVYTFAp1JigQUtApaQgPSm040mKAFFaOiaTc6xefZbVkVgpZnfoorPArufh5GtpZ6nq02RGi7ePRRuP9K5MXWdGi5R36HVhKSq1VGW3UyrnwVrkLHy0huV9Y5MH8jism80rUbIFrqxniUdWKHb+Y4r0S28a6JNgSSS2zHtLGcD8RmtKfU7G40S7vIJo54EifJU5BOOhryFj8XTaVWH4Nf8AAPWeBwtRN0p/jf8A4J43RigDaAKK+hPnxKcKSlxQA6gCinLUgKBzWD4q1pNPt5Ilk2sFzI4P3R6D3NbV7cLaWctwwz5a5A9T2FeJeMdUkurlrcuT826Q+rV5WPxDgvZx3f5HuZRhI1ZOtUV1Hbzf/AMzWtXl1FyCSsQPypVKCPyYvNP3pOnsKrgAsAelTyylvoK8Fo+qu27sd9oDkxHqvIHtULtzVO6QyEMrFXXoRUcb3R+Usv1IqGx3LhyaZ3qIZXmaQlfQDAps13Cq/JlsdqlsZLKwVCx7VD54SMZ+8efpVFrtpJkUqzFmCqgHcnAr3Sz+F+k+EPDd5418Wy/2klhD5qWYXbG8nAVSOrZYgc8e1c9SqoadWa06bmm+iPKV0O8tLKz1e/UxJfFjbRsMM8Y6yey5IA9easW8QkcLH8zHsOTXUWng/wCJfji6i1i/06TZdjML3DiKOGPPCqnVVHYY969k8AfBvTvD8sep6vONSv0GQoGIYvoO/wBTWLrKMd7vyNlTSeuiOB+F3w+uPEdxa6nfIY9HjbewIINwQeFH+z6n8B7fSkShECKAqqMAAYAFZz634fs8QTazptsy8CNrqNSPwzWhbywXUXnWs8VxF/ficOv5iuaTlJ3YOS2RKpwQe4rmbTwD4NtpbiaDw1YST3EjSyyTxec7MxyeXzjk9BXTDkYAJPtXyR8YPixr2ua3f6Lo1/Jp+hW8rQYt22vdFTgs7DnBIOFHGOua0o05VHZGU5qCueyeKoPg9pljrGk6pdaRpR1SAQ3UNq/zZByr+WmQHU8g4Br5r0Gws9P1CMS65p8lpFOcTB2BdA3DY28ZHOK5eEIsm7AJzkk10+lXaHCsAR6EV3KgoK1zGNVuVz6H0TUtP1V/tWl6la3iQ2+wrBKGYZPJI6gYGOlaBrwvw9YyL410G50t0tGmulV+PlPU9B/eAK475Fe7yY7DAr3soUYUnCK2Pms95pV1OT3W3YhNNNOam17Z88FJmlpMUxi0tIBTsUhiU4CjFKKVygpaUClxSuOwgpeaUClAqbjsIBTgKULTwtJspIZinAU4LmnBam5SQgFOApQtPUVDZaQKKcBSgU5RUNlpCgU8LQBTxWbZdhAKcKUClAqblpCgU6gU4CoZaACpIx86/Wminx/eX61LKOHYFjwM8D+VbXhiENcZI712cfhi2jjA2D7o/lT7TRorWTcqgVpVzGnODjEKOXVITUpFuNdsYAHagmpXGBioWrxU7nt2sKDUitUFOBoaBMsqalQ1WU1MjVm0aJk+KcOKarAinA5rJli7iKkjfPWosUq8GpaGWa5XxlpqT2zShRkCuoU5ArO19GksWVRk4rTDTcKqaMq0FODizxl1KuV9DSGtK8066jlkYxnGaz2Ug4Iwa+5jNSV0z4ycHF2aG0UtFWYhSU6g0CEopaKQWOi8O+FrvWLf7UZVt7ckhWYZLEeg9K3tatH8PeCXsBKsks8u1nUYzk5P6DFVvC3iu1sNMjsL6KQeTkI8QzkE55FZvirxF/bUkcUMTRW0RJUN95j6n0rxJRxVbE8s17id/u2PZjLDUsPzQfvtW+/c56NXkYhI3kI5O1ScV2+oIum/D2C3xtkuypbsfmO4/oAK3fBCWQ0C3Nrt8wjMxH3t/fNc38Rr+Oe7trKGVXEAZpNpzhj0H1x/Ok8Q8TiVRUbKLv8AcNYdYbDuq5Xclb7zjT1opaTFe2eKJTgKSnCgAxSrS0oFTcTOZ8dX/wBms44QeoMjD9B/WvEbyQyTySMcljmvTPiRcE3k8efuKqj8s/1ryyZvnNfMYqfNVkz7jA01TwsIrtf79RM4pN9Ic4pqjPNcbZ2oRgc0oHcUkrbFJPpSI2IlJ7ioKB+RiouFycCkmmjjGXYD271WMj3cscUKt8zYCgZLH0xUtgdj8IvDR8VfETTLcputbR/tc5xxtTkfm20V9kXsunWmnyyam9tFYwruke5wI0A5yc8V5D8KdO0X4U+DJfEHjG9h06+1TBKycyBB92JFHLN3OO59q8e+MvxMl8e6nDBYJPZ6JacxW0jDM0mT+8cDjOMYHOOfWuFU3iKnMtjeVRUo8j3PSPiB8foYJpLLwVZLdEHB1G8UiP6pHwT9Wx9K8W17xt4v8QyM2s+Iby5Qn/UrJ5cQ+iLgfpXN7vWjNejCjCn8KOKVSU92aVrBay8yxhiepNbujazq/hO6TWPDV7LaTwHc8QYmOVe4ZejD2NcvDKVI5rWtrtQmGxgjBqpK442PXfDvhT4sfEOxPifUvF1xplrdqXtYVkdA6HoVjQgKvp1JrxnxNpl1ouvX+kXrK1zZzGN3TOH6HcM88g5r7B+BFy9x8IvDhbJ8qGSIH2WVwP0AryX9pjwTNb6hH40s4SbaVVhvdo+4R9xz7c7T/wABrhpVnGtyPY6ZU1KlzJangoOKu2VwUYc1SbrxSKxBr0GjjTsd3pWpvbSW10jfPbzxTKfQq4P9K+kLoDzpMDADHH0zXyr4Shn1bXtO0iEEtd3CIfZc5ZvoFBP4V9V3bI8rsgwpJIr0ctTUpfI8XOZKSh31/QqNTKkIpuK9xHzthBRinbaXbTuVYaBTgKcBTgtTcpIYBTgtSBKcFqWylEjApwWnhaeFqGy0iMLTttSAYpQM1NyuUj204LT9tOAxSuOwwLTttPxSgVNykhgFOAp+KUCpuUkIKcBSAU8CpZSQAU8UgFPAqGy0gFOpMU4DJxUspIBThUqWs7/djJqcabdnpGazc4rdmqpyeyKgqWP7y/WpWsLlOsRpoRkdQykc96nmT2Y3CS3R10lwpOM9h/KomcHpVB25HPYfyqaEk18JgM0lXq+zmj35KyHvzURqVsUw819OjFkdKKDSVZI9TUqtVfNODVLQ0y0r1MjZqiGNSo9ZuJakXQadVdHqZWFYtWNEyQPio5SHGDzSnkVG3FJIGypc2EMyEbRzXn/iXS2tpTIo4r0xTWB4qgV7VjjtXp4LESp1Euh5+MoRqU2zzIUYpWG12HoaSvrD5NoWikpRQIMUuKBS0gFFHegUuKQD0kdc7HZCeu1iM0w0UhpAIKWjFKBTATFLUsEMtxMkEEbSSyHaqr1Jq5quj6hpez7bB5Yk+6wYMD7ZHes3UipKLerKVOTi5JaIzxSg0lLVEHlfxOV01ic9mVWH0Kj/AArzsxEhpT0zgV7B8TdJubqyXUbWJpPJQpMqjJC9Q2PbnP1ryeRGSxhyOo3H8TXzOKg4Vmn6n3GAqxq4aLXRWfy0KMhwKZCdwB9qWUgpkVmtLKilFfaPauJ6HaT6hNz5K9T19qpktjAdgPQGpLK3ub67S0s7ea6uZD8sUKF3b8BzXVf8Kx+IbxCSPwrd7euCyBv++d2axlOK3Y1GT2RxbAdTyfU1q+Gb690rXLDUrGAT3dvMHt4mQvvfsNo5P0pmo6de6XeNY3un3FreJ95bmMxke4B6/hXr/wCy7okU/ibWdWu8TPZ2qJFuHCM7HOPfC0VGoQcnqKCcppbFjRPhN4s8daiPEHxC1G6j3j5bbcPOK/3f7sS+wGfpXafEL4fafD8Mb/Q9E0u2tWgAuYFhT5nlj5+ZjyxI3DJPevXd4AxVW8CSxFG6Hv6V5NSvOUlK+3ToenSpQiuW2/XqfAAAYBl6Hmjaa9f+KvwzutH1S41nRrZrjTLhjJLBCu5rZjySAOqE88dPpXlc3lDIVlyPevcpVY1Y80Tyq1GVKXLIp5xQ0zKvAJPYDqTUtva3V9cpbWNtLdzucLFAhdifoK9/+D3wak068tfFXjwQ2SwSKbLTpnXLSk/K0nYYPROpPXpipqVIwWpEISk9D2T4a6NN4d+HOgaNOuy4t7RTOvcSNl2H4FiPwrUns/tiS2d1bpcWFxG0c8UoyrqRjBB61srBMwJMbfU8VgeIvF3hrwzbvLrOsW0bqCVtoXEkz+wQf1wK8SUZSfMz1aTsuSCuzxHxV+zwz3clx4U1SOKBiSLO9ydnsrjnH1B+tcpF8APGz3Wy4uNNtYu8hmL/AJACve/hV4zuvGthqd5cWEdn9kuhEgiYkMhXcM5/iHftXZzqSDWqxNWMbXFPDxjUcZrU4X4PfC7w54NaW4YNqerzRGJ7ydcBEP3ljX+EHueSfWnazYtp2p3FpklUb5Ce6nkfpXdaYjLcg1z/AI9ULrKEdWgUn8zXuZNXnKo4ye585nlCCipR6HMEZoA5p1LivqbnylhoWnBacBTgtK5VhoWnBacBTwBUtlpDAtOC08ClxUXLsNC08ClAp2KhspIQLRtp4FKBSuVYZtpQtSbaXFTcdhgFKFp4FOxSuOxHjFLin4pMUrjsJinAUAU8ClcqwgFPAoAqxaQGaQKPxrOUrK5pGLbshLa2knYKo/Guj0/RY0AeQZPvVzTbKOCMErzWhkAV5FfFSk7RPYo4aMFeW5FHbxRjCoKk2j0FIZKTfXFq9zs2HFFPVQarzWFvMRuQdasBxThQm47CaT0Zx7zncMf3R/KrMN0FTms4kbh9B/KnFhivzyjXlh6nPHcyUrosy3hLcVNHdLt5NZZpCa9CnnGJhJybuRY2kcOOKdiqdgSQMmrrMo4Jr7bCYj29FVH1FcTFJTsjGaTrXZcAzTg2KYaM0WAsI9SpJVMGnq1Q4lqRfV6Qvmqok4pfMrPkK5iyprB8UXAW1cZ7VrNJhCa4jxZesx8v1rswdJzqo5MXVUKTOVY5dj6mlUEnaASfQDNIcKCx7CvYPDOn2dhpkC28amRo1Z5cfM5Iz1r38Zi1hYJ2vc+fwuFeJk1e1jyDFFeh/EPS7f7AupxRKk0bhZCoxuU+v0OK4O0tLq9m8m0t5J5MZ2xrnH19KvDYmNel7TYjEYeVGp7PcgFOqe+sbywlEV5bSQMRkBxjP0Peq4NdKakrpnM4tOzFFOFNFPFJiCjFLQBSHYSir+m6PqOpsRZ2zOoOC54Ufia2v+EG1cqD9otQf7u5v8KwniaNN2nJJm0MNWqK8Itof8ObTztTmu2XK2yYX/eb/wCtn86n+JN6XntdOQjEf72T6ngD8s1XtH1rwgsplskkhmIy2crkdDkdPxrAle+1nVS4Xzbq5fhV4H09gBXDGl7TFPENrlS0/r7ztlV9nhVh0nzN6/19xU9qK9F0XwZZQKs2pYu58fc/5Zr/AI1yXiu3sLTW5YLAqIgoLIpyEbuB+ldFLG0q1R04a269Dnq4OpRpqpPS/TqZYOBXhfxAhMGt38ewKBMSAowMHkfzr3MYxXC/EvQmuoDqttEZCibbhVGSAOj/AIdD+FRjqbnTuuh05VWjSruMtpK3zPDpGZSw7GjS9OutX1W00qwj827vJVhiT1YnHPt3PsKnuYWLsF5I5HuK9D/Zw0xbv4li4lUZsrGaZM9mO1M/kxr5mq+WLZ9dFXaR7n8PfAmj+ENMW1sYlkumA+03rL+8nbvz2XPRf612aJEnClc+maJI/l2jj1qjPbJjdjBHQivnpN3u9We1BJrlTsih408KaP4v0p7DV4ASozDcIAJIG9VP9OhrD+FXgJPA1lqEbah9unvZldnEewKqghRjJ55JP1rsbOV5ISrtuKnGe+PepelUqknHl6EOmlLXcV2wKzruRs4FXXOaqzR7jWUjopWT1M1gXOGGarf8Ip4avp/OvtBsLmTu8kCkn8cVqrBz0q9axbcUoXTub1ai5bC6XpWmabH5em6da2S+lvCqfyFcZ8d9JutT8BO9ojStYXCXTRqMlkAKtx3wGz+Br0JBgUrYIxwfaujdHnQm4VFNdD4+Gl+Kri2RV+3fZ8fLHJcsq4/3SaydR0y+08gXlq8G7ozDhvxFfVGqeDIJpTJp06WyMcmGRSVH+6RyB7VWtPh/YyODrLx38IYOLYKQhIOQWJ5PPasPfvZn1H1zBKm5KTv2/r/Mq/AfR59G8AxSXURin1KZrsqwwQhAVM/VRn8a9CJzQW4AwBjsO1J3re58tOTqTc31LenLmbPpXE+LboXWuXDIcpHiMH6df1zXUanqQ0vTyUIN1MCIx/dH96uDbknJyT3NfU5NQcYuq+ux8dnOIUp+yj03IsU4ClApwFfQ3Pn0AFOApQOKcKls0SAClFKBTgKhstIAKdilxSipuUkAFLilFKBU3KQAU4CgCnYqblWExS4pQKWlcdhBS0UopDsJRinYopDsNApwpcUoFK5VgUc10uiWoVAxFc/AuZFHvXY2ChYB9K4cXO0bHfg4XldlotgYFMLUhpK8qx6rY7NLTaWmIcDT0PzCo6en3h9aljPO/tLMQf8AZH8qQ3Zzirf2VVAGP4R/Ks+7g8ts9q+BtCTPO95K5dhkLinkHPSqdtcxIMEir8U0co4Irmq03HWxrCSfUlhn8tcUjXLs3BpkiDFRgYNavMa6pqnF2SNeXUt/amC8VPbz561QFTW+N9a4XNMRCtFuV0NxNQcjIpaFKhRzSPIqqWzX6RTlzxTXUzuGQOpprTIoOWFc9qmseTLtBrGuNZlk4UmvRp4Kc1c5J4qMXY7cXCseDmpFkz3rldNuZim5smtCO9KnJqJ4dxdkVGvdXZ0ORsOa5XVdJutTuzFZRb3HLMThVHuatXOrqife7VreDdRtrmyuFVgZ0ly698EcH6Uoqrh4uqkKbp4iSpNnJ3fgnW4oy6CC4GOVjfn9QK7LwdPLJpMNvcxPFcWv7mRJFwePun8sflW4sqHqcU7KHkEE1y18dUr0+Sovmb0MHChPnpsz/EdjLqWj3FlBtEku0At0HzA5pdF0m10ezW2tV93kP3pD6mtD3rnPE3iq30eQ2iQtNdlQwVuEUHoSe/4VhS9tVj7CnqtzWr7Kk/bT9CP4itb/APCOnzNvmmVfK9c55x+Ga8vBzVnVNRvdWu/tF3OZnHCIo+VB6AdqZFaXbjKWk7fSJj/SvrMHQ+q0eST13Pl8XX+s1eeK0GCnipBZXijLWlwo94m/wpu0qcMCp/2hiunmT2OWzW4AZrc8K6ONVvyJci2hAaUjv6L+NZUadz09a9B8ERxLopli5Mkzbj9OBXn42u6VFuO+x24KgqtZKW250sMUUEKRQxrHGgwqqMAU6mxtkYp1fIu99T61WS0GyxxzRtFKiujjDKwyCK821u2l8La/BdWSgwtlog3IHZkP516ZXK/EVEbRIpCPmSdcH6g5r0MvquNZQesZaNHBj6SlSc1vHVM5XVfFOrX4KCQWsJGDHCSM/U9awB1JpSc0AV9XTpQpK0FY+VqVJ1XebuOFOxSKKkAptkWOdv8AwZ4bv5Xmm01Ip3BHmQsUwT/FgHBP4Vwfgea4+H/xb0+PVf3drd7rQ3HRHSThXz6bgufTmvXwKqavoml65ZNZatZpcwHkBuGQ+qsOQfpXnYvBwrQajoz1cHmFTDy97WP9bHqJDA/N1qORAy4rlfDN/cabYR6fqN89+kACQXMq/vSg6CQjhiOm7jPfnk7smsafCgkuL23gRjgNLKqAn05NfD4jC1aErTi/Xofc4bF0cRHmpyXp1+4njtkicvk5P5U5yB3rg/FvxZ8F6DC4bWIr64HS2sWEzsfqOB9Sa8u8OftAyt4imTxBpiwaRM/7l7fLyW4/2/7/AKnGMehrnVGbV4x0Ot1YX96Wp9E9aQiqGi6zpOt2CX+j6hb39swz5kDhsexHUH2NXQ2TxWL00ZstdUSKoqZMCq+4AU5ZKExNNlwNS5qsHHrTvMFXzGLgTUueKjDjqTUFzewQqS7jPZRyauMZTfLFXZnOUaa5puyLWazr7U0tyVjw8np2H1rMvNWnmBjiHlJ3Pc/4Vm5r3sJlEm1Ovou3+Z87jM5ik4YfXz/yJLueW4laWZy7t1JqsRUpph4r6eKSVkfKu7d2MApwFOxmlAqrjSEAzTgKcq04LzmpbNEhoFPApcCnAVDZaQ3FOApQKUCpuVYAKcBSgU4CpbGkNApadijFK5VhMUuKUClxSuOwmKKXFaek2BncMw4qJzUFdmsKbm7IrWtlNcEbVwPWtaDw+WGXY1v2ttHAoCgZqevJqYybfunrU8JCK97U5yXw/gfIxzWZdafPbE5XcPWu3+tRyxJKuHXIqYYyafvajnhaclpocPaxSmRWCHGa6+yDeQAwxU0drBGMKgpXIQYAqa1f2uiRVGj7LqMam5pGbNJmskjdjqWm4p4BpMBRT0+8KaBT1HzCpYzi1lWQAj+6P5Vg+I7wQREA81BaaqIsK5xwP5Vi+JL0TnCnNfM4XASeISktDw62JXstNyot/M54bFbGkahIJAhOc1y8T4rU0uVROCa9rGYSHs2rHm0a0lJanoUUm+MGkNZkd8iQjmnW98spxmvg54aau7aH0Ua0XZXNGlDbeaYh3DNOK5Fcq0kmze+hDPqEisFUE1MLiZ4CSO1RrAC+4irY2hCCO1few4jwtKEIKOxxqhUbbbOM1LL3J3daq7ApBrY1q3Jfeo5FYTOWYA8c1+i4HFU8XRVSm9Dya0eSVmdhomxoeatzQo3ArH01mSAFauwXDFsVyVIPnbR1xkuVJmZrcRiUnPFYFtNNbzie3meGQdGRiDXR66+6M54qPRPCWo6htnn/ANCtDzvkHzMP9lf8a7qVaFOjeq7I86tSnOramrsrp4o14YUalI3bDqrf0rt/Bl5rFzFO+rROoyphdowm4dxj8qsaToGnaeQbK13zDrcTfM/4dh+FbkVsqt5jsXc8ZJrw8Zi6E4uFOCXnbU9jCYWtCSlUm35dCfPAqndaVp13crc3VlDPMq7Q0i5wPSrlLXjxlKLvF2PUlFSVmrkMNrbQDENvFGB/cQD+VTUlLSbb3GklsFRyRRSjEsSSf7yg1JSUk7bA1cyL/wAOaPeqRJZrGf70J2H9OKfomjppFrJawTvLE0hdRJjK5AyMjr0rUorZ16rjyOWhkqNNS51HUrOWQ9MGnrN6ipiAeCM1DJDgEr0HaoTT3NNUKZ/auK+I2oI622nRsCwPnSY7cYUfzNWtW8V2lurw2UbXE443MpVFPuDya4K4llnneaeQySSNuZm6k17mX4KSmqs1a2x4mPxkXB0oO99yPvT1FIBUiivfbPAsKoqRRSKKkUVm2WkIFp4oC04LUNlJABVXVtN0/WNPk07VLSO6tZOqOOh7EHqCPUVdApQmTWbs1Zlq6d0eWa/8G9Au7dRo802m3CZ+aRjMj+zA8j6j9a871j4ReM7Ms1tYw36Do1tOvP4Ng19MsFVdzssaju5wP1rR0K0F0/2gr5tuBlGHKufY968+vh6Ci5beh6mGxWJclBO9+58jaB4D+J9vqQl0jR9U025Xn7QsogH/AH1uwf1r17QtS+NmjxLHqZ0TUVXjF9LiTH+9GB+ua+gobZeoTH4Utxo9vfJtuYY5F/2hXg1I0m/eVz6anOcVueeeF/Eet6j5y6zpNpZSRhSptLszK+c56qCMcetdEL0d0P51cfwVFbzNcaddGPcu0wycr+B6isu5tbu1k8q5gaM9j1DfQ14WLh7Oo3Be6fQYOdOrTSk/eLH28D+A/nSf2iykFo8JnGc0WtjJLy3C1X8WSRabo8c21tizKHYDO0EHk+g96zoLmmubYus4JOMFqa8c1rdxhGjdR3KSlTVn/hFopIhJBcyRlhnbKuf1FR+DdPMsEeozj90wzEp/i9/p6V1x5r6CjVnR0puyPlsXRpVpe+rs811PTbnT5hHcJjd91hyG+lU9temanYQahbeRPkc5Vl6qfWvP9Qs5LK7ktpeSh4I/iHY172FxSrKz3Pm8VhHRd18JTIppFSEUm2u65w2GAU8CnAU4Ck2WkIBTgKcopwFQ2WkNxShakC0u2puUkR4pQKeFpQKVyrCAU4CjFKKm5VgxRiilxSGAFGKUVNBC0jYUVLdilG7G26b5VU9K67TYliiBArOstK2lXbrWui7FC15mJqqeiPVw1JwV2Wgw9aXcPWqm45pymuHlO25Y3ClBBqDNOU0mhkp6VWkyTVimGPNEXYTK200uVHVhUsy7Ymb0FcDrGuXEN1JFH0FdmHoSxDtE561VUldnbPdQR/ecVSuNbtYgcOK89k1K7m+9IR9KiDSM2WYn616ccsS+NnC8c38KPTdI1OO8Jwa1wRngiuH8KHC4rqo93mLgnrXlYmioVGkenSlzwTZ4PqlxmfEZxgDp9KpMxfljmoXclsn0H8qTea6YUFBJI+KlJt3J4oizcVdjiaL5qhsJVzzV6WVAmc15+IlLm5bGkErXJ7aZn4Y1oWeRMNp4rmBdFZCVNb+jXClgWNePjcPKEHI7KNRNpHYW+BGM1KCDWcLpdnDVAt9+8xmvjXh5yuz3FWirI2aD0qvDMGA5qYHNcri09TdSTK9zbiRDkVyOoRCK8AHTNdtIwEZz6Vx2rsrXo2nvX6NwZWm5Tg3oeXj4qyZt6dGWgH0q3ZWk0tyIoYy7nsP60nh2NroiCJckDLMeij1NdpbW0NpH5cC9fvOerV9VicR7OTXUqhR9ok+hSstGtLZlmnVbi5HIJGVQ+w7n3rXjiaQ75CcUQxZ+dxx2FWa8SpVlJ6s9OFOMVohFAUYAwPSiilrE1CkpeaKACkoooAKWiigBKKWigBKKKWgDnvEvhyHVUNxb7YrxR97tJ7N/jXm1xBJbzPDKhSRDhlbqDXtVc34u0IajAbu2Qfa4hyB/y0X0+vpXsYDHOm1TqPT8v+AePjsCpp1Ka1/P/gnm6rUiilK44pVGa+ibPn0hQKkUUiinqKzbLSHACnBaFFRajeW2m2E9/eSCK3gQu7f0HuegrNstIi1TUbHSbNrzUbhbeBeMnksfQDua8v8AEfxPuC7JpQ+xw9BIwDSN7+grifGfiy81/UXu5yY4I8rBBniNfT3J7muLuZ3kJLtkn9K56km9D2KGFjBXmrs9z+DemJ8QfFV1d6xcS3ljpKLNLFK5YTOxIRTn+HgkjvgCvpuIRmNVSMBEGFVFwAPQV8PfB/xL4w0bWtRtPBuntqF9qlsIBEsXmbCGyJcdBt+bluOea1/iB4f+J2hRpq/jCa/eGdgGnS/MqxsegYKcL+HFeZUo+1nrK3Y9OMuVaI+zfNROGBX6ipkdCMgivgey1vxHYuJdP8Rapakcjy7x8fkTiu08O/Gjx3pN1bpqd9FrFgHUTC4iAlCZ5KuuOQPXNc88DNap3NVWT3PsXg9KR0SRCkqK6HqrDNc9YawzwRytGxSRQyuvzKwIyDWrFqVuyFjIOBkjvXnSi1udHoR3Vh5amSDJQdU7j/GsO5tBqlxHYfeib5pvZO4/HpXUQTrKocAqG6Z702G3t4Jp5oowjzYLn6VyToRb8jrpYmUE779CWBY4YY4YY1iijUIiKMBQBgAU+4uLe0tpLm6mjggiUs8srBVUDqSTwBWfbXn2m9mgj+7EBz65rjvjH4WvvF/w91WxBKTxJ9ptoVY/NInzAH1yMj8a64q7szjlEhh+M/hS98QR6RpEV9qal9kl9BDi3jPruJBI9wK0/FE8dzqe6IqyrGqllOQT1/rXzb4J1GH+xLZoVETKNrhRj5h3+te//D+eLxNob20rBL2zxtk9UPQH1Gc16NFxoVby22OXF4d1cP7m5GVpMVcu7aS1maGZdsi9RVfbXuKSauj5hxadmMApwFOC04ChspIFFOApQKcBUNlpCAUuKcBS4qblWG4pQKcBTlQnoKm47EeKMVOYnC5I4FQJPF5mxjihO+w7W3DafSnBT6GtW2a0AG4rWyljbTQblAOe4rmniFDdHVDDOezOSQbmAHeuj0mzCqGIrMNsItQ2j7tdJBhYgBWOIqe6rdTfDUrNt9CUsFGAKjLZpGPNFcCR3thSikFLTAeDUiVEKkU1DKRLS0wGlzWYxs/+pf6V5VryL/acnua9TuT+5b6V5R4gY/2lJ9a9rKl77PMzB2iiuwULTgQEqInKClJ+SveseSpanS+FZT5uK7SGRRIoPrXCeEz+/rstpMqY9RXz+Oivas97Cy/do+eGPIz6D+VITSN1/AfypK1PkbDldlPBqTz3bgmq7UJycVlKKeoFhCc5q7bTvGRinWVupXLVLLCqjIrzq04yfK0VGLWqNiwnaRRuNWtg3g5rmo7wwnAq3HqDHvXh18DPmvHY7oYhWszrLWUAAZrRjlBrl9OnaTnNaS3QiPJr5zEYVqVup6lKtpcvapIyWxK9cVxunW99q+tpZWy/Oxyzt92Ne7H2ro7i7+0bYY1Ls52qo6kntXZeH9Gg0izKgKbqbDTSDueyj2FfSZA50IyVtSKlL6zNa6Lcu6Xp9tplklrbZIHLyN9529T/AJ4rQgj3Hc33R+tQxKXYKO9XgAoCjoK9qcn8z1IRSVlsOpKO9FYmgtFJRQAtJS0UAH1ooooASiiigBaKKKACivOPG/xo8A+D9Qk03UdUe6v4jiW2sYjM0Z9GIwoPsTmpfA/xi8BeM7xNP0rVzDqD8JaXsZhkf2XPDH2BJquV2vYD0KgUUVIHn/jXSRaXYv4FxBcH5wP4X/8Ar1zdetalZx39jNaSj5ZFwD6Hsfzrym4ikguJIJV2yRsVYe4r6XL8R7WnyS3X5HzePw/sqnNHZ/mNWpFqMU9a9BnnomWvHPjR4kNzqCeH7WT9xaEPcYP3pSOB/wABB/M+1eratqEWk6Te6nMf3dpC0pB74HA/E4FfK97eTXM815cOXmmcyOx7sTk1lJ2PQwdPmlzdinfSb5NoPCdfrXQ/Df4f638Q9aNhpg+z2UJBvL91ykC+g/vOey/icCrXww+H2r/EHWzZ2RNtYQENe3zLlYVPYf3nPYfieK+1PCnhzSPCWg22h6Jai3tIB9WkY9XY92Pc15OJxCh7q3PbpwvqZ/gTwT4f8D6Iuk6HaeWDgz3D4Mtw39527/ToOwrQ8SaJZ+INGu9Iv4hJb3MTRupHYj+fcVqk0mcV47m73udiR8B6jYS6VqN7pFwczWFw9ux9dpIB/EVW2jnPNdn8abUWfxV14KMLM0c3HqyDP6iuKzX0EZOUVI4mknY+w/gTfDVvhjpM0+JJbdWtmJ6/IxUfoBXYapYW8kDuEww715j+y7K0nw9uIyeIdRlA/FUNeu3S5t5B/smvAre7Vfqd0HdJlfTVEmnwhs/Ko5z+FXGQEYPOaqaP/wAeSj0H9au1gym9TNiCwas4AADpkYrV6n1rLvvl1G2YdwRWovY0xS2ufG3i3R38J/EvxFoSDZavKLy1Hby5PmwPoSR+Feg/BPWzb+Mbe1d8JeRvCR743L+q/rS/tSaX9l1zwz4mjXCyh9PnYD/gaZ/8frzzwjqDaf4m0q+DYEN3ExPtuAP6E16aXtKVzNS6H11rWmrqNvlQFnQZRvX2NcW8bRuVdSrKcEHsa9Hz6Vz/AIl03zAb2BfmA/eKO49aMJiOV8ktjzcXh+Ze0jucvilAp4XFLivWueUkIBS4pwFKBU3LsNApJHWMZNSAVnaqxVDVQXNKwpaIle9RehFJHqsannFYKlmOM0/7NI9dn1eHUz55LVG7c61GYiq9a56W4keUurGrMVkf4qsJaxr1pwVOlsTP2lTcz0uLlnUF2AzXpmgzYsFDNniuDlWFcYxXRaRdAQBd3FcWOiqkFZHZg37ObuzQvuZy61d0yV3GGqiXR+9aGmBAOK8qppCx6NPWdy41JmhzzTc1yo6R4NOFMFOyB1IoYDqUVC9zCnVhUcV9DLL5aMCaOST1sHMti6DS5pQnHWk2GstCxk/MLfSvKfEnGpyV6vKp8puO1eV+J1P9pMa9nKf4jPLzH4Eykv8Aq6P4Kap/d0qnK1754/Mbnhd9txiu5hceYmfUVwPhv/j7FdqyMSm045FeFjo3qnuYOX7s+fG6/gP5UgGelNZuR9B/KrNqAx5pNny5EYWIzRHGd1agVQKgbaG4rLmuK5PBLtUCnySblqsKkTmuWpTV7jUnsQsmTmlQ4NTOBiq560l7ysBs6fdLGMZ5qae4Mh4PFYKyFTV2yaa6uYbWBd0szhEHuTXm1cCufnidUaza5TvPAWmlpH1WcZVCUgz3bu34dPzrtc1UsreOys4bOL7kKBQfX1P4nmrcI3uq+tdtKmqcLH0lGn7OCiXbVdse49W/lU1MzilzWT1dzqQ6lpopakY6kozRQAtFFcT8RPid4R8Axouu35N5KN0dlbJ5kzD12/wj3JFCV9EB21HevniH9qfwobsJceHNXhts484NGxA9duf617L4J8Z+G/G2lf2n4c1KO9hU7ZE+7JE3o6nlT/PtVOLW4HRUUUVIBXlv7RvjW68F/DqabTJjDqmpSiztpV+9FkEu49woOPQkV6lXzB+2xLMsfhGLnyC903tuxGB+hNXTSckmJ7HzA7liS7ncxyzscnJ6k+tff2i/C7wJD4KtNDTQbKe2aBCbryx57uQD5olHzBs8gg8dq/P04YYr3LwH+0X4i8L+FodBvNHt9Za0jEVpdSztGyoBhVcAHcAOOMHA/GumqpStYmJ7/wDCTxfPNrev/DvXL5rzWvDcxSK6kPz3lrxskb1cAqG9cg9zXqNfBPwp8YaifjppPiXULjfdarqJju2AwGE3yEY9BlcD2Ffew6Vz1I8rKTuFcH46shDqUd4i/LcLhv8AeH/1sV3grD8ZW32jRJHA+aBhIPp0P6GujBVPZ1l56HJjaftKL8tTzulFBHNKvFfUnzCOA+Nmo/ZPCkOnq2H1C4Cn/cT5j+u2vKPAXg3VvHXiKLRdMUxxLh7u7Zcpbx/3j6k9AO59s133xN0zVfGHxC0jwposXm3C2u4k/ciDNlnc9lAA/Qd6+jvh74O0vwN4bh0fTl3yffublhh7iTHLH+QHYV5eMxCpKy3Z7+Bpfu0y54R8N6R4Q0C30PRbcQ20I5J5eRz1dz3Y+v8AStYmkJyaTNfOyk27s9hRsLUcpwuc9KeaqXsoRDzUrVlI+Qfj7Mr/ABV1ML/DBAD9dpP9a8/U810nxVu/tvxK8QTg5VbgQg/7iAfzzXM7sDrX0lJWppeR5837zPq79mKHy/h1NLj/AF2oSt+SoP6V63P/AKl/901wPwG09tP+F2iq67XuEa4IP+2xYfpiu51CTyrSRsZOMV4FZ81V+p2wVooZpA/0JT6j+pq3UdsyeUFTG3HGPSpPesWynuU71C91a47MT+GK0B0HPas3UWljlhnRC4UFWA96u25ZoUZlKkjO09RTB7HnH7SGl/2j8JtUmVcy6c8V6ntscbv/AB0tXy4l4EQSKeQAw/nX294p01dZ8NarpDAML20lgwf9pCB+pr4Gty8diFmyHRCjg9mHB/UV6WFd4tHPJ2Z+gVhL51jbTZz5kSN+ag1awCOenpWR4cYt4e0pj3s4T/44K1FavOvZmrWhy2tWH2Sfcg/cycr7H0rNK13N3BHdW7wyD5WHX0PrXH3NrLazNFKMEdD2I9RXs4avzxs90eNiaHJK62ZWC0oFSbaCtdVzlsMArM1kfuya1gKzNaX90a1ov30Ka90wYGAfmtIToFFZcS7nxWjHZ5AJNejV5eplC/QJLnP3RUBmlY8CrogRetLsiHpWSlFbIpxk+pntHJJjmui0i1byRkmseWVE6Vu6RdoYgDWOIlLk0NKKjzak8kMqD5Sa09F8wDD1WM6HvWjpZVh8teRVk+TU9CklzaFuUhVyaoyanbxkgsARVy+H7h/pXmeqGc3sgDnGaeEw6r3uwxNd0bWR1974giiU7SDWFc+JpmJCA1jrE7jDGnLagHmvXhhKMN1c8ueJrT20JZtXvJs/ORWn4Sed9R3SOxHvWYII1rc8NbBd4FFfkVKSii6HM6qcmd+rLtHNOyPWq0gPlik5218ryn0RaOMEmvLfGO0aodvevRdz+W3J6V5n4oB/tEk816+VxtVZ5WZS/dIzVI2UoPFNX7tKtfRHz6ka/hyQLdgHvXepKoKfUV57oRAvFzXoVtEkgjye4rwswSU7s97ASbp2PnFuv4D+VSRSbDUb9fwH8qb1rM+dNBrv5OtQCYlvaq9AOKEkKxfWUVPG/HFZQkx1NdZ4d8J69qyrJBZmGBv+W1x8i49h1P4Cs6iSV2VCnObtFXZlE5pjCul8WeFbjw7b208lylzHMShKoV2tjOPp/hXMBsmsopNXRVSnOnLlmrMVUrrvhvYiXWpL1xlbSPK/77cD9M1yq16Z8PrYQ6AZ8fNcTM2fYcD+tQ+x0YKHPXXlqdP1q1ZDLFvQVUFXLTiM+5qJ7H1EdyzmlzUeacDXPY1JAacKYDThUsY+ikoFSBxXxj8cQ+APA13re1ZL1yILKFujzNnGfYAFj7Cvz+1XUb/VtSudT1S7lvL26cyTTytlnY/56dq+kv21tRk+0eFdKBIj23Fyw9T8qj+v518x110YpK5Ej6F/ZD8JaFr2r65rOr2kF9NpiwpbQzoHVC+4mTaeCflwPTJr1340adD4K0xPiV4XtYdP1XSJYxdpAojjv7ZnCtFIo4bqCD1GK+UPhT8QdV+HPiM6vp0SXUE6eVd2kjbVmTORz2YHkH6+tdx8Y/jrdfEDw+vh7T9HbSdPkdZLlpJhJJMVOQowAAucH1OB0qZRk5jTVj7E8Na3Y+IvD+n65p0m+0v4FmjPcAjofcHIPuK068E/Y61aa8+HmoaXM5ddN1Bliz/Ckih8f99bvzr3usJKzsUFeIftbeGptb+G8eq20ZeXRLkXDgDJ8lhtc/hlT9Aa9vqK6hhubaW2uIklgmQpJG4yrqRggjuCDSi7O4H5jrAxOAM082zKMkV9EeP/AIB6toeoXF74Vtn1bSZGLpbIcz24P8OD98DsRz6jvXnw+HnjLUJxbWXhLVXlJx81sY1H1ZsAfnXoR5XG9yDB+EWly6t8UvC9jCpLHUYpWwOiId7H8lNfogTgZJAHvXiPwK+D4+H6z+JfEDxT69NEY0jiO5LRD1UH+JjwCemOB3J9OmuJJ23SH6DsK46sk3odFGi6h0AIbkEH3FQ30Qnsp4TzvjZf0rHtJ3hlDA/L3HqK3vT0rNOzugrUuTR9TyIehpyIzuqIpZ2OAB1JpZwFuZVH8MjD9TXWeDNKB/4mc6+ohB/Vv6CvrK9ZUafOz42hRlVqciLXhTwtZaHcXuqGNX1TUAguJiOQqjCxg/3Ryfckmt9yTTnNR18nVqSqScpbn11KChFRXQSilNNJAODWRqDHCk1z2u3qW1pcXUjYjhjZ2PsBk1sXkmIyB3ryL49a7/ZHgK8hR9txqLC0i9cNyx/BQa2oQ5pJBJ8qufLt5dvfX11fyHL3U7zHP+0xP9afpdjPquqWWl2wLTXs6QIB6swH/wBeqZxjA4r2T9mLwwdX8Zz6/OmbTRY/kJHBncYH5Lk/iK+gqzVOm32POiuaVj6m0uzh07TLWwgG2K2iWJB7KAB/Kp5o1ljaNx8pp9FfMtnoFaztIrUMI8nd1JqxQTSUmMUU4Gm0tAiVelfCfxV0w6H4/wDEulKuyNLySWIf7En7xf8A0KvutDXyr+0/oMg+Jui3cKfLrUEUB95EkCf+guv5V3YSVpWMJo+lNAHl6NYw/wDPO2jX8kArQLYqhZbYURAchRirmQzVwvc6rFmP7tMubeG6jMcyBh29R9KRWxUit61cZW2MJRvuc7faTNb5eLMsXqOo+orOIrtveuf8TWB+ySXtouJIxudB0YdyPevToYjmajI86thuVc0DI4HU1l606mLANUXv5WPGcGq1zOzj5jXtU6DjJNnmSmmitFhX3VoLdYUACqEI3OBWpFbLsBNddRx6kQv0K7yu3QUzbK1X/LjFJuRfSslPsgce7KQtmb71dDpNkPKFZDzoOlbGkXeEGawxEpuGhrSUebUtvaHoDWno0TRcGqZuVNaWksJWNeTVlLk1PRpKPNoWr35oWrzjVXWO8cH1r066hzC2PSvMNegb7fIPet8sacmjHHpqKZVacAZFRNckjinJbkjBqWO1XPNe57iPJ95lYzORW14TZ/t4JqoLeMCtXw+qJeLjrXPiJp0mkjooRaqJs75v9Wv0pCRspHP7gH2qBGJSvlUrn0bZOigo1eaeL026j9a9IjfCmvO/GPN6CK9TLLqseVmf8Ewk6U9BxTUHFTQoXOAMk19FJnzqLGk/8fi/Wu7SZookIPpXLaTpU5nWQ/KK6eW3cIi57ivn8diqHPrJaHu4KnONNux8/MckfQfyppBHUGruk232mYA9OK3NS0YR224DnFY1cRClUUHueLGnKUbo5XNJSSgxyFT2poOeldNiDuPhKtk3i1Y7yFJXaB/I3jIVxg5+uM16L4j8f6Jos0tsvm3t3ExRoohgK3oWPA/DNeKeHL6aw1uxu4FaSWGdWCICSwzyAB7ZrqPi1YC28Um6jXbFfQrMOP4hw38gfxrmlRjOqubsenRxM6WGfJun+Z002tt428Ga0GtUhurFlmjjRi3yjkHnvgMK8uWXnIPFey/DXwpDpWkrfzyvJc6hbjzEz8io3IGO5x3968c1CGO11C7to3DpDM8asOhAYgVdBwvKMdiMZCbhCpU3a/4YkSX3r2jwrEIvDOmL6wBj+PP9a8L34BxXvHh458O6We32SL/0EUq0OWxWWL95J+Rfq3aH92frVPvVi1b7wrmmtD347lnNKDTCaUGsLGhKpp6moQakU1DQ0SCnUxaeBUFHzP8AtnaNJJa+GtfRSYoXls5T2UsA6/8AoLV8uCInkCv0d8deFtM8ZeFr7w9qoPkXScSKPmiccq6+4OD+lfFfjL4d634IvWtNbs28ncRDfRqTBMOxDdj/ALJ5FddBqS5SJHmxiI7Uwrg10N5bwxx79ygH3rrvhr8IPE3jzUIpFtZdM0PcPO1G4QqCvcRA8u36Dua2n7u4lqe5/scaPc2fgLU9VmUqmpX5MOf4kjULu/763D8K99Fc9cyaL8P/AAJJJDAYdJ0OyLLEnJ2IvT3Y+vqa+cz+1JrnmSkeELDYSfLBu3yo7bvl5P0xXDyubbRoj6urz/4p/FPw18PbRBqDte6nLjytOtmHmlf7zZ+4vuevbNfMviP9oj4h61HJBZTWmiwuME2ERaTH++5OPqAK8mu7u4vbuW6u7iS5uJm3STSuXdz6knkmrjS7gfoV8N/Guk+PvC8WvaSssUZdopYJgN8Mi4ypxweoII6g11I968K/Y+s5Lf4bX10+dl3qkrR59FRFJ/MH8q91NZSVnYClq2fsmB03DNYp610ksayxtGw+Vhisl9OuFYhQHHYg4qDuw9SKjyspoCWAAyScV0ijAA9Ko2Vj5LiWUgsOgHQVauZRDbSynoiFvyFNLWxliasZPToeaWlmb7WPswPEs7ZI7DJJ/SvTI0SGJIo1CogCqB2FcJ4PBOuo7dTG5H1/zmu8evUzKb51DokeHlsFyOfVsYxptBYCjNeOewBqCY4IqxVa74VTQtxoqXjZWvlH9o/xAuo+MbXRYnPl6XEWk9PNkwf0UL+dfT+uXkNjp1ze3LhILeJpZGPZVBJ/QV8Garqs+s6xf61dnEl7O8zZ7AnIH4DA/CvUwcPe5mYVpaWJLS3nu7qC0tYmmubiRYoo16u7HAFfcXwq8Iw+CvBlno6lZLk5mupgMeZK3U/QcAewFeS/s4/DN7ZIvGmv2xSeVc6bbyLzGh/5asOxYdPQc96+hWy2I1PJ6n2pYytzvkWwqMLasfGS2W7dBTsUvAGB0FJXmM3CkpaSpAKKSloGSp0rzr4w+Gjrdz4R1FUz/ZOrrNIfSMoxP/jypXoaHmq2twfadJuogPmMZK/Ucj+VawlbVEfaVzKtGJhTJ7Vft3+bBrI0lt9qhPpWjGSGFSbs0hRmofMAWmxuXfHYVNiLFtSSeMmnS7fKff02nOfTFNQ46Uy5ubeAATSAFuiYyW/AcmqTS1Zm4uTskeXRWu45xx2pLqyIQkV0eu29ta3qi3G1JF37CMbfaqMi5jwa2lnFeFZN7HgSoct4vc5qFX83CjpWqomCAYpgREmzWgkiECvQrZ3C6tHQxhB7XM/ZKTzmjyGbrWhIy4yKrmZRXq0a6qwUokyiluVmte9b2jW6mMVjtMSOBWro0zgcjFKu5OBdHl5zUe1XtWlosXlsazxPk8itHSpAzkV5NVy5Hc9Sly82hqyco30rzHX5QmpSA16c33G+leY+JYS2pPWuWW9o7mWYX5FYzvPFAuDSrbcc1LFbLnmvebijxfeZCHdjWpoBIvlzUttppkTKirdhYvb3Idugrgr4uhGLi5JM66VKfMmdoRm3H0qJFASqpvQsQUdahN22MCvi5ZthoPlufQ8rZeCnBxXB+LI/9JB967i3u0CfNjNcr4nt5LmUNCueelexlmYUJ1klLc87MKUnR0OWUcVNbXEdu29xVp9Ju0i3lfwrDuy6S7HBBFezj8WlQfs3dvQ8GlTlGackdNbeIkV1TGBXT2d2LlUYHIyK8zjtbiRd6Rkit/TtX+xW4jcYcdq/PMRhubWG571DENfHseeaK8ttcqroR0/lXbz7rmywBzipH0a3MgdVA4H8qv21ssa7a9DHZhTqzVRLVHm0KEorlex5fq1jLFM7EcZqna20szhVXrXoPiGxRlJAFUNHtYUkGQM16tLNE6HPbU5JYdqfKdF8ItOtbC7vp7tUW7ZVELvwQvO4D9KofGnV7G7utPsbR1mmti7SyIchc4AXPrxmt+GGGS32lR0rz/xnYpA5dABzWOAzBYjE2ktTvrc1PDeyS0IbXxr4kttHGlQ3+yBV2K4QeYq+gb/Jrnd9Rhuwp+x8ZKnFfUqEY7I8mU5StzO9h26vdPBU/wBo8I6VJnOIAh+qkj+leDivXvhPeCfw5NaE/Na3B4/2WGR+ua58TH3bndl0rVWu6OxJqSFtrg1GaK4Wrnvl7NANQxPkYPUVJWDVi7kqmpU5quDUqNioaKTH3NzFbIC5yx6KOprMk1S5Zvk2xj0AzVK4maad5GPU8ewpgOawZ7FLDRiveV2X11O5DDcysPQitGKS11GBoZoUkUj54pFDA/getYAq3pjMt9FjucH6YpXFWoQcW0rFhPC/hqOYTJ4f0xZQchxZxgg+xxWwAAAAMAdBSGjNVqzyzK8WaLD4i8M6poVw22LULWS3Lf3dykA/gcGvj/w/8NY9GuZbbxHAt1qkDlXhP+qjx/6Fnrk8c19qg1z3ijwlpuvkTyZtr1V2rcRjkj0YdxWNb2jhaDserldfDUa/NiY3X32fe3U+eItHWIgRrHEg6IiAAfgK5Tx94VW/gt49NsVm1i5uI4LVYUw0rMcbTjqMZJPbGa+iB8NLkuAdViCf3hCc/lmus8OeE9J0MieKLz7zBH2mUAsM9Qv90fSuKhRqxqKT0PqMyzfAzw0qcPebVlpt569hPh74ag8IeDNJ8OwMH+xQBJJB/HIeXb8WJNdFTc0Zr0nqfBC0tNzS0AFZPiq4Fvoswz80uIx+PX9M1rCuJ8b36Pex2gYbYBlv94//AFv5114Sn7Ssl21OXF1PZ0WzN0eeSHU7Z4lLP5gAUd88EV6LIp9a4LwjLaNrG+WVR5UZdQe56foDXcJcwzjMMqSD1Vga3zJr2iVuhjlsX7Ny6XGyJxUSuUOM5FWCKrzkYz3rykesWFYEZFVL6RcAA8iq5mYcA4qGd8IzkgBQSSTgADqSfStIw1Fc8d/aW8TtpPgkaRDJtuNamEGAefKXDSfn8q/8CrmfgT8GnvZ4vE3jOyMdpHiSz0yYYMrdQ8q9l9FPXvxwem8EXNl8Svi/q/iCNEutF8N2q2Wnu6BkeZ2JaVc9/lOD6bTXuUMYjTaK7XNwhyowaUndjxx0H0qZF2jn7x61A7bAKck6nrwa4pJ9DVE9JUZmj9aaZ09aysyiaiq5uV9aYboUWY7FqiqougaUXKmlYdiyDzUwwwwelVUlVu9WIzmmtyJI5LSyYLy4sZCBJE5GPUZ4I/CtgKRzXP8AioPZ68LpDtLorg+44P8AKtrStTtdUti0LATJxJH3U+v0966a0FG0o7P8DmoYnnk6U/iX4krNT4n2UGMgEnpUF3KlvBJM7BUjUsSawujts27Idf6jIrLbWuDOwyWPRF9frVe0zDIZNzNI33nY5Y1VsAwRpZeZZTuY/wAhVlnCjJ4FebKfM+Y9H2aguRfM0vOguV8q6jSRf9oZrI1zQpBC0+mEvjkwHkkf7J/pUkciseGq5Bc7MK+Smex5FXGcZfHqcGIwcZrQ84kmYnng+hqWCRzWh45tFtdTS6iA8m7XdkdNw6/0NZFpcJuCmu2pSXJeKPkpp06jhLoakMbycU2W38rqasW88agHIqhq10W+7W+Bx7pR9m0OpFWuXLfysY4q/bsqdK5GO6dD1rRtNQywUmuDFTxUpOSkOlVgtDr7UCQVqaZGEm4rmrS6ZFyKv2Woss4J6GtaWPi6ajN6no05JNHWN90/SvOfEbBdRavQYZlli3D0rg/E1vvvyR3r2cua9oGOu6ehiGY44FOgkO/LdBV6309WXLtg+lU9VtDBGfKbmu7FZhh6UZR5vePIjRqO0uhq22swx4jyM961YblJ1DKc15/FZTMN7OQa6PTZRb2/zPkgV+YYuHtJOpzXbPcoVXazWh0YK5xmoruYQxlq5G48QFLogHgVatdZS+kERNcywM4+81obfWYN8qeppQ6j5j9TWtFLGyb2xxWR5ECc5AzU2wywskR6itfZK6cdClJ211KmreIreBzEMGucF1Be36s2MZqnrGnXVvcO8oJBNRwaddmPz0GAK+mo0aUKWktzxqtWpKdmj0G0FosKqNvSqbaNBdXok7Zrh49RvI7lYi5645r0HSZdlskjt6VwVcNUw3vJ7nRCtCto1sc/bajkgMew6/Sr6XUbcg1y0qujDIxwP5URyzBhjOKdbCxk7o4oVpLRnQX0TXSEL3qlaaZMj5INa2inzFG7mtny17AV41TGToXpo7o0FU94yk3xRkc8CuE8XSSTPsAJzXpzQqykEdawdQ0eOaTcVroyzMYUK3PNE4nDylCyOF0PQjOwZ1rfvdARbfCqOlbtraC2GFFR6nfxwRFXr1p5niMTXXs3ockcPCnD3jzG+tGt5iprqvhhfix8QG1kbEV8nl89N45X+o/GsXVZ0uLgsvTNVoCyyq8bFHQhlYdQR0Nfewi50Vz7tHlRqeyqqUeh763Wkqh4e1SPWNKiuxgS/cmUfwuOv4Hr+NaBGK8pqzsz6qMlOKlHZgrFTmrKMGFVDSoxU8VLjctOxdpwNQJICOvNSA1k0WZV3A0UpP8AAxyDUIrbOCCCAQexqMW1uefKH4VhKn2PUp41KNpoywDx6mtfTLUxHz5RhsYVfT3qSGOKP7kaqfXHNTbqlQsZ1cVzrliibNGai3Uu6nY47kuaXNRBqN1KwEu6jNR7qN1FgH5ozTCaN1FgH5oLVFupHcIjO7BUUZZjwAPWnYLiXt0tnaSTt1Awo/vHsK8k8QvMZHkdiXdizN6k11Gr+IIb6U+UT5EfEef4v9quXu9+qXcdpbjMszBV+pr2sElRTlLdngY+qq3uxH+EdKn1C7+1u7R21scs4P3m/uj+vtXaLaQxnMIMLDoY+KmsrSHTrKKwt/8AVwjBPdm7sfqafivncwxTxVW/RbH1GV4T6nR5er1ZGmo39qcSr9ph9Rww/wAaureQ3UW+F93qO6/UVUIrN1KJYHhvYmeN43BbYcbh6H1Fc1CrJTUXqmd1aFNwcrWaV9P8jl/ip8SbTwC9hC1g2pXd4rP5CTCPZGOAxOD1bgD2NfPvxD+L3ifxfZyaYoj0jSpOJba0Ylph6SOeSPYYHrmsTx/rGp634w1a91aTfci4eEKBhY0QlVVR2AA/nXLvHkMPWvrIYeMEm1qeA6rltsfZP7OHhtNA+Fem3BTbcaszX0x9m4QfggX8zXp7OuK89+CXiix8RfDjSIbOVPtOmW0dpeQg/NE6DaCR6MACD7+1XPGfjnSvCu2K4D3l/Iu6OzhPzEf3mPRV/U9hXBGjUq1OWKuzVzjCPM3odc8hY7cfSszV9a0jSFJ1PVbSy77ZpgG/756/pXiuseMfFXiNWL3v9lWjcC3siVJH+0/3j+n0rCttJtPM3yRea2cl5Dkk17lHI5NXqyt5LX+vxPOqZlFaQVz1y8+J/g+3JEd7dXx9La2Yg/i2Kz2+LGlnJh0DVZV9W2L/AFNcRBbxpxHEqj2XFXooHfp0rs/sjCx3u/n/AJHM8xrPax0o+LllnH/CN3w+sqVZg+LOjEj7RpOoQD12o/8AWuWfTZ2T5I1kPoTWZc2kkTbZYHiJ/hccH6GpeVYOWiX4lLH11v8AkeoWnxM8I3J2teNAf+m8DKPzAIrotO1fR9TXfYX1vcD/AKZShv0614AbC2kYloxyOo4qmLMxsrwOyOv3WU4I+hFcdXJKT+CTX4/5HVTzGf2kfTQdVwQwxVmGdjgD868N8L+N9X064gstTLahayME3Mf3seTjIb+L6H869O1jUptO0Vr60ZSzsqox5A3AncB68V4FfBVKNRU5ddj0ViYOk6nRGL8QtWR9Z+zRHP2eMI5H948kfhxXLWV9Pb3S3EEzxSr0ZDg1SuZTLKzsxZmJJJOSTUQeu+NBRjynxlXESqVXM7b/AISzVpYgn2hAcfeEYzUMGv3EcE9vef6bDMOVlbkfQ+ntXJxzlT1qVp93eud4ZX20N1jayakpO6PUbC6ju7WG4j+66g49PasT4l6//wAIv4G1TXkhM72cYZYx0ZiwUZ9BkjPtWV4T1dLVjZXLbYpGyjnoreh9jXQ+IrO01fQNR0u+G62u7d4pB7Edf6189iKPsKnLLb9D7zL8UsXRU4P3lv6nyTH8U/iL9rN9H4hYOTnyPJTyR7bcdPxzX0j8HPHf/Ca+HDPeokGp2z+VdQoeA3UMPYjn8x2r5L1TTbvQNautHvV/fWz7d2OHX+Fh7EYNehfBu+bTfFFvdW9wIvN/dXER6Sp/iDyD/jXfiMNTlS56aFRqzVR06j/4c+lfGcgOk26NyUuPl+hU5/pXDtIVfIrV17UzfTKq58qPIXPcnqf0rEkzu5712YWm40kpbnx2ZVY1cTKUNi2t+6gDNaFuDcJljWfDZlk31KsslsCBmsK0Iy0p7nLFtfFsTzwKnHelsrb96HzWZJczzPgI5+imrtob/GFtZT/wGonQqKG44yTlojp4mVVAzUynnIrBgt9XkcYtJMfSuhstP1BwA8BX614tbCyhruejTcp6KLOo0Q77bJPauX8W3C29xuNblvbahAmEQfnWfq3h681NDvdUNe3gq6gtd7HZWhOdPlS1ODuNcm3/ALvOBT4NXNwQsn61vx/D2ct+8uwB7Crcfw6iB3NfP+FeTUw3tZOUlqYRo4hHIarqXlJsiP5UmkNc3i4LEA13C/DzSzzNPK//AAKtfT/Cek2KgRqx+poeEtTtFamkcNVcryeh5ZqumvATJnrVnw7BGMyswzXq0mg6XIMSW4f60+HRdKgXEdpGB9KpUKkqfLJmiwlp8yZ5Xqd9N52yEscegrb0C9kEY3xOT/umu/Ww09DlbWMH/dqZIoF+7Ei/QU3g4uHKzWNCSlzcx5prsl3eSrFDYTOCeoSriafqQsQkVg5YjvXoWVHRQPwo3gVssPBQUew/q15OTe54+fBviC5nMotVjycjc1b1t4X8RGBYnlhjH1Jr0LzR60glXcBuFdkpc6Sa2M44GnHueP6mEGD7D+VU2niEXGM00pPcQhiD90fyrKmSRHKtmuaeFkopyPHlLqjf0rVBE+3NdPb6gkgHPJrgLWMj5q0rOcxyjc3FeFi8JCo20dNGtKKsztTcCm+ar1h3WpRpBweao6fqxMuGPGa8mOBnKLklsdTxCTszrBGGHSue8RaRLcofLzXQ6bKs6gg1prHHuAYCjCYiphq6cdzeVCNaFmeMy6DqMbYELMKgawvIOXgYfhXvsNhauu4oM/SsPxf/AGRo2lSX94oJ+7FEOsjnoB/Mn0r9Qw+YVa3LBRu2eZVyqEYuXNax5v4U1ifRNQNy4P2NgFuVY4G31+o7flXrNndWmoWkV7Y3CXFtMN0csZyGH+e1fNninVJr0PhsDOdq8AfQVS8DePtU8HXzGIfa9OlbNxZO2A3+0p/hb36HvX1NTIJ1aHtIv3+3R+XqcuExSoP2b1j+R9RMKaayfCfirQvFtkbrRbxZWQfvrd/llhPoy/1HB9a2mWvlZwlTk4TVmj3U1JXRHnFSJIRTCppAMVOjGWVkBp4YVV5pQTUOJVy4Hp26qgY+tOEhFQ4lXLW6l31W8yjfU8oXLQelD1W3mnbqXKO5Pupd1Vw2aUE0uULk++k3VGuWOByaoza3pUV7Lp6XsM99CoaS2icM6AnA3Afd/GnGEpO0VcTkluabEKhdjgKMk+lc7qltrmv/AOjQxjT9N7tPw83vtHIHsce9dJp5keASygKz8hR2FWjzUc/IxTp+0Vm9Di4fAduP9dqUzeyIFH65qzp/g2207UUvra8ldkVgElUEZIxnIrqqKTrTaab3IjhaMGpKOqMGeGSJgsi4J7joaiPAreu4hNAy4+Ycr9a51myua8qrDkZ7tGftEU3vVFyIB1NO1GMNZyhjxtpC6CKKAr84lZ9xHUVW1q422bKDy3FYxjzSUYu7dvvZ01JcsHJqyV/uXU+UvjFpZ03xxdyIuIb9Rcofc8MP++gfzrhJPlzX018QPCkXirR1hR0i1C2Je2lfpz1RvY/oQDXz3r2hatpE7W+qadPasP4mTKn3DDg/nX3t7o+Lw9VSgl1Rl6Prmr6DqKajouo3Gn3acCa3cqceh7EexyK9V8N+ItV8axy6rr00VxfQOIDLHEIy6hQQWA4zyea8eliAPDKfoa9B+DsyBtVtCw3YjmUfmp/pXRgHy4lP1Kxd3Rdj0aJNo29BVqMDgDpUOBwRU0dfSM8Mv2wU9a07dQMVjxNg5rVspQ+B3rjqp2NIM1bdela9tDHMnlyorqeqsMisuDjGa0rViCK8qtc9CnY5rxt4aeytJNY0pXaONc3FuOcL/fX6dx6c9q5K3GY1J9K9105lZdrAMDwQRwRXnHizw4NJ1lI7VD9iuzut/RT3T8O3tTwuLcm6U9+hVajZc8TL8N2AvNUiJi8yK3Ill7AKPf64FdvrE0l3bJC2Fhi+5GvQU6K0g0PwziNf3tzIoZz1bv8A0rJa8LnGetcVaXt6nPFbaI5683TXsm99Wc/dRmOQioVBY1u3FukgLGspwIpfapa0PHlGzIniZRk1LAgPU064mVkwKqiQjpUKLkgdkzbgtI2XJOakc3iReVHcyiP+7u4rIt7uRGCk8VvwSCSDPGcV51aEov3tUdtGpbWDafkeUfGDwtJf6Sdfs4mku9PXNxgZLw9z/wABPP0JrynQdQktrqKdGKsjA8V9Sw3BhlddqsrgqysMhgeCCPSvBtR+H1//AMLJtfC+jKqRaq5ktJJc7IouS5b/AHMH68etNbWex6+Ar6cjep7z8M5NP8ZWZLSlLuJQWUH7w7mvQY/BmmqB5g3Eeprl/Bvwb03wubO+0vxJrB1OB1kaYyqIZf7yGLGNrDI65GetdJe6vd+a6FTGykgg9jXNCnOo7Rex6NSnh+bncdWacGgadCu3YpFSDR9MByYkP1rmm1a8JxvqNtRu248w1qsvnvcXtKK2idelhpkfSKMfhVhRZRjhUFcKby4PWVqja5uD/wAtG/Or/s9veQ/bx6I7/wC0WqdCgpPt1sOfMWuAMs5/5aN+dNZ5cY3t+dWsvX8wvrH90746najrKKifWLRP+WgrgsSHuaUo59TVrAU+sifrEukTuH1y0HO8VA3iK1HQ5rjvLek8pjVLBUVuxe3qPZHXN4jg7A03/hI4+yVygjbpUgjPrT+qUEHtavY6VvEQ7JUZ8ROeiVz4Q+tHlnPWj6vQXQfPWZvN4gk7LTG1+c9BiscRZpfKp+yoLoPmqs021y5Peom1e7b+OqHl+9KIx61SjRXQX719S5/aVyeshpPt1wzr+8PWq3lgU+MLvXkdad6a6C5aj6jYre3WAAAfdH8q5HXlWObIHeuj8zaAB6D+VZmqWouFLYrath/aQcTgqRUo2RiW8qGPryKtWFpPeT4RTt9ao2Fi8l95WflzXpnh+xt7SJWfGfevmVgWpNsujhnPWWxy93oE4i3HOMVzrxyQylCDkGvYblreVCoxiuffSLWS53kDrXR9Ui46aGlbBp6wKnhKC4ZAWBFdWtq5cE0unpb2yBVwK04njboRXk1Mspqpzs76NPkgo3GLGyR8da8C+K3iJtT8Ry20Uu610/MEeDwX/jb8+Pwr3TxLqK6R4e1LVCcG1tnkX/eA4/XFfI1xcM+SzbmPLE9z3NfoHC2CjOcq7Xw6L57/AIfmeVm9ZqMaS66sbdT7s81zmpNtk2p1b9K0p5sZrIn+eRn/AAFfoWyPBitbkdle3mmXUd5p11NZ3MRyk0LlHH4ivVPCvx71ixjW38S6dHq0S8faISIZ8e4xtY/gK4Dwr4V13xdqX9m6FZNPIOZZW+WOEert2+nU9hX0J4H+AfhnR/Ku/ETnXr5eTG42W6n2Tq3/AAI/hXzmc4nAQXLiFzS6Jb/8A9bCU6z1hojW8I/E/wAHeKGEVjdXUFwesVzauMe25QV/WuzVoZPuSo30YGr8WlafDCsFvbJbQoMLFAojQD6Diq11odtKMxkqfcZFfBTq0JS9xOK89f0R7PLNLUYYuM4pBGa57XdBu5LG5tbTULrT7mSMiGa3mZSj/wAJH44r52g+J/xDsSYz4inlMZKss8UcmCDg9Vr1MHldTGxbozWne6/zOWriY0WlNbn1VspNp9K+atI+M3jgahax3d1ZTQPMiSZs1BKlgDyMete922p6jJEryeVuPXEeKjFZZiMK0qjWvZ/8AqniIVFeJtbWpQprKN/fEcGMf8Apn2vUG480D6IK4vYy7o050bQFLjHJOB71jA3j/euJPwOKBZNI2XZmP+0c0vZJbsfO+iNWS8tIuGnQt6Kcn9KRrw+Uxt4g0hB2eadq57Zxziq9ppTlsrHgep4rYhsIlA8z5z6dqym6cfMuKkz5L8X/ABN8faveXumXd0NEWCVoZrSwBjIIOCC/3iPxGaufA2Ke08c28spKQXatBJuPLk8qf++gK6L9oPw5FpXjbT/EMEIW31hDDPgcCdAMH8Vx/wB8msLwzI1rqFrdKcGKVG/Iiv0Kh7Ctl37mKjzLp32/Bng1pThibSezPq2PCxoPQCl3CoWcZ46U0tX5jy3PpLljcKNwqvuo30coXLBYAZrjLy9itbv7PNIqmTLpk4yM108sh2kCvLPE1/Ff6zLJCQ0MQEUZHcDqfxOa2p4JYq8ZO3mYVce8GlKKvfobuoX9uq8SZf8Ah2nnNYd1czXBBkbIHaqManIIU/lVsRSsvCHP0rtwuW0sK+beXc87GZrXxi5F7sey/VkXmc4qxAQy7Wwynqp5B/Cq32S5Z+Imq9a6deN/BivQqcqW55kIzb2IjoujzNvm0iwlPq9qh/pVfWdA0r+zJ3sNLs7SdF3B7e3SNiByRkDpXRwaZckc4FOu7I21rNcXMojgiQtIx6BQOa4oVeSopReqZ3KlUcdjx8rtYqelSrSyPDMBPCCI3yVDdQM96ap5r7k81llOlTwuVIIOKrIalHSs2hI37C/RiEk4PrW9bkEAjpXCKxBrX0zU3gIWQ7krz6+HurxOqlVtozurKVlYela7wQahbeRcKGAIZD3Vh0IrndPuop0DIwINb9k2MHNfPV4uLv1PYpSTRzvjeXJs7CNDmJDI+B3PA/QfrXKxwXDPgROfwr1e5htZR58ka7lHJx2qoradHyAtZ08W6ceRRuc1bL/bVHUcjhVsLxouIWrLuNF1J5cCA16gb+yTgYqNtStByFGfpUvFVXtETyyk95HnVv4W1SUcx7avQeCb5z877foK7U61Ep+VKjk10gZVah1MS9lY0WXYVbu5zkfgOTILTGtu08IxRxANIT+NDa9cHoKjbWrs98VnOliKnxM2jhsLDaJbh8K2KPmQAn3q4nh/So7iC5WGL7Rb5MUpUbkyMHB7ZHBrBfU7tv46iN7dk58w1P1ap1kbRVGPwxO906XbmFvwrE8XWZRlv41+Rvllx2PY/wBKq+H76WWRreViXHzRn27j+tda6xXdo0Ui7kkUqwqJ3pT5kaL3o6Hmn2qHOO9SxujnisjWbaXTtUntJfvRtwf7ynoaLO5K8k12VFPl5oM8f65JScZI3fIJ5qrNKIm245qMakBxmnWm25ukzzzWNB1237TY6KdfnlZFjbIIfMKHGKzW1JQTx0rtdSgjj0o7VGdteYzMQ7j3NdWGftrkY+rOg1y9TVOrKDjbVy1vFl5rmB1zVmGcxng101sPePubnnxxtW+rNfUNRMBwozVEavMWAVOtU7l2l5PNMswPtC5ryJudOLv0NfrdWT0Z2mlxNcQh5RjNXvskYFRabKogUD0qzNKEQsa/O6+Z411Zcs2tT3KbXIm2c3rUs9nIPLGVpdPumlAMnFSarNHcrjrVCNGRMgEV9TgsTVrYZKb944pTkpuz0LGsXMkSgwNg1lf2hdn+OpbtmdQD2qrtr6nBU/3C5tWV7ST6jory8klA8w4zW7aTMSA7Vl6fb7iWxWg0RXnpXmZq+XlSdhSclZ3JdXv44bfCH5q5kX91vBEpHNP1VmaXBOcVSUfMv1rmw0JQhq7nDVrSlLc7ZbSTfyOw/lU62ihG3irQu4y/bkD+VQahdIsBKnBr6bnnJ2OqNr3KFhpyPf7kHSrWsNc2wUISBV7wkgmLu1P8TIokRfeualU/fcrO6uv3V0U7NLr7N5rE8ism41WWOcpnkV2lvEp04DGOK4PVLNheyMDxmpc5VG1FHDinOnGPKy/Dqkzc7jW/oF5LPLhmrjra2mbpmum8LxSJOSx7151anNbiwtWcpq4z40Xcdn8P72GQtvvilvHt/vE7sn2wpr5ilVwcZB/Gvov9oIf8UPayhS0iXybOcDlWzn8K+cJpgeWR1Puuf5V+h8MQUcDddZP9DhzSTeIs+iKlyrgE7a1vh74R1DxrrsWl2R8uEDzLq5IysMeevuT0A7n2zWTM4ZHJBwqntX1f8FvCieE/BNnDNGF1G9UXN23fcw4T/gIwPrmuzOcx+o4fmj8UtF/n8v8AIjBUPbTs9ludN4W8O6T4X0eHSdHthBbxDJPVpG7sx7sfWtiikyK/KZzlUk5Sd2z6hJJWQtFJmjNSMhuoVmQKRzng+lfFWrW8M2pX7ocBrqYgj08xsV9i+KNUi0bw/qWqSsFFrbSSD3IHA/PFfGgZgo3/AHjy317/AK197wpCXLVm9tF+Z4Oby+CK31ILGxkfVbCBRuMt1Egx6lwK+w0tFxgYAr5x+EelHWviDpcZUtFZFr2X0AQfL+bla+o4bbnnpWXEmISrQpror/f/AMMXltNum5PqZq2WTj+lWYdMY9eB71rRxKvQU+vkJV5PY9dU0UU06JfvMTVqOCGP7sYz61JRWLnJ7stJIKWiioGcF8cNGGs/DrUWVd0+n7b2HA5BjOT/AOO7hXhFnKgaER8+YVx+OK+rb+2S8sbm0kAZJ4mjYHuGBB/nXyh4Hspr7xHpOnlS2y6CSD0EZ5/Ra+44erJ4WrGT0hr96/4B4eZQ/eQa66f1959RpIcYPalMlQRZbnHWpJCkS7pXVAO7HFfJNK569yQMTSjJrFvfEWm22Vjc3MnpGOPzNYN/rt5dqUJEMR/gTv8AU962hhpy6WMpVoR6mz4g1ATQPZWsnDjbJIp7dwP8a523061QD5RxUIkcj72KE812wrGu6NN042TOSXJUnd6s1Y4LZB91amEtqox8tYs2+Lliag80HvR7Hm1uDq04O1joftdsvTFIdShT7ornmuAKSKbzZAo70PDq12CxUL2R0J1j+6tcX8RPEss9idJiO1HYGcjvjkL/AFNbusSRaXpLXkhG/GI1/vNXluqStP8AO5yxOST3NehluFpzl7W2i29TPFV5RXJ3I9Kk3xSwd0O9foetWVyDWNbzG2vEl/hBw3uD1rdlXa/HI6ivozypIkU1Mpquh4walWpZBLTkqMU8VAHQeHZGWZvm+Udq6+1vNpAzXGaMpSLcerGtqKbDDmvGxVNTkz0aE3FHc2U6OAGwQexrA1q0SyuiqnET/Mh9vT8Kfp11kqN3NaGs2h1LSpBEM3EI8yPHU46j8RXhuDpz8j03NyptrdHLmeIH73NKkyMdqmufZ2znJ5rS0VTLcqOvNds6XLG9zzKOMlUmo2NZoiqbjUAlVuK2dSjWKzPY4rmYWBPWuKDc02dOJqypTUUaabMUtrCZ5cKOBWc8209a3vDJDyEmspqcIuQ8NVdSpysrX0P2cjI602JQ4q54iz5y/KcVnQSYNct5SjdMdWTjVaEnlns5Fmg++hytdvomox3UUcqjasozt/unuK4qeQN1rS8N3ccNx9mdsLKfl9m/+vTV3G0hUarjUs3oza8YaLHqNul2i/v7cckfxJ3H4dfzrg77T2gTctes20gliweo4Iri/EdqbW7aMr+5f5oz7en4UqdWcGo9CsZQi/3iOCKy7xwa6jw1CWmXd1qAQRdcCtXRwFuVAr1HX54tJHJgqfLUub2tqV0x8eleVshaZ8+tetayM6a/0ryuQ7bh/rVZYrxka5r8USF4toogiMjgVYfDLU2nhQ2TXZi6jpUZTXQ8dRTkkWBYgx9KppZtHcbu1bqkYFRTY61+X/2pXc5cz3PVdCFtCW1lKKoBqa9uC0O3NUI3waklbKV5jiufmZ0KT5bFO2UGcBjxmta/WOO1yo7VjJnzht65rpra186BfMFddTFrDNSewUIud0jj2l3kjFMNdBrlhFbxh0UDNc+RzX6Lk+Khi8IqkdhOEoaSNzw7btcswA4UZNZ2pXjxXMsQ42nFdd4FhX7HK5HJrkPE/ljU5ggx81cOLkp1uVrYvFLloxaMmVzI2TUePmH1pwxSgfMPrUpWPHbPTG8OsrDDHoP5VieI9NmtYSwJwK7v+1LYnG8dB/Kub8X6hC9qyqQSRXbSxVVySZ9C6NFK6GeA1LWxz1NbWpaQbqVXJPBrD8BzJHB8xHJrszdw4+8K5alSVOq2jpajKKTM02ciQeWpPAxXP3ujSDfKc12KTxu2Ac1DqrKLRz7UU8TOD0IqUYTWpwlq0cblGxkVpWNykUuQQK4jUL901OUKTjNXrS6uCm7BNefjq1d6rQ8mFZKVl0LHxon+3eAbkJybeeKXj03bT/6FXzszHHNe96lK+o2F3prrxcxNHz2JHH64rwe9hkt5nhmQpLGxV1PUEdRX3fBWKlPC1KFR+9GV/k0v1TODHy9pUU12sW/C9lHqPinR7GXmO4voY3B7guMivstn+f2zXxNpl++marZalGCWs7iOcAd9rA4/Svsuzuob+2hvbWQSW9xGssTA8FWGR+hrXimEnKlLpZ/fodmVNJSXXQ0t9JuquHxwadvHrXxHKe3cnDUjPioGmA71n6vqlppmnXOpX8629paxmSWRj0Uf1PQDuSKqNNydkDkkeR/tE+KNhtfC9tL80qi4uwp+6mfkU/UjP0HvXiBkzyTVjxJrU/iHxDqGt3IKSXsxcIf4E6Iv4KAK6f4U+CpvF+uo90jLoto4a7k6eaeoiU+p7+g+or9VwsKeV4FKppZXfr/WiPlarliq75eux61+z74ZbTfDc2v3cZS61cgxBhgrbr93/vo5b6Yr1pBUEaoiKkaKiKAFVRgKB0AHpUymvzLG4meKryrS3f8ASR9NRpqlBQXQlFFIDS1wmwtFJS0AFFFJQAtfPXg6wbTvF+v3wYosF7cRQsOOWkOcfQcfjX0L0rwe9uljvLhohtjeeRwB7sST+tengsVKhTqQivjsjy8fZcj7G/calfMxxez49A5qDe0nzOzO3qxzVGznEw5NOnuBE2BUwrzVTY4pyvC9zVs7bzXyakurbYwA4pdDmVlzmrV2pkuAAOK7Y1vevLY0lD90rGS+UOM1qaLF5jEmo7rT327hV7QYXjB3VNTF0alJuMhYelNVlzFTxCgSMAcVhRqSa3fFDjgAViwByPunH0rsw38FMwxa/fNENypAqxo4UzFnYKqDczHoBS+RLcyCKNPmPPPAA7knsKwtTvhFE1rbuGDnLuvRh2H0rthT9quRHNBOM+foit4p1Z9Ru+CRCnyxr6CuYuzhOvParty2fmrNnySSTzXt0qcacFGOyKlNyd2UJO9bWmz+fYqCcvCdjfTtWNKKm0ebyr0Rk4SYbT9e3+femwtdG6h5zVharcqxFWIuVGeooZkyVakQZIFR1NbjMorNiRs252IAOwqykhzVJWwKkVq4ZRudKkbVjcFXU56Guw0ycja4PFefQy4PWuk0O93DySeR0rysXRvG6PQw9WzsZfjSxWw1TzIlxb3QMiY6A/xD8+fxqv4XbN6o967HVrKPWdLa0biZDvib0I7fiKxNH0OfT7pXkzXOq8fYOEnqR9VlHEqcF7ppeJ9y2JK+lcZpspllEfc16Fq1g97aFFzyK5OLQZbGXzGJyK44YqjRw0nN2ZpjqFSVaMorQsvoxkiDA81p6JAbLhqLa4wgVj0p73UY6kCvzTFcQ4uqpU1sd1LD06bU1uXrhEuTlgKwNRtnhmHlrkGti1nSQ8HirM0cbgFgKyyzNa1Cq3Ud12Nq1GNVXW5lWml+dHuk61m6jpk8TloWIxyPauqRwq4Wo5VDg7hnNelTz3EQrOT27GM8HCULdSTw1qT3dukknEinZMP9od/x61p+IdO/tHTWRP8AXR/PGff0/GsvR7EQtLLF/GPmHr6Vu2U/mJsP3lr6uFZVoqrBFRg3T5Kh5WzyqxAVuD6Vt6GGM6lgQa7C40q0aZ5fLALnJ471kS20dveDYO9d8cRCScbWOejg5Upczdy9qnzWDADt0ry+e1uGuZMQsfm9K9bSNZUCnpigaba5z5Yz9KMLjFh7q17muMwn1i2trHlK2V0V/wBS35URWl3G+4xMB716wLG36eWKhvdOga3YBBnFaV8z56co8pyf2WlrzHnqEhQDSSnirN7C0U7Jt71G1u7JnFfmNWolUbeg1B7FIPg1ctk887aoSqUbBFaehDdJk1FefLDmQqUbzsy1baWEkDkZrZjAVQopTgCkBrwateVX4j14Uo09jL8Rrm0BrkyOa7DXhmyNck1frfCbvly9WcGKXvnbeB/+POQVyPiexlj1GeU5Ks2a6vwOf3EgqfW7WG4MisRmjMK3sK1+hVan7WhFHmGMVJGNzqB61LqsC2s7KDxWfb3YSUZ7Gn7aFk0zw3Bp2ZtrfXCnPmnoP5VTvr+adwjsSK7lvByDAyTwP5VVk8EBn3AmvXjVpc1zrhg8QmrmRo0z26Da2K6GG6lZc7zT4fCxjTbk1ch0GSMYBNediUpyvFnY6Vbm8iTSZZHnwWrb1CPdaNk9qzLPTZbeTdk1o3ayS25jHcVzpSW53UoyULSPKruziOsvz1NdZY2MH2boOlQT+FppLszhiM1pQ6RcxxbQxrzcyw1evb2bOShQcJNyiYssFtDdg8V5/wDFvwhJdg+JNHi81wv+mW8YyxwP9YB346j8fWvRLvw7eyz7xI2KcNG1FMBXYVOXLMMrxUMXQd2tGns11T/rRk1KPtIuDjofKe4N0Neq/CL4qW3huFPDfiNn/szJNteAFjbZPKsOpTPII6fTp2XiD4V6brKvOqnT75uTNCvysf8AaTofqMGvNdc+Cfi9HJsprG+UfdKymJj+DD+tfrKzfLc0w/ssR7j7Po+6e39ao8yGGxGHnzRR9M2V1a6jaJeafdQ3ltIMrNA4dSPqKeCS20ZJ9BXyPafD74r6LOW0vS9RtZD1exu1XP8A3y/NbJ8PfHy9i8qS411Iz/z01FY/135rw55VQT9zERt5tf5npqvJ7wZ9CeK/FOgeFLU3GvanFakjKQ53TSeyoOT/AC96+Z/iV8Sb/wAbXi28KvYaFbvvjtS3zSsOjyEcE+g6D3PNbPhr4La3qFzcSeMr2eyl+UxmGZJ5Juu7cSSRjj1616t4Y+GvhLw9sltNLFzdryLm9PnOD6gH5R+Arrw0sBgHzp+0n0fT5f56mNVVay5dkeQeAPhzqviZ47y+WTTdJPPmuuJZx6Rqe3+0ePTNfS2gaXZaLpkGn6fbpb20K4SNe31PcnqT3NMiT5tx5NXY34FeZmOYVsa/e0S6G2Hw8KK03LitTw3NVg9SK1eK4namWlapAaqq1Sq1ZOJVyaikBpagYvaikoBoAp63c/ZNJup84KxkL/vHgfqa8gfRmMOeTxXoHjWeWcwadByA3mSn+Q/r+VZQhlS3wU5xXn4jEV6c0qa0OOtTVWWvQ46ztXhm2VoXGmNIofHNPuBJFcbvLPWtW0uBIoUqa7cRiKtKlGaRxqnFx5WZul200MnQ4rdhwZBu61btIUK5I5rJ1i5Fo5I4rzcdmLr0eWDszr9mqcE3sbmUZccU62VQ+BXGwa4xk25rodNuy5DV85QhUpVFzysjWFWE3oW9SsEnwW5xSWmmRbcYFPnui5CKCWPAA71dgt7mCLzrgiJF5Kk/Mfwr6OOYV6klSw92kU6VNz5mjhPF2ox2/m2dnhTu2SMOpx2rgJJCR83UcVu+JWzrV8MEL5zMAfQnI/nXPXBw+7s386/W8FSVOjFdbHzdebnNkEjE9apzVaeq0tdxkU5BVdgQcg4I5Bq44qIrWbLR0Ebie2iuB1defr3qxDxis3RH3RS2x/h+df61poPl+lK+hElqSVYtR85PpUAFWbYYFRLYlbl1TxUimoFNSKa52jRMnVsVbs7hopVdTyDVAGnq2KylFNWNIys7noGn3AdElU9ea2ZZo5YkbjNcJod2QfKZsA8jmumtpPtUbQxt8/VfrXzGOwzW3Q9zDVro3IJUSL5u1YWt3Su2I8ZqO9muIoePSuYnv5RIS3WviM3n7Sl+6kmmdVSslozWVSVznFV54GlGAxrHuNUkjHXApBrqxxZLc18msPUWqOd1YPRm/aK9sv3s4rO1LX5Y5dg4ArFl8SZUjNZ4luL6dXWI7c+ld+Fwtp81UxqV/dtA7XRtXe4cK2a6F5htFczojQwqqyLtat6aaHySwI4qqjp8+iOqjKXLqy9a6gIEOas6beJPI7xnDIfmX2NcoupQs7ISOKtWGo2ttP5pYDPB9xX0mBhjISTa9wvnud64EkRCnG4cGsAxsbgmQYYHkVp6fcLIoUMGVhlSO4pupqkWLluFPDH+VdGOo17qVF6rp3RrFp7jEl8oZ7UranAONwzWfPd2zRkeYPzrGaBHkLiU4PvXfgYyrRft1ytE1ZuPwq507apAFzuFY2oeJoFYxA8mqL2TSRkLNXPX+j3Qcsjg1GY05Qp/uNWc8qtW2kTUlvIppN7Ec1aiKMny4Nc1HpepEcYOPetXTre/jG2RP1r4PFYDEK82mTTnJuzRBqyKpyKfocmH5qXVLO5Me4RMfpSaTaS7SzRsp+lck4zVG0kyuVqroad7eCJODS6dO04rJv45TKEKtjPpW5pUHkwgkc159SChTTfU6ISc52INeXFka5Mjmuv1/wD48jXJ4r9Y4Qf/AAnfNnLi1751fgxtkcnNcl4m1q6i1qeFGworofDkpXcB3rgfGEmzW5iDya1zakpXb7mVWbVJJEd5cyXBLOcms5ifMGOOafBMHXBNWPscr4kA4rwU+XRnBufRJBz+AowfSs19VjB49B/Kozq4HQV9CqM+x9Bzx7mtRWK2sf7JqI6y392rWHqPoL2se5vUcVg/2tIegpG1OUjpT+rzD2sTd3L6ijK+ornGvZT3NN+1znuar6rLuT7aJ0vyeopPl9q5v7VP/fNAu5v7xo+qvuHtkdHhfajYnoK58Xso7mnC/kpfVpFe2ib3lx/3RmsfWtQ8oG3tAPM6NJ/d9h70iXkrg4OMDrVB03NnrWlKilK8zKpUurRMlY5UuVvAxaZTnLHOfaulgdJ4llj+63b0PpWZIgA6VFaXElnMSBujb7y/1HvXdUTqLTdHJH3Gbe3FOFLE0c8YkiYMh/SgjFcN+jOgUPT1kqE0gzStcd7FxZM1ZhBc8dPWqFupd8Hp3rVjwAABgVhU0NI6j1UCloBzS1zlhTWBwduA2OCfWnUlAHFai9xbXjJeR/vD828dGHqKiOoIRtIxXZ3tpDeQ7JVBxyp9DWJLoUJJ4wa74VaLXvrUjlm9mYLtbynnFPgFujDkVqN4fX+FqrTaDKPuua2bw81ZszdOe9ixFcQBcZFZOrWEF8etTPpF2o45qH7FeofutXP9QwsndWFJya5ZR0M5fDUe4FGIP1resdIMMYHmVR/0yM8hhTjqFyg2lj+Nc9fJaFcmHs6bukdVpenxWSfaHG+d+hP8I9qydTumkuJ13EgORj6cVvMxIGfSuY1QeXqM6+rbh+IzWuX4anQXJBWSLqvQ4bxxYlEj1JFypIjlx2P8J/pXFyEMCD0NesXsaXEEtvMnmQyrtZT3/wDr15dqlpJZXbwODgE7WP8AEK+3wFbmhyPdHhYmnZ8yM5sg4P5+tQuM1YcAjpUJUj3FekciK5FMK1Z25NSJb56ism7GiVw0lH+2RMgzzgj1BrdZNhI9DWp4L0KW5vYz5fLHj2HrVjxnph0rWZLfHyOBIh9Qf/r5rjhioSr+x62ua1aLVPnMNemPSrMPC1VWp0bC11yRxosBqcH561VMgHNTwrkbmHHYVm0Ui5BG8p4wB/ePStC2tYAw3Ayn/aOB+QqlC5q9A2Dk1x1Gzop2Nq1kEZVY1RAOyqBXQ2Um0K4AyPauCvfEOjaVF52oahBboO7uBk+lY1t8ZPDP9oQ6dZR3eozzSCOJLaFnLsegAxzXk16EpLRHpUaiR6X4gn8qNHUfu5SR9D6VyU43ktivR4NIXULCMalC0RYhzCG5U+hP+FWP+Ee0Xy9h0+MjpyST/OvznFZQ/rEpUWlF/wBM7pwlUPE74mRyoPArNuI26Zr1rW/ANnMjzaTK1tOORHIxaNvb1FeR6z9psL6W0uYminhba6N1H+fWueWFq0XaR59WnKHxEcEWZlEhwua9D0GKzFuv3c4ry5rqSRwBxWpZaleWuPmOK5cTh51Y2TsKlUVN3aO91Uwxv8jAfSpbYtLa43ZyK4SbVJ5nVnPFdNpWqRiAAsM4rknh5wppbs3jWjKTM7UTNZzs3Ymp9Oma6PzVF4guopUJBFZ+lXoh6mvQWIxHsfdbTMk1GfkeneErsgNZu3zw/NF7r3H4f1rsJFivLR4pBujkXawrxvTtaZLtJoj88bZHv7V6pot7FcwxyxNmKZdy+3tX0uAdWphYzqPVdTupVFLRHIappklgXDOTtPX1rAk1LZwGP513vj+3f+x3vYQSYB+8A/u+v4V5A0pLEk1yTx+Oo1HGTuunoZYiSg7I6FdZKf8ALRh+NSrrBk4Eprlyc1LbxncTXbg8zq1KqhNI51VkdVHq7R/8thViLWZm+5IpNcRchg/BNbnhywkmbexOKeNzj6q37SmmjSnUnOXKjp01m4K7XCNTotceM48pay9RsJYV3xsfpUeh2dxPc7ps7a4I8QYScHKVI671FLlN7+24ycvb5NWY9diIwYcCpf7PtyBlBUM+nW6rkDBpRzfLK3uzpNG9qkdblHXNViuLfyo0wTXO5zWzqFmixGQdqxm4r7DLJYeVH/Z1ZHnVpNyuzV0aeOEnecVW1Lwzp+qXjXbXjIzdgazvtBgcAjINb9rYyzW6yjjdU46OHSvXlZDi+ZWauZCeA4N+6PUuPfFbsXhFjAsUd3GT6kVG1lcx8cn6U5Ir5WXazjnsa8iWEwFW1qi+8pRgvsm61jcEjCjoO/tThp1wewqzaSyRSBZZARgfyrT81OueK3hi5Tje1jrjTizCbTZh1IqrNbSRnkV0RvLXdtMgzUwWF1ztBBrSOKYexi9jkhuHY/lTsv8A3T+VdQbWE9IxSC3iBx5YrT60uwvYeZzS+Z/cP5U8LJ/cP5V0ogQfwClMSf8APMVLxS7DVDzObCueNh/Kpo4N3VDW8Il/uCl2D+4Kh4m+yKVFdzJjskbtUw0yM89Kv7fRaMuOgrN1pvZl+ziZdxarbABf4hVNhitTUQSEYjHUVlSV1Um5RuzmqK0rEElV5cYqd6gauyJzSIYp57aXzIHKnuOoP1Fa1tq8MgC3K+S/qOVP+FZLjNRMtXKnCe4lJx2OpBjkXdE6uPVTmmgEGuXCOG3IzKR3U4rc0Vp3hczu7/PhS3pXLUo8iumaxnzO1jYtxtxV1TxVFDg1ZVuK8+audMSyppwqJDmpAawaLHUlLSCkAVHMMYapKRxlDQNOzK5PvRxSZFLkelBsGB6Um1T1UUufalz7UCImgibqgqCTTbWTrGPyq5uHpRu9qalJbMTVyruIG3+7xWLr8WJoZx0ZSjfUcj9DW3KMTH/aGapapF51jKoGWT51+o/+tmu6jK0kzlqRumjmpRhTjriq2vz+GX0xLK5t98kqZJjT94GHU7uxqy3zLkdDWhoFtZOrSzWImnRzsd0yAMds8V6kpci59dOxxw958vc8VvLZIZ2SDzpYv4WeMg/jVIjmvomeGMcmJAPSsjUPC+gaswa505EfvLAdjH8utdlPOUl+8j80ZTy+79xniKRbuoyK2NJ01p5U/dlsnAUHrXoelfDe0Fuy3uoSNMrna0IG1kz8pwehx1966TR/CmmaXJ5itJcSYwDJjA/AVNfOKPK+TVip4Gpf3jC0tl0yDyrUr5mMO/qfQe1Ynjt5Ly2t7qQAvCShI/un/wCv/OvSJNJsnOfKA+lZup+GLe7sriFGIZ0IUf7Xb9cV5mHxtKNZVJbnbWw85UnBHioqQuAuc4FMmzGxBGGU4I9DSQr5hyfuA5A9TX2j7ny5YgXJDsPoP61aU1CpxQGkeUQwjdK3bsPc1jNpK72KinJ2W5Y88ROqYLu5wqL1NdRoHhjUNVAluSIbf0Jwv/1/5VSsvC7Wfk6xdNIZV+VFXq2eoPoK9OjuYk063ubiH7MhUBIOuPrivnMZjpyVqOz69T3MPhIQ1qav8Dnr34eaDqOmS6fqa/arWRcMvlqoHuD1B9xWR8MfhL4V8FXsmsWr3Go6gdyw3V5tzCh4wgAwCf73U+1dZcalNdgQRpgseFXq3oDW1DGEjRTjcqgY7CvHnKrCDUnuegnGT06EyMT0GB6mnGoLi5htYTNcSLGg7muavvEs0jFLJPKT++4yx/DtXNGnKexodaOBXL+L/Bul+JYzLOhgvlTbFdJ1HoGHRhWHPd3UxzLcSv8AVjTre8uYDmGeRD7NVSw91Zg6amrM8zfR5dM1CeyvUCXEDbWHY+hHsRzTbkJnAFep3egReKmW/lYwXcS+VIycBx1U4/OsW6+HNyHLQ3gb2Ir5+rg6qm7bHmTwlRNqKueeuoC1X8+RDhXIrtb3wBrqqfKaJx+VY03gjxHEc/Y9/wDutUKhUW6Od0Ki3izCkuJZcBmzQfMC5U1oXHh7WbcZk06YY9FzVKWK7iBEltKuPVDUuDXQz5ZLdFrRZWExDV6V4G1MJK+mu2A5MkPs3cfj1/A15roY3TEEYPvXSWhaG4SWN9siMGUjsRX12VwU8I0zWnJxd0e0xlLq2ZJFDo6lXU9CD1FeGeLtIk0LWZrLkwn54GP8SHp+I6fhXsGgajFeWcV1GQFk4df7rDqKzfiPon9r6G1xAm67swZI8Dll/iX8ufqK8vFUlJaHfXh7Sndbo8WErDvV+yuMjFZhYEZFbfh2x+1N0rzaMo05qR5kLt6FWd8yD612vh6aJLUdM4rm9X01oJsKK0NNtriK23DNXm1OGKw6aeprh5ThVd0bmoahGDsOK19F8toQygZNcFM7tKd+eK6Xw7fAARsa+cngFTpXW56NLEc1SzOrNY+p6hFHJ5e7mrd/eLBbM+eccV5zqF48900m44zxWuBwvtJcz2RWKr+zVkdVfXsUluVBySKwWNUrOV3lwzEirjda/ScmpKnh7eZ5vO56ktvb+fMi4zzXaW4WGBEyBgVzugRb5s+lJrt9Na3ewHjFcWeUnWpqK6HRFqEeZnTCVCcAjNTQkeYv1rhbTVXEuXarza8ElTDd6+Kng5p2RpGvG1zSnuzG6tuJwB/KtRNdilsggA39K54usj7evA/lUTqIzheM15eHxtbD3Se5tzNbHRRRrduip97PJFdVBF5cSr6CuC0i6ltXLA5rYh1mZnAkOAa9/A47DwXvy95lwmjqaQ7SOetUIJZZIw6sHFTLKpOG4Poa+i3V0b2J94U4JpDIg71E2G5HSk6dqdh2Jt6+tG5fWoacDjsKLASbh60Ej1puRTuKVgK94okgYDll5FYUp5rpelY2p2bRkzRDMZ5IH8P/ANaurDzSfKzCtHqjMeoWFPJ5pOtektDiZCVNOSIk9KnRBnmtjT7BSolmHynovrU1KygrsqMHJ2RmwWO5RJICEz2HJrV8yMiOOOPy1jGAMVo4AAwAAOgHaoLtd8RIAyvNee6/O9TrVLljoVg1TRvxVelRsGm1czTL6NUymqUb1YVqwlE0TLANLUStUgNZNFDqQ8A/Sior3zPsk3lDMhQhR70JXYEIdMDkU8EHvXOJYanwCoH/AAOpVstV6CQAf71dLoQX20P2kv5TeorIS11del0o+pzU0cesK3zvA49azdJdJIpT8jRpRUUIuMfvQmf9mpMP7Vi0WR3i/uw46oc/hVXdzV8qzAg4weDWcymNzGTyv8u1b0npYxmuplW+l7b+TzRm1Q5Qf389voK29q7QFAUDoAMYpgI6EcUZz0b866JzlN6mUYqOw9DGv8INXYJUYYGAfSs8I56bT/wKnpHLngD/AL6FYTSfU1Roso9AKhx61JDv2DeMGqtzLIkrKNuOorBRbehaZNSjrVLzpj0x+VKJLk9CPyp+zY7nkXxE0r7D4lmVBtivP38Z7DP3vyOfzrBGEACjAXoK9J+JtpNLplnduit5MxUuByoYdPoSK80mkSGNnc/Koya+8y6q62Gi3utPuPlMZT9nWaW242a5WHAyN7EBR6k13vg3w8pQXk3K9Sx/iNePeBJZfGPjC+lViNO03asSr/G5J5+nBr6FtN0Noltn5UGMCvNzGtOcuSOy/E9HB040o8z+J/gO1KUSNFCh+RemKTz7m5aOAkysBtQVVn3/AG2FFUsXBAA7muj0+zS1QscNMw+ZvT2FeXOUacEdMU5yY6xso7RcjDzMPmf+g9qXUdQh0+DzJTuZvuIOrGo9Sv4tPtjNLyTwid2NcZd3Ut7O08z7mPYdAPQVyRg6j5pHUrRVkSajfXF9N5s5zj7qjoo9hVZWX1oFHtXTZJWRSHck9aeox3pqKvpj6VNBbyTzJDDlnc4ArKTNUbGl6imnWxDIWMzZGOwHH881pxa5aMAXDL9RWBPp1+jZe2bCjC45wBVR1lQ4eNk+oxWiw1KavfU5ZVpJvTQ7JNUsX6TAfWrCXVs/3ZkP41wm7immT0NDwUXsxfWH1R6AfLf+6wqJrO1k+/bxsPda4ZLmZfuyuPoasx6rexj5Z2/HmspYGXRlrER6o6WXQtJkJJsYgT3C4qjN4S0mRiREUJ7qaox6/fL1Kt9RVyLxG5HzwA/Q1CoV6atEOelLdEmmeH10hZzazO0chDNGxyAR3H4Vu2UnmRYP3hWVD4gtm+/E6/rU1ndQySF7diYwcEHqKy9nNRtJDXKvhOD8RfDu4k1We402WNLaVt6xMPuE9R9M1a0PwtqWmtmWNWHqpr0cgMuRTA4bgMD+NeZPCwlchYenfmRwmo6VPI4Jt2P0FW4bIR2hVoyDjuK7DFIVUjBUH8K5qmA54qKkaRpKLueM6sgjvHC1DY3DQzhgeK9hn0zT58mWzicnuVrOl8KaJIxb7GEP+ycVr9VfLa5ySwsua8Wef6vqLSwBAe1c+fevU7nwPpcudkksZ9mzWZcfD4N/qNQI/wB5aVPDunGyMquHrSd9zh7I/vq0G5NbieA9Sgl3rcwyD8qJfDGroeIA4/2Wr6jLKkIUeWbszONGolqhfDP+tNZni841EfSt/RtNv7a5xNayKPXHFY/jWCRb1G2NyPQ1jmDUk7M0qRao6nNZwaDksD70gHY09fvD614TR59zuorNQ27GMqP5VSnsp5Z8gkKOlblqwYgH+6P5VbMagZAFflixM4yufUewi42Ofht5YkO4VDJdwq+xmAIrpDGjKQQOa898XWNzHdiW2BIPUCvQwc416nLJ2OTEQdKPNHU9I0G+i+ygKrP9Klu9Ry2wWE7Z/iArh/AK3yTsbpyE7A130t5Eo2iv0Kni8PTw6cmtNNyqMnOCexkX+tvpoXFrLKG6jHSqL+MJyuU08Ln++TXSR3CvwYi34VIRx8luSPQgYr1sJXoOkvd5vO45Qm9pW+RyDeLr4/dghX86rv4t1TsYR/wCu2MUTAb7JMnsVFRPp1lJ9/TLc/VRXfHE4Zb0jF0a385xB8U6w3S4QfRBSf8ACT6z/wA/X5IK7E6LphOTpVv+eKjfQNLYY/s6Nfo7CtlisJ/z7/BEOhX/AJ/zOQbxNrJH/H235Cq8viXXwpCXjE+hwP6Vt6h4TlMpazkiSM/wMTkfjWfP4V1dRlIo5P8AdkFdkKmCl2+djnlDER7mFJr/AIliYslvaXAPOJDj9RS/8JT4hGMaDaE+v2o4/lV19E1VDiSwnwPRc/yqCW0nhH7yCVP95CK6lGhLZJ/15GN6i3LWl+JNeEoa40ywx6bycfrXc6br8E6ZvZLe3OOMS5/SvNM4OM4qTLHvXPXwVOr0sa08TKB6yl/Yy/cvIW+kgqdHhYZWRGHswNeRJIV61Kt1tNefLKu0jpWN7o9NlTYxXqOxqI1w2l6s9nciTcXiPDpnqPb3rt4Jobm3WeCQSRt0I/kfesKtCVHfUcainsSo2O9WEkqnnFG/Fc7jc0TsaKyD1p/nKO9ZfmH1pQ7VDpFc5qLOKfI/RfxNZcMoD5dgAPU4qxJeWy8vKo/4EKxlDWyNYd2WTSVT/tPTx1u4x9TTW1fTB/y+x/gc0vZT/lf3F88e5f8Axo57Gs063pY/5e1/AGoLvX7BYj9nuUMnbcpxVqhVbtyv7iXVgups5YU7fgc8fWuKm1y6lyBdYH+yMVnzXkshJeZ2+rGuuOAm92YSxMFsj0GS8tUHzzRr9WFZ99qOkkBm1GCORehZuCPQ1wzT5681SubeGcEOmQa6YZek7uRjLE3Wx3Fpq2n3cpit7yGSQfwBxn8PWtAgjqCPqK8fuvDelXP37dt3qrkEVJY6I9iP9E1jWLdf7qX8mB+BOK2ngV9mX3kRxHdHrYIqWM85rzAW+skZXxLrAHvOD/7LWhY6V4hu8Kuta06/3jc7B+eBXNPCOKu5I2hWT2R6fbsWSoZctKW2hh0FcjYeDXiuY7u71W/uZYzuVZL6VgD9M4/SurxP6xj8682cIxfuu51R7sNyj7yFfwp6lD0IP0qMJcf34x9FP+NJ5ErH5pV/BP8A69RbzLuR6vp6arplzp8vCToVz/dPY/gcV8z/ABINzpej3do4KXO4wv8A7Jzg19PCGYfdunH/AAEGuc8T+BdF8TMX1aEySEYMkTeWxHoeoP4ivYyzHxwjcZ/C/wAzzsZhfbtSjujwj9mm0KjW5AvCTRD/AMdaveBntyT2Hes7SPB2heC7eSLRYJoxdsGmaaTfuKjj+Zro9Nt9gE8o+cj5VP8ACPX61eJxEKjdSO3QiFOS917ktjZrb5lkAM7d/wC6PQVJeXMVnbvPM2EXoB1J9BT5ZUiieWVgiIMsT2FcTq+pPqFxu5WFOI0/qfeuCEJVZXZ16RVkNvr2W+uDNMcdlUdFHpVYKPQGmA04E12WSVkCHbF9MfSnhM9C350IN1Twxu8gjRCzNwFAzmspOxokNiSR3WNMszHAUDJJrsdG0tbGLfId1y4+Yjoo9BTdG06CzAldlkuSOT/c9h/jWrx1FefUqX0RoN2+rGo3t4X++gb61KTRWN2hlRtNsCDm1TmqkugadIcqrx/7rVrUuK0VapHaTIcIvdHOS+GV/wCWN2R7OtUpvDmoL9xo5B7NiuwoxW0cZVXW5m6MGcHJpWpRfftJPqozVcq8Z2ujKf8AaGK9F5FIyq33lVvqM1sse+sSPYLozz6M5PWr+mXH2e5G4/I/yt7ehrqpdOsJeXtYs+oGKqS6FYPnYzxE+jZH61X1qnNWaF7JrYu2twoRhIwUIMkk4AHrXKanqOm3N676TqltdAn94LadX2N3zg14h+054l1mz16y8JQ3E0OmLaJcS7fl+1MWYfMR1VduMdM9a5H4Xa+NI1iJywEMpCsO1RGPJ+8Wpokp+6z6Zj1G9i+7cP8Aic1YTXr5Orq31Ws+KOW8tIry1heWCUZV0GfwqGSN0Pzqyn/aGK7VClPWyZg3OLszei8RTf8ALSFD9OKtxeI7cj97C6n/AGea5Ldz1prPSeEpPoL20kdxFrmmyYHnFP8AeGKuRXlnJ/q7mM/8CrznfR5lZvAR6MaxD7HpqlW+6wP0NLg15qlzMn3JXX6MatRaxqMeNt05x681jLAS6MtV11R6ACaa8aP99Fb/AHhmuNh8Sagv3yjj3XFXoPFBP+tth9VNYywdVdC1VgzZm0rTZv8AW2EDfVBVGbwpoMxBNgqH/YYinQ+I7FvvrJH9RmrkGr6bKQFukBPZuKwlQmt4g1TlvY4r7TtlGz0HT6VowXJcAEVFYaeoVWbk4H8q0VtEFfjEpRvZHoRjKxXecA4FQywRyrudQanltcNuFVbucQx/N2qob+6Z1NFqPto0Q4QAfSrBXvWHBqsW/BYdfWtaC7ilHDA1vOnUW5hCcXsWopSjDnitm3Yumaw1wWHce1akVyERVWGU++K+x4cdWSkm9Cm9S7zSVUkvdn/LNvxqBtSYf8sT/wB9V9WpRcuW+ouZI0sUhrOXUwekX5tUq3jOuU8vPozH/CtnTkgumWyOKqy39nAds1zGh9zUiSGX5S0eT1HNZ954dtbp/MZ9jeqj/wCvVU1C9qjsKfNb3RZtd0qM/wDHyX/3FJqrL4n04DCpPJ7bcfzpf+EUsz1u5/wApf8AhFNPx/r7j8x/hXWvqa3bZzt1+yM268Q6dJ10dJf+ugX/AArFvdQtJeYtEtYvfc39CK68eFtL7tcN/wBtB/hSjwzpA/5Zyn6ymumGJwtP4U/x/wAzKVKtLe39fI84mLE5VAuew7VVk8z0r1BvC2jsP9VMPpKahbwlpR6G4H/Awf6V1xzOh2Zg8JUPL2lmA4BqG28Qa1pk5kstw/vIRuVvqK9TPg/TO01wPxX/AApn/CG6bnInnH/fP+FU8xwzVpL8CVhKq2OLsvinGrCPV/D97ER1ltAJFP8AwE4I/M1rR/EjwpJjM99Ef7sljID+gNb6+DdMzlprg/8AfI/pUieDdGByfOY+7D/CuKVXAt3V16f8E6FTr+Ryt58TNBi4s7PVL9+yx2pjH5uRWcfiDqV/MIYtFls429G3Pj3OP5V6CPCujocqkuf98f4VRuPDT+Zm2ESL6tISaqlXwSez+YSpVrHPrO0kYcq6k9m6ikEjE81snw3fk/62AD/e/wDrUv8AwjN5jm6hH4//AFq3+sUP5jH2VV9DEdzUe5q3f+EZuOr3kI/E/wCFRvoCpnffwjHuapYij0YnRqdjGDH1oJrRk02zj66jGfoGNVpILZRhJxJ+BFbKcZbfkZuLjuV804E0vlRf3R+dJ5aDogqtCLi/hQCvcgfWm7V/uj8qcm1Bwi/iKLDujS0zSpr9/wB3JCqjqWcfyrftvC1vkG4umk/2YhgfnXI/aGAwAoz/ALIpqSupyjuh/wBljXJUpVp/DO3yNo1Kcd1c9HtdLsbT/UwDI7t8x/WrmP8AOK8zS9vE6XMp/wCBkVImq6hGcrdTr/wMmuGWAqS1crnVHFQWiR6Rn0pMV5/Fr+or/wAvrt9cGrCeJtQXrKpHugrF4CqtrGixMDuKUVxa+KrsdRE3/AakHi2YfeghP4kVm8FW7FfWKfc7HIFG70rkP+Ex7NaIR6hzUsfjK2z89o34SCpeDrr7I/b0+50N/BFMITJjCSZwe/HSmO45JIA6k1hN4rsbqWKAQSxl3ADsRgH3rN8b6wLe3TTYn2yzLulOeQnYfj/KtI0Kl1GSsQ5xeqIPEGurey/Z7dj9mQ9f+eh9fp6VjfaFrHNxheHxUX2h/UV6UaSirIz5joFnBPUVPHIGrnI7hiQD+hroPD2l6hqlwPIUpbg/PO4+Vfp6n2rOpaKuzSLuaVhaz3U4ht49znuegHqTXZ6fpS2SfJIDKR8zlOfoOelWdPsYLC2EMC/7zHqx9TVjmvInUcnobkJhdvvS5/4AKPs3TE8i4/u4FS5NG71FZXY9RojwMF2PvxRsGPvv+dO3LRUgM2gfxN+dGwep/OnGkpFCbB6H86Nqf3f1paKQCYHTFOAHpTaWgBaUGm0uaBHlf7Q/gceKvBr6nZwb9X0YNPDtHzSRdZI/fgbh7r718i6bdFWAVuOoNfofnAr4o+OPglvBPjudrSIppGpk3VkQOEyfnj/4Cx49iK78NO65GYzVnc9s/Z/8W+fB/Y13L/rDmIk9Hx0/ED8x717U4D8MAw9wDXwh4T8SSaPqUM6z+WFYHIPIIPBr7Y8H69a+JfDVjrNpIki3CfOUPAccMPzrCrBwlpsbNqS5i3PpmnT8y2iZ9VG0/pVKbw1psg+QzRH/AGXz/OtmipjWqR2kyHCL3Ry83hRsZgvgfaRMfyrPm8N6rGcrEko9Ucf1xXcUuTXRHG1VvqZuhFnnE9le2+fOtJkx3KHH51W3Dpnn0r1DLetRTW1tP/r7eKT/AHkBrojmH80TJ4fszzTNKHx3rup/D+ky/wDLqYie8bkVnz+ErdsmC9lT2dQ3+FdMcbRe+hk6M0csHpUf5x9a2ZvC2pRn91JBOP8AeKn9aozaRqkDDzLGXAPVBuH6V0RrUpbSRDjJbo0tIvEmjX5h90fyrVzXk/h/XjDKqSN0A/lXfWWswShR5gyfevwDFYOpRm9ND2sPiI1I7m2QCOax9etibZmUZ4rWicOgZTwaWSNZEKsMg1zQlySTOicVONjxK9uZ4bl1yykHvWz4avbuaYKclfWug8S+HopMyouDUWg28FmvzAAivrJYyjWw94x1Pm/q9SlWs3odNaBgoLda0Y5yowaxU1K3zt3CrsVxG4+Ug14sMRicO703Y9WDi+pNcOXPtUBxinO2ajbODWf1uu6ntHJ3KaRXmyMkGollcHrUkmTwOtMEJPev1/L8VCWGi6ktThe+hYiuJARg1qQahKoAPzAdjWbBbnGc1aihOcV1zVORtDmNWC9ikHzfu2/2un51YzkZHPvWakJUcjOe1PSIRDMMzQn0HKn8P8K4pQV9DdPuX8GkC81RbUTB/wAfMW5f+ekXI/EdRUyX1tKm+GVZB7Gp5Jdh3RZApcVQlv1Wq7algZHaqVGbE5xRrHHemNLGo5NYEurk5IGR9apy6qT0PWt44Sb3MnXijpJL1EHrVKXVAucCsCfUC4IEgFUprpm/irqp4JdTGWJ7HQSas+Miqc2rTY4bGaxjNzySaN2TgK35V1xw0F0OaVeTLzapdHkSECoJL+7c8zMPoahKv02E0io+ORW6pwXQyc5vqOaaVj80jn6mmli3GSfxpCjd+KcqgHjNXoiNWRMpNMKkVYCFzkgj8aa6YxjmqUhWIefWlGakxgZwKDx2xTuKxEenpSYNTYyKNvtRcViICjDeuKk2/nQV4ouBEA3c/pSlio4GafilEZPRSfwoug1K5YscmMGm4PcVeitJ3OEgkb6KasLo+oyfdtJPx4qXVhHdlqE3sjGcN2X8qrur9wa6iPw3qj8mFV/3nFWV8KXrD554U/M1m8XRjvJFKhUfQ4aQyAfLVOVrgdAa9EbwdMx5u4v++DUZ8Ez9ryLHupoWOw38w3hqvY8xk1G7hJBBFZ+seLL29vY3u9LuppEjEZngwd4HTKnv7g16y3gSd+Gu7fH+4aaPh3GvzyajEn0T/wCvQ8ZhHu/zHGjWj0PIzr8QXmyvwfQ2/wD9emNrz4/daTfSH3VUH6mvVJfBOlJKRdaymwdBGgz+pobQPB1so33F7cn0XCj88UKrQfw3fyNGqi3sjzzQdYuBdLNdaL5kY6RyPkfjjGa9f0TxgJLZEksEjCjASL5QPoKw0Tw5bf8AHvoQk9552b9BVmDXntTiz02xth/sRZP51lWpRrLSn97t+rCNXlesjv7G8iuoBIoK57MMEVYOM151L4m1lzkToo/2YhUf9u6u3/L7ID7AD+led/ZtTul/Xob/AFqHZnpGBRj0FebHWNXJyb+b/vrFOOqak4Ae8mI/66EUf2dP+ZB9aj2PRiue1NKgdePrXnn224f/AFk8px/00JpHmZx8zufqxNL+z31l+BX1ldj0EyxL96VB9WFQtfWa53XcIx/tivPXIz3pVPpxVrLl1kS8U+x3rarpw4N7F+dN/tjTP+fxD9M/4VwuT60qnJ9/aq/s+n3ZP1qXY7r+1tN/5+k/I0p1XTgP+PlT9Aa46K0vZceXbSsD0O01dj0jU2wDBt/3mArGWEox3l+KNFWqPodH/a+nf89//HTTTrOmj/luf++DWXF4fuSB5s8aewBNWo/D1uP9ZcyMf9kAVi4YZfaf9fI0Uqr6Er6/pq9ZJD9IzXnXxwXQ/F/gqfTUEyalAwnsZmhOFlHYnsrDIP1B7V6RHoemp1jdz/tOaspp9ihG20h49UB/nSjPDwd0m/mDjUl1R+fMHhPxTLd/Z00K+MxbGfKO367umK+3vg94YTwl8PdL0n7R9pmKtPPLtKhpHOWwGwQB0GfSuwVEQYjRVA9FAp3Pes6tZTVkrf16Fxg1uxcH0pOfSkzS/gfzrmNA5HXFHHqKTP1pcjHSgBeKMimZ9DSg5HrQFh2RS59KjAwaWgLD/wAaVTyKaKVfvCgR8ssz7gwODgfyq1aapdQTId5wDVJZ0bAz2FX7S3SU8149Skre8j5uM5J3TPXPCmqpdWiBn5x610YdT3FeP6ZcTaeRsztrUl8ZrbAKz8ivjsRldV1X7JXTPoqGOhye/udvr1zHFbNuIzivN7nU5GZwh4zVbV/FLX42IxwaoRvlRzXtZflssPC9Vas8rG4pVZ+5sXFvJw+7zDmur8PXc0gG8muZ0u1NzMOMiu1sbVLeMADmjHypqPJbUzwylfmubAlXAJNOE8R43Cuf1e4eCAuprjx4inW45JwDXlUMtnXTlE7Z4tU3ZnphAJyKRm2jNYuj6oLiIEnk1Pf3wiQjNHJWhLku9Cvaxa5i5/aTRvtCg/U1aS9vCAyRxYPq1cU1y80wKHvXUaYzCAb/AEr77Jqs6kOWprYzhWcnY0/tN6y5LRL9M1G014eDcIPolRGQZ64pDIo5BzX0Sgl0NXMd+/Lc3TfgoFOFlA8nmGeRZMffTC/n61DvyfSnbyvOcfWqcX0EpE6W3lriZftSj+JWIb8qiZrDJESDPdWJBH4VWlvHU4FVLiQXA/eKCexHBH41UaUm7tilVVtizMsWfljUfhVYlQPuL+VVWkuIgQreavo3X86VJVdgHJjY9mrqULI53K5ZUB+WKqB3xSP5WcDJx3pGj4yCaYOB0pokdu5HyjFP3qFyRzTMbh6UoAA5GaBC71PY0jYI+XpSsy4xTVIJ27qADHGCOaYcjtVpbdm5BpzW47tilzorkZTGaNjE5qyywoMF+aQzQKOuafN2QuXuVSr+1L5be1SmeH0FMa7TGABVXl2JtHuJsYAZFSx27ueOKrtcg0C5dfulqLSBcprw6UrgFpPwq9Bo1sfvHJ+tc6Ly4B+UtUi394vIYj8a550qz2kbxnSX2TsbXTLKPjylP1FXY7aBD8kSj8K4M6pqA+7PinLq+pDn7U1cc8FWlvI3jiqa6HoIUAcKBQK4Ea3qIOTcE+1XLfWr44JkBrnll9VdUaRxMGdnmm8VkWGpTTAFwK1VcEcmuGdOUHZnTFpq6FZlRcseBWLqevpbZWNNzds1ruY3BVsn8KxNS0aKfLRq+6t8OqXN+8IqqfL7hiT69fzZxJ5Y9FrOmuZ5CfMmdvqxq9Lo9+jYW3dh2NMGj6kx4tW/GvehKhFe7ZHkyjWk9bmdzSE1tx+HdSfrGif7zVPH4VvD9+eJR7ZNU8VRW8kJUKj6HNGJTyBtPqOKDE3UMG9jXYReFFA/eXn/AHytWI/C9kv35pn/ABArJ4+iupawtRnEqBj5lI+tPUL2wa7xPDulJz5LOf8Aac1YTSdMVdosYvrjmsZZlT6Jmiwk+rPP8A9Keltcyf6uCR8+imvQUsooc/Z1RPZkBFPE1wnEkAI9Yz/SsnmP8sfxNVg/M4WLSNRkHy2cv4jFWo/DuqNgmNE/3nrtEnib+LafRuKUyxAZ3jHtWEsxq9EjRYSK3OWh8LXDf666jQf7IJNXoPDNmg/eTyyfTC1sG4iH8WfoKBcIegY/8BrCWLry6mioQXQpRaHpcf8Ay7bz/tsTV2K3t4V2xQRoB/dUUef6Rv8AlTTO+eIT+JrnlOpL4mzVQS2RYzSVB5sx6RqPq1G+c/8APMfnUWKsT96WoMzf30H0Wl/e95R+C0WCxNRUO1z1mb8hRsP/AD0c/jSCxNzR9RUPlqQcs5/4FTfKTGPm/FjRYLE5IB5P5mk8yP8Avr+dQ+VH/cBoEcY6Iv5UDsSmaEdZV/Ok+0Q9pAfoKaAB/CPyp3H0oCwn2iIjI3H/AICaQTqekcn/AHzTvxo+tAWQglPaJ6XzH7RH8TRS5oCyAPKf+Waj6tSo05Yf6sc+5pM9qeh+YfWglo+RDvQjORwK0dOvikigmtTXtMWHJUdAP5VzAyr8djUU5wxMLo+TacHqenacYp7UM2Olcn4mt1EuU9adp+qNFbBc1VnvDPN89ceGwVSnVc3saVKsXGyKtvFIgBxWlFIQBT4WjZaZJtDcV1VXd2aOdbXOw8LFOCetdYDXnGi3ckMgABxXWLrEax4YgNivk8dh5urdHqUKiULMn15gbQg9SK83aNjcFQOprptS1X7QxQGs1VRZA5FehgYyoQafU567U5aHU+GrQpAGPpV7VLQyR5B4qHRL2DyQu4A1p3EsbQMcgjFeNWnNVnJnbCMXTscnHdQ2k+HPIrotN1e3mQAMBXnHiB2+3MFJAqvp13LFLjecV9Xg+fDx9pF7nDGu4Ssen6hfqP8AVmq9tfkn5mrm4royKNzZqdZD2NY1szxHteZaLsbqpd3OwjuFZcjmopZHcYzgVnabIzJirw96+3wk/a0lN9TXmuhjfU00ls96nAOOgxTwveuvmsFrlYA+maVsMu1k3D0Iq105xSdf4anmHylNY5Y/mhZsf3G5FON4qjEsLI36VeUc89KmxCU2soYehqXNdilEzDI7LlITj60ga5bgRAfjVp7XHNs5X/ZPSlgk8th58ZU+vanzq2gcmpVSC9Y/dUCpHtZlG4yKK20aN4/lwarTws4IUGslWbfY09kktDJMk4GBMRUZLt1larb2kw/gNM+zSj+A1upRMHGRW8rPJZj+NHkrnuatrbzH/lmakSynb+A03US6iUG+hS2IB92gIvoK0l02dj0qdNHlPU1m68Fuy1Rm+hk5A4wKWt2LQ8/eNXItEiUcjNYyxdJdTRYebOWCN6E09YJXOFjNdlFpcK4+UVaSziXoornlmEVsjZYTuziYtNuXPCVbj0KdupxXYpDGBwBS7R6VzyzCfRGiwsFuczB4bUkGRzWlb6JaREZXP1rV6UZrmniqs92bxowjsiOG2hiGEjA/CpePSkzS1ytt7mgUoNJRSGL1opKWmIKM0UhoAXvRRRmgAp3FNozQAuaM4pKKLiEYK4IdAR7iohbIgPksY/bqKnzSU7jIP9IQ/MiyL6rwaT7RHnDEof8AaGKtA01grDDKCPei4XIwd3Qg0hBpDbQhtyM0Z/2TTXM6cqySj0PBq/QB4ozUBukXiRGjPuOKcJozyrg/Q0+VhcnoFQeeo7003KjvS5GF0WhTqpfa19aY96o70/ZSYuZF+kzVE3SlN+6oGvhjOeKpUZMTmkahYetNLrnrWO1/71C9+cda0WGkyHVSN7zV9aQzJ6iucN83rTGvG9a0WEZPt0dIZ0HemtdoO9c01256Gm/aXPU1awncn250TXijkGm/bl965/z29aesx71X1ZIXtjeF2vUGpI7oGRee9c/5jAZFLHO+4YGOaX1dB7U8q8RzxlTz/CP5VxO3fKcdM1e1O8kmI64IH8qgtEJ52mvJwGHdGnZnzVapzO5Mi7VqJ8hs1ZKtj7pqvIDk8GvSRzXBZ2XpWhpZN3cKh9ayDnJ4NanhmURagu4Hk1x4uP7qUlujWnrJJno2maHH9nDFRkiq1x4ekmuOCdtdTpp8y1Tap6elaUULddh/KvzWWOrQm3c+jjh4Sikcd/wjKpFnGTXLa7E1gxB6V6+Y2IxsOPpXn/xBs8W7uFIx7V3Zbjp1K6hUd0zHF4aMafNE4SHVZoH+RjW5p+vSz/umY1x8YYtjBq/a7oZA4Br7OvhqcltqeJGpKPU6W7s1mBlYckVgTp5UhC1rHUt0O3B6Vi3Ls7k4NcmHhUV1LYdSUXqiza3ThwtdNp8LzhSelclZHE67gfyr0HR1BgGxSTj0rlx0lRalY1oe9obOmWaLGM9avG0GDxmqlq8isF2n8q6WxtnkjBKE/hX0WAzSFenaOlj16UFLQwDbMBwKFik/u4rqTp567D+VKun56xn8q9P62jf2ByxgkP8ADSrbTf3TXWLYAfwH8qkFkB/B+lQ8YhrD+ZyYtZjxg1MlhKetdStoB/B+lPFtj+D9KyljH0LVCKOeh0445FW009Cu10BHvWwICP4T+VO8s/3T+VYSxMmaqnFGA2jmNt9sxX/ZPSpYAitsnTY1bYRv7ppklusow8efwqHWk9xqMVsVRaRMMgAil+wxf3RSNbXNv80BLL/dNSQ3O4hZEZH9xU80+jKshosogfuinC2jH8IqyFJHANBVv7p/Ko55dw0IVhQdqXy09Kk2t/dP5UbH/un8qm7GMwPSlpwVv7p/KjY3900h3EFIadtb+6fypdrf3T+VArjKM07Y390/lShG/umgLoZRT9jf3T+VJsb+6fypBcbRTgjf3T+VGxv7poGNpadsb+6fypNrf3T+VAriUUu1v7p/KjY390/lQAlFO2N/dP5Um1v7p/KgBKKXa3dTSHcP4T+VFmwClqNpD/dP5VXlnYdj+VWqcmF0W80hkUd6ypryQcBTVSS6mPY/lW8cPJmbqJG6bhB3qJryMd6wWlmbsfypCZCOQfyrdYVdTN1jcN9GO9QtqK84NYjeYexpMP8A3T+VarDRRHtmar6iT0qJrxm71QAc/wAJ/KjD/wB0/lVqjFC9oy79qfpnI9DUUhjbkZRvVTVf5h/CfypGLf3T+VUqaJ5xzvdp/q5RIPRutQPqMiHEsLJ7jkU/L5+6fyoZmIwUJ+orRQXYlzBbtZBlZAfbNKZWPU1UmtY5Dnyyh9V4qE295FzFIXH91hWihEjnZp/aPkwarvMTwKoGedOJoHHuBU0bo4ypqlStqJzbJWkPrSFyRyaQo552mja3TafyqkkQ2xN1ANKEb+6fypdjf3T+VMBAacDRsb+6fypNr/3T+VIBQaUMaQI390/lShW/un8qVh3JA59afG/zr9ahKP8A3T+VOiV96/KevpUtIq5//9k=';

main().catch(function (e) {
  figma.closePlugin('Nungil UI builder stopped: ' + (e && e.message ? e.message : e));
});
