package br.com.minevoxel.mundos.gui;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import br.com.minevoxel.mundos.utils.ItemBuilder;
import br.com.minevoxel.mundos.utils.NameGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class WorldCreationGUI {

    private final MinevoxelMundos plugin;
    private final Player player;
    private Inventory inventory;

    // Dados temporários para a criação do mundo
    private String worldName;
    private String displayName;
    private String worldType = "NORMAL";
    private String environment = "NORMAL";
    private boolean generateStructures = true;
    private long seed = 0;
    private GameMode gameMode = GameMode.SURVIVAL;

    // Mapeamento de slots para ações
    private Map<Integer, Runnable> clickActions = new HashMap<>();

    public WorldCreationGUI(MinevoxelMundos plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Gerar nome aleatório único para o mundo
        this.worldName = generateUniqueWorldName();
        this.displayName = worldName;

        // Inicializar inventário
        createInventory();
    }

    private String generateUniqueWorldName() {
        String prefix = player.getName().toLowerCase() + "_";
        String randomName = NameGenerator.generateName(8);
        return prefix + randomName;
    }

    private void createInventory() {
        inventory = Bukkit.createInventory(null, 54, "§8Criar Novo Mundo");
        updateInventory();
    }

    private void updateInventory() {
        inventory.clear();
        clickActions.clear();

        // Informações do mundo
        addInfoItems();

        // Opções de tipo de mundo
        addWorldTypeOptions();

        // Opções de ambiente
        addEnvironmentOptions();

        // Opções de estruturas
        addStructureOptions();

        // Opções de modo de jogo
        addGameModeOptions();

        // Semente personalizada
        addSeedOption();

        // Botões de finalização
        addActionButtons();

        // Preencher espaços vazios
        fillEmptySlots();
    }

    private void addInfoItems() {
        // Mostrar informações do mundo que será criado
        ItemStack worldInfo = new ItemBuilder(Material.NAME_TAG)
                .setName("§a§lInformações do Mundo")
                .addLore("§7Nome: §f" + worldName)
                .addLore("§7Nome de Exibição: §f" + displayName)
                .addLore("")
                .addLore("§7Clique para editar o nome de exibição")
                .build();

        inventory.setItem(4, worldInfo);

        clickActions.put(4, () -> {
            player.closeInventory();
            plugin.getGuiHandler().startAnvilInput(player, "Editar Nome", displayName, (newName) -> {
                if (newName != null && !newName.isEmpty()) {
                    displayName = newName;
                }
                openInventory();
            });
        });
    }

    private void addWorldTypeOptions() {
        ItemStack normal = new ItemBuilder(Material.GRASS_BLOCK)
                .setName("§a§lMundo Normal")
                .addLore("§7Um mundo padrão com montanhas,")
                .addLore("§7vales, florestas, planícies, etc.")
                .addLore("")
                .addLore(worldType.equals("NORMAL") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack flat = new ItemBuilder(Material.DIRT)
                .setName("§a§lMundo Plano")
                .addLore("§7Um mundo completamente plano")
                .addLore("§7ideal para construções.")
                .addLore("")
                .addLore(worldType.equals("FLAT") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack amplified = new ItemBuilder(Material.STONE)
                .setName("§a§lMundo Amplificado")
                .addLore("§7Um mundo com terrenos extremos")
                .addLore("§7e montanhas gigantescas.")
                .addLore("")
                .addLore(worldType.equals("AMPLIFIED") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        inventory.setItem(19, normal);
        inventory.setItem(20, flat);
        inventory.setItem(21, amplified);

        clickActions.put(19, () -> {
            worldType = "NORMAL";
            updateInventory();
        });

        clickActions.put(20, () -> {
            worldType = "FLAT";
            updateInventory();
        });

        clickActions.put(21, () -> {
            worldType = "AMPLIFIED";
            updateInventory();
        });
    }

    private void addEnvironmentOptions() {
        ItemStack overworld = new ItemBuilder(Material.OAK_SAPLING)
                .setName("§a§lMundo Normal (Overworld)")
                .addLore("§7O ambiente padrão do Minecraft.")
                .addLore("")
                .addLore(environment.equals("NORMAL") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack nether = new ItemBuilder(Material.NETHERRACK)
                .setName("§c§lNether")
                .addLore("§7Um mundo infernal com fogo e lava.")
                .addLore("")
                .addLore(environment.equals("NETHER") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack end = new ItemBuilder(Material.END_STONE)
                .setName("§d§lEnd")
                .addLore("§7O mundo final do Minecraft.")
                .addLore("")
                .addLore(environment.equals("THE_END") ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        inventory.setItem(23, overworld);
        inventory.setItem(24, nether);
        inventory.setItem(25, end);

        clickActions.put(23, () -> {
            environment = "NORMAL";
            updateInventory();
        });

        clickActions.put(24, () -> {
            environment = "NETHER";
            updateInventory();
        });

        clickActions.put(25, () -> {
            environment = "THE_END";
            updateInventory();
        });
    }

    private void addStructureOptions() {
        ItemStack structures = new ItemBuilder(Material.MOSSY_COBBLESTONE)
                .setName("§a§lEstruturas")
                .addLore("§7Gerar estruturas como vilas,")
                .addLore("§7templos, masmorras, etc.")
                .addLore("")
                .addLore(generateStructures ? "§a§lATIVADO" : "§c§lDESATIVADO")
                .addLore("")
                .addLore("§7Clique para " + (generateStructures ? "desativar" : "ativar"))
                .build();

        inventory.setItem(31, structures);

        clickActions.put(31, () -> {
            generateStructures = !generateStructures;
            updateInventory();
        });
    }

    private void addGameModeOptions() {
        ItemStack survival = new ItemBuilder(Material.IRON_SWORD)
                .setName("§a§lSobrevivência")
                .addLore("§7Modo de jogo padrão com vida,")
                .addLore("§7fome e monstros.")
                .addLore("")
                .addLore(gameMode == GameMode.SURVIVAL ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack creative = new ItemBuilder(Material.GRASS_BLOCK)
                .setName("§a§lCriativo")
                .addLore("§7Recursos infinitos, voo e")
                .addLore("§7invulnerabilidade.")
                .addLore("")
                .addLore(gameMode == GameMode.CREATIVE ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        ItemStack adventure = new ItemBuilder(Material.MAP)
                .setName("§a§lAventura")
                .addLore("§7Semelhante à sobrevivência,")
                .addLore("§7mas com restrições.")
                .addLore("")
                .addLore(gameMode == GameMode.ADVENTURE ? "§a§lSELECIONADO" : "§7Clique para selecionar")
                .build();

        inventory.setItem(37, survival);
        inventory.setItem(38, creative);
        inventory.setItem(39, adventure);

        clickActions.put(37, () -> {
            gameMode = GameMode.SURVIVAL;
            updateInventory();
        });

        clickActions.put(38, () -> {
            gameMode = GameMode.CREATIVE;
            updateInventory();
        });

        clickActions.put(39, () -> {
            gameMode = GameMode.ADVENTURE;
            updateInventory();
        });
    }

    private void addSeedOption() {
        ItemStack seedItem = new ItemBuilder(Material.WHEAT_SEEDS)
                .setName("§a§lSemente Personalizada")
                .addLore("§7Define a geração do mundo.")
                .addLore("§7Semente atual: §f" + (seed == 0 ? "Aleatória" : seed))
                .addLore("")
                .addLore("§7Clique para definir uma semente")
                .addLore("§7Clique com botão direito para usar semente aleatória")
                .build();

        inventory.setItem(41, seedItem);

        clickActions.put(41, () -> {
            player.closeInventory();
            plugin.getGuiHandler().startAnvilInput(player, "Semente", String.valueOf(seed), (input) -> {
                try {
                    seed = Long.parseLong(input);
                } catch (NumberFormatException e) {
                    // Se o input não for um número válido, usar input como seed string
                    seed = input.hashCode();
                }
                openInventory();
            });
        });
    }

    private void addActionButtons() {
        ItemStack randomize = new ItemBuilder(Material.REPEATER)
                .setName("§6§lGerar Novo Nome")
                .addLore("§7Gera um novo nome aleatório")
                .addLore("§7para o mundo.")
                .build();

        ItemStack create = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .setName("§a§lCriar Mundo")
                .addLore("§7Clique para criar o mundo")
                .addLore("§7com as configurações atuais.")
                .build();

        ItemStack cancel = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .setName("§c§lCancelar")
                .addLore("§7Clique para cancelar a")
                .addLore("§7criação do mundo.")
                .build();

        inventory.setItem(45, randomize);
        inventory.setItem(49, create);
        inventory.setItem(53, cancel);

        clickActions.put(45, () -> {
            worldName = generateUniqueWorldName();
            displayName = worldName;
            updateInventory();
        });

        clickActions.put(49, this::createWorld);

        clickActions.put(53, () -> player.closeInventory());
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

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot >= 0 && slot < inventory.getSize()) {
            event.setCancelled(true);

            // Verificar botão direito para seed
            if (slot == 41 && event.getClick() == ClickType.RIGHT) {
                // Definir semente aleatória
                seed = new Random().nextLong();
                updateInventory();
                return;
            }

            // Executar ação associada ao slot, se houver
            if (clickActions.containsKey(slot)) {
                clickActions.get(slot).run();
            }
        }
    }

    private void createWorld() {
        player.closeInventory();

        // Mostrar mensagem de carregamento
        player.sendMessage("§aIniciando criação do mundo " + displayName + "...");

        // Verificar se estamos no lobby
        if (!plugin.isLobbyServer()) {
            player.sendMessage("§cErro ao criar mundo: Esta operação deve ser realizada no servidor de lobby.");
            return;
        }

        // Criar objeto WorldData
        WorldData worldData = new WorldData(0, worldName, player.getUniqueId(), player.getName());
        worldData.setDisplayName(displayName);
        worldData.setWorldType(worldType);
        worldData.setEnvironment(environment);
        worldData.setGenerateStructures(generateStructures);
        worldData.setSeed(seed);
        worldData.setGameMode(gameMode);

        // Salvar no banco de dados
        plugin.getDatabaseManager().createWorld(worldData).thenAccept(worldId -> {
            if (worldId > 0) {
                // Sucesso ao criar no banco de dados
                worldData.setId(worldId);

                // Enviar mensagem para criar o mundo no servidor Worlds
                plugin.getMessageChannels().sendCreateWorldMessage(worldData);

                // Enviar mensagem para teleportar o jogador quando o mundo for criado
                plugin.getServerManager().connectToWorldsServer(player, worldName);

                player.sendMessage("§aMundo criado com sucesso! Teleportando para " + displayName + "...");
            } else {
                // Falha ao criar no banco de dados
                player.sendMessage("§cErro ao criar o mundo: Falha ao registrar no banco de dados.");
            }
        });
    }

    public void openInventory() {
        player.openInventory(inventory);
    }
}