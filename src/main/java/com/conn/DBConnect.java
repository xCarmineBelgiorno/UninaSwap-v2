package com.conn;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnect {
    private static Connection conn = null;

    public static Connection getConn() {
        try {
            if (conn != null && !conn.isClosed()) {
                try {
                    if (!conn.getAutoCommit()) {
                        conn.setAutoCommit(true);
                    }
                } catch (Exception ignore) {
                }
                return conn;
            }
        } catch (Exception e) {
            conn = null;
        }
        try {
            System.out.println("=== TENTATIVO CONNESSIONE DATABASE (MySQL) ===");
            String driver = getConfig("DB_DRIVER", "com.mysql.cj.jdbc.Driver");
            Class.forName(driver);
            System.out.println("Driver DB caricato con successo: " + driver);

            String defaultUrl = "jdbc:mysql://localhost:3306/UninaSwapDB?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            String url = getConfig("DB_URL", defaultUrl);
            String user = getConfig("DB_USER", "uninaswap");
            String password = getConfig("DB_PASSWORD", "uninaswappass");

            System.out.println("URL: " + url);
            System.out.println("User: " + user);

            conn = DriverManager.getConnection(url, user, password);
            try {
                conn.setAutoCommit(true);
            } catch (Exception ignore) {
            }

            System.out.println("Connessione al database riuscita!");
            if (conn != null) {
                System.out.println("Database: " + conn.getMetaData().getDatabaseProductName());
                System.out.println("Versione: " + conn.getMetaData().getDatabaseProductVersion());
            }

        } catch (Exception e) {
            System.out.println("ERRORE CONNESSIONE DATABASE: " + e.getMessage());
            e.printStackTrace();
            String causeMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            throw new IllegalStateException(
                "Connessione al database fallita. Verifica DB_URL, DB_USER, DB_PASSWORD e che il DB sia in esecuzione. Causa: "
                    + causeMessage,
                e
            );
        }
        return conn;
    }

    private static String getConfig(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.trim().isEmpty()) {
            return env;
        }
        String prop = System.getProperty(key);
        if (prop != null && !prop.trim().isEmpty()) {
            return prop;
        }
        return defaultValue;
    }
}
