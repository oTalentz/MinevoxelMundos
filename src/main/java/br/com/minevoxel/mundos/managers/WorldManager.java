package br.com.minevoxel.mundos.managers;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {

    private final MinevoxelMundos plugin;
    private final Map<String, WorldData> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<String, Integer> worldIdCache = new ConcurrentHashMap<>();

    public WorldManager(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Verificar se estamos no servidor de mundos
        if (!plugin.isWorldsServer()) {
            plugin.getLogger().warning("WorldManager foi inicializado em um servidor que não é de mundos!");
            return;
        }

        // Registrar o mundo padrão se ele existir
        World defaultWorld = Bukkit.getWorlds().get(0);
        if (defaultWorld != null) {
            WorldData defaultWorldData = new WorldData(0, defaultWorld.getName(),
                    UUID.fromString("00000000-0000-0000-0000-000000000000"), "Server");
            defaultWorldData.setDisplayName("Lobby");
            defaultWorldData.setPublic(true);
            loadedWorlds.put(defaultWorld.getName(), defaultWorldData);
        }

        // Carregar mundos eventualmente
        Bukkit.getScheduler().runTaskLater(plugin, this::loadCachedWorlds, 60L);
    }

    private void loadCachedWorlds() {
        // Carregar IDs de mundos do banco de dados para o cache
        CompletableFuture.runAsync(() -> {
            // Aqui você poderia implementar um método para carregar todos os mundos
            // registrados no banco de dados para o cache.
            // Para este exemplo, vamos simplesmente simular o carregamento dos mundos
            // que já estão no diretório de mundos.

            File worldsDir = new File(Bukkit.getWorldContainer().getAbsolutePath());
            File[] worldDirs = worldsDir.listFiles(File::isDirectory);

            if (worldDirs != null) {
                for (File worldDir : worldDirs) {
                    String worldName = worldDir.getName();

                    // Verificar se é um diretório de mundo válido (tem level.dat)
                    File levelFile = new File(worldDir, "level.dat");
                    if (!levelFile.exists()) continue;

                    // Verificar se é um mundo que já foi registrado
                    if (Bukkit.getWorld(worldName) != null) continue;

                    // Verificar se o nome do mundo segue o padrão esperado (username_randomname)
                    if (worldName.contains("_")) {
                        // Buscar o mundo no banco de dados
                        plugin.getDatabaseManager().getWorldByName(worldName).thenAccept(worldData -> {
                            if (worldData != null) {
                                // Armazenar no cache
                                worldIdCache.put(worldName, worldData.getId());

                                // Verificar se devemos carregar o mundo automaticamente
                                // (Por exemplo, se ele estava carregado quando o servidor foi desligado)
                                // Neste exemplo, não carregaremos automaticamente para economizar recursos
                            }
                        });
                    }
                }
            }
        });
    }

    public CompletableFuture<World> loadWorld(WorldData worldData) {
        CompletableFuture<World> future = new CompletableFuture<>();

        // Verificar se o mundo já está carregado
        World existingWorld = Bukkit.getWorld(worldData.getWorldName());
        if (existingWorld != null) {
            loadedWorlds.put(worldData.getWorldName(), worldData);
            worldIdCache.put(worldData.getWorldName(), worldData.getId());
            worldData.setLoaded(true);
            future.complete(existingWorld);
            return future;
        }

        // Verificar se o diretório do mundo existe
        File worldDir = new File(Bukkit.getWorldContainer(), worldData.getWorldName());
        boolean worldExists = worldDir.exists() && new File(worldDir, "level.dat").exists();

        // Criar configurações do mundo
        WorldCreator creator = new WorldCreator(worldData.getWorldName());

        // Definir tipo de mundo
        switch (worldData.getWorldType()) {
            case "FLAT":
                creator.type(WorldType.FLAT);
                break;
            case "AMPLIFIED":
                creator.type(WorldType.AMPLIFIED);
                break;
            default:
                creator.type(WorldType.NORMAL);
        }

        // Definir ambiente (NORMAL, NETHER, THE_END)
        switch (worldData.getEnvironment()) {
            case "NETHER":
                creator.environment(World.Environment.NETHER);
                break;
            case "THE_END":
                creator.environment(World.Environment.THE_END);
                break;
            default:
                creator.environment(World.Environment.NORMAL);
        }

        // Definir semente se houver
        if (worldData.getSeed() != 0) {
            creator.seed(worldData.getSeed());
        }

        // Definir se gera estruturas
        creator.generateStructures(worldData.isGenerateStructures());

        // Se o mundo não existir, criar o mundo assincronamente
        if (!worldExists) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Criar o mundo em outro thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        World world = creator.createWorld();
                        if (world != null) {
                            // Configurar o mundo
                            configureWorld(world, worldData);

                            // Registrar no gerenciador
                            loadedWorlds.put(worldData.getWorldName(), worldData);
                            worldIdCache.put(worldData.getWorldName(), worldData.getId());
                            worldData.setLoaded(true);

                            // Atualizar data de último acesso
                            updateLastAccessedDate(worldData);

                            future.complete(world);
                        } else {
                            future.completeExceptionally(new Exception("Falha ao criar o mundo: " + worldData.getWorldName()));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao criar mundo " + worldData.getWorldName() + ": " + e.getMessage());
                        e.printStackTrace();
                        future.completeExceptionally(e);
                    }
                });
            });
        } else {
            // O mundo já existe, apenas carregar
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    World world = creator.createWorld();
                    if (world != null) {
                        // Configurar o mundo
                        configureWorld(world, worldData);

                        // Registrar no gerenciador
                        loadedWorlds.put(worldData.getWorldName(), worldData);
                        worldIdCache.put(worldData.getWorldName(), worldData.getId());
                        worldData.setLoaded(true);

                        // Atualizar data de último acesso
                        updateLastAccessedDate(worldData);

                        future.complete(world);
                    } else {
                        future.completeExceptionally(new Exception("Falha ao carregar o mundo: " + worldData.getWorldName()));
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao carregar mundo " + worldData.getWorldName() + ": " + e.getMessage());
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            });
        }

        return future;
    }

    public void configureWorld(World world, WorldData worldData) {
        // Configurar propriedades do mundo
        world.setPVP(worldData.isPvp());

        // Configurar regras de jogo
        world.setGameRule(GameRule.DO_FIRE_TICK, worldData.isFireSpread());
        world.setGameRule(GameRule.DO_MOB_SPAWNING, worldData.isMobSpawning());
        world.setGameRule(GameRule.DO_MOB_LOOT, true);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);

        // Configurar física do mundo
        if (!worldData.isPhysics()) {
            world.setGameRule(GameRule.DISABLE_RAIDS, true);
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
            world.setGameRule(GameRule.DO_INSOMNIA, false);
        }

        // Alternativa para lidar com GameRules não padrão
        for (GameRule<?> rule : GameRule.values()) {
            String ruleName = rule.getName();

            // Verificar regras específicas
            if (ruleName.equals("DO_REDSTONE")) {
                setGameRuleSafely(world, rule, worldData.isRedstone());
            } else if (ruleName.equals("DO_LEAF_DECAY")) {
                setGameRuleSafely(world, rule, worldData.isLeafDecay());
            }
        }

        // Configurar mundo para o modo de jogo
        GameMode defaultGameMode = worldData.getGameMode();
        for (Player player : world.getPlayers()) {
            player.setGameMode(defaultGameMode);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void setGameRuleSafely(World world, GameRule<T> rule, boolean value) {
        try {
            if (rule.getType() == Boolean.class) {
                GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                world.setGameRule(booleanRule, value);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao definir GameRule " + rule.getName() + ": " + e.getMessage());
        }
    }

    public boolean unloadWorld(String worldName, boolean save) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }

        // Teleportar jogadores para o spawn do servidor
        World defaultWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(defaultWorld.getSpawnLocation());
        }

        // Descarregar o mundo
        boolean success = Bukkit.unloadWorld(world, save);

        // Se descarregado com sucesso, remover do registro
        if (success) {
            WorldData worldData = loadedWorlds.remove(worldName);
            if (worldData != null) {
                worldData.setLoaded(false);

                // Atualizar data de último acesso
                updateLastAccessedDate(worldData);
            }
        }

        return success;
    }

    public CompletableFuture<Boolean> deleteWorld(String worldName) {
        // Primeiro, descarregar o mundo se estiver carregado
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            boolean unloaded = unloadWorld(worldName, false);
            if (!unloaded) {
                return CompletableFuture.completedFuture(false);
            }
        }

        // Remover do cache
        loadedWorlds.remove(worldName);
        Integer worldId = worldIdCache.remove(worldName);

        // Deletar o diretório do mundo
        File worldDir = new File(Bukkit.getWorldContainer(), worldName);
        if (!worldDir.exists()) {
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Deletar o mundo do banco de dados
        if (worldId != null) {
            plugin.getDatabaseManager().deleteWorld(worldId).thenAccept(deleted -> {
                if (deleted) {
                    // Deletar os arquivos do mundo
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            deleteWorldFiles(worldDir);
                            future.complete(true);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Erro ao deletar arquivos do mundo " + worldName + ": " + e.getMessage());
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
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    deleteWorldFiles(worldDir);
                    future.complete(true);
                } catch (Exception e) {
                    plugin.getLogger().severe("Erro ao deletar arquivos do mundo " + worldName + ": " + e.getMessage());
                    e.printStackTrace();
                    future.complete(false);
                }
            });
        }

        return future;
    }

    private void deleteWorldFiles(File worldDir) {
        if (worldDir.exists() && worldDir.isDirectory()) {
            File[] files = worldDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorldFiles(file);
                    } else {
                        file.delete();
                    }
                }
            }
            worldDir.delete();
        }
    }

    public CompletableFuture<World> getOrLoadWorld(String worldName) {
        // Verificar se o mundo já está carregado
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return CompletableFuture.completedFuture(world);
        }

        // Verificar se temos os dados do mundo
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
        // Atualizar configurações do mundo se estiver carregado
        World world = Bukkit.getWorld(worldData.getWorldName());
        if (world != null) {
            configureWorld(world, worldData);
        }

        // Atualizar no banco de dados
        plugin.getDatabaseManager().updateWorld(worldData);

        // Atualizar no cache local
        loadedWorlds.put(worldData.getWorldName(), worldData);
        worldIdCache.put(worldData.getWorldName(), worldData.getId());
    }

    public boolean isWorldLoaded(String worldName) {
        return Bukkit.getWorld(worldName) != null;
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
        // Salvar todos os mundos carregados
        for (World world : Bukkit.getWorlds()) {
            world.save();
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