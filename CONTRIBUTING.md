# Contribuir a MeaCore Launcher

¡Gracias por tu interés en contribuir! Las pull requests son bienvenidas.

## Cómo empezar

1. Haz un fork del repositorio y crea tu rama desde `Estable`
2. Ejecuta `./gradlew build` para verificar que todo compila
3. Haz tus cambios
4. Ejecuta `./gradlew test` para asegurarte de que los tests pasen
5. Envía una pull request

## Directrices

- **Java 21** — el código debe compilar y ejecutarse en Java 21
- **Sin module-info.java** — el proyecto es intencionalmente no modular
- **Mantenlo simple** — evita añadir nuevas dependencias a menos que sea necesario
- **javafx.controls y javafx.fxml** son los únicos módulos de JavaFX utilizados
- **Español** — los textos de la interfaz deben estar en español (los comentarios del código pueden estar en inglés o español)

## Qué aceptamos

- Corrección de errores
- Mejoras de rendimiento
- Actualizaciones de compatibilidad de modloaders (Forge, Fabric, NeoForge)
- Mejoras en la integración con la tienda (API de Modrinth)
- Mejoras en la documentación
- Correcciones de traducción

## Qué no aceptamos

- Pull requests que añadan funcionalidades premium/de pago
- Eliminación de atribuciones o avisos de licencia
- Cambios que rompan la compatibilidad sin discusión previa — abre un issue primero

## Revisión de código

Todas las contribuciones requieren revisión. Podemos pedirte que hagas cambios antes de fusionar.

## Licencia

Al contribuir, aceptas que tus contribuciones se licenciarán bajo los mismos términos de la [Licencia MIT con Restricción de Uso No Comercial](LICENSE).
