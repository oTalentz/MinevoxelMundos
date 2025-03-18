package br.com.minevoxel.mundos.gui;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import br.com.minevoxel.mundos.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class WorldPermissionsGUI {

    private final MinevoxelMundos plugin;
    private final Player player;
    private final WorldData worldData;
    private Inventory inventory;

    private int currentPage = 0;
    private final int PLAYERS_PER_PAGE = 36;

    // Mapeamento de slots para ações
    private final Map<Integer, Runnable> clickActions = new HashMap<>();
    private final Map<Integer, UUID> playerSlots = new HashMap<>();

    public WorldPermissionsGUI() {
        this.plugin = MinevoxelMundos.getInstance();
        this.player = null;
        this.worldData = null;
    }

    public WorldPermissionsGUI(MinevoxelMundos plugin, Player player, WorldData worldData) {
        this.plugin = plugin;
        this.player = player;
        this.worldData = worldData;

        // Inicializar inventário
        createInventory();
    }

    private void createInventory() {
        inventory = Bukkit.createInventory(null, 54, "§8Permissões: " + worldData.getDisplayName());
        updateInventory();
    }

    private void updateInventory() {
        inventory.clear();
        clickActions.clear();
        playerSlots.clear();

        // Informações do mundo
        addWorldInfo();

        // Lista de jogadores com permissões
        addPlayerList();

        // Botões de navegação
        addNavigationButtons();

        // Botão para adicionar jogador
        addAddPlayerButton();

        // Preencher espaços vazios
        fillEmptySlots();
    }

    private void addWorldInfo() {
        // Mostrar informações básicas do mundo
        ItemStack worldInfo = new ItemBuilder(Material.GRASS_BLOCK)
                .setName("§a§l" + worldData.getDisplayName())
                .addLore("§7ID: §f" + worldData.getId())
                .addLore("§7Nome: §f" + worldData.getWorldName())
                .addLore("§7Proprietário: §f" + worldData.getOwnerName())
                .addLore("")
                .addLore("§7Visibilidade: " + (worldData.isPublic() ? "§a§lPÚBLICO" : "§c§lPRIVADO"))
                .build();

        // Botão para alternar visibilidade pública
        ItemStack publicToggle = new ItemBuilder(
                worldData.isPublic() ? Material.LIME_DYE : Material.RED_DYE
        )
                .setName(worldData.isPublic() ? "§a§lMundo Público" : "§c§lMundo Privado")
                .addLore(worldData.isPublic()
                        ? "§7Clique para tornar o mundo privado"
                        : "§7Clique para tornar o mundo público")
                .addLore("")
                .addLore(worldData.isPublic()
                        ? "§7Mundos públicos podem ser acessados"
                        : "§7Mundos privados só podem ser acessados")
                .addLore(worldData.isPublic()
                        ? "§7por qualquer jogador sem permissão."
                        : "§7por jogadores com permissão.")
                .build();

        inventory.setItem(4, worldInfo);
        inventory.setItem(5, publicToggle);

        // Adicionar ação para alternar visibilidade
        clickActions.put(5, () -> {
            worldData.setPublic(!worldData.isPublic());
            updateWorld();
            updateInventory();
        });
    }

    private void addPlayerList() {
        // Obter lista de jogadores com permissões
        Map<UUID, String> playerPermissions = worldData.getPlayerPermissions();
        List<Map.Entry<UUID, String>> playerList = new ArrayList<>(playerPermissions.entrySet());

        // Ordenar: primeiro o dono, depois builders, depois visitantes
        playerList.sort((a, b) -> {
            // Proprietário sempre primeiro
            if (a.getKey().equals(worldData.getOwnerUUID())) return -1;
            if (b.getKey().equals(worldData.getOwnerUUID())) return 1;

            // Ordenar por tipo de permissão
            int permCompare = getPermissionWeight(b.getValue()) - getPermissionWeight(a.getValue());
            if (permCompare != 0) return permCompare;

            // Se mesma permissão, ordenar por UUID (consistente mas arbitrário)
            return a.getKey().compareTo(b.getKey());
        });

        // Calcular índices para a página atual
        int startIndex = currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, playerList.size());

        // Verificar se a página atual é válida
        if (startIndex >= playerList.size() && currentPage > 0) {
            currentPage = 0;
            updateInventory();
            return;
        }

        // Mostrar jogadores da página atual
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, String> entry = playerList.get(i);
            UUID playerUUID = entry.getKey();
            String permission = entry.getValue();

            // Obter jogador
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Desconhecido";

            // Criar item de cabeça do jogador
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            meta.setOwningPlayer(offlinePlayer);
            meta.setDisplayName("§b" + playerName);

            // Adicionar informações de permissão
            List<String> lore = new ArrayList<>();
            lore.add("§7Permissão: " + formatPermission(permission));
            lore.add("");

            // Adicionar instruções com base na permissão
            if (permission.equals("OWNER")) {
                lore.add("§7Este jogador é o proprietário");
                lore.add("§7do mundo e não pode ser removido.");
            } else {
                lore.add("§7Clique esquerdo para alterar permissão");
                lore.add("§7Clique direito para remover acesso");
            }

            meta.setLore(lore);
            playerHead.setItemMeta(meta);

            // Adicionar ao inventário
            int slot = i - startIndex;
            inventory.setItem(slot, playerHead);

            // Registrar ações se não for o proprietário
            if (!permission.equals("OWNER")) {
                playerSlots.put(slot, playerUUID);
            }
        }
    }

    private void addNavigationButtons() {
        // Adicionar botões de navegação se houver mais de uma página
        Map<UUID, String> playerPermissions = worldData.getPlayerPermissions();
        int totalPages = (int) Math.ceil((double) playerPermissions.size() / PLAYERS_PER_PAGE);

        if (totalPages > 1) {
            // Botão de página anterior
            ItemStack prevPage = new ItemBuilder(Material.ARROW)
                    .setName("§a§lPágina Anterior")
                    .addLore("§7Página atual: §f" + (currentPage + 1) + "/" + totalPages)
                    .build();

            // Botão de próxima página
            ItemStack nextPage = new ItemBuilder(Material.ARROW)
                    .setName("§a§lPróxima Página")
                    .addLore("§7Página atual: §f" + (currentPage + 1) + "/" + totalPages)
                    .build();

            inventory.setItem(45, prevPage);
            inventory.setItem(53, nextPage);

            // Adicionar ações
            clickActions.put(45, () -> {
                if (currentPage > 0) {
                    currentPage--;
                    updateInventory();
                }
            });

            clickActions.put(53, () -> {
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    updateInventory();
                }
            });
        }

        // Botão para voltar à tela de edição
        ItemStack backButton = new ItemBuilder(Material.BARRIER)
                .setName("§c§lVoltar")
                .addLore("§7Clique para voltar à")
                .addLore("§7tela de edição do mundo.")
                .build();

        inventory.setItem(49, backButton);

        clickActions.put(49, () -> {
            player.closeInventory();
            plugin.getGuiHandler().openWorldEditGUI(player, worldData);
        });
    }

    private void addAddPlayerButton() {
        // Botão para adicionar jogador
        ItemStack addPlayerButton = new ItemBuilder(Material.EMERALD)
                .setName("§a§lAdicionar Jogador")
                .addLore("§7Clique para adicionar um jogador")
                .addLore("§7ao seu mundo.")
                .build();

        inventory.setItem(48, addPlayerButton);

        clickActions.put(48, this::openAddPlayerPrompt);
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

    @SuppressWarnings("deprecation")
    private void openAddPlayerPrompt() {
        player.closeInventory();
        player.sendMessage("§aDigite o nome do jogador que você deseja adicionar:");

        // Registrar input listener para o próximo chat do jogador
        plugin.getGuiHandler().registerPlayerInputListener(player, (input) -> {
            // Buscar jogador pelo nome
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(input);

            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                player.sendMessage("§cJogador não encontrado: " + input);
                openInventory();
                return;
            }

            // Verificar se o jogador já tem permissão
            if (worldData.getPlayerPermissions().containsKey(targetPlayer.getUniqueId())) {
                player.sendMessage("§cO jogador " + targetPlayer.getName() + " já tem acesso a este mundo.");
                openInventory();
                return;
            }

            // Adicionar jogador com permissão VISITOR por padrão
            worldData.addPlayerPermission(targetPlayer.getUniqueId(), "VISITOR");
            updateWorld();

            player.sendMessage("§aJogador " + targetPlayer.getName() + " adicionado com sucesso como visitante.");
            openInventory();
        });
    }

    private void openChangePermissionPrompt(UUID playerUUID) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerUUID);
        String currentPermission = worldData.getPlayerPermissions().get(playerUUID);

        // Abrir menu para selecionar permissão
        Inventory permMenu = Bukkit.createInventory(null, 9, "§8Permissão: " + targetPlayer.getName());

        // Opções de permissão
        ItemStack builderOption = new ItemBuilder(Material.DIAMOND_PICKAXE)
                .setName("§a§lCONSTRUTOR")
                .addLore("§7Permite que o jogador construa")
                .addLore("§7e modifique o mundo.")
                .addLore("")
                .addLore(currentPermission.equals("BUILDER") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack visitorOption = new ItemBuilder(Material.COMPASS)
                .setName("§a§lVISITANTE")
                .addLore("§7Permite que o jogador visite o mundo,")
                .addLore("§7mas não permite construir.")
                .addLore("")
                .addLore(currentPermission.equals("VISITOR") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        // Posicionar opções
        permMenu.setItem(3, builderOption);
        permMenu.setItem(5, visitorOption);

        // Abrir menu
        player.closeInventory();
        player.openInventory(permMenu);

        // Registrar handler de clique
        plugin.getGuiHandler().registerPermissionSelectionGui(player, permMenu, playerUUID, worldData, this);
    }

    private void removePlayerAccess(UUID playerUUID) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerUUID);

        // Remover acesso
        worldData.removePlayerPermission(playerUUID);
        updateWorld();

        player.sendMessage("§aAcesso removido para " + targetPlayer.getName());
        updateInventory();
    }

    private int getPermissionWeight(String permission) {
        switch (permission) {
            case "OWNER": return 3;
            case "BUILDER": return 2;
            case "VISITOR": return 1;
            default: return 0;
        }
    }

    private String formatPermission(String permission) {
        switch (permission) {
            case "OWNER": return "§a§lPROPRIETÁRIO";
            case "BUILDER": return "§e§lCONSTRUTOR";
            case "VISITOR": return "§7§lVISITANTE";
            default: return "§7§lDESCONHECIDO";
        }
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
        } else if (playerSlots.containsKey(slot)) {
            UUID playerUUID = playerSlots.get(slot);

            if (event.getClick() == ClickType.LEFT) {
                // Clique esquerdo: alterar permissão
                openChangePermissionPrompt(playerUUID);
            } else if (event.getClick() == ClickType.RIGHT) {
                // Clique direito: remover acesso
                removePlayerAccess(playerUUID);
            }
        }
    }

    public void openInventory() {
        player.openInventory(inventory);
    }
}