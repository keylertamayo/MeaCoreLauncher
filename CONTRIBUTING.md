# Contribuir a MeaCore Launcher

Gracias por interesarte en mejorar el proyecto.

## Ramas: Estable y Beta

El repositorio usa dos ramas de trabajo; **elige la rama al clonar, al hacer `pull` y al abrir el PR** según el tipo de cambio.

| Rama | Propósito |
|------|-----------|
| **`Estable`** | Línea principal (equivalente a lo que antes era `main`): launcher **funcional** y pulido, mejoras de **compatibilidad** (por ejemplo Forge u otros loaders), ajustes seguros que no implican reescribir la UI React ni el flujo de juego inestable. |
| **`Beta`** | Trabajo sobre **inestabilidades** del cliente: UI **React** / WebView, integración con **Minecraft**, corrección y **estabilización** hacia versiones pre (por ejemplo **1.1.0**) cuando el código actual se considera experimental o problemático. |

**Ejemplos:** un cambio pensado para compatibilidad con Forge → PR hacia **`Estable`**. Un arreglo de estabilidad del launcher 1.1.0 o del stack React/Minecraft → PR hacia **`Beta`**.

Tras `git clone`, entra en la rama que vayas a usar:

```bash
git fetch origin
git checkout Estable   # o: git checkout Beta
```

Si ya tenías el repo, actualiza la rama elegida con `git pull origin Estable` o `git pull origin Beta`.

## Cómo reportar errores (Issues)

1. **Busca** si ya existe un issue similar.
2. Incluye **SO y versión** (p. ej. Ubuntu 24.04), **Java** (`java -version`), y **rama o tag** del launcher.
3. **Pasos para reproducir** el fallo, uno por uno.
4. **Qué esperabas** vs **qué ocurrió** (mensajes de consola, capturas si aplica).
5. Si puedes, propón **implementación**: enlace a documentación, pseudocódigo o un *pull request*.

**Referencia estable:** solo la **primera release** del repo, etiqueta **`v0.1.0-pre.1`**. Las versiones posteriores son experimentales; los informes en Issues ayudan, pero no hay compromiso de soporte.

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
