const MAX_SCREENSHOT_BASE64 = 5_500_000;
const MAX_TITLE = 200;
const MAX_DESCRIPTION = 8000;
const CANONICAL_HOST = "meacorelauncher.meacore-enterprise.workers.dev";

function json(statusCode, payload) {
  return new Response(JSON.stringify(payload), {
    status: statusCode,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

function addSecurityHeaders(response) {
  const h = new Headers(response.headers);
  h.set("X-Content-Type-Options", "nosniff");
  h.set("X-Frame-Options", "DENY");
  h.set("Referrer-Policy", "strict-origin-when-cross-origin");
  h.set("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers: h });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // ── Sitemap dinámico ──────────────────────────────────────────
    if (url.pathname === "/sitemap.xml") {
      let lastmod = new Date().toISOString().split("T")[0];
      try {
        const gh = await fetch(
          "https://api.github.com/repos/MeaCore-Enterprise/MeaCoreLauncher/releases/latest",
          { headers: { Accept: "application/vnd.github.v3+json", "User-Agent": "MeaCoreLauncher-Website" } }
        );
        if (gh.ok) {
          const d = await gh.json();
          lastmod = d.published_at?.split("T")[0] || lastmod;
        }
      } catch {}

      const base = `https://${CANONICAL_HOST}`;
      return new Response(
        `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>${base}/</loc>
    <lastmod>${lastmod}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>
  <url>
    <loc>${base}/changelog.html</loc>
    <lastmod>${lastmod}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>0.8</priority>
  </url>
</urlset>`,
        { headers: { "Content-Type": "application/xml; charset=utf-8", "Cache-Control": "public, max-age=3600" } }
      );
    }

    // ── Bug report ────────────────────────────────────────────────
    if (url.pathname === "/.netlify/functions/report-bug") {
      if (request.method !== "POST") return json(405, { error: "Método no permitido. Usa POST." });

      let payload;
      try { payload = await request.json(); }
      catch { return json(400, { error: "El cuerpo de la petición no es JSON válido." }); }

      const { name, email, title, description, screenshot, screenshotName, honeypot } = payload;
      if (honeypot) return json(200, { ok: true });

      if (!email || !title || !description)
        return json(400, { error: "Faltan campos obligatorios: correo, título y descripción." });
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email)))
        return json(400, { error: "El correo no tiene un formato válido." });
      if (String(title).length > MAX_TITLE)
        return json(400, { error: `El título no puede superar ${MAX_TITLE} caracteres.` });
      if (String(description).length > MAX_DESCRIPTION)
        return json(400, { error: `La descripción no puede superar ${MAX_DESCRIPTION} caracteres.` });

      const token = env.GITHUB_TOKEN;
      const repo = env.GITHUB_REPO;
      const branch = env.GITHUB_BRANCH || "main";
      if (!token || !repo) return json(500, { error: "El servidor de reportes no está configurado correctamente." });

      const ghHeaders = {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "MeaCore-Bug-Reporter",
        "Content-Type": "application/json"
      };

      let screenshotUrl = null;
      if (screenshot && screenshotName) {
        if (typeof screenshot !== "string" || screenshot.length > MAX_SCREENSHOT_BASE64)
          return json(413, { error: "La captura excede el tamaño máximo permitido (4 MB)." });

        const safeName = String(screenshotName).replace(/[^a-zA-Z0-9.\-_]/g, "_").slice(-80);
        const path = `bug-reports/${Date.now()}-${safeName}`;
        try {
          const upload = await fetch(
            `https://api.github.com/repos/${repo}/contents/${encodeURIComponent(path).replace(/%2F/g, "/")}`,
            { method: "PUT", headers: ghHeaders, body: JSON.stringify({ message: `chore(bug-report): captura para "${String(title).slice(0, 60)}"`, content: screenshot, branch }) }
          );
          if (!upload.ok) return json(502, { error: "No se pudo subir la captura al repositorio." });
          const uploadJson = await upload.json();
          screenshotUrl = uploadJson?.content?.download_url || null;
        } catch { return json(502, { error: "Fallo de red al subir la captura." }); }
      }

      const reporter = (name && String(name).trim()) || "Anónimo";
      const bodyParts = [
        `**Reportado por:** ${reporter}`,
        `**Correo de contacto:** ${email}`,
        "",
        "### Descripción del problema",
        description
      ];
      if (screenshotUrl) bodyParts.push("", "### Captura de pantalla", `![captura](${screenshotUrl})`);
      bodyParts.push("", "---", "_Este reporte fue enviado automáticamente desde el formulario de la web de MeaCore Launcher._");

      try {
        const issueRes = await fetch(`https://api.github.com/repos/${repo}/issues`, {
          method: "POST",
          headers: ghHeaders,
          body: JSON.stringify({ title: `[Bug] ${title}`, body: bodyParts.join("\n"), labels: ["bug", "web-report"] })
        });
        if (!issueRes.ok) return json(502, { error: "No se pudo crear el issue en GitHub." });
        const issue = await issueRes.json();
        return json(200, { ok: true, url: issue.html_url, number: issue.number });
      } catch { return json(502, { error: "Fallo de red al crear el issue en GitHub." }); }
    }

    // ── Latest release con caché ──────────────────────────────────
    if (url.pathname === "/api/latest-release") {
      const cacheKey = new Request("https://api.github.com/repos/MeaCore-Enterprise/MeaCoreLauncher/releases/latest", request);
      const cached = await caches.default.match(cacheKey);
      if (cached) return cached;

      const ghRes = await fetch("https://api.github.com/repos/MeaCore-Enterprise/MeaCoreLauncher/releases/latest", {
        headers: { Accept: "application/vnd.github.v3+json", "User-Agent": "MeaCoreLauncher-Website" }
      });
      if (!ghRes.ok) return json(502, { error: "GitHub API no disponible" });

      const data = await ghRes.json();
      const response = json(200, {
        tag_name: data.tag_name,
        assets: (data.assets || []).map(a => ({ name: a.name, browser_download_url: a.browser_download_url }))
      });
      ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
      return response;
    }

    // ── Config Supabase ───────────────────────────────────────────
    if (url.pathname === "/api/config") {
      return json(200, {
        SUPABASE_URL: env.SUPABASE_URL || "",
        SUPABASE_KEY: env.SUPABASE_KEY || ""
      });
    }

    // ── Assets estáticos con headers de seguridad ─────────────────
    try {
      const assetRes = await env.ASSETS.fetch(request);
      if (assetRes.status === 404) {
        return new Response("Página no encontrada", { status: 404, headers: { "Content-Type": "text/plain; charset=utf-8" } });
      }
      return addSecurityHeaders(assetRes);
    } catch (err) {
      return new Response("Error interno del servidor", { status: 500, headers: { "Content-Type": "text/plain; charset=utf-8" } });
    }
  }
};
