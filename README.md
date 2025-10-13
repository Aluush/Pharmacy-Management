# Pharmacy Management (JavaFX)

A desktop application for managing pharmacy operations built with JavaFX and Maven. It provides inventory management, sales processing with cart functionality, dashboards, and application settings, backed by a MySQL database.

## Features

- Inventory management (add/update/search items)
- Sales (cart handling, price/quantity, invoice flow)
- Dashboard overview
- Settings and session handling
- JavaFX UI built with FXML and CSS

## Tech Stack

- Java 17
- JavaFX 21 (controls, FXML)
- Maven
- MySQL Connector/J

## Project Structure

```
.
├─ pom.xml
├─ src
│  ├─ main/java/com/example/
│  │  ├─ App.java
│  │  ├─ Database.java
│  │  ├─ Session.java
│  │  ├─ DashboardController.java
│  │  ├─ InventoryController.java
│  │  ├─ InventoryItemDialogController.java
│  │  ├─ MainController.java
│  │  ├─ SalesController.java
│  │  ├─ SettingsController.java
│  │  └─ HelloController.java
│  └─ main/resources/com/example/
│     ├─ main-view.fxml
│     ├─ dashboard-view.fxml
│     ├─ inventory-view.fxml
│     ├─ inventory-item-dialog.fxml
│     ├─ sales-view.fxml
│     ├─ settings-view.fxml
│     ├─ hello-view.fxml
│     └─ styles.css
└─ target/ (ignored)
```

## Prerequisites

- JDK 17+
- Maven 3.8+
- MySQL 8.x (if database functionality is required locally)

## Configuration

Update database connection settings in `src/main/java/com/example/Database.java` to match your local environment.

Example (adjust to your setup):
```
URL:      jdbc:mysql://localhost:3306/pharmacy
User:     root
Password: your_password
```

Ensure the required schema and tables exist in MySQL.

## Run (Development)

Run the app using the JavaFX Maven plugin (handles module paths automatically):

```
mvn clean javafx:run
```

## Build

- Package (standard Maven build; artifact in `target/`):
```
mvn -DskipTests package
```

- Create a custom runtime image (zip) using JavaFX Maven Plugin jlink goal:
```
mvn -DskipTests javafx:jlink
```
This produces `target/javafx-app.zip` as configured in `pom.xml` (jlinkZipName).

## Troubleshooting

- Missing JavaFX modules or runtime errors:
  - Use `mvn javafx:run` instead of running the JAR directly, so module paths are configured.
- Database connection issues:
  - Verify URL/user/password in `Database.java`
  - Ensure MySQL is running and the schema/table names match your configuration.

## License

This project’s license is not specified in the repository. Add a LICENSE file if needed.

## Repository

GitHub: https://github.com/Aluush/Pharmacy-Management
