package br.com.minevoxel.mundos.gui;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import br.com.minevoxel.mundos.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GUIHandler implements Listener {

    private final MinevoxelMundos plugin;

    // Mapeamento de jogadores para GUIs
    private final Map<UUID, Object> playerGuis = new HashMap<>();
    private final Map<UUID, AnvilGUI> anvilGuis = new HashMap<>();
    private final Map<UUID, Consumer<String>> anvilCallbacks = new HashMap<>();

    // Mapas para gerenciar GUIs especiais
    private final Map<UUID, Consumer<String>> chatInputListeners = new ConcurrentHashMap<>();
    private final Map<UUID, DeleteConfirmData> deleteConfirmGuis = new HashMap<>();
    private final Map<UUID, PermissionSelectionData> permissionGuis = new HashMap<>();

    public GUIHandler(MinevoxelMundos plugin) {
        this.plugin = plugin;

        // Registrar este manipulador como um listener de eventos
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // Métodos para abrir GUIs

    public void openMainGUI(Player player) {
        MainWorldGUI gui = new MainWorldGUI(plugin, player);
        playerGuis.put(player.getUniqueId(), gui);
        gui.openInventory();
    }

    public void openWorldCreationGUI(Player player) {
        WorldCreationGUI gui = new WorldCreationGUI(plugin, player);
        playerGuis.put(player.getUniqueId(), gui);
        gui.openInventory();
    }

    public void openWorldListGUI(Player player) {
        WorldListGUI gui = new WorldListGUI(plugin, player);
        playerGuis.put(player.getUniqueId(), gui);
        gui.openInventory();
    }

    public void openWorldListGUI(Player player, WorldListGUI.ListType listType) {
        WorldListGUI gui = new WorldListGUI(plugin, player, listType);
        playerGuis.put(player.getUniqueId(), gui);
        gui.openInventory();
    }

    public void openWorldEditGUI(Player player, WorldData worldData) {
        WorldEditGUI gui = new WorldEditGUI(plugin, player, worldData);
        playerGuis.put(player.getUniqueId(), gui);
        gui.openInventory();
    }

    public void openWorldPermissionsGUI(Player player, WorldData worldData) {
        WorldPermissionsGUI gui = new WorldPermissionsGUI(plugin, player, worldData);
        playerGuis.put(player.getUniqueId(), gui);
        gui.openInventory();
    }

    // Métodos para registrar GUIs especiais

    public void registerDeleteConfirmGui(Player player, Inventory inventory, WorldData worldData, WorldEditGUI previousGui) {
        UUID playerUuid = player.getUniqueId();
        DeleteConfirmData data = new DeleteConfirmData(inventory, worldData, previousGui);
        deleteConfirmGuis.put(playerUuid, data);

        // Remover após 30 segundos se não for utilizado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DeleteConfirmData storedData = deleteConfirmGuis.get(playerUuid);
            if (storedData != null && storedData.equals(data)) {
                deleteConfirmGuis.remove(playerUuid);
                if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                    player.closeInventory();
                    player.sendMessage("§cTempo esgotado para confirmar a exclusão.");
                }
            }
        }, 600L); // 30 segundos
    }

    public void registerPermissionSelectionGui(Player player, Inventory inventory, UUID targetUUID, WorldData worldData, WorldPermissionsGUI previousGui) {
        UUID playerUuid = player.getUniqueId();
        PermissionSelectionData data = new PermissionSelectionData(inventory, targetUUID, worldData, previousGui);
        permissionGuis.put(playerUuid, data);

        // Remover após 30 segundos se não for utilizado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PermissionSelectionData storedData = permissionGuis.get(playerUuid);
            if (storedData != null && storedData.equals(data)) {
                permissionGuis.remove(playerUuid);
                if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                    player.closeInventory();
                    player.sendMessage("§cTempo esgotado para selecionar permissão.");
                    previousGui.openInventory();
                }
            }
        }, 600L); // 30 segundos
    }

    public void registerPlayerInputListener(Player player, Consumer<String> callback) {
        UUID playerUuid = player.getUniqueId();
        chatInputListeners.put(playerUuid, callback);

        // Mensagem de instrução
        player.sendMessage("§aDigite sua resposta no chat ou 'cancelar' para cancelar.");

        // Remover após 60 segundos se não for utilizado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chatInputListeners.containsKey(playerUuid)) {
                chatInputListeners.remove(playerUuid);
                player.sendMessage("§cTempo esgotado para entrada de texto.");
            }
        }, 1200L); // 60 segundos
    }

    // Métodos para manipular input do jogador via GUI de Anvil

    public void startAnvilInput(Player player, String title, String initialText, Consumer<String> callback) {
        // Fechar qualquer inventário aberto
        player.closeInventory();

        // Criar nova GUI de Anvil
        AnvilGUI anvilGUI = new AnvilGUI(plugin, player, title, initialText, callback);
        anvilGuis.put(player.getUniqueId(), anvilGUI);
        anvilCallbacks.put(player.getUniqueId(), callback);

        // Abrir GUI
        anvilGUI.open();
    }

    // Event handlers

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            UUID playerUuid = player.getUniqueId();

            // Verificar se o jogador tem uma GUI de confirmação de exclusão aberta
            if (deleteConfirmGuis.containsKey(playerUuid)) {
                DeleteConfirmData data = deleteConfirmGuis.get(playerUuid);
                if (event.getInventory().equals(data.getInventory())) {
                    event.setCancelled(true);
                    handleDeleteConfirmClick(event, player, data);
                    return;
                }
            }

            // Verificar se o jogador tem uma GUI de seleção de permissão aberta
            if (permissionGuis.containsKey(playerUuid)) {
                PermissionSelectionData data = permissionGuis.get(playerUuid);
                if (event.getInventory().equals(data.getInventory())) {
                    event.setCancelled(true);
                    handlePermissionSelectionClick(event, player, data);
                    return;
                }
            }

            // Verificar se o jogador tem uma GUI aberta
            if (playerGuis.containsKey(playerUuid)) {
                Object gui = playerGuis.get(playerUuid);

                // Encaminhar o evento para a GUI correta
                if (gui instanceof MainWorldGUI) {
                    ((MainWorldGUI) gui).handleClick(event);
                } else if (gui instanceof WorldCreationGUI) {
                    ((WorldCreationGUI) gui).handleClick(event);
                } else if (gui instanceof WorldListGUI) {
                    ((WorldListGUI) gui).handleClick(event);
                } else if (gui instanceof WorldEditGUI) {
                    ((WorldEditGUI) gui).handleClick(event);
                } else if (gui instanceof WorldPermissionsGUI) {
                    ((WorldPermissionsGUI) gui).handleClick(event);
                }
            }

            // Verificar se o jogador tem uma GUI de Anvil aberta
            if (anvilGuis.containsKey(playerUuid)) {
                AnvilGUI anvilGUI = anvilGuis.get(playerUuid);
                anvilGUI.handleClick(event);
            }
        }
    }

    private void handleDeleteConfirmClick(InventoryClickEvent event, Player player, DeleteConfirmData data) {
        int slot = event.getRawSlot();

        if (slot == 11) {
            // Botão de confirmar
            player.closeInventory();
            deleteConfirmGuis.remove(player.getUniqueId());

            // Processar exclusão
            WorldData worldData = data.getWorldData();

            player.sendMessage("§aIniciando exclusão do mundo " + worldData.getDisplayName() + "...");

            // Verificar se o jogador é o proprietário ou um administrador
            if (!player.getUniqueId().equals(worldData.getOwnerUUID()) &&
                    !player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
                player.sendMessage("§cVocê não tem permissão para excluir este mundo.");
                return;
            }

            // Excluir mundo
            if (plugin.isWorldsServer() && plugin.getWorldManager() != null) {
                // Se estamos no servidor de mundos, usar WorldManager
                plugin.getWorldManager().deleteWorld(worldData.getWorldName()).thenAccept(success -> {
                    if (success) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§aMundo excluído com sucesso!");
                            plugin.getGuiHandler().openWorldListGUI(player);
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§cFalha ao excluir mundo!");
                        });
                    }
                });
            } else {
                // Se estamos no lobby, excluir apenas do banco de dados
                plugin.getDatabaseManager().deleteWorld(worldData.getId()).thenAccept(success -> {
                    if (success) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§aMundo excluído com sucesso!");
                            plugin.getGuiHandler().openWorldListGUI(player);
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§cFalha ao excluir mundo!");
                        });
                    }
                });
            }

        } else if (slot == 15) {
            // Botão de cancelar
            player.closeInventory();
            deleteConfirmGuis.remove(player.getUniqueId());
            data.getPreviousGui().openInventory();
        }
    }

    private void handlePermissionSelectionClick(InventoryClickEvent event, Player player, PermissionSelectionData data) {
        int slot = event.getRawSlot();

        if (slot == 3 || slot == 5) {
            // Slot 3: Construtor, Slot 5: Visitante
            String newPermission = (slot == 3) ? "BUILDER" : "VISITOR";

            // Atualizar permissão
            data.getWorldData().addPlayerPermission(data.getTargetUUID(), newPermission);

            // Atualizar no banco de dados
            plugin.getDatabaseManager().updateWorld(data.getWorldData());

            // Fechar inventário e voltar à tela anterior
            player.closeInventory();
            permissionGuis.remove(player.getUniqueId());

            // Notificar jogador
            player.sendMessage("§aPermissão atualizada para " + (newPermission.equals("BUILDER") ? "Construtor" : "Visitante"));

            // Abrir GUI anterior
            data.getPreviousGui().openInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Verificar se o jogador está em modo de entrada de texto
        if (chatInputListeners.containsKey(playerUuid)) {
            event.setCancelled(true);
            String message = event.getMessage();

            // Verificar se o jogador quer cancelar
            if (message.equalsIgnoreCase("cancelar")) {
                chatInputListeners.remove(playerUuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§cOperação cancelada.");
                });
                return;
            }

            // Obter callback e remover do mapa
            Consumer<String> callback = chatInputListeners.remove(playerUuid);

            // Executar callback na thread principal
            Bukkit.getScheduler().runTask(plugin, () -> {
                callback.accept(message);
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID playerUuid = player.getUniqueId();

            // Verificar se a GUI fechada é uma GUI de Anvil
            if (anvilGuis.containsKey(playerUuid)) {
                AnvilGUI anvilGUI = anvilGuis.get(playerUuid);

                // Verificar se o inventário fechado é o da GUI de Anvil
                if (event.getInventory().equals(anvilGUI.getInventory())) {
                    // Limpar referências
                    anvilGuis.remove(playerUuid);
                    anvilCallbacks.remove(playerUuid);
                }
            } else {
                // Verificar se a GUI fechada é uma GUI de confirmação de exclusão
                if (deleteConfirmGuis.containsKey(playerUuid) &&
                        deleteConfirmGuis.get(playerUuid).getInventory().equals(event.getInventory())) {
                    deleteConfirmGuis.remove(playerUuid);
                }

                // Verificar se a GUI fechada é uma GUI de seleção de permissão
                if (permissionGuis.containsKey(playerUuid) &&
                        permissionGuis.get(playerUuid).getInventory().equals(event.getInventory())) {
                    permissionGuis.remove(playerUuid);
                }

                // Programar uma verificação atrasada para manter GUIs que estão sendo substituídas
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Inventory topInventory = player.getOpenInventory().getTopInventory();
                    if (topInventory == null || topInventory.getHolder() == null) {
                        // Se o jogador não tem mais inventário aberto, remover a referência
                        playerGuis.remove(playerUuid);
                    }
                }, 2L);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Limpar todas as referências quando o jogador sair
        playerGuis.remove(playerUuid);
        anvilGuis.remove(playerUuid);
        anvilCallbacks.remove(playerUuid);
        chatInputListeners.remove(playerUuid);
        deleteConfirmGuis.remove(playerUuid);
        permissionGuis.remove(playerUuid);
    }

    // Classes auxiliares

    private static class DeleteConfirmData {
        private final Inventory inventory;
        private final WorldData worldData;
        private final WorldEditGUI previousGui;

        public DeleteConfirmData(Inventory inventory, WorldData worldData, WorldEditGUI previousGui) {
            this.inventory = inventory;
            this.worldData = worldData;
            this.previousGui = previousGui;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public WorldData getWorldData() {
            return worldData;
        }

        public WorldEditGUI getPreviousGui() {
            return previousGui;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DeleteConfirmData) {
                DeleteConfirmData other = (DeleteConfirmData) obj;
                return this.inventory.equals(other.inventory) && this.worldData.getId() == other.worldData.getId();
            }
            return false;
        }
    }

    private static class PermissionSelectionData {
        private final Inventory inventory;
        private final UUID targetUUID;
        private final WorldData worldData;
        private final WorldPermissionsGUI previousGui;

        public PermissionSelectionData(Inventory inventory, UUID targetUUID, WorldData worldData, WorldPermissionsGUI previousGui) {
            this.inventory = inventory;
            this.targetUUID = targetUUID;
            this.worldData = worldData;
            this.previousGui = previousGui;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public UUID getTargetUUID() {
            return targetUUID;
        }

        public WorldData getWorldData() {
            return worldData;
        }

        public WorldPermissionsGUI getPreviousGui() {
            return previousGui;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PermissionSelectionData) {
                PermissionSelectionData other = (PermissionSelectionData) obj;
                return this.inventory.equals(other.inventory) &&
                        this.targetUUID.equals(other.targetUUID) &&
                        this.worldData.getId() == other.worldData.getId();
            }
            return false;
        }
    }

    // Classe interna para representar uma GUI de Anvil
    private class AnvilGUI {
        private final MinevoxelMundos plugin;
        private final Player player;
        private final String title;
        private final String initialText;
        private final Consumer<String> callback;
        private Inventory inventory;

        public AnvilGUI(MinevoxelMundos plugin, Player player, String title, String initialText, Consumer<String> callback) {
            this.plugin = plugin;
            this.player = player;
            this.title = title;
            this.initialText = initialText;
            this.callback = callback;
        }

        public void open() {
            // Implementação para abrir uma GUI de Anvil
            // Nota: Esta é uma implementação simplificada, na prática você usaria
            // uma biblioteca como ProtocolLib ou NMS para criar uma verdadeira GUI de Anvil

            // Para este exemplo, usaremos um inventário normal
            inventory = Bukkit.createInventory(null, 9, title);

            // Adicionar item com o texto inicial
            ItemStack inputItem = new ItemStack(Material.PAPER);
            ItemMeta meta = inputItem.getItemMeta();
            meta.setDisplayName(initialText);
            inputItem.setItemMeta(meta);

            // Adicionar itens para confirmar/cancelar
            ItemStack confirmItem = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                    .setName("§a§lConfirmar")
                    .build();

            ItemStack cancelItem = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .setName("§c§lCancelar")
                    .build();

            // Posicionar itens no inventário
            inventory.setItem(4, inputItem);
            inventory.setItem(3, confirmItem);
            inventory.setItem(5, cancelItem);

            // Abrir inventário para o jogador
            player.openInventory(inventory);
        }

        public void handleClick(InventoryClickEvent event) {
            if (event.getInventory().equals(inventory)) {
                event.setCancelled(true);

                int slot = event.getRawSlot();
                if (slot == 3) {
                    // Botão Confirmar
                    ItemStack inputItem = inventory.getItem(4);
                    String input = inputItem != null && inputItem.hasItemMeta()
                            ? inputItem.getItemMeta().getDisplayName()
                            : initialText;

                    player.closeInventory();
                    callback.accept(input);
                } else if (slot == 5) {
                    // Botão Cancelar
                    player.closeInventory();
                    callback.accept(null);
                } else if (slot == 4) {
                    // Item de entrada
                    // Como não podemos implementar uma GUI de Anvil real aqui,
                    // vamos usar o sistema de chat para capturar o input
                    player.closeInventory();

                    player.sendMessage("§aDigite o novo texto no chat:");
                    player.sendMessage("§7Texto atual: §f" + initialText);

                    // Registrar listener de chat
                    registerPlayerInputListener(player, callback);
                }
            }
        }

        public Inventory getInventory() {
            return inventory;
        }
    }
}