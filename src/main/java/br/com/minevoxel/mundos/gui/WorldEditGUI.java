package br.com.minevoxel.mundos.gui;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import br.com.minevoxel.mundos.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class WorldEditGUI {

    private final MinevoxelMundos plugin;
    private final Player player;
    private final WorldData worldData;
    private Inventory inventory;

    // Mapeamento de slots para ações
    private final Map<Integer, Runnable> clickActions = new HashMap<>();

    public WorldEditGUI() {
        this.plugin = MinevoxelMundos.getInstance();
        this.player = null;
        this.worldData = null;
    }

    public WorldEditGUI(MinevoxelMundos plugin, Player player, WorldData worldData) {
        this.plugin = plugin;
        this.player = player;
        this.worldData = worldData;

        // Inicializar inventário
        createInventory();
    }

    private void createInventory() {
        inventory = Bukkit.createInventory(null, 54, "§8Editar Mundo: " + worldData.getDisplayName());
        updateInventory();
    }

    private void updateInventory() {
        inventory.clear();
        clickActions.clear();

        // Informações básicas do mundo
        addInfoItems();

        // Configurações gerais
        addGeneralSettings();

        // Configurações de física
        addPhysicsSettings();

        // Configurações de permissões
        addPermissionSettings();

        // Ações de mundo
        addWorldActions();

        // Botões de navegação
        addNavigationButtons();

        // Preencher espaços vazios
        fillEmptySlots();
    }

    private void addInfoItems() {
        // Mostrar informações do mundo
        ItemStack worldInfo = new ItemBuilder(Material.NAME_TAG)
                .setName("§a§lInformações do Mundo")
                .addLore("§7Nome: §f" + worldData.getWorldName())
                .addLore("§7ID: §f" + worldData.getId())
                .addLore("§7Nome de Exibição: §f" + worldData.getDisplayName())
                .addLore("§7Proprietário: §f" + worldData.getOwnerName())
                .addLore("")
                .addLore("§7Clique para editar o nome de exibição")
                .build();

        inventory.setItem(4, worldInfo);

        clickActions.put(4, () -> {
            player.closeInventory();
            plugin.getGuiHandler().startAnvilInput(player, "Editar Nome", worldData.getDisplayName(), (newName) -> {
                if (newName != null && !newName.isEmpty()) {
                    worldData.setDisplayName(newName);
                    updateWorld();
                }
                openInventory();
            });
        });
    }

    private void addGeneralSettings() {
        // Configuração de PVP
        ItemStack pvpItem = new ItemBuilder(Material.IRON_SWORD)
                .setName("§a§lPVP")
                .addLore("§7Estado atual: " + (worldData.isPvp() ? "§a§lATIVADO" : "§c§lDESATIVADO"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isPvp() ? "desativar" : "ativar"))
                .build();

        // Configuração de modo de jogo
        ItemStack gameModeItem = new ItemBuilder(Material.DIAMOND_PICKAXE)
                .setName("§a§lModo de Jogo")
                .addLore("§7Modo atual: §f" + formatGameMode(worldData.getGameMode()))
                .addLore("")
                .addLore("§7Clique para alterar")
                .build();

        // Configuração de visibilidade
        ItemStack visibilityItem = new ItemBuilder(Material.ENDER_EYE)
                .setName("§a§lVisibilidade")
                .addLore("§7Estado atual: " + (worldData.isPublic() ? "§a§lPÚBLICO" : "§c§lPRIVADO"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isPublic() ? "tornar privado" : "tornar público"))
                .build();

        // Posicionar itens
        inventory.setItem(19, pvpItem);
        inventory.setItem(20, gameModeItem);
        inventory.setItem(21, visibilityItem);

        // Configurar ações
        clickActions.put(19, () -> {
            worldData.setPvp(!worldData.isPvp());
            updateWorld();
            updateInventory();
        });

        clickActions.put(20, this::cycleGameMode);

        clickActions.put(21, () -> {
            worldData.setPublic(!worldData.isPublic());
            updateWorld();
            updateInventory();
        });
    }

    private void addPhysicsSettings() {
        // Física geral
        ItemStack physicsItem = new ItemBuilder(Material.CLOCK)
                .setName("§a§lFísica")
                .addLore("§7Estado atual: " + (worldData.isPhysics() ? "§a§lATIVADA" : "§c§lDESATIVADA"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isPhysics() ? "desativar" : "ativar"))
                .build();

        // Fluxo de água
        ItemStack waterFlowItem = new ItemBuilder(Material.WATER_BUCKET)
                .setName("§a§lFluxo de Água")
                .addLore("§7Estado atual: " + (worldData.isWaterFlow() ? "§a§lATIVADO" : "§c§lDESATIVADO"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isWaterFlow() ? "desativar" : "ativar"))
                .build();

        // Fluxo de lava
        ItemStack lavaFlowItem = new ItemBuilder(Material.LAVA_BUCKET)
                .setName("§a§lFluxo de Lava")
                .addLore("§7Estado atual: " + (worldData.isLavaFlow() ? "§a§lATIVADO" : "§c§lDESATIVADO"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isLavaFlow() ? "desativar" : "ativar"))
                .build();

        // Propagação de fogo
        ItemStack fireSpreadItem = new ItemBuilder(Material.FLINT_AND_STEEL)
                .setName("§a§lPropagação de Fogo")
                .addLore("§7Estado atual: " + (worldData.isFireSpread() ? "§a§lATIVADA" : "§c§lDESATIVADA"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isFireSpread() ? "desativar" : "ativar"))
                .build();

        // Queda de folhas
        ItemStack leafDecayItem = new ItemBuilder(Material.OAK_LEAVES)
                .setName("§a§lQueda de Folhas")
                .addLore("§7Estado atual: " + (worldData.isLeafDecay() ? "§a§lATIVADA" : "§c§lDESATIVADA"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isLeafDecay() ? "desativar" : "ativar"))
                .build();

        // Redstone
        ItemStack redstoneItem = new ItemBuilder(Material.REDSTONE)
                .setName("§a§lRedstone")
                .addLore("§7Estado atual: " + (worldData.isRedstone() ? "§a§lATIVADA" : "§c§lDESATIVADA"))
                .addLore("")
                .addLore("§7Clique para " + (worldData.isRedstone() ? "desativar" : "ativar"))
                .build();

        // Posicionar itens
        inventory.setItem(23, physicsItem);
        inventory.setItem(24, waterFlowItem);
        inventory.setItem(25, lavaFlowItem);
        inventory.setItem(32, fireSpreadItem);
        inventory.setItem(33, leafDecayItem);
        inventory.setItem(34, redstoneItem);

        // Configurar ações
        clickActions.put(23, () -> {
            worldData.setPhysics(!worldData.isPhysics());
            updateWorld();
            updateInventory();
        });

        clickActions.put(24, () -> {
            worldData.setWaterFlow(!worldData.isWaterFlow());
            updateWorld();
            updateInventory();
        });

        clickActions.put(25, () -> {
            worldData.setLavaFlow(!worldData.isLavaFlow());
            updateWorld();
            updateInventory();
        });

        clickActions.put(32, () -> {
            worldData.setFireSpread(!worldData.isFireSpread());
            updateWorld();
            updateInventory();
        });

        clickActions.put(33, () -> {
            worldData.setLeafDecay(!worldData.isLeafDecay());
            updateWorld();
            updateInventory();
        });

        clickActions.put(34, () -> {
            worldData.setRedstone(!worldData.isRedstone());
            updateWorld();
            updateInventory();
        });
    }

    private void addPermissionSettings() {
        // Gerenciar permissões
        ItemStack permissionsItem = new ItemBuilder(Material.PLAYER_HEAD)
                .setName("§a§lGerenciar Permissões")
                .addLore("§7Clique para gerenciar as permissões")
                .addLore("§7de jogadores neste mundo.")
                .build();

        inventory.setItem(40, permissionsItem);

        clickActions.put(40, () -> {
            player.closeInventory();
            plugin.getGuiHandler().openWorldPermissionsGUI(player, worldData);
        });
    }

    private void addWorldActions() {
        // Teleportar para o mundo
        ItemStack teleportItem = new ItemBuilder(Material.ENDER_PEARL)
                .setName("§a§lTeleportar")
                .addLore("§7Clique para teleportar para")
                .addLore("§7este mundo.")
                .build();

        // Excluir mundo (apenas para o proprietário)
        ItemStack deleteItem = new ItemBuilder(Material.BARRIER)
                .setName("§c§lExcluir Mundo")
                .addLore("§7Clique para excluir este mundo.")
                .addLore("§c§lATENÇÃO: Esta ação é irreversível!")
                .build();

        inventory.setItem(38, teleportItem);

        // Apenas mostrar botão de exclusão para o proprietário
        if (player.getUniqueId().equals(worldData.getOwnerUUID()) ||
                player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
            inventory.setItem(42, deleteItem);

            clickActions.put(42, this::confirmDeleteWorld);
        }

        clickActions.put(38, () -> {
            player.closeInventory();

            if (plugin.isLobbyServer()) {
                plugin.getServerManager().connectToWorldsServer(player, worldData.getWorldName());
            } else if (plugin.isWorldsServer()) {
                plugin.getTeleportManager().teleportToWorld(player, worldData.getWorldName());
            }
        });
    }

    private void addNavigationButtons() {
        // Botão para voltar
        ItemStack backButton = new ItemBuilder(Material.ARROW)
                .setName("§c§lVoltar")
                .addLore("§7Clique para voltar à lista de mundos.")
                .build();

        inventory.setItem(45, backButton);

        clickActions.put(45, () -> {
            player.closeInventory();
            plugin.getGuiHandler().openWorldListGUI(player);
        });
    }

    private void fillEmptySlots() {
        ItemStack emptyItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .build();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, emptyItem);
            }
        }
    }

    private void cycleGameMode() {
        GameMode currentMode = worldData.getGameMode();
        GameMode newMode;

        switch (currentMode) {
            case SURVIVAL:
                newMode = GameMode.CREATIVE;
                break;
            case CREATIVE:
                newMode = GameMode.ADVENTURE;
                break;
            case ADVENTURE:
                newMode = GameMode.SURVIVAL;
                break;
            default:
                newMode = GameMode.SURVIVAL;
        }

        worldData.setGameMode(newMode);
        updateWorld();
        updateInventory();
    }

    private String formatGameMode(GameMode gameMode) {
        switch (gameMode) {
            case SURVIVAL:
                return "Sobrevivência";
            case CREATIVE:
                return "Criativo";
            case ADVENTURE:
                return "Aventura";
            case SPECTATOR:
                return "Espectador";
            default:
                return gameMode.name();
        }
    }

    private void confirmDeleteWorld() {
        // Confirmar exclusão com uma nova GUI
        Inventory confirmInventory = Bukkit.createInventory(null, 27, "§c§lConfirmar Exclusão");

        // Informações sobre o mundo
        ItemStack worldInfo = new ItemBuilder(Material.GRASS_BLOCK)
                .setName("§c§lExcluir: §f" + worldData.getDisplayName())
                .addLore("§7Nome: §f" + worldData.getWorldName())
                .addLore("§7ID: §f" + worldData.getId())
                .addLore("")
                .addLore("§c§lAVISO: Esta ação não pode ser desfeita!")
                .addLore("§c§lTodos os dados do mundo serão perdidos!")
                .build();

        // Botão de confirmação
        ItemStack confirmButton = new ItemBuilder(Material.LIME_WOOL)
                .setName("§a§lCONFIRMAR EXCLUSÃO")
                .addLore("§7Clique para confirmar a exclusão")
                .addLore("§7do mundo permanentemente.")
                .build();

        // Botão de cancelamento
        ItemStack cancelButton = new ItemBuilder(Material.RED_WOOL)
                .setName("§c§lCANCELAR")
                .addLore("§7Clique para cancelar a exclusão")
                .addLore("§7e voltar à tela anterior.")
                .build();

        // Posicionar itens
        confirmInventory.setItem(13, worldInfo);
        confirmInventory.setItem(11, confirmButton);
        confirmInventory.setItem(15, cancelButton);

        // Preencher espaços vazios
        ItemStack emptyItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                .setName(" ")
                .build();

        for (int i = 0; i < confirmInventory.getSize(); i++) {
            if (confirmInventory.getItem(i) == null) {
                confirmInventory.setItem(i, emptyItem);
            }
        }

        // Abrir inventário de confirmação
        player.closeInventory();
        player.openInventory(confirmInventory);

        // Registrar manipulador de cliques para este inventário
        plugin.getGuiHandler().registerDeleteConfirmGui(player, confirmInventory, worldData, this);
    }

    private void updateWorld() {
        // Atualizar mundo no banco de dados
        plugin.getDatabaseManager().updateWorld(worldData);

        // Se estivermos no servidor de mundos e o mundo estiver carregado, atualizar configurações
        if (plugin.isWorldsServer() && plugin.getWorldManager().isWorldLoaded(worldData.getWorldName())) {
            plugin.getWorldManager().updateWorldSettings(worldData);
        } else if (plugin.isLobbyServer()) {
            // Se estivermos no lobby, enviar mensagem para atualizar no servidor de mundos
            plugin.getMessageChannels().sendWorldUpdateMessage(worldData.getId());
        }
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (clickActions.containsKey(slot)) {
            clickActions.get(slot).run();
        }
    }

    public void openInventory() {
        player.openInventory(inventory);
    }
}