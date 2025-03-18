package br.com.minevoxel.mundos.gui;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class MainWorldGUI {

    private final MinevoxelMundos plugin;
    private final Player player;
    private Inventory inventory;

    // Mapeamento de slots para ações
    private final Map<Integer, Runnable> clickActions = new HashMap<>();

    public MainWorldGUI(MinevoxelMundos plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Inicializar inventário
        createInventory();
    }

    private void createInventory() {
        inventory = Bukkit.createInventory(null, 27, "§8Menu de Mundos");
        updateInventory();
    }

    private void updateInventory() {
        inventory.clear();
        clickActions.clear();

        // Item para criar mundo
        ItemStack createWorld = new ItemBuilder(Material.GRASS_BLOCK)
                .setName("§a§lCriar Novo Mundo")
                .addLore("§7Clique para criar um novo mundo")
                .addLore("§7privado só para você.")
                .build();

        // Item para listar mundos
        ItemStack listWorlds = new ItemBuilder(Material.BOOK)
                .setName("§a§lMeus Mundos")
                .addLore("§7Clique para ver a lista de")
                .addLore("§7seus mundos.")
                .build();

        // Item para mundos acessíveis
        ItemStack accessibleWorlds = new ItemBuilder(Material.ENDER_EYE)
                .setName("§a§lMundos Acessíveis")
                .addLore("§7Clique para ver a lista de")
                .addLore("§7mundos que você pode acessar.")
                .build();

        // Item para configurações
        ItemStack settings = new ItemBuilder(Material.REDSTONE)
                .setName("§c§lConfigurações")
                .addLore("§7Clique para acessar as")
                .addLore("§7configurações.")
                .build();

        // Posicionar itens no inventário
        inventory.setItem(10, createWorld);
        inventory.setItem(12, listWorlds);
        inventory.setItem(14, accessibleWorlds);
        inventory.setItem(16, settings);

        // Configurar ações
        clickActions.put(10, this::openCreateWorldMenu);
        clickActions.put(12, this::openMyWorldsMenu);
        clickActions.put(14, this::openAccessibleWorldsMenu);
        clickActions.put(16, this::openSettingsMenu);

        // Preencher slots vazios
        ItemStack emptyItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .build();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, emptyItem);
            }
        }
    }

    private void openCreateWorldMenu() {
        player.closeInventory();
        plugin.getGuiHandler().openWorldCreationGUI(player);
    }

    private void openMyWorldsMenu() {
        player.closeInventory();
        plugin.getGuiHandler().openWorldListGUI(player, WorldListGUI.ListType.MY_WORLDS);
    }

    private void openAccessibleWorldsMenu() {
        player.closeInventory();
        plugin.getGuiHandler().openWorldListGUI(player, WorldListGUI.ListType.ACCESSIBLE_WORLDS);
    }

    private void openSettingsMenu() {
        // Implementar configurações futuramente
        player.sendMessage("§aFuncionalidade em breve!");
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