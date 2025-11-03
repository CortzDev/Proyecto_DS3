package Postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DbPostgres {

    private static final String URL  = "jdbc:postgresql://switchback.proxy.rlwy.net:19460/railway";
    private static final String USER = "postgres";
    private static final String PASS = "AVssGhMQQPLMTTbVzozjfCbFDODTfOUh";
    private static final boolean SSL = true;

    public static Connection open() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASS);
        if (SSL) props.setProperty("sslmode", "require");
        return DriverManager.getConnection(URL, props);
    }
}
