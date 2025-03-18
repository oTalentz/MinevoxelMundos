package br.com.minevoxel.mundos.events;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerEvents implements Listener {

    private final MinevoxelMundos plugin;
    private final Map<UUID, Long> lastWorldMenuOpen = new HashMap<>();

    public PlayerEvents(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Verificar tipo de servidor
        if (plugin.isLobbyServer()) {
            // Estamos no servidor de lobby
            handleLobbyJoin(player);
        } else if (plugin.isWorldsServer()) {
            // Estamos no servidor de mundos
            handleWorldsJoin(player);
        }
    }

    private void handleLobbyJoin(Player player) {
        // Dar item do menu de mundos para o jogador
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            giveWorldMenuItem(player);
        }, 10L);
    }

    private void handleWorldsJoin(Player player) {
        // Verificar se o jogador tem um mundo para teleportar
        // Geralmente isso é tratado pelo BungeeCord/Velocity quando o jogador
        // se conecta ao servidor de mundos

        // Verificar se o jogador está no mundo correto
        World world = player.getWorld();
        String worldName = world.getName();

        // Se estiver no mundo de lobby, está ok
        if (worldName.equalsIgnoreCase("lobby") || worldName.equalsIgnoreCase("world")) {
            return;
        }

        // Verificar se o jogador tem permissão para estar neste mundo
        plugin.getWorldManager().fetchWorldData(worldName).thenAccept(worldData -> {
            if (worldData != null) {
                if (!worldData.canPlayerVisit(player.getUniqueId())) {
                    // Jogador não tem permissão, teleportar para o lobby
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        World lobbyWorld = Bukkit.getWorlds().get(0);
                        player.teleport(lobbyWorld.getSpawnLocation());
                        player.sendMessage("§cVocê não tem permissão para visitar este mundo.");
                    });
                } else {
                    // Jogador tem permissão, definir modo de jogo conforme configuração do mundo
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.setGameMode(worldData.getGameMode());
                    });
                }
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Limpar dados temporários do jogador
        lastWorldMenuOpen.remove(player.getUniqueId());

        // Se estamos no servidor de mundos, registrar a última localização do jogador
        if (plugin.isWorldsServer()) {
            plugin.getTeleportManager().clearPlayerData(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && isWorldMenuItem(item)) {
            event.setCancelled(true);

            // Evitar múltiplos cliques
            long currentTime = System.currentTimeMillis();
            Long lastOpen = lastWorldMenuOpen.getOrDefault(player.getUniqueId(), 0L);

            if (currentTime - lastOpen < 500) {
                return;
            }

            lastWorldMenuOpen.put(player.getUniqueId(), currentTime);

            // Abrir menu de mundos
            openWorldMenu(player);
        }
    }

    private void openWorldMenu(Player player) {
        // Abrir menu principal de mundos
        plugin.getGuiHandler().openMainGUI(player);
    }

    private boolean isWorldMenuItem(ItemStack item) {
        if (item.getType() != Material.COMPASS) {
            return false;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = item.getItemMeta().getDisplayName();
        return displayName.contains("Mundos") || displayName.contains("Worlds");
    }

    private void giveWorldMenuItem(Player player) {
        // Criar item do menu de mundos
        ItemStack compass = new ItemStack(Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName("§a§lMeus Mundos");
        meta.setLore(List.of(
                "§7Clique para abrir o menu de mundos",
                "§7e gerenciar seus mundos privados."
        ));
        compass.setItemMeta(meta);

        // Verificar se o jogador já tem o item
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isWorldMenuItem(item)) {
                return;
            }
        }

        // Dar o item ao jogador
        player.getInventory().setItem(4, compass);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        // Verificar permissões de construção apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        Player player = event.getPlayer();

        // Admins podem construir em qualquer lugar
        if (player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
            return;
        }

        // Verificar permissão de construção no mundo atual
        World world = player.getWorld();
        String worldName = world.getName();

        // Ignorar mundo principal/lobby
        if (worldName.equalsIgnoreCase("lobby") || worldName.equalsIgnoreCase("world")) {
            return;
        }

        // Verificar permissões do jogador para este mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData == null) {
            event.setCancelled(true);
            return;
        }

        // Verificar se o jogador pode construir
        if (!worldData.canPlayerBuild(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§cVocê não tem permissão para construir neste mundo.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Verificar permissões de construção apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        Player player = event.getPlayer();

        // Admins podem construir em qualquer lugar
        if (player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
            return;
        }

        // Verificar permissão de construção no mundo atual
        World world = player.getWorld();
        String worldName = world.getName();

        // Ignorar mundo principal/lobby
        if (worldName.equalsIgnoreCase("lobby") || worldName.equalsIgnoreCase("world")) {
            return;
        }

        // Verificar permissões do jogador para este mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData == null) {
            event.setCancelled(true);
            return;
        }

        // Verificar se o jogador pode construir
        if (!worldData.canPlayerBuild(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§cVocê não tem permissão para construir neste mundo.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Verificar comandos bloqueados apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Admins podem usar qualquer comando
        if (player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
            return;
        }

        // Verificar se o comando está na lista de comandos desabilitados
        List<String> disabledCommands = plugin.getConfigManager().getDisabledCommands();

        for (String disabledCmd : disabledCommands) {
            if (command.startsWith("/" + disabledCmd.toLowerCase())) {
                event.setCancelled(true);
                player.sendMessage("§cEste comando está desabilitado nos mundos privados.");
                return;
            }
        }

        // Verificar comandos específicos do mundo atual
        World world = player.getWorld();
        String worldName = world.getName();

        // Ignorar mundo principal/lobby
        if (worldName.equalsIgnoreCase("lobby") || worldName.equalsIgnoreCase("world")) {
            return;
        }

        // Verificar permissões do jogador para este mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData != null) {
            // Se o jogador não é construtor, bloquear comandos que podem modificar o mundo
            if (!worldData.canPlayerBuild(player.getUniqueId())) {
                // Lista de comandos que podem modificar o mundo
                String[] buildCommands = {"/fill", "/clone", "/setblock", "/worldedit", "/we", "//set", "//replace"};

                for (String buildCmd : buildCommands) {
                    if (command.startsWith(buildCmd)) {
                        event.setCancelled(true);
                        player.sendMessage("§cVocê não tem permissão para usar este comando neste mundo.");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        // Verificar permissões de modo de jogo apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        Player player = event.getPlayer();

        // Admins podem mudar o modo de jogo livremente
        if (player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
            return;
        }

        // Verificar permissões no mundo atual
        World world = player.getWorld();
        String worldName = world.getName();

        // Ignorar mundo principal/lobby
        if (worldName.equalsIgnoreCase("lobby") || worldName.equalsIgnoreCase("world")) {
            return;
        }

        // Verificar permissões do jogador para este mundo
        WorldData worldData = plugin.getWorldManager().getWorldData(worldName);

        if (worldData != null) {
            // Se o jogador não é proprietário, forçar o modo de jogo do mundo
            if (!worldData.getOwnerUUID().equals(player.getUniqueId())) {
                GameMode worldGameMode = worldData.getGameMode();
                GameMode newGameMode = event.getNewGameMode();

                if (newGameMode != worldGameMode) {
                    event.setCancelled(true);

                    // Agendar uma tarefa para resetar o modo de jogo
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.setGameMode(worldGameMode);
                    }, 1L);

                    player.sendMessage("§cVocê não pode alterar o modo de jogo neste mundo.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Processar apenas no servidor de mundos
        if (!plugin.isWorldsServer()) {
            return;
        }

        Player player = event.getPlayer();
        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo().getWorld();

        // Verificar se o jogador está mudando de mundo
        if (!fromWorld.equals(toWorld)) {
            // Salvar última localização no mundo de origem
            plugin.getTeleportManager().savePlayerLocation(player, fromWorld.getName());

            // Verificar permissões no mundo de destino
            String toWorldName = toWorld.getName();

            // Ignorar mundo principal/lobby
            if (!toWorldName.equalsIgnoreCase("lobby") && !toWorldName.equalsIgnoreCase("world")) {
                // Verificar permissões do jogador para o mundo de destino
                WorldData worldData = plugin.getWorldManager().getWorldData(toWorldName);

                if (worldData != null) {
                    // Verificar se o jogador pode visitar
                    if (!worldData.canPlayerVisit(player.getUniqueId())) {
                        event.setCancelled(true);
                        player.sendMessage("§cVocê não tem permissão para visitar este mundo.");
                        return;
                    }

                    // Definir modo de jogo do jogador conforme configuração do mundo
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.setGameMode(worldData.getGameMode());
                    }, 1L);
                }
            }
        }
    }
}