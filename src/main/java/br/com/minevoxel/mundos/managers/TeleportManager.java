package br.com.minevoxel.mundos.managers;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private final MinevoxelMundos plugin;

    // Cache de últimas localizações dos jogadores em cada mundo
    private final Map<String, Map<UUID, Location>> lastLocations = new HashMap<>();

    public TeleportManager(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    public void teleportToWorld(Player player, String worldName) {
        // Verificar se estamos no servidor de mundos
        if (!plugin.isWorldsServer()) {
            plugin.getLogger().warning("Tentativa de teleporte em servidor que não é de mundos");

            // Se estivermos no Lobby, enviar jogador para o servidor Worlds
            if (plugin.isLobbyServer()) {
                plugin.getServerManager().connectToWorldsServer(player, worldName);
            }
            return;
        }

        // Verificar permissões do jogador
        plugin.getWorldManager().fetchWorldData(worldName).thenAccept(worldData -> {
            if (worldData == null) {
                player.sendMessage("§cMundo não encontrado: " + worldName);
                return;
            }

            // Verificar se o jogador tem permissão para acessar o mundo
            if (!worldData.canPlayerVisit(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem permissão para visitar este mundo.");
                return;
            }

            // Prosseguir com o teleporte
            Bukkit.getScheduler().runTask(plugin, () -> {
                doTeleport(player, worldName, worldData);
            });
        });
    }

    private void doTeleport(Player player, String worldName, WorldData worldData) {
        // Obter mundo atual do jogador
        World currentWorld = player.getWorld();

        // Salvar localização atual do jogador
        savePlayerLocation(player, currentWorld.getName());

        // Verificar se o mundo está carregado
        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            // Mundo não está carregado, precisamos carregá-lo
            player.sendMessage("§aCarregando mundo " + worldData.getDisplayName() + "...");

            plugin.getWorldManager().getOrLoadWorld(worldName).thenAccept(world -> {
                if (world != null) {
                    // Mundo carregado com sucesso, continuar teleporte
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        teleportPlayerToWorld(player, world, worldData);
                    });
                } else {
                    player.sendMessage("§cErro ao carregar o mundo " + worldData.getDisplayName());
                }
            }).exceptionally(ex -> {
                player.sendMessage("§cErro ao carregar o mundo: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });

        } else {
            // Mundo já está carregado, teleportar diretamente
            teleportPlayerToWorld(player, targetWorld, worldData);
        }
    }

    private void teleportPlayerToWorld(Player player, World world, WorldData worldData) {
        // Definir o modo de jogo do jogador de acordo com a configuração do mundo
        GameMode gameMode = worldData.getGameMode();
        player.setGameMode(gameMode);

        // Buscar a última localização do jogador neste mundo, se existir
        Location teleportLocation = getPlayerLastLocation(player, world.getName());

        if (teleportLocation == null) {
            // Não há localização salva, usar o spawn do mundo
            teleportLocation = world.getSpawnLocation();

            // Verificar se o spawn é seguro
            teleportLocation = findSafeLocation(teleportLocation);
        }

        // Teleportar o jogador
        player.teleport(teleportLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);

        // Mensagem de teleporte
        player.sendMessage("§aTeleportado para o mundo §b" + worldData.getDisplayName());

        // Aplicar efeitos de teleporte
        applyTeleportEffects(player);

        // Atualizar data de último acesso ao mundo
        updateWorldLastAccess(worldData);
    }

    public void savePlayerLocation(Player player, String worldName) {
        // Ignorar mundos especiais como o lobby
        if (worldName.equalsIgnoreCase("lobby") || worldName.equalsIgnoreCase("world")) {
            return;
        }

        // Obter mapa de localizações para este mundo
        Map<UUID, Location> worldLocations = lastLocations.computeIfAbsent(worldName, k -> new HashMap<>());

        // Salvar localização atual do jogador
        worldLocations.put(player.getUniqueId(), player.getLocation());
    }

    private Location getPlayerLastLocation(Player player, String worldName) {
        Map<UUID, Location> worldLocations = lastLocations.get(worldName);
        if (worldLocations != null) {
            return worldLocations.get(player.getUniqueId());
        }
        return null;
    }

    private Location findSafeLocation(Location location) {
        World world = location.getWorld();

        // Verificar se a localização é segura
        if (isSafeLocation(location)) {
            return location;
        }

        // Encontrar um bloco seguro próximo
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Procurar em um raio de 5 blocos
        for (int offsetY = 0; offsetY < 10; offsetY++) {
            for (int offsetX = -5; offsetX <= 5; offsetX++) {
                for (int offsetZ = -5; offsetZ <= 5; offsetZ++) {
                    Location checkLocation = new Location(world, x + offsetX, y + offsetY, z + offsetZ);
                    if (isSafeLocation(checkLocation)) {
                        // Ajustar para o centro do bloco
                        checkLocation.add(0.5, 0, 0.5);
                        return checkLocation;
                    }
                }
            }
        }

        // Se não encontrou um local seguro, tentar o topo do mundo
        Location highLocation = world.getHighestBlockAt(x, z).getLocation();
        highLocation.add(0, 1, 0); // Ficar em cima do bloco

        return highLocation;
    }

    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Verificar se há dois blocos de ar para o jogador ficar
        org.bukkit.block.Block feetBlock = world.getBlockAt(x, y, z);
        org.bukkit.block.Block headBlock = world.getBlockAt(x, y + 1, z);
        org.bukkit.block.Block groundBlock = world.getBlockAt(x, y - 1, z);

        return feetBlock.getType() == Material.AIR &&
                headBlock.getType() == Material.AIR &&
                groundBlock.getType().isSolid();
    }

    private void applyTeleportEffects(Player player) {
        // Efeitos visuais e sonoros para o teleporte
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Usar spawnParticle em vez do método obsoleto playEffect
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, player.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
    }

    private void updateWorldLastAccess(WorldData worldData) {
        worldData.setLastAccessed(new java.util.Date());
        worldData.setModified(true);

        // Atualizar no banco de dados (assíncrono)
        plugin.getDatabaseManager().updateWorld(worldData);
    }

    // Método para teleportar o jogador para o spawn do mundo
    public void teleportToWorldSpawn(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            plugin.getLogger().warning("Tentativa de teleporte para mundo não carregado: " + worldName);
        }
    }

    // Método para limpar dados de localização de um jogador
    public void clearPlayerData(Player player) {
        UUID playerUUID = player.getUniqueId();

        for (Map<UUID, Location> worldLocations : lastLocations.values()) {
            worldLocations.remove(playerUUID);
        }
    }

    // Método para salvar localizações no despejo do servidor
    public void saveAllLocations() {
        // Este método seria implementado para salvar as localizações em um arquivo
        // de configuração ou banco de dados para restauração após reinício do servidor
        // Para simplificação, não implementaremos isso neste exemplo
    }
}