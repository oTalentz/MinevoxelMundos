package br.com.minevoxel.mundos.commands;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final MinevoxelMundos plugin;
    private final List<String> subCommands = Arrays.asList(
            "criar", "listar", "carregar", "descarregar", "ir", "info", "config", "remover"
    );

    public CommandHandler(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessages().getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        // Se não tiver argumentos, abrir o menu principal
        if (args.length == 0) {
            plugin.getGuiHandler().openMainGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "criar":
                handleCreateCommand(player, args);
                break;
            case "listar":
                handleListCommand(player, args);
                break;
            case "carregar":
                handleLoadCommand(player, args);
                break;
            case "descarregar":
                handleUnloadCommand(player, args);
                break;
            case "ir":
                handleTeleportCommand(player, args);
                break;
            case "info":
                handleInfoCommand(player, args);
                break;
            case "config":
                handleConfigCommand(player, args);
                break;
            case "remover":
                handleRemoveCommand(player, args);
                break;
            default:
                // Comando desconhecido, mostrar ajuda
                showHelp(player);
                break;
        }

        return true;
    }

    private void handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.criar")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        // Verificar se o jogador pode criar mundos
        if (!plugin.getPlayerManager().canCreateWorld(player)) {
            int maxWorlds = plugin.getConfigManager().getMaxWorldsPerPlayer();
            player.sendMessage(plugin.getMessages().formatMessage("general.world-limit-reached", maxWorlds));
            return;
        }

        // Abrir GUI de criação de mundo
        plugin.getGuiHandler().openWorldCreationGUI(player);
    }

    private void handleListCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.listar")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        // Abrir GUI de lista de mundos
        plugin.getGuiHandler().openWorldListGUI(player);
    }

    private void handleLoadCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.admin.carregar")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /mundo carregar <nome>");
            return;
        }

        String worldName = args[1];

        if (!plugin.isWorldsServer()) {
            player.sendMessage(ChatColor.RED + "Este comando só pode ser usado no servidor de mundos!");
            return;
        }

        plugin.getWorldManager().fetchWorldData(worldName).thenAccept(worldData -> {
            if (worldData == null) {
                player.sendMessage(plugin.getMessages().formatMessage("general.world-not-found", worldName));
                return;
            }

            plugin.getWorldManager().getOrLoadWorld(worldName).thenAccept(world -> {
                player.sendMessage(plugin.getMessages().formatMessage("world-management.world-loaded", worldData.getDisplayName()));
            }).exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "Erro ao carregar mundo: " + ex.getMessage());
                return null;
            });
        });
    }

    private void handleUnloadCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.admin.descarregar")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /mundo descarregar <nome>");
            return;
        }

        String worldName = args[1];

        if (!plugin.isWorldsServer()) {
            player.sendMessage(ChatColor.RED + "Este comando só pode ser usado no servidor de mundos!");
            return;
        }

        boolean success = plugin.getWorldManager().unloadWorld(worldName, true);
        if (success) {
            player.sendMessage(plugin.getMessages().formatMessage("world-management.world-unloaded", worldName));
        } else {
            player.sendMessage(plugin.getMessages().formatMessage("world-management.world-not-loaded", worldName));
        }
    }

    private void handleTeleportCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.ir")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /mundo ir <nome>");
            return;
        }

        String worldName = args[1];

        if (plugin.isLobbyServer()) {
            // No servidor de lobby, conectar ao servidor de mundos
            player.sendMessage(plugin.getMessages().getMessage("general.connecting"));
            plugin.getServerManager().connectToWorldsServer(player, worldName);
        } else if (plugin.isWorldsServer()) {
            // No servidor de mundos, teleportar diretamente
            plugin.getWorldManager().fetchWorldData(worldName).thenAccept(worldData -> {
                if (worldData == null) {
                    player.sendMessage(plugin.getMessages().formatMessage("general.world-not-found", worldName));
                    return;
                }

                if (!worldData.canPlayerVisit(player.getUniqueId())) {
                    player.sendMessage(plugin.getMessages().formatMessage("world-management.world-access-denied", worldData.getDisplayName()));
                    return;
                }

                player.sendMessage(plugin.getMessages().formatMessage("general.teleporting", worldData.getDisplayName()));
                plugin.getTeleportManager().teleportToWorld(player, worldName);
            });
        }
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.info")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /mundo info <nome>");
            return;
        }

        String worldName = args[1];

        plugin.getDatabaseManager().getWorldByName(worldName).thenAccept(worldData -> {
            if (worldData == null) {
                player.sendMessage(plugin.getMessages().formatMessage("general.world-not-found", worldName));
                return;
            }

            boolean isLoaded = plugin.isWorldsServer() && plugin.getWorldManager().isWorldLoaded(worldName);
            String status = isLoaded ? "§a§lCARREGADO" : "§7§lDESCARREGADO";

            player.sendMessage(plugin.getMessages().formatMessage("world-management.world-info",
                    worldData.getDisplayName(),
                    String.valueOf(worldData.getId()),
                    worldData.getOwnerName(),
                    worldData.getCreationDate().toString(),
                    worldData.getLastAccessed().toString(),
                    worldData.getWorldType(),
                    worldData.getEnvironment(),
                    worldData.getGameMode().name(),
                    status));
        });
    }

    private void handleConfigCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.config")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /mundo config <nome>");
            return;
        }

        String worldName = args[1];

        plugin.getDatabaseManager().getWorldByName(worldName).thenAccept(worldData -> {
            if (worldData == null) {
                player.sendMessage(plugin.getMessages().formatMessage("general.world-not-found", worldName));
                return;
            }

            // Verificar se o jogador é o dono ou admin
            if (!worldData.getOwnerUUID().equals(player.getUniqueId()) &&
                    !player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
                player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
                return;
            }

            // Abrir GUI de edição
            plugin.getGuiHandler().openWorldEditGUI(player, worldData);
        });
    }

    private void handleRemoveCommand(Player player, String[] args) {
        if (!player.hasPermission("minevoxel.mundo.remover")) {
            player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /mundo remover <nome>");
            return;
        }

        String worldName = args[1];

        plugin.getDatabaseManager().getWorldByName(worldName).thenAccept(worldData -> {
            if (worldData == null) {
                player.sendMessage(plugin.getMessages().formatMessage("general.world-not-found", worldName));
                return;
            }

            // Verificar se o jogador é o dono ou admin
            if (!worldData.getOwnerUUID().equals(player.getUniqueId()) &&
                    !player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
                player.sendMessage(plugin.getMessages().getMessage("general.no-permission"));
                return;
            }

            if (plugin.isWorldsServer()) {
                plugin.getWorldManager().deleteWorld(worldName).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getMessages().formatMessage("world-management.world-deleted", worldData.getDisplayName()));
                    } else {
                        player.sendMessage(plugin.getMessages().formatMessage("general.error-occurred", "Falha ao excluir o mundo"));
                    }
                });
            } else {
                plugin.getDatabaseManager().deleteWorld(worldData.getId()).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getMessages().formatMessage("world-management.world-deleted", worldData.getDisplayName()));
                    } else {
                        player.sendMessage(plugin.getMessages().formatMessage("general.error-occurred", "Falha ao excluir o mundo"));
                    }
                });
            }
        });
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== MinevoxelMundos - Ajuda ===");
        player.sendMessage(ChatColor.YELLOW + "/mundo " + ChatColor.WHITE + "- Abre o menu principal");
        player.sendMessage(ChatColor.YELLOW + "/mundo criar " + ChatColor.WHITE + "- Cria um novo mundo");
        player.sendMessage(ChatColor.YELLOW + "/mundo listar " + ChatColor.WHITE + "- Lista seus mundos");
        player.sendMessage(ChatColor.YELLOW + "/mundo ir <nome> " + ChatColor.WHITE + "- Teleporta para um mundo");
        player.sendMessage(ChatColor.YELLOW + "/mundo info <nome> " + ChatColor.WHITE + "- Mostra informações de um mundo");
        player.sendMessage(ChatColor.YELLOW + "/mundo config <nome> " + ChatColor.WHITE + "- Configura um mundo");
        player.sendMessage(ChatColor.YELLOW + "/mundo remover <nome> " + ChatColor.WHITE + "- Remove um mundo");

        if (player.hasPermission("minevoxel.mundo.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/mundo carregar <nome> " + ChatColor.WHITE + "- Carrega um mundo");
            player.sendMessage(ChatColor.YELLOW + "/mundo descarregar <nome> " + ChatColor.WHITE + "- Descarrega um mundo");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            return subCommands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .filter(cmd -> {
                        switch (cmd) {
                            case "criar": return player.hasPermission("minevoxel.mundo.criar");
                            case "listar": return player.hasPermission("minevoxel.mundo.listar");
                            case "ir": return player.hasPermission("minevoxel.mundo.ir");
                            case "info": return player.hasPermission("minevoxel.mundo.info");
                            case "config": return player.hasPermission("minevoxel.mundo.config");
                            case "remover": return player.hasPermission("minevoxel.mundo.remover");
                            case "carregar": return player.hasPermission("minevoxel.mundo.admin.carregar");
                            case "descarregar": return player.hasPermission("minevoxel.mundo.admin.descarregar");
                            default: return false;
                        }
                    })
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}