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
});

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
        const versionNum = data.tag_name.replace('bat-', '');
        const versionTag = `v${versionNum}`;

        badge.textContent = `${versionTag} — Alfa`;

        // Botón Linux (.deb)
        const debAsset = data.assets.find(a => a.name.endsWith('.deb'));
        if (debAsset && downloadBtn) {
            downloadBtn.href = debAsset.browser_download_url;
            if (installCommand) {
                installCommand.textContent = `sudo apt install ./${debAsset.name}`;
            }
        }

        // Botón Windows (.msi)
        const msiAsset = data.assets.find(a => a.name.endsWith('.msi'));
        if (msiAsset && downloadWin) {
            downloadWin.href = msiAsset.browser_download_url;
            downloadWin.textContent = `⬇ Descargar .MSI (Windows)`;
        } else if (downloadWin) {
            // Si aún no hay .msi en esta release, apuntar a releases general
            downloadWin.href = 'https://github.com/MeaCore-Enterprise/MeaCoreLauncher/releases/latest';
        }

        console.log(`MeaCore: Actualizado a la versión ${versionTag}`);
    } catch (error) {
        console.warn('MeaCore: No se pudo obtener la última versión de GitHub.', error);
    }
}
