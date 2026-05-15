# Contributing to MeaCore Launcher

Thanks for your interest in contributing! Pull requests are welcome.

## Getting Started

1. Fork the repo and create your branch from `Estable`
2. Run `./gradlew build` to verify everything compiles
3. Make your changes
4. Run `./gradlew test` to ensure tests pass
5. Submit a pull request

## Guidelines

- **Java 21** — code must compile and run on Java 21
- **No module-info.java** — the project is intentionally non-modular
- **Keep it simple** — avoid adding new dependencies unless necessary
- **javafx.controls and javafx.fxml** are the only JavaFX modules used
- **Spanish** — UI strings should be in Spanish (code comments can be in English or Spanish)

## What we welcome

- Bug fixes
- Performance improvements
- Mod loader compatibility updates (Forge, Fabric, NeoForge)
- Store integration improvements (Modrinth API)
- Documentation improvements
- Translation fixes

## What we don't accept

- Pull requests that add premium/paywalled features
- Removals of attribution or license notices
- Breaking changes without prior discussion — open an issue first

## Code review

All submissions require review. We may ask you to make changes before merging.

## License

By contributing, you agree that your contributions will be licensed under the project's [MIT License with Non-Commercial Use Restriction](LICENSE).
