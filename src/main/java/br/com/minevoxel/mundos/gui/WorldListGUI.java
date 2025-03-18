package br.com.minevoxel.mundos.gui;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import br.com.minevoxel.mundos.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.GameMode;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WorldListGUI {

    private final MinevoxelMundos plugin;
    private final Player player;
    private Inventory inventory;

    private List<WorldData> worlds = new ArrayList<>();
    private int currentPage = 0;
    private final int pageSize = 36; // Slots 0-35 para mundos (4 linhas)
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    // Mapeamento de slots para mundos e ações
    private Map<Integer, WorldData> worldSlots = new HashMap<>();
    private Map<Integer, Runnable> actionSlots = new HashMap<>();

    // Tipo de lista
    private ListType listType = ListType.MY_WORLDS;

    public enum ListType {
        MY_WORLDS,
        ACCESSIBLE_WORLDS,
        ALL_WORLDS // Apenas para administradores
    }

    public WorldListGUI(MinevoxelMundos plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Inicializar inventário
        createInventory();
        loadWorlds();
    }

    public WorldListGUI(MinevoxelMundos plugin, Player player, ListType listType) {
        this.plugin = plugin;
        this.player = player;
        this.listType = listType;

        // Inicializar inventário
        createInventory();
        loadWorlds();
    }

    private void createInventory() {
        String title = switch (listType) {
            case MY_WORLDS -> "§8Meus Mundos";
            case ACCESSIBLE_WORLDS -> "§8Mundos Acessíveis";
            case ALL_WORLDS -> "§8Todos os Mundos";
        };

        inventory = Bukkit.createInventory(null, 54, title);
    }

    private void loadWorlds() {
        // Limpar dados existentes
        worlds.clear();

        // Mostrar mensagem de carregamento
        updateLoadingInventory();

        // Carregar mundos com base no tipo de lista
        CompletableFuture<List<WorldData>> worldsFuture;

        switch (listType) {
            case MY_WORLDS:
                worldsFuture = plugin.getDatabaseManager().getWorldsByOwner(player.getUniqueId());
                break;
            case ACCESSIBLE_WORLDS:
                worldsFuture = plugin.getDatabaseManager().getAccessibleWorlds(player.getUniqueId());
                break;
            case ALL_WORLDS:
                if (player.hasPermission("minevoxel.admin")) {
                    // Implementar método para buscar todos os mundos
                    worldsFuture = CompletableFuture.completedFuture(new ArrayList<>());
                } else {
                    // Jogador sem permissão, mostrar apenas os acessíveis
                    worldsFuture = plugin.getDatabaseManager().getAccessibleWorlds(player.getUniqueId());
                }
                break;
            default:
                worldsFuture = CompletableFuture.completedFuture(new ArrayList<>());
        }

        worldsFuture.thenAccept(loadedWorlds -> {
            worlds = loadedWorlds;

            // Classificar mundos (mais recentemente acessados primeiro)
            worlds.sort((w1, w2) -> w2.getLastAccessed().compareTo(w1.getLastAccessed()));

            // Atualizar inventário
            updateInventory();
        });
    }

    private void updateLoadingInventory() {
        inventory.clear();

        // Item de carregamento
        ItemStack loadingItem = new ItemBuilder(Material.HOPPER)
                .setName("§eCarregando mundos...")
                .addLore("§7Por favor, aguarde enquanto")
                .addLore("§7carregamos seus mundos.")
                .build();

        inventory.setItem(22, loadingItem);

        // Preencher espaços vazios
        fillEmptySlots();
    }

    private void updateInventory() {
        inventory.clear();
        worldSlots.clear();
        actionSlots.clear();

        // Adicionar mundos
        addWorldItems();

        // Adicionar botões de navegação
        addNavigationButtons();

        // Adicionar botões de ação
        addActionButtons();

        // Preencher espaços vazios
        fillEmptySlots();
    }

    private void addWorldItems() {
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, worlds.size());

        if (startIndex >= worlds.size()) {
            // Página atual não tem mundos, voltar para a primeira página
            currentPage = 0;
            startIndex = 0;
            endIndex = Math.min(pageSize, worlds.size());
        }

        // Verificar se há mundos para exibir
        if (worlds.isEmpty()) {
            ItemStack noWorldsItem = new ItemBuilder(Material.BARRIER)
                    .setName("§cNenhum mundo encontrado")
                    .addLore("§7Você não possui mundos criados.")
                    .addLore("")
                    .addLore("§7Clique no botão §a§lCriar Mundo")
                    .addLore("§7para criar seu primeiro mundo!")
                    .build();

            inventory.setItem(22, noWorldsItem);
            return;
        }

        // Adicionar itens de mundo
        for (int i = startIndex; i < endIndex; i++) {
            WorldData world = worlds.get(i);
            int slot = i - startIndex;

            Material material;
            if (world.getOwnerUUID().equals(player.getUniqueId())) {
                // Mundo do jogador
                material = Material.GRASS_BLOCK;
            } else if (world.isPublic()) {
                // Mundo público
                material = Material.OAK_SIGN;
            } else {
                // Mundo compartilhado
                material = Material.CHEST;
            }

            // Identificar o tipo de permissão do jogador
            String permission = world.getPlayerPermissions().getOrDefault(player.getUniqueId(), "VISITOR");
            String permissionDisplay = switch (permission) {
                case "OWNER" -> "§a§lPROPRIETÁRIO";
                case "BUILDER" -> "§e§lCONSTRUTOR";
                case "VISITOR" -> "§7§lVISITANTE";
                default -> "§7§lVISITANTE";
            };

            // Status do mundo (carregado, etc.)
            boolean isLoaded = plugin.isWorldsServer() &&
                    plugin.getWorldManager() != null &&
                    plugin.getWorldManager().isWorldLoaded(world.getWorldName());

            String status = isLoaded ? "§a§lCARREGADO" : "§7§lDESCARREGADO";

            // Criar item do mundo
            ItemStack worldItem = new ItemBuilder(material)
                    .setName("§b§l" + world.getDisplayName())
                    .addLore("§7ID: §f" + world.getId())
                    .addLore("§7Nome: §f" + world.getWorldName())
                    .addLore("§7Proprietário: §f" + world.getOwnerName())
                    .addLore("§7Criado em: §f" + dateFormat.format(world.getCreationDate()))
                    .addLore("§7Último acesso: §f" + dateFormat.format(world.getLastAccessed()))
                    .addLore("")
                    .addLore("§7Tipo: §f" + formatWorldType(world.getWorldType()))
                    .addLore("§7Ambiente: §f" + formatEnvironment(world.getEnvironment()))
                    .addLore("§7Modo de jogo: §f" + formatGameMode(world.getGameMode()))
                    .addLore("")
                    .addLore("§7Status: " + status)
                    .addLore("§7Permissão: " + permissionDisplay)
                    .addLore("§7Público: " + (world.isPublic() ? "§a§lSIM" : "§c§lNÃO"))
                    .addLore("")
                    .addLore("§eClique para entrar neste mundo")
                    .addLore("§eClique direito para opções")
                    .addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                    .build();

            inventory.setItem(slot, worldItem);
            worldSlots.put(slot, world);
        }
    }

    private String formatWorldType(String worldType) {
        return switch (worldType) {
            case "NORMAL" -> "Normal";
            case "FLAT" -> "Plano";
            case "AMPLIFIED" -> "Amplificado";
            default -> worldType;
        };
    }

    private String formatEnvironment(String environment) {
        return switch (environment) {
            case "NORMAL" -> "Mundo Normal";
            case "NETHER" -> "Nether";
            case "THE_END" -> "End";
            default -> environment;
        };
    }

    private String formatGameMode(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "Sobrevivência";
            case CREATIVE -> "Criativo";
            case ADVENTURE -> "Aventura";
            case SPECTATOR -> "Espectador";
            default -> gameMode.name();
        };
    }

    private void addNavigationButtons() {
        // Adicionar botões apenas se houver mais de uma página
        if (worlds.size() > pageSize) {
            // Botão de página anterior
            ItemStack previousPage = new ItemBuilder(Material.ARROW)
                    .setName("§aPágina Anterior")
                    .addLore("§7Página atual: §f" + (currentPage + 1))
                    .addLore("§7Total de páginas: §f" + ((worlds.size() - 1) / pageSize + 1))
                    .build();

            // Botão de próxima página
            ItemStack nextPage = new ItemBuilder(Material.ARROW)
                    .setName("§aPróxima Página")
                    .addLore("§7Página atual: §f" + (currentPage + 1))
                    .addLore("§7Total de páginas: §f" + ((worlds.size() - 1) / pageSize + 1))
                    .build();

            inventory.setItem(45, previousPage);
            inventory.setItem(53, nextPage);

            actionSlots.put(45, this::previousPage);
            actionSlots.put(53, this::nextPage);
        }
    }

    private void addActionButtons() {
        // Botão para criar um novo mundo
        ItemStack createWorld = new ItemBuilder(Material.EMERALD)
                .setName("§a§lCriar Novo Mundo")
                .addLore("§7Clique para criar um novo mundo.")
                .build();

        // Botão para atualizar a lista
        ItemStack refreshList = new ItemBuilder(Material.CLOCK)
                .setName("§e§lAtualizar Lista")
                .addLore("§7Clique para atualizar a lista de mundos.")
                .build();

        // Botão para voltar ao menu principal
        ItemStack backButton = new ItemBuilder(Material.BARRIER)
                .setName("§c§lVoltar")
                .addLore("§7Clique para voltar ao menu principal.")
                .build();

        // Botão para alternar tipo de lista
        ItemStack toggleList = new ItemBuilder(Material.BOOK)
                .setName("§b§lAlterar Visualização")
                .addLore("§7Visualização atual: §f" + formatListType(listType))
                .addLore("")
                .addLore("§7Clique para alternar entre:")
                .addLore("§7- Meus Mundos")
                .addLore("§7- Mundos Acessíveis")
                .addLore(player.hasPermission("minevoxel.admin") ? "§7- Todos os Mundos" : "")
                .build();

        inventory.setItem(49, createWorld);
        inventory.setItem(48, refreshList);
        inventory.setItem(50, toggleList);
        inventory.setItem(46, backButton);

        actionSlots.put(49, this::openCreateWorldGUI);
        actionSlots.put(48, this::refreshList);
        actionSlots.put(50, this::toggleListType);
        actionSlots.put(46, () -> plugin.getGuiHandler().openMainGUI(player));
    }

    private String formatListType(ListType type) {
        return switch (type) {
            case MY_WORLDS -> "Meus Mundos";
            case ACCESSIBLE_WORLDS -> "Mundos Acessíveis";
            case ALL_WORLDS -> "Todos os Mundos";
        };
    }

    private void fillEmptySlots() {
        ItemStack emptyItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .build();

        // Linha de separação (slots 36-44)
        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, emptyItem);
        }

        // Preencher slots vazios restantes
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, emptyItem);
            }
        }
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot >= 0 && slot < inventory.getSize()) {
            event.setCancelled(true);

            // Verificar se é um slot de mundo
            if (worldSlots.containsKey(slot)) {
                WorldData world = worldSlots.get(slot);

                if (event.isRightClick()) {
                    // Abrir menu de opções do mundo
                    plugin.getGuiHandler().openWorldEditGUI(player, world);
                } else {
                    // Teleportar para o mundo
                    teleportToWorld(world);
                }
                return;
            }

            // Verificar se é um slot de ação
            if (actionSlots.containsKey(slot)) {
                actionSlots.get(slot).run();
            }
        }
    }

    private void teleportToWorld(WorldData world) {
        player.closeInventory();

        // Verificar permissões
        if (!world.canPlayerVisit(player.getUniqueId())) {
            player.sendMessage("§cVocê não tem permissão para visitar este mundo.");
            return;
        }

        if (plugin.isLobbyServer()) {
            // Estamos no lobby, precisamos teleportar para o servidor de mundos
            player.sendMessage("§aConectando ao mundo " + world.getDisplayName() + "...");
            plugin.getServerManager().connectToWorldsServer(player, world.getWorldName());
        } else if (plugin.isWorldsServer()) {
            // Estamos no servidor de mundos, teleportar diretamente
            player.sendMessage("§aTeleportando para o mundo " + world.getDisplayName() + "...");
            plugin.getTeleportManager().teleportToWorld(player, world.getWorldName());
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateInventory();
        }
    }

    private void nextPage() {
        int maxPage = (worlds.size() - 1) / pageSize;
        if (currentPage < maxPage) {
            currentPage++;
            updateInventory();
        }
    }

    private void openCreateWorldGUI() {
        player.closeInventory();
        plugin.getGuiHandler().openWorldCreationGUI(player);
    }

    private void refreshList() {
        loadWorlds();
    }

    private void toggleListType() {
        // Alternar entre os tipos de lista
        switch (listType) {
            case MY_WORLDS:
                listType = ListType.ACCESSIBLE_WORLDS;
                break;
            case ACCESSIBLE_WORLDS:
                if (player.hasPermission("minevoxel.admin")) {
                    listType = ListType.ALL_WORLDS;
                } else {
                    listType = ListType.MY_WORLDS;
                }
                break;
            case ALL_WORLDS:
                listType = ListType.MY_WORLDS;
                break;
        }

        // Atualizar título do inventário
        String title = switch (listType) {
            case MY_WORLDS -> "§8Meus Mundos";
            case ACCESSIBLE_WORLDS -> "§8Mundos Acessíveis";
            case ALL_WORLDS -> "§8Todos os Mundos";
        };

        // É necessário recriar o inventário para atualizar o título
        Inventory newInventory = Bukkit.createInventory(null, 54, title);
        player.openInventory(newInventory);
        inventory = newInventory;

        // Recarregar mundos
        loadWorlds();
    }

    public void openInventory() {
        player.openInventory(inventory);
    }
}