package br.com.minevoxel.mundos.events;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.*;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.HashMap;
import java.util.Map;

public class WorldEvents implements Listener {

    private final MinevoxelMundos plugin;
    private final Map<String, Long> worldLastUsed = new HashMap<>();

    public WorldEvents(MinevoxelMundos plugin) {
        this.plugin = plugin;

        // Iniciar tarefa para verificar mundos não utilizados
        startWorldUnloadTask();
    }

    private void startWorldUnloadTask() {
        int checkInterval = 5 * 60 * 20; // 5 minutos em ticks
        int unloadDelay = plugin.getConfigManager().getWorldUnloadDelay() * 60 * 1000; // minutos para ms

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            // Verificar cada mundo carregado
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();

                // Ignorar mundos principais
                if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
                    continue;
                }

                // Verificar se o mundo está vazio
                if (world.getPlayers().isEmpty()) {
                    Long lastUsed = worldLastUsed.get(worldName);

                    if (lastUsed == null) {
                        // Registrar a primeira vez que o mundo ficou vazio
                        worldLastUsed.put(worldName, currentTime);
                    } else if (currentTime - lastUsed > unloadDelay) {
                        // O mundo está vazio por tempo suficiente, descarregar
                        plugin.getLogger().info("Descarregando mundo não utilizado: " + worldName);

                        // Obter dados do mundo antes de descarregar
                        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

                        // Descarregar o mundo
                        boolean success = plugin.getWorldManager().unloadWorld(worldName, true);

                        if (success) {
                            plugin.getLogger().info("Mundo descarregado com sucesso: " + worldName);
                            worldLastUsed.remove(worldName);
                        } else {
                            plugin.getLogger().warning("Falha ao descarregar mundo: " + worldName);
                        }
                    }
                } else {
                    // O mundo tem jogadores, resetar o temporizador
                    worldLastUsed.remove(worldName);
                }
            }
        }, checkInterval, checkInterval);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        World world = event.getWorld();
        String worldName = world.getName();

        // Ignorar mundos principais
        if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
            return;
        }

        plugin.getLogger().info("Mundo carregado: " + worldName);

        // Buscar dados do mundo do banco de dados, se não estiver no cache
        if (plugin.getWorldManager().getWorldData(worldName) == null) {
            plugin.getWorldManager().fetchWorldData(worldName).thenAccept(worldData -> {
                if (worldData != null) {
                    // Aplicar configurações do mundo
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getWorldManager().configureWorld(world, worldData);
                        plugin.getLogger().info("Configurações aplicadas ao mundo: " + worldName);
                    });
                } else {
                    plugin.getLogger().warning("Não foi possível encontrar dados para o mundo: " + worldName);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        World world = event.getWorld();
        String worldName = world.getName();

        // Ignorar mundos principais
        if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
            return;
        }

        plugin.getLogger().info("Mundo descarregado: " + worldName);

        // Remover do mapa de mundos não utilizados
        worldLastUsed.remove(worldName);

        // Atualizar status do mundo no gerenciador
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);
        if (worldData != null) {
            worldData.setLoaded(false);
            worldData.setLastAccessed(new java.util.Date());
            worldData.setModified(true);

            // Atualizar no banco de dados
            plugin.getDatabaseManager().updateWorld(worldData);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        World world = event.getWorld();
        String worldName = world.getName();

        // Ignorar mundos principais
        if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
            return;
        }

        plugin.getLogger().info("Mundo inicializado: " + worldName);

        // Podemos otimizar a geração do mundo aqui, se necessário
        world.setKeepSpawnInMemory(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        // Otimizações adicionais de chunks podem ser feitas aqui
        // Este método é chamado com muita frequência, por isso deve ser leve
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        Player player = event.getPlayer();
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();

        // Verificar se o mundo de origem ficou vazio
        if (fromWorld.getPlayers().isEmpty()) {
            String fromWorldName = fromWorld.getName();

            // Ignorar mundos principais
            if (!fromWorldName.equalsIgnoreCase("world") && !fromWorldName.equalsIgnoreCase("lobby")) {
                // Registrar quando o mundo ficou vazio
                worldLastUsed.put(fromWorldName, System.currentTimeMillis());
            }
        }

        // Configurações para o novo mundo
        String toWorldName = toWorld.getName();

        // Ignorar mundos principais
        if (!toWorldName.equalsIgnoreCase("world") && !toWorldName.equalsIgnoreCase("lobby")) {
            // Buscar configurações do mundo
            WorldData worldData = plugin.getWorldManager().getWorldData(toWorldName);

            if (worldData != null) {
                // Aplicar modo de jogo
                player.setGameMode(worldData.getGameMode());

                // Atualizar data de último acesso
                worldData.setLastAccessed(new java.util.Date());
                worldData.setModified(true);

                // Atualizar no banco de dados
                plugin.getDatabaseManager().updateWorld(worldData);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStructureGrow(StructureGrowEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        World world = event.getWorld();
        String worldName = world.getName();

        // Ignorar mundos principais
        if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
            return;
        }

        // Verificar configurações de física do mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData != null && !worldData.isPhysics()) {
            // Se a física está desativada, impedir crescimento de estruturas
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        World world = event.getBlock().getWorld();
        String worldName = world.getName();

        // Ignorar mundos principais
        if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
            return;
        }

        // Verificar configurações de física do mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData != null && !worldData.isPhysics()) {
            // Se a física está desativada, cancelar o evento
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLiquidFlow(BlockFromToEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        World world = event.getBlock().getWorld();
        String worldName = world.getName();

        // Ignorar mundos principais
        if (worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("lobby")) {
            return;
        }

        // Verificar configurações de física do mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData != null) {
            // Verificar tipo de líquido
            org.bukkit.Material material = event.getBlock().getType();

            if (material == org.bukkit.Material.WATER || material == org.bukkit.Material.WATER_CAULDRON) {
                // Verificar fluxo de água
                if (!worldData.isWaterFlow()) {
                    event.setCancelled(true);
                }
            } else if (material == org.bukkit.Material.LAVA || material == org.bukkit.Material.LAVA_CAULDRON) {
                // Verificar fluxo de lava
                if (!worldData.isLavaFlow()) {
                    event.setCancelled(true);
                }
            }
        }
    }
}