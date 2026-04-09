document.addEventListener('DOMContentLoaded', () => {
    // Smooth scroll para links internos
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if(target) {
                target.scrollIntoView({
                    behavior: 'smooth'
                });
            }
        });
    });

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
