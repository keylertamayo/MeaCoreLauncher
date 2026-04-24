const MAX_SCREENSHOT_BASE64 = 5_500_000;
const MAX_TITLE = 200;
const MAX_DESCRIPTION = 8000;

const json = (statusCode, payload) => ({
  statusCode,
  headers: {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  },
  body: JSON.stringify(payload)
});

exports.handler = async (event) => {
  if (event.httpMethod !== "POST") {
    return json(405, { error: "Método no permitido. Usa POST." });
  }

  let payload;
  try {
    payload = JSON.parse(event.body || "{}");
  } catch {
    return json(400, { error: "El cuerpo de la petición no es JSON válido." });
  }

  const {
    name,
    email,
    title,
    description,
    screenshot,
    screenshotName,
    honeypot
  } = payload;

  if (honeypot) {
    return json(200, { ok: true });
  }

  if (!email || !title || !description) {
    return json(400, {
      error: "Faltan campos obligatorios: correo, título y descripción."
    });
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email))) {
    return json(400, { error: "El correo no tiene un formato válido." });
  }
  if (String(title).length > MAX_TITLE) {
    return json(400, { error: `El título no puede superar ${MAX_TITLE} caracteres.` });
  }
  if (String(description).length > MAX_DESCRIPTION) {
    return json(400, {
      error: `La descripción no puede superar ${MAX_DESCRIPTION} caracteres.`
    });
  }

  const token = process.env.GITHUB_TOKEN;
  const repo = process.env.GITHUB_REPO;
  const branch = process.env.GITHUB_BRANCH || "main";

  if (!token || !repo) {
    console.error("Faltan variables de entorno GITHUB_TOKEN o GITHUB_REPO.");
    return json(500, {
      error: "El servidor de reportes no está configurado correctamente."
    });
  }

  const ghHeaders = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "MeaCore-Bug-Reporter",
    "Content-Type": "application/json"
  };

  let screenshotUrl = null;
  if (screenshot && screenshotName) {
    if (typeof screenshot !== "string" || screenshot.length > MAX_SCREENSHOT_BASE64) {
      return json(413, { error: "La captura excede el tamaño máximo permitido (4 MB)." });
    }
    const safeName = String(screenshotName)
      .replace(/[^a-zA-Z0-9.\-_]/g, "_")
      .slice(-80);
    const path = `bug-reports/${Date.now()}-${safeName}`;

    try {
      const upload = await fetch(
        `https://api.github.com/repos/${repo}/contents/${encodeURIComponent(path).replace(/%2F/g, "/")}`,
        {
          method: "PUT",
          headers: ghHeaders,
          body: JSON.stringify({
            message: `chore(bug-report): captura para "${String(title).slice(0, 60)}"`,
            content: screenshot,
            branch
          })
        }
      );
      if (!upload.ok) {
        const errText = await upload.text();
        console.error("Error subiendo captura:", upload.status, errText);
        return json(502, { error: "No se pudo subir la captura al repositorio." });
      }
      const uploadJson = await upload.json();
      screenshotUrl = uploadJson?.content?.download_url || null;
    } catch (err) {
      console.error("Excepción subiendo captura:", err);
      return json(502, { error: "Fallo de red al subir la captura." });
    }
  }

  const reporter = (name && String(name).trim()) || "Anónimo";
  const bodyParts = [
    `**Reportado por:** ${reporter}`,
    `**Correo de contacto:** ${email}`,
    "",
    "### Descripción del problema",
    description
  ];
  if (screenshotUrl) {
    bodyParts.push("", "### Captura de pantalla", `![captura](${screenshotUrl})`);
  }
  bodyParts.push(
    "",
    "---",
    "_Este reporte fue enviado automáticamente desde el formulario de la web de MeaCore Launcher._"
  );

  try {
    const issueRes = await fetch(`https://api.github.com/repos/${repo}/issues`, {
      method: "POST",
      headers: ghHeaders,
      body: JSON.stringify({
        title: `[Bug] ${title}`,
        body: bodyParts.join("\n"),
        labels: ["bug", "web-report"]
      })
    });

    if (!issueRes.ok) {
      const errText = await issueRes.text();
      console.error("Error creando issue:", issueRes.status, errText);
      return json(502, { error: "No se pudo crear el issue en GitHub." });
    }

    const issue = await issueRes.json();
    return json(200, {
      ok: true,
      url: issue.html_url,
      number: issue.number
    });
  } catch (err) {
    console.error("Excepción creando issue:", err);
    return json(502, { error: "Fallo de red al crear el issue en GitHub." });
  }
};
