# Contribuir a MeaCore Launcher

Gracias por interesarte en mejorar el proyecto.

## Cómo reportar errores (Issues)

1. **Busca** si ya existe un issue similar.
2. Incluye **SO y versión** (p. ej. Ubuntu 24.04), **Java** (`java -version`), y **rama o tag** del launcher.
3. **Pasos para reproducir** el fallo, uno por uno.
4. **Qué esperabas** vs **qué ocurrió** (mensajes de consola, capturas si aplica).
5. Si puedes, propón **implementación**: enlace a documentación, pseudocódigo o un *pull request*.

Las versiones **pre-release** (p. ej. `1.1.0-pre.1`) se publican sabiendo que aún hay **bugs**; los informes detallados ayudan a estabilizar.

## Cómo proponer cambios (Pull Requests)

1. **Fork** del repositorio y rama descriptiva (`fix/webview-clicks`, `feat/servers-dat`, …).
2. Cambios **acotados** al problema o función; evita mezclar refactors grandes sin consenso.
3. Ejecuta **`./gradlew test`** y, si tocas el frontend, **`cd frontend && npm run build`**.
4. Describe en el PR **qué cambia y por qué** (en frases completas, no solo listas de archivos).

## Estilo de código

- **Java:** seguir el estilo ya presente en el paquete; no añadir dependencias sin necesidad.
- **TypeScript/React:** componentes existentes y Tailwind/utilidades ya usadas en `MinecraftLauncher.tsx`.
- **Mensajes de usuario:** español claro es bienvenido si encaja con la UI actual.

## Seguridad

No abras PRs con **credenciales, tokens o datos personales**. Los logs adjuntos deben estar **anonimizados**.

## Licencia

Al contribuir, aceptas que tu aporte se distribuya bajo los términos del [LICENSE](LICENSE) del repositorio, salvo acuerdo explícito distinto con los mantenedores.
