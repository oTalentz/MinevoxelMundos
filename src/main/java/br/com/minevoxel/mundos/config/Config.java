package br.com.minevoxel.mundos.config;

import br.com.minevoxel.mundos.MinevoxelMundos;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Config {

    private final MinevoxelMundos plugin;
    private FileConfiguration config;
    private File configFile;

    // Configurações em cache
    private String serverType;
    private String lobbyServer;
    private String worldsServer;
    private boolean autoLoadWorlds;
    private int maxWorldsPerPlayer;
    private int worldUnloadDelay;
    private int defaultSlotLimit;
    private List<String> disabledCommands;

    public Config(MinevoxelMundos plugin) {
        this.plugin = plugin;
        this.loadConfig();
    }

    private void loadConfig() {
        // Salvar configuração padrão se não existir
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        matchConfig();
        loadCachedValues();
    }

    private void matchConfig() {
        try {
            boolean hasUpdated = false;
            InputStream is = plugin.getResource("config.yml");

            if (is != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

                for (String key : defConfig.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defConfig.get(key));
                        hasUpdated = true;
                    }
                }

                if (hasUpdated) {
                    config.save(configFile);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao atualizar config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadCachedValues() {
        // Configurações gerais
        serverType = config.getString("server-type", "AUTO");
        lobbyServer = config.getString("servers.lobby", "lobby");
        worldsServer = config.getString("servers.worlds", "worlds-1");
        autoLoadWorlds = config.getBoolean("worlds.auto-load", false);
        maxWorldsPerPlayer = config.getInt("worlds.max-per-player", 5);
        worldUnloadDelay = config.getInt("worlds.unload-delay", 30);
        defaultSlotLimit = config.getInt("worlds.default-slot-limit", 10);
        disabledCommands = config.getStringList("worlds.disabled-commands");
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream is = plugin.getResource("config.yml");
        if (is != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }

        loadCachedValues();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getters

    public String getServerType() {
        return serverType;
    }

    public String getLobbyServer() {
        return lobbyServer;
    }

    public String getWorldsServer() {
        return worldsServer;
    }

    public boolean isAutoLoadWorlds() {
        return autoLoadWorlds;
    }

    public int getMaxWorldsPerPlayer() {
        return maxWorldsPerPlayer;
    }

    public int getWorldUnloadDelay() {
        return worldUnloadDelay;
    }

    public int getDefaultSlotLimit() {
        return defaultSlotLimit;
    }

    public List<String> getDisabledCommands() {
        return disabledCommands;
    }

    // Métodos para acessar outras configurações específicas

    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "minevoxel");
    }

    public String getDatabaseUser() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "");
    }

    public boolean getDatabaseUseSSL() {
        return config.getBoolean("database.useSSL", false);
    }

    public boolean isWorldPublicByDefault() {
        return config.getBoolean("worlds.public-by-default", false);
    }

    public boolean allowWorldNameChange() {
        return config.getBoolean("worlds.allow-name-change", true);
    }

    public boolean allowWorldTypeChange() {
        return config.getBoolean("worlds.allow-type-change", false);
    }

    public boolean allowGameModeChange() {
        return config.getBoolean("worlds.allow-gamemode-change", true);
    }

    public boolean useEconomyForWorldCreation() {
        return config.getBoolean("economy.enabled", false);
    }

    public double getWorldCreationCost() {
        return config.getDouble("economy.world-creation-cost", 100.0);
    }

    public String getWorldCreationPermission() {
        return config.getString("permissions.create-world", "minevoxel.world.create");
    }

    public String getWorldLimitBypassPermission() {
        return config.getString("permissions.bypass-limit", "minevoxel.world.bypass-limit");
    }

    public String getWorldAdminPermission() {
        return config.getString("permissions.admin", "minevoxel.admin");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}