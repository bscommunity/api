<picture>
  <source media="(prefers-color-scheme: dark)" srcset="/.github/cover.png">
  <source media="(prefers-color-scheme: light)" srcset="/.github/cover_light.png">
    <img alt="bscm API" src="/.github/cover_light.png">
</picture>

# 🗄️ About

This is the official API backend for the bscm project: a lightweight, community-driven hub for everything Beatstar, built by players, for players.

This repository contains the source code for the backend server, powering the dashboard and other services. It’s built with [Ktor](https://ktor.io/) and [Exposed](https://github.com/JetBrains/Exposed), designed to be fast, secure, and scalable, with a focus on supporting creators and the community.

> [!WARNING]
> This is a work in progress! We’re actively developing features and improving the experience. Check back often for updates.

## 🚀 Features

- **RESTful API for Beatstar content**  
  Endpoints for charts, contributors, accounts, users, and more.
- **Authentication & Authorization**  
  Supports JWT and OAuth (Discord, Google).
- **Database integration**  
  Uses PostgreSQL with Exposed ORM.
- **Rate limiting, security, and serialization**  
  Built-in middleware for robust, safe APIs.
- **Swagger documentation**  
  Interactive API docs for easy exploration.

> [!TIP]
> Check the [wiki](https://github.com/bscommunity/api/wiki) for more details on endpoints, architecture, and future plans.

## 📦 Project Structure

- `src/main/kotlin/org/bscm/` — Main source code (Kotlin)
  - `models/` — Data models
  - `dao/` — Database entities
  - `dto/` — Data transfer objects
  - `enums/` — Enum types
  - `tables/` — Database table definitions
  - `plugins/` — Ktor plugins (routing, security, etc)
  - `repository/` — Data access logic
  - `routes/` — API route definitions
  - `serialization/` — Custom serializers
  - `services/` — Business logic and integrations
  - `utils/` — Utility classes
- `resources/` — Config files and assets
- `build.gradle.kts` — Build configuration

## 🛠️ Running Locally

1. Install dependencies:
    ```bash
    ./gradlew build
    ```
2. Run the server:
    ```bash
    ./gradlew run
    ```
3. The API will be available at [http://localhost:8080](http://localhost:8080).

> Requires [Java 17+](https://adoptium.net/) and [Gradle](https://gradle.org/) installed.

## 🤝 Contributing

Contributions are welcome!

- Found a bug? Open an [issue](https://github.com/bscommunity/api/issues)
- Have a feature idea? Suggest or submit a PR
- Into API design? Help us improve our endpoints and documentation!

## 📄 License

This project follows the bscm organization license. See the main repository for details.