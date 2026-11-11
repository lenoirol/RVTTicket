// Trang chủ RVT Ticket: cảnh 3D (Three.js tự lưu ở /vendor), bảng giờ lật chữ, vé lật theo bước,
// thẻ nghiêng theo con trỏ, dải ảnh trượt ngang. Tôn trọng "giảm chuyển động" của hệ điều hành.
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const finePointer = matchMedia('(pointer: fine)').matches;
if (!reduceMotion) document.documentElement.classList.add('js-motion');

/* ---------------- Header: nền trong suốt trên hero, đặc lại khi cuộn ---------------- */
const header = document.querySelector('.header');
const hero = document.querySelector('[data-hero]');
if (header && hero) {
  const io = new IntersectionObserver(([e]) => header.classList.toggle('is-solid', !e.isIntersecting), { rootMargin: '-120px 0px 0px 0px' });
  io.observe(hero);
}

/* ---------------- Xuất hiện khi cuộn ---------------- */
const reveal = new IntersectionObserver((entries) => {
  for (const e of entries) if (e.isIntersecting) { e.target.classList.add('is-in'); reveal.unobserve(e.target); }
}, { threshold: 0.18 });
document.querySelectorAll('[data-board]').forEach((el) => reveal.observe(el));

/* ---------------- Đồng hồ giờ Việt Nam ---------------- */
const clock = document.querySelector('[data-clock]');
if (clock) {
  const fmt = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZone: 'Asia/Ho_Chi_Minh' });
  const tick = () => { clock.textContent = fmt.format(new Date()); };
  tick(); setInterval(tick, 1000);
}

/* ---------------- Bảng giờ: tách ký tự, lật chữ khi hiện ---------------- */
const board = document.querySelector('[data-board]');
if (board) {
  const cells = [...board.querySelectorAll('[data-flap]')];
  for (const cell of cells) {
    const text = cell.textContent.trim();
    cell.setAttribute('aria-label', text);
    cell.textContent = '';
    for (const c of text) {
      const s = document.createElement('span');
      s.className = c === ':' ? 'ch sep' : 'ch';
      s.textContent = c; s.dataset.final = c; s.setAttribute('aria-hidden', 'true');
      cell.append(s);
    }
  }
  if (!reduceMotion) {
    const flip = new IntersectionObserver(([e]) => {
      if (!e.isIntersecting) return; flip.disconnect();
      cells.forEach((cell, row) => {
        cell.querySelectorAll('.ch:not(.sep)').forEach((ch, col) => {
          let n = 0; const steps = 5 + col + row % 3;
          const spin = () => {
            ch.classList.remove('flip'); void ch.offsetWidth; ch.classList.add('flip');
            ch.textContent = n < steps ? String(Math.floor(Math.random() * 10)) : ch.dataset.final;
            if (n++ < steps) setTimeout(spin, 70);
          };
          setTimeout(spin, row * 90 + col * 40);
        });
      });
    }, { threshold: 0.3 });
    flip.observe(board);
  }
}

/* ---------------- Đếm số ---------------- */
const counters = document.querySelectorAll('[data-count]');
if (counters.length && !reduceMotion) {
  const nf = new Intl.NumberFormat('vi-VN');
  const count = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (!e.isIntersecting) continue; count.unobserve(e.target);
      const el = e.target, end = Number(el.dataset.count) || 0, t0 = performance.now(), dur = 1400;
      const step = (now) => {
        const p = Math.min(1, (now - t0) / dur), v = Math.round(end * (1 - Math.pow(1 - p, 4)));
        el.textContent = nf.format(v); if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    }
  }, { threshold: 0.5 });
  counters.forEach((c) => count.observe(c));
}

/* ---------------- Thẻ nghiêng 3D theo con trỏ ---------------- */
if (finePointer && !reduceMotion) {
  document.querySelectorAll('[data-tilt]').forEach((card) => {
    const max = card.classList.contains('hx-place') ? 7 : 6;
    card.addEventListener('pointermove', (ev) => {
      const r = card.getBoundingClientRect();
      const x = (ev.clientX - r.left) / r.width - 0.5, y = (ev.clientY - r.top) / r.height - 0.5;
      card.classList.add('is-tilting');
      card.style.setProperty('--ry', `${x * max * 2}deg`); card.style.setProperty('--rx', `${-y * max * 2}deg`);
      card.style.setProperty('--mx', x.toFixed(3)); card.style.setProperty('--my', y.toFixed(3));
    });
    card.addEventListener('pointerleave', () => {
      card.classList.remove('is-tilting');
      for (const p of ['--rx', '--ry', '--mx', '--my']) card.style.removeProperty(p);
    });
  });
}

/* ---------------- Mã QR minh họa trên mặt sau vé ---------------- */
const qr = document.querySelector('[data-qr]');
if (qr) {
  const n = 25, finder = (x, y) => [[0, 0], [n - 7, 0], [0, n - 7]].some(([fx, fy]) => x >= fx && x < fx + 7 && y >= fy && y < fy + 7);
  const ring = (x, y) => { for (const [fx, fy] of [[0, 0], [n - 7, 0], [0, n - 7]]) { const dx = x - fx, dy = y - fy; if (dx >= 0 && dx < 7 && dy >= 0 && dy < 7) return dx === 0 || dy === 0 || dx === 6 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4); } return false; };
  let s = 42; const r = () => ((s = (s * 48271) % 2147483647) / 2147483647);
  let d = '';
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const on = finder(x, y) ? ring(x, y) : (x === 7 || y === 7 || (x < 8 && y > n - 9) || (x > n - 9 && y < 8)) ? false : r() > 0.52;
    if (on) d += `M${x} ${y}h1v1h-1z`;
  }
  qr.innerHTML = `<svg viewBox="-2 -2 ${n + 4} ${n + 4}" shape-rendering="crispEdges"><rect x="-2" y="-2" width="${n + 4}" height="${n + 4}" fill="#ffffff"/><path d="${d}" fill="#15201c"/></svg>`;
}

/* ---------------- Ba bước: vé lật theo bước đang đọc ---------------- */
const ticket = document.querySelector('[data-ticket]');
const steps = [...document.querySelectorAll('.hx-story-steps li')];
if (ticket && steps.length) {
  const setStep = (i) => { ticket.dataset.step = i; steps.forEach((s, k) => s.classList.toggle('is-active', k === i)); };
  setStep(0);
  const stepIO = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) setStep(Number(e.target.dataset.step));
  }, { rootMargin: '-45% 0px -45% 0px' });
  steps.forEach((s) => stepIO.observe(s));
}

/* ---------------- Dải ảnh: cuộn dọc → trượt ngang (màn hình lớn) ---------------- */
const places = document.querySelector('[data-places]');
const track = document.querySelector('[data-track]');
if (places && track && !reduceMotion && matchMedia('(min-width: 1001px)').matches) {
  let distance = 0;
  const layout = () => {
    places.classList.add('is-pinned');
    distance = Math.max(0, track.scrollWidth - innerWidth);
    places.style.setProperty('--pin-h', `${innerHeight + distance}px`);
    update();
  };
  const update = () => {
    const r = places.getBoundingClientRect();
    const p = Math.min(1, Math.max(0, -r.top / Math.max(1, distance)));
    track.style.setProperty('--tx', `${-p * distance}px`);
  };
  addEventListener('scroll', () => requestAnimationFrame(update), { passive: true });
  addEventListener('resize', layout);
  addEventListener('load', layout); layout();
}

/* ---------------- Cảnh 3D ---------------- */
const canvas = document.querySelector('[data-scene]');
// Máy yếu / tiết kiệm dữ liệu / giảm chuyển động: dùng ảnh tĩnh, không tải three.js.
const lowEnd = reduceMotion || navigator.connection?.saveData
  || (navigator.deviceMemory && navigator.deviceMemory <= 2) || (navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 2);
if (canvas && hero) {
  if (lowEnd) hero.classList.add('no-webgl');
  else {
    // Tải cảnh 3D khi trang đã hiển thị và trình duyệt rảnh, không chặn thao tác đầu tiên.
    const go = () => startScene(canvas, hero).catch(() => hero.classList.add('no-webgl'));
    const idle = () => (window.requestIdleCallback ? requestIdleCallback(go, { timeout: 2500 }) : setTimeout(go, 300));
    if (document.readyState === 'complete') idle(); else addEventListener('load', idle, { once: true });
  }
}

async function startScene(canvas, hero) {
  const probe = document.createElement('canvas');
  if (!(probe.getContext('webgl2') || probe.getContext('webgl'))) throw new Error('no webgl');
  const THREE = await import(canvas.dataset.three || '/vendor/three.module.min.js'); // URL có mã phiên bản -> trình duyệt lưu lâu dài
  const small = innerWidth < 800;
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: !small && devicePixelRatio < 1.5, alpha: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(devicePixelRatio, small ? 1.25 : 1.5));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;

  const scene = new THREE.Scene();
  scene.fog = new THREE.Fog('#dce8e7', 90, 260);
  const camera = new THREE.PerspectiveCamera(small ? 46 : 34, 1, 0.1, 500);

  // Ánh sáng bình minh
  scene.add(new THREE.HemisphereLight('#eef6f3', '#5f7458', 1.3));
  const sun = new THREE.DirectionalLight('#fff6e6', 2.3);
  sun.position.set(-38, 52, 26); sun.castShadow = true;
  sun.shadow.mapSize.set(small ? 512 : 1024, small ? 512 : 1024);
  Object.assign(sun.shadow.camera, { left: -70, right: 70, top: 60, bottom: -60, near: 1, far: 200 });
  sun.shadow.bias = -0.0006;
  scene.add(sun);

  // Số ngẫu nhiên có hạt giống để cảnh luôn giống nhau
  let seed = 7; const rand = () => ((seed = (seed * 16807) % 2147483647) - 1) / 2147483646;
  const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const coast = (z) => 9 + 5 * Math.sin(z * 0.07 + 1.2) + 2 * Math.sin(z * 0.19);
  const raw = (x, z) => {
    const d = coast(z) - x;
    const n = 1.7 * Math.sin(x * 0.11 + 0.5) * Math.cos(z * 0.13) + 0.9 * Math.sin(x * 0.23 + z * 0.17) + 0.5 * Math.cos(x * 0.37 - z * 0.29) + 0.3 * Math.sin(x * 0.61 + z * 0.53);
    let h = 1.6 + n + Math.max(0, -z - 22) * 0.3 * (0.65 + 0.35 * Math.sin(x * 0.17 + 1));
    const shore = smooth(0, 8, d);
    return h * shore + (d < 0 ? d * 0.4 : 0) - (1 - shore) * 0.6;
  };

  // Con đường: từ núi phía sau, uốn qua đồi, xuống bến cảng
  const roadCurve = new THREE.CatmullRomCurve3([[-70, -40], [-50, -27], [-31, -31], [-16, -18], [-24, -5], [-10, 5], [2, 2], [11, 7]]
    .map(([x, z]) => new THREE.Vector3(x, 0, z)), false, 'catmullrom', 0.35);
  const N = 320, roadPts = roadCurve.getSpacedPoints(N);
  const roadY = roadPts.map((p) => raw(p.x, p.z));
  const roadYs = roadY.map((_, i) => { let s = 0, c = 0; for (let k = -14; k <= 14; k++) { const j = i + k; if (j >= 0 && j <= N) { s += roadY[j]; c++; } } return Math.max(0.45, s / c); });
  roadPts.forEach((p, i) => { p.y = roadYs[i]; });
  const nearRoad = (x, z) => {
    let best = 1e9, y = 0;
    for (let i = 0; i <= N; i += 2) { const p = roadPts[i], d = (p.x - x) ** 2 + (p.z - z) ** 2; if (d < best) { best = d; y = p.y; } }
    return [Math.sqrt(best), y];
  };
  const height = (x, z) => { const h = raw(x, z); const [d, y] = nearRoad(x, z); return d < 6 ? THREE.MathUtils.lerp(y - 0.08, h, smooth(2.2, 5.5, d)) : h; };

  // Địa hình low-poly
  const seg = small ? 70 : 120;
  let terrainGeo = new THREE.PlaneGeometry(170, 120, seg, Math.round(seg * 0.7));
  terrainGeo.rotateX(-Math.PI / 2);
  const tp = terrainGeo.attributes.position;
  for (let i = 0; i < tp.count; i++) tp.setY(i, height(tp.getX(i), tp.getZ(i)));
  terrainGeo = terrainGeo.toNonIndexed();
  const pos = terrainGeo.attributes.position, colors = [];
  const palette = [[-99, '#d9c08c'], [0.25, '#e6d2a3'], [0.9, '#a5b874'], [2.6, '#86a15d'], [5, '#62844c'], [8.5, '#7f7a69'], [12, '#a39d90'], [99, '#ece7de']];
  const col = new THREE.Color();
  for (let i = 0; i < pos.count; i += 3) {
    const h = (pos.getY(i) + pos.getY(i + 1) + pos.getY(i + 2)) / 3;
    let c = palette[0][1]; for (const [lim, hex] of palette) { if (h >= lim) c = hex; }
    col.set(c).offsetHSL(0, 0, (rand() - 0.5) * 0.04);
    for (let k = 0; k < 3; k++) colors.push(col.r, col.g, col.b);
  }
  terrainGeo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  terrainGeo.computeVertexNormals();
  const terrain = new THREE.Mesh(terrainGeo, new THREE.MeshStandardMaterial({ vertexColors: true, flatShading: true, roughness: 0.95 }));
  terrain.receiveShadow = true; scene.add(terrain);

  // Mặt đường + vạch kẻ
  const rv = [], ri = [];
  for (let i = 0; i <= N; i++) {
    const p = roadPts[i], t = roadCurve.getTangentAt(i / N), nx = -t.z, nz = t.x, w = 1.55;
    rv.push(p.x + nx * w, p.y + 0.05, p.z + nz * w, p.x - nx * w, p.y + 0.05, p.z - nz * w);
    if (i < N) { const a = i * 2; ri.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); }
  }
  const roadGeo = new THREE.BufferGeometry();
  roadGeo.setAttribute('position', new THREE.Float32BufferAttribute(rv, 3)); roadGeo.setIndex(ri); roadGeo.computeVertexNormals();
  const road = new THREE.Mesh(roadGeo, new THREE.MeshStandardMaterial({ color: '#3d3b3f', roughness: 0.9 }));
  road.receiveShadow = true; scene.add(road);
  const dashCount = Math.floor(N / 4);
  const dashes = new THREE.InstancedMesh(new THREE.BoxGeometry(0.12, 0.03, 0.9), new THREE.MeshBasicMaterial({ color: '#f3dfaa' }), dashCount);
  const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), e3 = new THREE.Euler(), v3 = new THREE.Vector3(), s3 = new THREE.Vector3(1, 1, 1);
  for (let k = 0; k < dashCount; k++) {
    const i = k * 4, p = roadPts[i], t = roadCurve.getTangentAt(i / N);
    q.setFromEuler(e3.set(0, Math.atan2(t.x, t.z), 0));
    dashes.setMatrixAt(k, m4.compose(v3.set(p.x, p.y + 0.08, p.z), q, s3));
  }
  scene.add(dashes);

  // Biển có sóng
  const seaGeo = new THREE.PlaneGeometry(260, 220, small ? 50 : 80, small ? 40 : 70); seaGeo.rotateX(-Math.PI / 2);
  const sea = new THREE.Mesh(seaGeo, new THREE.MeshStandardMaterial({ color: '#2e8f93', flatShading: true, roughness: 0.35, metalness: 0.08, transparent: true, opacity: 0.94 }));
  sea.position.set(60, 0, 0); sea.receiveShadow = true; scene.add(sea);
  const seaPos = seaGeo.attributes.position, seaBase = Float32Array.from(seaPos.array);

  // Cây thông / cây lá kim (instanced)
  const treeN = small ? 140 : 260;
  const crowns = new THREE.InstancedMesh(new THREE.ConeGeometry(0.9, 2.6, 6), new THREE.MeshStandardMaterial({ color: '#3f6b45', flatShading: true }), treeN);
  const trunks = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.14, 0.18, 0.8, 5), new THREE.MeshStandardMaterial({ color: '#6b4f36', flatShading: true }), treeN);
  crowns.castShadow = trunks.castShadow = true;
  let placed = 0;
  for (let tries = 0; placed < treeN && tries < treeN * 30; tries++) {
    const x = -80 + rand() * 90, z = -55 + rand() * 75, h = raw(x, z);
    if (coast(z) - x < 4 || h < 0.8 || h > 10) continue;
    if (nearRoad(x, z)[0] < 3.4) continue;
    const s = 0.7 + rand() * 0.8, y = height(x, z);
    q.setFromEuler(e3.set(0, rand() * 6, 0));
    crowns.setMatrixAt(placed, m4.compose(v3.set(x, y + 0.8 * s + 1.3 * s, z), q, new THREE.Vector3(s, s, s)));
    trunks.setMatrixAt(placed, m4.compose(v3.set(x, y + 0.4 * s, z), q, new THREE.Vector3(s, s, s)));
    placed++;
  }
  crowns.count = trunks.count = placed; scene.add(crowns, trunks);

  // Dãy núi xa
  const farMat = new THREE.MeshStandardMaterial({ color: '#9fb2bc', flatShading: true, roughness: 1 });
  [[-110, -120, 34, 40], [-60, -135, 30, 34], [-15, -140, 38, 44], [35, -138, 30, 30], [80, -130, 26, 24], [-150, -100, 24, 26]].forEach(([x, z, r, h]) => {
    const m = new THREE.Mesh(new THREE.ConeGeometry(r, h, 7), farMat); m.position.set(x, h / 2 - 3, z); m.rotation.y = rand() * 3; scene.add(m);
  });

  // Bến cảng
  const dock = new THREE.Group();
  const plank = new THREE.Mesh(new THREE.BoxGeometry(9, 0.35, 2.4), new THREE.MeshStandardMaterial({ color: '#8a6a4a', flatShading: true }));
  plank.position.set(4, 0.55, 0); plank.castShadow = plank.receiveShadow = true; dock.add(plank);
  for (let i = 0; i < 4; i++) { const post = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.15, 1.6, 5), plank.material); post.position.set(1 + i * 2.4, 0, 1.1); dock.add(post); const p2 = post.clone(); p2.position.z = -1.1; dock.add(p2); }
  const hut = new THREE.Mesh(new THREE.BoxGeometry(2.6, 1.8, 2.2), new THREE.MeshStandardMaterial({ color: '#f1e6d1', flatShading: true }));
  hut.position.set(-0.6, roadPts[N].y + 0.9, -2.6); hut.castShadow = true; dock.add(hut);
  const hutRoof = new THREE.Mesh(new THREE.ConeGeometry(2.1, 1.1, 4), new THREE.MeshStandardMaterial({ color: '#c62a2f', flatShading: true }));
  hutRoof.position.set(-0.6, roadPts[N].y + 2.35, -2.6); hutRoof.rotation.y = Math.PI / 4; hutRoof.castShadow = true; dock.add(hutRoof);
  dock.position.set(roadPts[N].x + 2, 0, roadPts[N].z); scene.add(dock);

  // Đảo + hải đăng + dừa
  const island = new THREE.Group();
  const sand = new THREE.Mesh(new THREE.CylinderGeometry(7, 9.5, 1.6, 10), new THREE.MeshStandardMaterial({ color: '#e7d3a1', flatShading: true }));
  sand.position.y = -0.2; sand.receiveShadow = true; island.add(sand);
  const hill = new THREE.Mesh(new THREE.ConeGeometry(6, 4.2, 9), new THREE.MeshStandardMaterial({ color: '#7d9c58', flatShading: true }));
  hill.position.set(-1, 2.6, -0.5); hill.castShadow = hill.receiveShadow = true; island.add(hill);
  const white = new THREE.MeshStandardMaterial({ color: '#f7f1e4', flatShading: true }), red = new THREE.MeshStandardMaterial({ color: '#d4482a', flatShading: true });
  for (let i = 0; i < 4; i++) { const b = new THREE.Mesh(new THREE.CylinderGeometry(0.62 - i * 0.07, 0.7 - i * 0.07, 1.1, 10), i % 2 ? red : white); b.position.set(3, 1.1 + i * 1.1, 1.4); b.castShadow = true; island.add(b); }
  const lamp = new THREE.Mesh(new THREE.CylinderGeometry(0.45, 0.45, 0.7, 8), new THREE.MeshStandardMaterial({ color: '#fff2c4', emissive: '#ffcf6b', emissiveIntensity: 1.2 }));
  lamp.position.set(3, 5.85, 1.4); island.add(lamp);
  const cap = new THREE.Mesh(new THREE.ConeGeometry(0.6, 0.7, 8), red); cap.position.set(3, 6.55, 1.4); island.add(cap);
  const beam = new THREE.PointLight('#ffcf6b', 6, 22, 1.6); beam.position.copy(lamp.position); island.add(beam);
  const palmLeaf = new THREE.MeshStandardMaterial({ color: '#4f8a4a', flatShading: true, side: THREE.DoubleSide });
  [[-4.5, 3.2], [-2, 4.6], [4.8, -2.5]].forEach(([x, z]) => {
    const palm = new THREE.Group();
    const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.2, 3.4, 5), new THREE.MeshStandardMaterial({ color: '#8b6b45', flatShading: true }));
    trunk.position.y = 1.7; trunk.rotation.z = 0.18; palm.add(trunk);
    for (let k = 0; k < 6; k++) { const leaf = new THREE.Mesh(new THREE.ConeGeometry(0.35, 2.2, 3), palmLeaf); leaf.position.set(0.3, 3.4, 0); leaf.rotation.set(0, k * 1.05, 1.9); leaf.geometry.translate(0, 1.1, 0); palm.add(leaf); }
    palm.position.set(x, 0.6, z); palm.traverse((o) => { o.castShadow = true; }); island.add(palm);
  });
  island.position.set(36, 0, -30); scene.add(island);

  // Xe khách
  const makeBus = (stripe) => {
    const g = new THREE.Group(), body = new THREE.Group();
    const cream = new THREE.MeshStandardMaterial({ color: '#f6efe2', flatShading: true, roughness: 0.6 });
    const glass = new THREE.MeshStandardMaterial({ color: '#22304b', roughness: 0.2, metalness: 0.3 });
    const accent = new THREE.MeshStandardMaterial({ color: stripe, flatShading: true });
    const box = (w, h, d, mat, x, y, z) => { const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat); m.position.set(x, y, z); m.castShadow = true; body.add(m); return m; };
    box(1.35, 1.25, 4.2, cream, 0, 0.95, 0);
    box(1.37, 0.42, 3.4, glass, 0, 1.2, -0.25);
    box(1.2, 0.5, 0.06, glass, 0, 1.2, 2.1);
    box(1.37, 0.2, 4.22, accent, 0, 0.55, 0);
    box(1.2, 0.12, 3.6, cream, 0, 1.63, -0.1);
    const lightMat = new THREE.MeshStandardMaterial({ color: '#fff4cf', emissive: '#ffdf8a', emissiveIntensity: 0.9 });
    box(0.22, 0.14, 0.05, lightMat, 0.45, 0.6, 2.11); box(0.22, 0.14, 0.05, lightMat, -0.45, 0.6, 2.11);
    const wheelGeo = new THREE.CylinderGeometry(0.34, 0.34, 0.26, 10); wheelGeo.rotateZ(Math.PI / 2);
    const tyre = new THREE.MeshStandardMaterial({ color: '#1e2230', flatShading: true });
    [[0.62, 1.3], [-0.62, 1.3], [0.62, -1.35], [-0.62, -1.35]].forEach(([x, z]) => { const w = new THREE.Mesh(wheelGeo, tyre); w.position.set(x, 0.34, z); body.add(w); });
    g.add(body); g.userData.body = body; scene.add(g); return g;
  };
  const buses = [
    { mesh: makeBus('#c62a2f'), u: 0.08, dir: 1, speed: 0.022 },
    { mesh: makeBus('#0d5c63'), u: 0.72, dir: -1, speed: 0.018 },
    { mesh: makeBus('#f0ae3c'), u: 0.42, dir: 1, speed: 0.02 },
  ];
  const along = (u) => {
    const f = Math.min(N - 1e-4, Math.max(0, u * N)), i = Math.floor(f), t = f - i;
    const a = roadPts[i], b = roadPts[Math.min(N, i + 1)];
    return [a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, b.x - a.x, b.y - a.y, b.z - a.z];
  };

  // Tàu cao tốc
  const boat = new THREE.Group();
  const hullShape = new THREE.Shape(); hullShape.moveTo(-1.1, -2.6); hullShape.lineTo(1.1, -2.6); hullShape.lineTo(1.2, 1.4); hullShape.quadraticCurveTo(0, 3.6, -1.2, 1.4); hullShape.closePath();
  const hullGeo = new THREE.ExtrudeGeometry(hullShape, { depth: 0.9, bevelEnabled: false }); hullGeo.rotateX(Math.PI / 2); hullGeo.translate(0, 0.55, 0);
  const hull = new THREE.Mesh(hullGeo, new THREE.MeshStandardMaterial({ color: '#f7f1e4', flatShading: true })); hull.castShadow = true; boat.add(hull);
  const band = new THREE.Mesh(new THREE.BoxGeometry(2.3, 0.18, 4.6), new THREE.MeshStandardMaterial({ color: '#0d5c63' })); band.position.set(0, 0.0, -0.4); boat.add(band);
  const cabin = new THREE.Mesh(new THREE.BoxGeometry(1.6, 0.8, 2.4), new THREE.MeshStandardMaterial({ color: '#f7f1e4', flatShading: true })); cabin.position.set(0, 0.95, -0.5); cabin.castShadow = true; boat.add(cabin);
  const win = new THREE.Mesh(new THREE.BoxGeometry(1.62, 0.3, 2.0), new THREE.MeshStandardMaterial({ color: '#22304b', roughness: 0.2 })); win.position.set(0, 1.05, -0.4); boat.add(win);
  const wake = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 6), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0.35 }));
  wake.rotation.x = -Math.PI / 2; wake.position.set(0, 0.06, -5.4); boat.add(wake);
  scene.add(boat);

  // Mây
  const cloudMat = new THREE.MeshLambertMaterial({ color: '#ffffff', emissive: '#f4f8f8', emissiveIntensity: 0.55, flatShading: true });
  const clouds = [];
  for (let i = 0; i < 7; i++) {
    const c = new THREE.Group();
    for (let k = 0; k < 6; k++) { const p = new THREE.Mesh(new THREE.IcosahedronGeometry(1.6 + rand() * 1.8, 1), cloudMat); p.position.set(k * 2.1 - 5, rand() * 0.7, rand() * 1.8); p.scale.y = 0.55; c.add(p); }
    c.position.set(-110 + rand() * 220, 34 + rand() * 10, -120 + rand() * 50); c.scale.setScalar(0.9 + rand() * 0.9); c.userData.v = 0.6 + rand() * 0.7;
    scene.add(c); clouds.push(c);
  }

  // Máy quay
  const target = new THREE.Vector3(10, 3.5, -14);
  const base = small ? new THREE.Vector3(16, 30, 92) : new THREE.Vector3(24, 18, 78);
  let px = 0, py = 0, sx = 0, sy = 0, scrollP = 0;
  if (finePointer) addEventListener('pointermove', (e) => { px = e.clientX / innerWidth - 0.5; py = e.clientY / innerHeight - 0.5; }, { passive: true });
  addEventListener('scroll', () => { scrollP = Math.min(1, Math.max(0, scrollY / innerHeight)); }, { passive: true });

  const resize = () => {
    const w = hero.clientWidth, h = hero.clientHeight;
    renderer.setSize(w, h, false); camera.aspect = w / h;
    // Màn hình hẹp: đẩy cảnh sang phải để chừa chỗ cho chữ
    camera.setViewOffset(w, h, w > 900 ? -w * 0.1 : 0, 0, w, h);
    camera.updateProjectionMatrix();
  };
  new ResizeObserver(resize).observe(hero); resize();

  let visible = true, last = performance.now(), t = 0;
  const resume = () => { if (reduceMotion) { place(); renderer.render(scene, camera); return; } last = performance.now(); requestAnimationFrame(frame); };
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; if (visible) resume(); }).observe(hero);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && visible) resume(); });

  const fwd = new THREE.Vector3();
  function update(dt) {
    t += dt;
    for (const b of buses) {
      b.u += b.dir * b.speed * dt * (0.6 + 0.4 * Math.cos(b.u * 9));
      if (b.u > 1) { b.u = 1; b.dir = -1; } if (b.u < 0) { b.u = 0; b.dir = 1; }
      const [x, y, z, dx, dy, dz] = along(b.u);
      b.mesh.position.set(x, y + 0.02, z);
      b.mesh.rotation.y = Math.atan2(dx * b.dir, dz * b.dir);
      b.mesh.userData.body.rotation.x = -Math.atan2(dy * b.dir, Math.hypot(dx, dz)) ;
      b.mesh.userData.body.position.y = Math.sin(t * 9 + b.u * 40) * 0.015;
    }
    const a = t * 0.07, cx = 28, cz = -14, rx = 12, rz = 10;
    boat.position.set(cx + Math.cos(a) * rx, 0.05 + Math.sin(t * 1.6) * 0.12, cz + Math.sin(a) * rz);
    boat.rotation.set(Math.sin(t * 1.3) * 0.04, Math.atan2(-Math.sin(a) * rx, Math.cos(a) * rz), Math.sin(t * 1.1) * 0.05);
    for (let i = 0; i < seaPos.count; i++) {
      const x = seaBase[i * 3], z = seaBase[i * 3 + 2];
      seaPos.setY(i, Math.sin(x * 0.28 + t * 1.1) * 0.16 + Math.cos(z * 0.33 + t * 0.9) * 0.12);
    }
    seaPos.needsUpdate = true; // flatShading: pháp tuyến tính trong shader, không cần computeVertexNormals mỗi khung
    for (const c of clouds) { c.position.x += c.userData.v * dt; if (c.position.x > 110) c.position.x = -110; }
    lamp.material.emissiveIntensity = 0.6 + 0.8 * Math.max(0, Math.sin(t * 2.2));
    beam.intensity = 3 + 5 * Math.max(0, Math.sin(t * 2.2));
  }
  function place() {
    sx += (px - sx) * 0.05; sy += (py - sy) * 0.05;
    const orbit = Math.sin(t * 0.05) * 0.1 + sx * 0.18;
    const r = Math.hypot(base.x, base.z) * (1 - scrollP * 0.18);
    const ang = Math.atan2(base.x, base.z) + orbit;
    camera.position.set(Math.sin(ang) * r, base.y - scrollP * 5 + sy * 2.5, Math.cos(ang) * r);
    camera.lookAt(fwd.copy(target).setY(target.y + scrollP * 2));
  }
  // Giới hạn ~30 khung/giây: chuyển động chậm nên mắt khó thấy khác, CPU/GPU giảm khoảng một nửa.
  const FRAME_MS = 1000 / 30;
  function frame(now) {
    if (!visible || document.hidden) return;
    if (now - last < FRAME_MS - 1) { requestAnimationFrame(frame); return; }
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    update(dt); place(); renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }
  update(0.016); place(); renderer.render(scene, camera);
  canvas.classList.add('is-ready');
}
