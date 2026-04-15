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
    const badge = document.getElementById('version-badge');
    const downloadBtn = document.getElementById('download-btn-hero');
    const installCommand = document.getElementById('install-command');

    if (!badge || !downloadBtn || !installCommand) return;

    try {
        const response = await fetch('https://api.github.com/repos/keylertamayo/MeaCoreLauncher/releases/latest');
        if (!response.ok) throw new Error('GitHub API query failed');
        
        const data = await response.json();
        // Limpiamos el prefijo 'bat-' si existe
        const versionNum = data.tag_name.replace('bat-', '');
        const versionTag = `v${versionNum}`;
        
        const debAsset = data.assets.find(asset => asset.name.endsWith('.deb'));

        if (debAsset) {
            badge.textContent = `${versionTag} — Alfa`;
            downloadBtn.href = debAsset.browser_download_url;
            installCommand.textContent = `sudo apt install ./${debAsset.name}`;
            console.log(`MeaCore: Actualizado a la versión ${versionTag}`);
        }
    } catch (error) {
        console.warn('MeaCore: No se pudo obtener la última versión de GitHub. Usando valores por defecto.', error);
        // Si falla, el HTML ya tiene valores de escape razonables.
    }
}
