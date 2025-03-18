package br.com.minevoxel.mundos.database;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final MinevoxelMundos plugin;
    private MySQLConnector connector;
    private ExecutorService executorService;

    // Consultas SQL
    private static final String CREATE_WORLDS_TABLE =
            "CREATE TABLE IF NOT EXISTS minevoxel_worlds (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "world_name VARCHAR(64) UNIQUE NOT NULL, " +
                    "display_name VARCHAR(64) NOT NULL, " +
                    "owner_uuid VARCHAR(36) NOT NULL, " +
                    "owner_name VARCHAR(16) NOT NULL, " +
                    "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "world_type VARCHAR(32) DEFAULT 'NORMAL', " +
                    "environment VARCHAR(32) DEFAULT 'NORMAL', " +
                    "pvp BOOLEAN DEFAULT FALSE, " +
                    "generate_structures BOOLEAN DEFAULT TRUE, " +
                    "seed BIGINT DEFAULT 0, " +
                    "game_mode VARCHAR(32) DEFAULT 'SURVIVAL', " +
                    "physics BOOLEAN DEFAULT TRUE, " +
                    "water_flow BOOLEAN DEFAULT TRUE, " +
                    "lava_flow BOOLEAN DEFAULT TRUE, " +
                    "fire_spread BOOLEAN DEFAULT TRUE, " +
                    "leaf_decay BOOLEAN DEFAULT TRUE, " +
                    "redstone BOOLEAN DEFAULT TRUE, " +
                    "mob_spawning BOOLEAN DEFAULT TRUE, " +
                    "animal_spawning BOOLEAN DEFAULT TRUE, " +
                    "is_public BOOLEAN DEFAULT FALSE, " +
                    "INDEX(owner_uuid), " +
                    "INDEX(world_name))";

    private static final String CREATE_PERMISSIONS_TABLE =
            "CREATE TABLE IF NOT EXISTS minevoxel_world_permissions (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT, " +
                    "world_id INT NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "permission VARCHAR(32) NOT NULL, " +
                    "FOREIGN KEY (world_id) REFERENCES minevoxel_worlds(id) ON DELETE CASCADE, " +
                    "UNIQUE KEY unique_world_player (world_id, player_uuid))";

    public DatabaseManager(MinevoxelMundos plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newFixedThreadPool(3);
    }

    public void initialize() {
        // Inicializar conexão com o banco de dados
        connector = new MySQLConnector(plugin);

        // Criar tabelas se não existirem
        createTables();
    }

    public void shutdown() {
        // Desligar executorService
        if (executorService != null) {
            executorService.shutdown();
        }

        // Fechar conexão com o banco de dados
        if (connector != null) {
            connector.close();
        }
    }

    private void createTables() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = connector.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Criar tabela de mundos
                stmt.execute(CREATE_WORLDS_TABLE);

                // Criar tabela de permissões
                stmt.execute(CREATE_PERMISSIONS_TABLE);

                plugin.getLogger().info("Tabelas do banco de dados criadas ou verificadas com sucesso!");

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao criar tabelas do banco de dados: " + e.getMessage());
                e.printStackTrace();
            }
        }, executorService);
    }

    // Métodos para gerenciar mundos
    public CompletableFuture<Integer> createWorld(WorldData worldData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO minevoxel_worlds (world_name, display_name, owner_uuid, owner_name, " +
                                 "world_type, environment, pvp, generate_structures, seed, game_mode, " +
                                 "physics, water_flow, lava_flow, fire_spread, leaf_decay, redstone, " +
                                 "mob_spawning, animal_spawning, is_public) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, worldData.getWorldName());
                stmt.setString(2, worldData.getDisplayName());
                stmt.setString(3, worldData.getOwnerUUID().toString());
                stmt.setString(4, worldData.getOwnerName());
                stmt.setString(5, worldData.getWorldType());
                stmt.setString(6, worldData.getEnvironment());
                stmt.setBoolean(7, worldData.isPvp());
                stmt.setBoolean(8, worldData.isGenerateStructures());
                stmt.setLong(9, worldData.getSeed());
                stmt.setString(10, worldData.getGameMode().name());
                stmt.setBoolean(11, worldData.isPhysics());
                stmt.setBoolean(12, worldData.isWaterFlow());
                stmt.setBoolean(13, worldData.isLavaFlow());
                stmt.setBoolean(14, worldData.isFireSpread());
                stmt.setBoolean(15, worldData.isLeafDecay());
                stmt.setBoolean(16, worldData.isRedstone());
                stmt.setBoolean(17, worldData.isMobSpawning());
                stmt.setBoolean(18, worldData.isAnimalSpawning());
                stmt.setBoolean(19, worldData.isPublic());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Creating world failed, no rows affected.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int worldId = generatedKeys.getInt(1);
                        worldData.setId(worldId);

                        // Inserir permissões para o mundo
                        saveWorldPermissions(conn, worldData);

                        return worldId;
                    } else {
                        throw new SQLException("Creating world failed, no ID obtained.");
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao criar mundo no banco de dados: " + e.getMessage());
                e.printStackTrace();
                return -1;
            }
        }, executorService);
    }

    public CompletableFuture<Boolean> updateWorld(WorldData worldData) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE minevoxel_worlds SET " +
                                 "display_name = ?, last_accessed = ?, " +
                                 "world_type = ?, environment = ?, pvp = ?, generate_structures = ?, " +
                                 "seed = ?, game_mode = ?, physics = ?, water_flow = ?, lava_flow = ?, " +
                                 "fire_spread = ?, leaf_decay = ?, redstone = ?, mob_spawning = ?, " +
                                 "animal_spawning = ?, is_public = ? " +
                                 "WHERE id = ?")) {

                stmt.setString(1, worldData.getDisplayName());
                stmt.setTimestamp(2, new Timestamp(new Date().getTime()));
                stmt.setString(3, worldData.getWorldType());
                stmt.setString(4, worldData.getEnvironment());
                stmt.setBoolean(5, worldData.isPvp());
                stmt.setBoolean(6, worldData.isGenerateStructures());
                stmt.setLong(7, worldData.getSeed());
                stmt.setString(8, worldData.getGameMode().name());
                stmt.setBoolean(9, worldData.isPhysics());
                stmt.setBoolean(10, worldData.isWaterFlow());
                stmt.setBoolean(11, worldData.isLavaFlow());
                stmt.setBoolean(12, worldData.isFireSpread());
                stmt.setBoolean(13, worldData.isLeafDecay());
                stmt.setBoolean(14, worldData.isRedstone());
                stmt.setBoolean(15, worldData.isMobSpawning());
                stmt.setBoolean(16, worldData.isAnimalSpawning());
                stmt.setBoolean(17, worldData.isPublic());
                stmt.setInt(18, worldData.getId());

                int affectedRows = stmt.executeUpdate();

                // Atualizar permissões
                if (affectedRows > 0) {
                    // Limpar permissões antigas e inserir novas
                    clearWorldPermissions(conn, worldData.getId());
                    saveWorldPermissions(conn, worldData);
                    return true;
                }

                return false;

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao atualizar mundo no banco de dados: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, executorService);
    }

    public CompletableFuture<Boolean> deleteWorld(int worldId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM minevoxel_worlds WHERE id = ?")) {

                stmt.setInt(1, worldId);

                int affectedRows = stmt.executeUpdate();
                return affectedRows > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao excluir mundo do banco de dados: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, executorService);
    }

    public CompletableFuture<WorldData> getWorld(int worldId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM minevoxel_worlds WHERE id = ?")) {

                stmt.setInt(1, worldId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        WorldData worldData = extractWorldDataFromResultSet(rs);
                        loadWorldPermissions(conn, worldData);
                        return worldData;
                    }
                }

                return null;

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao buscar mundo no banco de dados: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executorService);
    }

    public CompletableFuture<WorldData> getWorldByName(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM minevoxel_worlds WHERE world_name = ?")) {

                stmt.setString(1, worldName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        WorldData worldData = extractWorldDataFromResultSet(rs);
                        loadWorldPermissions(conn, worldData);
                        return worldData;
                    }
                }

                return null;

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao buscar mundo por nome no banco de dados: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executorService);
    }

    public CompletableFuture<List<WorldData>> getWorldsByOwner(UUID ownerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            List<WorldData> worlds = new ArrayList<>();

            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM minevoxel_worlds WHERE owner_uuid = ?")) {

                stmt.setString(1, ownerUUID.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        WorldData worldData = extractWorldDataFromResultSet(rs);
                        loadWorldPermissions(conn, worldData);
                        worlds.add(worldData);
                    }
                }

                return worlds;

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao buscar mundos por dono no banco de dados: " + e.getMessage());
                e.printStackTrace();
                return worlds;
            }
        }, executorService);
    }

    public CompletableFuture<List<WorldData>> getAccessibleWorlds(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            List<WorldData> worlds = new ArrayList<>();

            try (Connection conn = connector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT w.* FROM minevoxel_worlds w " +
                                 "LEFT JOIN minevoxel_world_permissions p ON w.id = p.world_id " +
                                 "WHERE w.is_public = TRUE OR w.owner_uuid = ? OR p.player_uuid = ?")) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerUUID.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        WorldData worldData = extractWorldDataFromResultSet(rs);
                        loadWorldPermissions(conn, worldData);
                        worlds.add(worldData);
                    }
                }

                return worlds;

            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao buscar mundos acessíveis no banco de dados: " + e.getMessage());
                e.printStackTrace();
                return worlds;
            }
        }, executorService);
    }

    private WorldData extractWorldDataFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String worldName = rs.getString("world_name");
        UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");

        WorldData worldData = new WorldData(id, worldName, ownerUUID, ownerName);

        worldData.setDisplayName(rs.getString("display_name"));
        worldData.setCreationDate(rs.getTimestamp("creation_date"));
        worldData.setLastAccessed(rs.getTimestamp("last_accessed"));
        worldData.setWorldType(rs.getString("world_type"));
        worldData.setEnvironment(rs.getString("environment"));
        worldData.setPvp(rs.getBoolean("pvp"));
        worldData.setGenerateStructures(rs.getBoolean("generate_structures"));
        worldData.setSeed(rs.getLong("seed"));
        worldData.setGameMode(GameMode.valueOf(rs.getString("game_mode")));
        worldData.setPhysics(rs.getBoolean("physics"));
        worldData.setWaterFlow(rs.getBoolean("water_flow"));
        worldData.setLavaFlow(rs.getBoolean("lava_flow"));
        worldData.setFireSpread(rs.getBoolean("fire_spread"));
        worldData.setLeafDecay(rs.getBoolean("leaf_decay"));
        worldData.setRedstone(rs.getBoolean("redstone"));
        worldData.setMobSpawning(rs.getBoolean("mob_spawning"));
        worldData.setAnimalSpawning(rs.getBoolean("animal_spawning"));
        worldData.setPublic(rs.getBoolean("is_public"));

        return worldData;
    }

    private void loadWorldPermissions(Connection conn, WorldData worldData) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT player_uuid, permission FROM minevoxel_world_permissions WHERE world_id = ?")) {

            stmt.setInt(1, worldData.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    String permission = rs.getString("permission");

                    worldData.getPlayerPermissions().put(playerUUID, permission);
                    worldData.getAllowedPlayers().add(playerUUID);
                }
            }
        }
    }

    private void clearWorldPermissions(Connection conn, int worldId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM minevoxel_world_permissions WHERE world_id = ?")) {

            stmt.setInt(1, worldId);
            stmt.executeUpdate();
        }
    }

    private void saveWorldPermissions(Connection conn, WorldData worldData) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO minevoxel_world_permissions (world_id, player_uuid, permission) VALUES (?, ?, ?)")) {

            for (Map.Entry<UUID, String> entry : worldData.getPlayerPermissions().entrySet()) {
                stmt.setInt(1, worldData.getId());
                stmt.setString(2, entry.getKey().toString());
                stmt.setString(3, entry.getValue());
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }
}