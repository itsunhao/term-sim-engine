/**
 * bg3d.js —— TermSim 3D 终端背景（Three.js）
 * 深绿科技终端风：粒子星云 + 网格地平线 + 漂浮线框几何体 + 光晕。
 * 透明背景垫在 UI 之下（#bg3d, z-index:0, pointer-events:none），
 * 不遮挡任何交互；THREE 加载失败时静默降级，页面原有样式不受影响。
 */
(function () {
    'use strict';
    if (typeof window === 'undefined' || !window.THREE) return; // 降级：无 THREE 时静默
    var THREE = window.THREE;
    var canvas = document.getElementById('bg3d');
    if (!canvas) {
        // 脚本在 DOM 解析前执行（如 head 内引用）：等 DOMContentLoaded 后再初始化一次
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function () { initBG3D(); });
        }
        return;
    }
    initBG3D();
    function initBG3D() {
        var canvas2 = document.getElementById('bg3d');
        if (!canvas2 || canvas2.__bg3d) return;
        canvas2.__bg3d = true;
        canvas = canvas2;

    var reduced = (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

    var renderer;
    try {
        renderer = new THREE.WebGLRenderer({ canvas: canvas, alpha: true, antialias: true });
    } catch (e) {
        return;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(window.innerWidth, window.innerHeight, false);

    var scene = new THREE.Scene();
    var camera = new THREE.PerspectiveCamera(58, window.innerWidth / window.innerHeight, 0.1, 2000);
    camera.position.set(0, 62, 205);
    camera.lookAt(0, -20, 0);

    // ---------------- 粒子星云（双层） ----------------
    function makeStars(count, color, size, opacity, spread) {
        var geo = new THREE.BufferGeometry();
        var pos = new Float32Array(count * 3);
        for (var i = 0; i < count; i++) {
            var r = spread[0] + Math.random() * (spread[1] - spread[0]);
            var theta = Math.random() * Math.PI * 2;
            var phi = Math.acos(2 * Math.random() - 1);
            pos[i * 3] = r * Math.sin(phi) * Math.cos(theta);
            pos[i * 3 + 1] = (Math.random() - 0.5) * (spread[2] || 220) + (spread[3] || 0);
            pos[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
        }
        geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
        var mat = new THREE.PointsMaterial({
            color: color, size: size, transparent: true, opacity: opacity,
            blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true
        });
        var pts = new THREE.Points(geo, mat);
        scene.add(pts);
        return pts;
    }
    var nearCount = Math.max(420, Math.min(1000, Math.floor((window.innerWidth * window.innerHeight) / 1800)));
    var starsNear = makeStars(nearCount, 0x4dffa6, 2.5, 0.78, [70, 300, 170, -70]);
    var starsFar = makeStars(Math.floor(nearCount * 0.6), 0x27c98a, 3.4, 0.5, [300, 600, 200, -85]);

    // ---------------- 网格地平线 ----------------
    var grid = new THREE.GridHelper(340, 26, 0x45ff9a, 0x1a7a3c);
    grid.position.y = -46;
    grid.material.transparent = true;
    grid.material.opacity = 0.55;
    scene.add(grid);

    // ---------------- 漂浮线框几何体 ----------------
    var shapes = [];
    var shapeDefs = [
        { g: new THREE.OctahedronGeometry(15, 0),  c: 0x35ff9b, x: -88, y: -16, z: -12 },
        { g: new THREE.BoxGeometry(17, 17, 17),     c: 0x27d08a, x: 96,  y: -8,  z: -26 },
        { g: new THREE.TorusGeometry(12, 3.4, 10, 28), c: 0x52ffb8, x: -22, y: 18, z: -40 },
        { g: new THREE.IcosahedronGeometry(10, 0),   c: 0x1fe07f, x: 52,  y: -34, z: -8 },
        { g: new THREE.TetrahedronGeometry(14, 0),  c: 0x6affd0, x: -120, y: -30, z: 6 },
        { g: new THREE.TorusKnotGeometry(9, 2.6, 42, 8), c: 0x36f0ac, x: 10, y: -52, z: -22 }
    ];
    shapeDefs.forEach(function (d) {
        var m = new THREE.MeshBasicMaterial({ color: d.c, wireframe: true, transparent: true, opacity: 0.62 });
        var mesh = new THREE.Mesh(d.g, m);
        mesh.position.set(d.x, d.y, d.z);
        scene.add(mesh);
        shapes.push({ mesh: mesh, baseY: d.y, speed: 0.22 + Math.random() * 0.34, phase: Math.random() * Math.PI * 2 });
    });

    // ---------------- 光晕 ----------------
    function glowTexture(rgb) {
        var c = document.createElement('canvas');
        c.width = c.height = 128;
        var g = c.getContext('2d');
        var grad = g.createRadialGradient(64, 64, 0, 64, 64, 64);
        grad.addColorStop(0, 'rgba(' + rgb + ',0.55)');
        grad.addColorStop(0.4, 'rgba(' + rgb + ',0.18)');
        grad.addColorStop(1, 'rgba(' + rgb + ',0)');
        g.fillStyle = grad;
        g.fillRect(0, 0, 128, 128);
        return new THREE.CanvasTexture(c);
    }
    var glows = [];
    var glowDefs = [
        { rgb: '57,255,136', x: -160, y: 40,  z: -220, size: 150, op: 0.5 },
        { rgb: '45,180,110', x: 170,  y: -30, z: -260, size: 190, op: 0.42 },
        { rgb: '70,255,190', x: 0,    y: 70,  z: -300, size: 170, op: 0.38 }
    ];
    glowDefs.forEach(function (d) {
        var sp = new THREE.Sprite(new THREE.SpriteMaterial({
            map: glowTexture(d.rgb), transparent: true, opacity: d.op,
            blending: THREE.AdditiveBlending, depthWrite: false
        }));
        sp.position.set(d.x, d.y, d.z);
        sp.scale.set(d.size, d.size, 1);
        scene.add(sp);
        glows.push(sp);
    });

    // ---------------- 动画 ----------------
    var t0 = performance.now();
    function animate() {
        requestAnimationFrame(animate);
        var t = (performance.now() - t0) / 1000;
        starsNear.rotation.y = t * 0.018;
        starsNear.rotation.x = Math.sin(t * 0.05) * 0.015;
        starsFar.rotation.y = -t * 0.012;
        starsFar.rotation.x = Math.cos(t * 0.04) * 0.01;
        grid.rotation.y = t * 0.028;
        for (var i = 0; i < shapes.length; i++) {
            var s = shapes[i];
            s.mesh.rotation.x += 0.0028 * s.speed;
            s.mesh.rotation.y += 0.0048 * s.speed;
            s.mesh.position.y = s.baseY + Math.sin(t * 0.4 + s.phase) * 5;
        }
        for (var j = 0; j < glows.length; j++) {
            glows[j].material.opacity = (j === 0 ? 0.5 : j === 1 ? 0.42 : 0.38) + Math.sin(t * 0.6 + j * 2) * 0.06;
        }
        camera.position.x = Math.sin(t * 0.035) * 22;
        camera.position.y = 62 + Math.sin(t * 0.05) * 6;
        camera.lookAt(0, -20, 0);
        renderer.render(scene, camera);
    }

    function onResize() {
        var w = window.innerWidth, h = window.innerHeight;
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h, false);
    }
    window.addEventListener('resize', onResize);

    if (reduced) {
        // 减弱动态：只渲染一帧静态画面
        renderer.render(scene, camera);
        return;
    }
    animate();
    }
})();
