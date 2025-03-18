package br.com.minevoxel.mundos.managers;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.exceptions.PropertyDoesNotExistException;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MultiverseWorldManager {

    private final MinevoxelMundos plugin;
    private MultiverseCore mvCore;
    private MVWorldManager mvWorldManager;
    private final Map<String, WorldData> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<String, Integer> worldIdCache = new ConcurrentHashMap<>();

    public MultiverseWorldManager(MinevoxelMundos plugin) {
        this.plugin = plugin;
        setupMultiverse();
    }

    private void setupMultiverse() {
        Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin instanceof MultiverseCore) {
            mvCore = (MultiverseCore) mvPlugin;
            mvWorldManager = mvCore.getMVWorldManager();
            plugin.getLogger().info("Integração com Multiverse-Core concluída com sucesso!");
        } else {
            plugin.getLogger().severe("Não foi possível encontrar o Multiverse-Core! Verifique se está instalado corretamente.");
        }
    }

    public void initialize() {
        if (!plugin.isWorldsServer() || mvCore == null) {
            return;
        }

        // Registrar mundos existentes do Multiverse
        for (MultiverseWorld mvWorld : mvWorldManager.getMVWorlds()) {
            String worldName = mvWorld.getName();

            // Ignorar mundos padrão
            if (worldName.equalsIgnoreCase("world") ||
                    worldName.equalsIgnoreCase("lobby") ||
                    worldName.equals("world_nether") ||
                    worldName.equals("world_the_end")) {
                continue;
            }

            // Buscar dados do banco
            plugin.getDatabaseManager().getWorldByName(worldName).thenAccept(worldData -> {
                if (worldData != null) {
                    // Registrar no cache
                    loadedWorlds.put(worldName, worldData);
                    worldIdCache.put(worldName, worldData.getId());
                }
            });
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::loadCachedWorlds, 60L);
    }

    private void loadCachedWorlds() {
        if (mvCore == null) return;

        // Buscando mundos que existem no banco de dados mas não estão carregados
        File worldsDir = new File(Bukkit.getWorldContainer().getAbsolutePath());
        File[] worldDirs = worldsDir.listFiles(File::isDirectory);

        if (worldDirs != null) {
            for (File worldDir : worldDirs) {
                String worldName = worldDir.getName();

                // Verificar se é um diretório de mundo válido (tem level.dat)
                File levelFile = new File(worldDir, "level.dat");
                if (!levelFile.exists()) continue;

                // Verificar se é um mundo que já foi registrado no Multiverse
                if (mvWorldManager.isMVWorld(worldName)) continue;

                // Verificar se o nome do mundo segue o padrão esperado (username_randomname)
                if (worldName.contains("_")) {
                    // Buscar o mundo no banco de dados
                    plugin.getDatabaseManager().getWorldByName(worldName).thenAccept(worldData -> {
                        if (worldData != null) {
                            // Armazenar no cache
                            worldIdCache.put(worldName, worldData.getId());
                        }
                    });
                }
            }
        }
    }

    public CompletableFuture<World> loadWorld(WorldData worldData) {
        CompletableFuture<World> future = new CompletableFuture<>();

        if (mvCore == null) {
            future.completeExceptionally(new Exception("Multiverse-Core não está disponível!"));
            return future;
        }

        // Verificar se o mundo já está carregado no Multiverse
        if (mvWorldManager.isMVWorld(worldData.getWorldName())) {
            MultiverseWorld mvWorld = mvWorldManager.getMVWorld(worldData.getWorldName());
            if (mvWorld != null) {
                World world = mvWorld.getCBWorld();
                loadedWorlds.put(worldData.getWorldName(), worldData);
                worldIdCache.put(worldData.getWorldName(), worldData.getId());
                worldData.setLoaded(true);

                // Atualizar configurações
                updateWorldSettings(worldData);

                future.complete(world);
                return future;
            }
        }

        // Verificar se o diretório existe
        File worldDir = new File(Bukkit.getWorldContainer(), worldData.getWorldName());
        boolean worldExists = worldDir.exists() && new File(worldDir, "level.dat").exists();

        // Configurar o ambiente
        org.bukkit.World.Environment environment;
        switch (worldData.getEnvironment()) {
            case "NETHER":
                environment = org.bukkit.World.Environment.NETHER;
                break;
            case "THE_END":
                environment = org.bukkit.World.Environment.THE_END;
                break;
            default:
                environment = org.bukkit.World.Environment.NORMAL;
                break;
        }

        // Configurar tipo de mundo
        WorldType worldType;
        switch (worldData.getWorldType()) {
            case "FLAT":
                worldType = WorldType.FLAT;
                break;
            case "AMPLIFIED":
                worldType = WorldType.AMPLIFIED;
                break;
            default:
                worldType = WorldType.NORMAL;
                break;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                boolean success;

                if (worldExists) {
                    // Carregar mundo existente
                    success = mvWorldManager.loadWorld(worldData.getWorldName());
                } else {
                    // Criar novo mundo - usando enum WorldType diretamente
                    success = mvWorldManager.addWorld(
                            worldData.getWorldName(),
                            environment,
                            worldData.getSeed() != 0 ? String.valueOf(worldData.getSeed()) : null,
                            worldType,
                            worldData.isGenerateStructures(),
                            null // generator
                    );
                }

                if (success) {
                    MultiverseWorld mvWorld = mvWorldManager.getMVWorld(worldData.getWorldName());
                    World world = mvWorld.getCBWorld();

                    // Configurar o mundo
                    configureWorld(world, worldData);

                    // Registrar no cache
                    loadedWorlds.put(worldData.getWorldName(), worldData);
                    worldIdCache.put(worldData.getWorldName(), worldData.getId());
                    worldData.setLoaded(true);

                    // Atualizar data de acesso
                    updateLastAccessedDate(worldData);

                    future.complete(world);
                } else {
                    future.completeExceptionally(new Exception("Falha ao carregar mundo via Multiverse!"));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Erro ao criar/carregar mundo " + worldData.getWorldName() + ": " + e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public void configureWorld(World world, WorldData worldData) {
        if (mvCore == null) return;

        // Obter o mundo do Multiverse
        MultiverseWorld mvWorld = mvWorldManager.getMVWorld(world);
        if (mvWorld == null) return;

        // Configurar propriedades do mundo
        world.setPVP(worldData.isPvp());

        // Definir modo de jogo padrão
        GameMode gameMode = worldData.getGameMode();
        try {
            mvWorld.setPropertyValue("gameMode", gameMode.name().toLowerCase());
        } catch (PropertyDoesNotExistException e) {
            throw new RuntimeException(e);
        }

        // Configurar regras de jogo
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, worldData.isFireSpread());
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, worldData.isMobSpawning());
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);

        // Configurar física do mundo
        if (!worldData.isPhysics()) {
            world.setGameRule(org.bukkit.GameRule.DISABLE_RAIDS, true);
            world.setGameRule(org.bukkit.GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_TRADER_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.DO_INSOMNIA, false);
        }

        // Definir modo de jogo para jogadores no mundo
        for (Player player : world.getPlayers()) {
            player.setGameMode(gameMode);
        }
    }

    public boolean unloadWorld(String worldName, boolean save) {
        if (mvCore == null) return false;

        if (!mvWorldManager.isMVWorld(worldName)) {
            return false;
        }

        // Teleportar jogadores para o spawn
        MultiverseWorld mvWorld = mvWorldManager.getMVWorld(worldName);
        if (mvWorld != null) {
            World defaultWorld = Bukkit.getWorlds().get(0);
            for (Player player : mvWorld.getCBWorld().getPlayers()) {
                player.teleport(defaultWorld.getSpawnLocation());
            }
        }

        // Descarregar o mundo via Multiverse
        boolean success = mvWorldManager.unloadWorld(worldName, save);

        // Atualizar cache
        if (success) {
            WorldData worldData = loadedWorlds.remove(worldName);
            if (worldData != null) {
                worldData.setLoaded(false);
                updateLastAccessedDate(worldData);
            }
        }

        return success;
    }

    public CompletableFuture<Boolean> deleteWorld(String worldName) {
        if (mvCore == null) return CompletableFuture.completedFuture(false);

        // Primeiro, descarregar o mundo
        if (mvWorldManager.isMVWorld(worldName)) {
            boolean unloaded = unloadWorld(worldName, false);
            if (!unloaded) {
                return CompletableFuture.completedFuture(false);
            }
        }

        // Remover do cache
        loadedWorlds.remove(worldName);
        Integer worldId = worldIdCache.remove(worldName);

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Deletar o mundo do banco de dados
        if (worldId != null) {
            plugin.getDatabaseManager().deleteWorld(worldId).thenAccept(deleted -> {
                if (deleted) {
                    // Deletar os arquivos do mundo via Multiverse
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            boolean success = mvWorldManager.deleteWorld(worldName, true, true);
                            future.complete(success);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Erro ao deletar mundo " + worldName + ": " + e.getMessage());
                            e.printStackTrace();
                            future.complete(false);
                        }
                    });
                } else {
                    future.complete(false);
                }
            });
        } else {
            // Mundo não encontrado no banco de dados, apenas deletar os arquivos
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean success = mvWorldManager.deleteWorld(worldName, true, true);
                    future.complete(success);
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao deletar mundo " + worldName + ": " + e.getMessage());
                    e.printStackTrace();
                    future.complete(false);
                }
            });
        }

        return future;
    }

    public CompletableFuture<World> getOrLoadWorld(String worldName) {
        if (mvCore == null) {
            CompletableFuture<World> future = new CompletableFuture<>();
            future.completeExceptionally(new Exception("Multiverse-Core não está disponível!"));
            return future;
        }

        // Verificar se o mundo já está carregado no Multiverse
        if (mvWorldManager.isMVWorld(worldName)) {
            MultiverseWorld mvWorld = mvWorldManager.getMVWorld(worldName);
            if (mvWorld != null) {
                return CompletableFuture.completedFuture(mvWorld.getCBWorld());
            }
        }

        // Verificar se temos os dados do mundo no cache
        WorldData worldData = loadedWorlds.get(worldName);
        if (worldData != null) {
            return loadWorld(worldData);
        }

        // Verificar se temos o ID do mundo no cache
        Integer worldId = worldIdCache.get(worldName);
        if (worldId != null) {
            // Buscar dados do mundo no banco de dados
            return plugin.getDatabaseManager().getWorld(worldId).thenCompose(data -> {
                if (data != null) {
                    return loadWorld(data);
                } else {
                    CompletableFuture<World> future = new CompletableFuture<>();
                    future.completeExceptionally(new Exception("Mundo não encontrado: " + worldName));
                    return future;
                }
            });
        }

        // Buscar mundo pelo nome no banco de dados
        return plugin.getDatabaseManager().getWorldByName(worldName).thenCompose(data -> {
            if (data != null) {
                return loadWorld(data);
            } else {
                CompletableFuture<World> future = new CompletableFuture<>();
                future.completeExceptionally(new Exception("Mundo não encontrado: " + worldName));
                return future;
            }
        });
    }

    public void updateWorldSettings(WorldData worldData) {
        if (mvCore == null) return;

        // Atualizar configurações do mundo se estiver carregado no Multiverse
        if (mvWorldManager.isMVWorld(worldData.getWorldName())) {
            MultiverseWorld mvWorld = mvWorldManager.getMVWorld(worldData.getWorldName());
            if (mvWorld != null) {
                World world = mvWorld.getCBWorld();
                configureWorld(world, worldData);
            }
        }

        // Atualizar no banco de dados
        plugin.getDatabaseManager().updateWorld(worldData);

        // Atualizar no cache local
        loadedWorlds.put(worldData.getWorldName(), worldData);
        worldIdCache.put(worldData.getWorldName(), worldData.getId());
    }

    public boolean isWorldLoaded(String worldName) {
        if (mvCore == null) return false;
        return mvWorldManager.isMVWorld(worldName);
    }

    public WorldData getWorldData(String worldName) {
        return loadedWorlds.get(worldName);
    }

    public CompletableFuture<WorldData> fetchWorldData(String worldName) {
        // Primeiro verificar o cache local
        WorldData cachedData = loadedWorlds.get(worldName);
        if (cachedData != null) {
            return CompletableFuture.completedFuture(cachedData);
        }

        // Verificar se temos o ID no cache
        Integer worldId = worldIdCache.get(worldName);
        if (worldId != null) {
            return plugin.getDatabaseManager().getWorld(worldId);
        }

        // Buscar pelo nome no banco de dados
        return plugin.getDatabaseManager().getWorldByName(worldName);
    }

    private void updateLastAccessedDate(WorldData worldData) {
        worldData.setLastAccessed(new Date());
        worldData.setModified(true);

        // Atualizar no banco de dados (assíncrono)
        plugin.getDatabaseManager().updateWorld(worldData);
    }

    public void saveAllWorlds() {
        if (mvCore == null) return;

        // Como MVWorldManager não possui saveAllWorlds(), implementamos nossa própria versão
        // Salvar todos os mundos
        for (MultiverseWorld mvWorld : mvWorldManager.getMVWorlds()) {
            World world = mvWorld.getCBWorld();
            if (world != null) {
                world.save();
            }
        }

        // Atualizar dados de mundos modificados no banco de dados
        for (WorldData worldData : loadedWorlds.values()) {
            if (worldData.isModified()) {
                plugin.getDatabaseManager().updateWorld(worldData);
                worldData.setModified(false);
            }
        }
    }

    public Collection<WorldData> getLoadedWorldsData() {
        return Collections.unmodifiableCollection(loadedWorlds.values());
    }
}