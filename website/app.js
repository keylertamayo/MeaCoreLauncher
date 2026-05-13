document.addEventListener('DOMContentLoaded', () => {
    // Smooth scroll para links internos
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if(target) {
                closeMenu();
                target.scrollIntoView({ behavior: 'smooth' });
            }
        });
    });

    // Menú hamburguesa
    const hamburger = document.getElementById('hamburger');
    const navLinks  = document.getElementById('nav-links');

    if (hamburger && navLinks) {
        hamburger.addEventListener('click', () => {
            const isOpen = navLinks.classList.toggle('open');
            hamburger.classList.toggle('open', isOpen);
            hamburger.setAttribute('aria-expanded', isOpen);
        });

        // Cerrar al hacer clic en un enlace del menú
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', closeMenu);
        });

        // Cerrar al hacer clic fuera del menú
        document.addEventListener('click', (e) => {
            if (!hamburger.contains(e.target) && !navLinks.contains(e.target)) {
                closeMenu();
            }
        });
    }

    function closeMenu() {
        if (navLinks) navLinks.classList.remove('open');
        if (hamburger) {
            hamburger.classList.remove('open');
            hamburger.setAttribute('aria-expanded', 'false');
        }
    }

    // Fetch GitHub Latest Release
    updateReleaseInfo();

    // Bug report form
    setupBugReportForm();
    applyBugReportTemplate();

    // Community Reviews (Supabase)
    setupReviews();
});

// --- Supabase Config (Inyectado por GitHub Actions o manual) ---
const SUPABASE_URL = '__SUPABASE_URL__';
const SUPABASE_KEY = '__SUPABASE_KEY__';
const SUPABASE_CONFIGURED = !SUPABASE_URL.startsWith('__SUPABASE_');

async function setupReviews() {
    const container = document.getElementById('reviews-container');
    const formToggle = document.getElementById('show-review-form');
    const form = document.getElementById('review-form');
    const status = document.getElementById('rev-status');

    if (!container || !form) return;

    // 1. Cargar reseñas
    fetchReviews(container);

    // 2. Manejar el toggle del formulario
    if (formToggle) {
        formToggle.addEventListener('click', () => {
            const isHidden = form.style.display === 'none';
            form.style.display = isHidden ? 'block' : 'none';
            formToggle.textContent = isHidden ? 'Cancelar reseña' : 'Escribir una reseña';
        });
    }

    // 3. Manejar envío de reseña
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const submitBtn = document.getElementById('rev-submit');
        submitBtn.disabled = true;
        status.textContent = 'Publicando...';

        const name = document.getElementById('rev-name').value;
        const rating = parseInt(document.getElementById('rev-rating').value);
        const comment = document.getElementById('rev-comment').value;

        try {
            if (!SUPABASE_CONFIGURED) {
                throw new Error('Supabase no configurado. Contacta al administrador.');
            }

            const response = await fetch(`${SUPABASE_URL}/rest/v1/reviews`, {
                method: 'POST',
                headers: {
                    'apikey': SUPABASE_KEY,
                    'Authorization': `Bearer ${SUPABASE_KEY}`,
                    'Content-Type': 'application/json',
                    'Prefer': 'return=minimal'
                },
                body: JSON.stringify({
                    user_name: name,
                    rating: rating,
                    comment: comment,
                    approved: false // Por defecto requiere aprobación
                })
            });

            if (!response.ok) throw new Error('Error al publicar reseña');

            status.innerHTML = '<span style="color: #4CAF50;">✅ ¡Gracias! Tu reseña ha sido enviada para moderación.</span>';
            form.reset();
            setTimeout(() => { form.style.display = 'none'; formToggle.textContent = 'Escribir una reseña'; }, 3000);
        } catch (err) {
            status.innerHTML = `<span style="color: #f44336;">❌ Error: ${err.message}</span>`;
        } finally {
            submitBtn.disabled = false;
        }
    });
}

async function fetchReviews(container) {
    if (!SUPABASE_CONFIGURED) {
        container.innerHTML = '<p>Configura Supabase para ver las reseñas.</p>';
        return;
    }

    try {
        const response = await fetch(`${SUPABASE_URL}/rest/v1/reviews?approved=eq.true&select=*&order=created_at.desc&limit=6`, {
            headers: {
                'apikey': SUPABASE_KEY,
                'Authorization': `Bearer ${SUPABASE_KEY}`
            }
        });

        if (!response.ok) throw new Error();
        const reviews = await response.json();

        if (reviews.length === 0) {
            container.innerHTML = '<p>Aún no hay reseñas. ¡Sé el primero!</p>';
            return;
        }

        container.innerHTML = reviews.map(r => `
            <div class="review-card">
                <div class="review-header">
                    <strong>${r.user_name}</strong>
                    <span class="stars">${'⭐'.repeat(r.rating)}</span>
                </div>
                <p>"${r.comment}"</p>
            </div>
        `).join('');
    } catch (err) {
        container.innerHTML = '<p>No se pudieron cargar las reseñas.</p>';
    }
}

const MAX_SCREENSHOT_BYTES = 4 * 1024 * 1024; // 4 MB

function setupBugReportForm() {
    const form     = document.getElementById('bug-form');
    const statusEl = document.getElementById('bug-status');
    const submit   = document.getElementById('bug-submit');
    if (!form || !statusEl || !submit) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setStatus(statusEl, 'info', 'Enviando reporte…');
        submit.disabled = true;

        try {
            const name        = form.name.value.trim();
            const email       = form.email.value.trim();
            const title       = form.title.value.trim();
            const description = form.description.value.trim();
            const honeypot    = form.company.value;
            const fileInput   = document.getElementById('bug-screenshot');
            const file        = fileInput && fileInput.files && fileInput.files[0];

            if (!email || !title || !description) {
                throw new Error('Por favor completa correo, título y descripción.');
            }

            let screenshot = null;
            let screenshotName = null;
            if (file) {
                if (!/^image\//.test(file.type)) {
                    throw new Error('La captura debe ser una imagen.');
                }
                if (file.size > MAX_SCREENSHOT_BYTES) {
                    throw new Error('La captura supera el tamaño máximo de 4 MB.');
                }
                screenshot     = await fileToBase64(file);
                screenshotName = file.name;
            }

            const res = await fetch('/.netlify/functions/report-bug', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name, email, title, description,
                    screenshot, screenshotName, honeypot
                })
            });

            if (res.status === 404) {
                throw new Error('El sistema de reportes no está disponible en este sitio espejo. Usa el sitio principal: <a href="https://meacorelauncher.netlify.app" target="_blank">meacorelauncher.netlify.app</a>');
            }
            const data = await res.json().catch(() => ({}));
            if (!res.ok || !data.ok) {
                throw new Error(data.error || 'No se pudo enviar el reporte. Intenta de nuevo.');
            }

            setStatus(
                statusEl,
                'success',
                `¡Gracias! Tu reporte se publicó como <a href="${data.url}" target="_blank" rel="noopener">issue #${data.number}</a> en GitHub.`
            );
            form.reset();
            const desc = document.getElementById('bug-desc');
            if (desc) {
                delete desc.dataset.userTouched;
                delete desc.dataset.template;
            }
            applyBugReportTemplate();
        } catch (err) {
            setStatus(statusEl, 'error', err.message || 'Error inesperado.');
        } finally {
            submit.disabled = false;
        }
    });
}

function setStatus(el, kind, html) {
    el.className = `bug-status ${kind}`;
    el.innerHTML = html;
}

function detectOperatingSystem() {
    const uaData = navigator.userAgentData;
    if (uaData && uaData.platform) {
        const plat = uaData.platform;
        if (uaData.mobile) return `${plat} (móvil)`;
        return plat;
    }
    const ua  = navigator.userAgent || '';
    const plt = navigator.platform  || '';

    if (/Windows NT 10/i.test(ua)) return 'Windows 10/11';
    if (/Windows NT 6\.3/i.test(ua)) return 'Windows 8.1';
    if (/Windows NT 6\.2/i.test(ua)) return 'Windows 8';
    if (/Windows NT 6\.1/i.test(ua)) return 'Windows 7';
    if (/Windows/i.test(ua))         return 'Windows';
    if (/Ubuntu/i.test(ua))          return 'Ubuntu Linux';
    if (/Debian/i.test(ua))          return 'Debian Linux';
    if (/Fedora/i.test(ua))          return 'Fedora Linux';
    if (/Linux/i.test(ua) || /Linux/i.test(plt)) return 'Linux';
    if (/Mac OS X/i.test(ua))        return 'macOS';
    if (/Android/i.test(ua))         return 'Android';
    if (/iPhone|iPad|iPod/i.test(ua)) return 'iOS';
    return plt || 'Desconocido';
}

function buildBugReportTemplate() {
    const so      = detectOperatingSystem();
    const version = detectedLauncherVersion || 'Desconocida';
    return [
        '### Entorno',
        `- Sistema operativo: ${so}`,
        `- Versión del launcher: ${version}`,
        '',
        '### Pasos para reproducir',
        '1. ',
        '2. ',
        '3. ',
        '',
        '### Resultado esperado',
        '',
        '### Resultado obtenido',
        ''
    ].join('\n');
}

function applyBugReportTemplate() {
    const desc = document.getElementById('bug-desc');
    if (!desc) return;
    // Solo prerrellenar si el usuario aún no escribió nada (o si solo había una plantilla previa)
    if (desc.dataset.userTouched === 'true') return;
    if (desc.value && !desc.dataset.template) return;

    desc.value = buildBugReportTemplate();
    desc.dataset.template = 'true';

    // En cuanto el usuario escriba algo distinto al template, dejamos de pisarlo
    if (!desc.dataset.listenerBound) {
        desc.addEventListener('input', () => {
            desc.dataset.userTouched = 'true';
        });
        desc.dataset.listenerBound = 'true';
    }
}

function fileToBase64(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload  = () => {
            const result = String(reader.result || '');
            // result viene como "data:<mime>;base64,XXXX" — necesitamos solo XXXX
            const comma = result.indexOf(',');
            resolve(comma >= 0 ? result.slice(comma + 1) : result);
        };
        reader.onerror = () => reject(new Error('No se pudo leer el archivo de captura.'));
        reader.readAsDataURL(file);
    });
}

let detectedLauncherVersion = null;

async function updateReleaseInfo() {
    const badge          = document.getElementById('version-badge');
    const downloadBtn    = document.getElementById('download-btn-hero');
    const downloadWin    = document.getElementById('download-btn-windows');
    const installCommand = document.getElementById('install-command');

    if (!badge) return;

    try {
        const response = await fetch('https://api.github.com/repos/MeaCore-Enterprise/MeaCoreLauncher/releases/latest');
        if (!response.ok) throw new Error('GitHub API query failed');

        const data = await response.json();
        const versionNum = data.tag_name.replace(/^(bat-|v)/i, '');
        const versionTag = `v${versionNum}`;
        detectedLauncherVersion = versionTag;
        applyBugReportTemplate();

        badge.textContent = `${versionTag} — Alfa`;

        // Botón Linux (.deb)
        const debAsset = data.assets.find(a => a.name.endsWith('.deb'));
        if (debAsset && downloadBtn) {
            downloadBtn.href = debAsset.browser_download_url;
            if (installCommand) {
                installCommand.textContent = `sudo apt install ./${debAsset.name}`;
            }
        }

        // Botón Windows (.exe). Mantenemos un fallback a .msi por si la
        // release aún es la antigua durante la transición.
        const exeAsset = data.assets.find(a => a.name.endsWith('.exe'));
        const msiAsset = data.assets.find(a => a.name.endsWith('.msi'));
        const winAsset = exeAsset || msiAsset;
        if (winAsset && downloadWin) {
            downloadWin.href = winAsset.browser_download_url;
            const ext = winAsset.name.endsWith('.exe') ? '.EXE' : '.MSI';
            downloadWin.textContent = `⬇ Descargar ${ext} (Windows)`;
        } else if (downloadWin) {
            // Si aún no hay instalador en esta release, apuntar a releases general
            downloadWin.href = 'https://github.com/MeaCore-Enterprise/MeaCoreLauncher/releases/latest';
        }

        console.log(`MeaCore: Actualizado a la versión ${versionTag}`);
    } catch (error) {
        console.warn('MeaCore: No se pudo obtener la última versión de GitHub.', error);
    }
}
