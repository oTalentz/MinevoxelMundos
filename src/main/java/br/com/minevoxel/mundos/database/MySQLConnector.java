package br.com.minevoxel.mundos.database;

import br.com.minevoxel.mundos.MinevoxelMundos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class MySQLConnector {

    private final MinevoxelMundos plugin;

    // Credenciais de conexão
    private static final String DB_HOST = "glacier.lowping.host";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "s39_MinevoxelMundos";
    private static final String DB_USER = "u39_5gSLkk4k41";
    private static final String DB_PASSWORD = "whgx+v2PEkk=Put^2^muN+zF";

    private String jdbcUrl;
    private Properties connectionProperties;

    public MySQLConnector(MinevoxelMundos plugin) {
        this.plugin = plugin;
        setupConnection();
    }

    private void setupConnection() {
        try {
            // Carregar o driver JDBC
            Class.forName("com.mysql.jdbc.Driver");

            // Configurar URL JDBC
            jdbcUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

            // Configurar propriedades de conexão
            connectionProperties = new Properties();
            connectionProperties.setProperty("user", DB_USER);
            connectionProperties.setProperty("password", DB_PASSWORD);
            connectionProperties.setProperty("useSSL", "false");
            connectionProperties.setProperty("autoReconnect", "true");
            connectionProperties.setProperty("useUnicode", "true");
            connectionProperties.setProperty("characterEncoding", "utf8");
            connectionProperties.setProperty("serverTimezone", "UTC");

            // Testar conexão
            try (Connection conn = getConnection()) {
                if (conn.isValid(5)) {
                    plugin.getLogger().info("Conexão com o banco de dados estabelecida com sucesso!");
                } else {
                    plugin.getLogger().warning("Teste de conexão com o banco de dados falhou!");
                }
            }

        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Driver JDBC MySQL não encontrado: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erro ao conectar ao banco de dados: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, connectionProperties);
    }

    public void close() {
        // Não é necessário fechar nada aqui, pois não mantemos um pool de conexões
    }

    public boolean isConnected() {
        try (Connection conn = getConnection()) {
            return conn.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    public void reconnect() {
        setupConnection();
    }
}