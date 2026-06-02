package ec.edu.espe.Switch.Batch.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class PostgreSqlDatabaseInitializer {

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "5432";
    private static final String DEFAULT_DATABASE = "file_reception";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "123";

    private PostgreSqlDatabaseInitializer() {
    }

    public static void ensureDatabaseExists() {
        Properties localProperties = loadLocalEnv();
        boolean autoCreate = Boolean.parseBoolean(resolve("POSTGRES_AUTO_CREATE_DB", "true", localProperties));
        if (!autoCreate) {
            return;
        }

        String host = resolve("POSTGRES_HOST", DEFAULT_HOST, localProperties);
        String port = resolve("POSTGRES_PORT", DEFAULT_PORT, localProperties);
        String database = resolve("POSTGRES_DB", DEFAULT_DATABASE, localProperties);
        String user = resolve("POSTGRES_USER", DEFAULT_USER, localProperties);
        String password = resolve("POSTGRES_PASSWORD", DEFAULT_PASSWORD, localProperties);
        String driver = resolve("POSTGRES_DRIVER", "org.postgresql.Driver", localProperties);
        String maintenanceDatabase = resolve("POSTGRES_MAINTENANCE_DB", "postgres", localProperties);

        if (!"org.postgresql.Driver".equals(driver) || database.isBlank()) {
            return;
        }

        String maintenanceUrl = "jdbc:postgresql://" + host + ":" + port + "/" + maintenanceDatabase;
        try {
            Class.forName(driver);
            try (Connection connection = DriverManager.getConnection(maintenanceUrl, user, password)) {
                connection.setAutoCommit(true);
                if (!databaseExists(connection, database)) {
                    createDatabase(connection, database);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se encontro el driver PostgreSQL: " + driver, e);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo validar o crear la base PostgreSQL '" + database + "'", e);
        }
    }

    private static boolean databaseExists(Connection connection, String database) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select 1 from pg_database where datname = ?")) {
            statement.setString(1, database);
            return statement.executeQuery().next();
        }
    }

    private static void createDatabase(Connection connection, String database) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create database " + quoteIdentifier(database));
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String resolve(String key, String defaultValue, Properties localProperties) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        String propertyValue = localProperties.getProperty(key);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        return defaultValue;
    }

    private static Properties loadLocalEnv() {
        Properties properties = new Properties();
        Path envPath = Path.of(".env");
        if (!Files.isRegularFile(envPath)) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(envPath)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo .env", e);
        }
    }
}
