package br.com.minevoxel.mundos;

import br.com.minevoxel.mundos.commands.CommandHandler;
import br.com.minevoxel.mundos.config.Config;
import br.com.minevoxel.mundos.config.Messages;
import br.com.minevoxel.mundos.database.DatabaseManager;
import br.com.minevoxel.mundos.events.PlayerEvents;
import br.com.minevoxel.mundos.events.WorldEvents;
import br.com.minevoxel.mundos.gui.GUIHandler;
import br.com.minevoxel.mundos.managers.MultiverseWorldManager;
import br.com.minevoxel.mundos.managers.PlayerManager;
import br.com.minevoxel.mundos.managers.ServerManager;
import br.com.minevoxel.mundos.managers.TeleportManager;
import br.com.minevoxel.mundos.velocity.MessageChannels;
import br.com.minevoxel.mundos.velocity.ServerConnector;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class MinevoxelMundos extends JavaPlugin {

    private static MinevoxelMundos instance;
    private Config config;
    private Messages messages;
    private DatabaseManager databaseManager;
    private MultiverseWorldManager worldManager;
    private PlayerManager playerManager;
    private ServerManager serverManager;
    private TeleportManager teleportManager;
    private GUIHandler guiHandler;
    private ServerConnector serverConnector;
    private MessageChannels messageChannels;
    private String serverType; // "LOBBY" ou "WORLDS"
    private CommandHandler commandHandler;

    @Override
    public void onEnable() {
        instance = this;

        // Verificar dependência do Multiverse-Core
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
            getLogger().severe("Multiverse-Core não encontrado! Este plugin requer Multiverse-Core para funcionar.");
            getLogger().severe("Desativando o plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Carregar configurações
        loadConfigs();

        // Determinar tipo de servidor
        determineServerType();

        // Inicializar banco de dados
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // Inicializar gerenciadores
        initializeManagers();

        // Registrar eventos
        registerEvents();

        // Configurar conexão com o Velocity
        setupVelocityConnection();

        // Registrar comandos
        registerCommands();

        getLogger().info("MinevoxelMundos ativado com sucesso! Rodando como: " + serverType);
    }

    @Override
    public void onDisable() {
        // Fechar conexões com o banco de dados
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        // Salvar mundos ativos (apenas no servidor WORLDS)
        if (serverType.equals("WORLDS") && worldManager != null) {
            worldManager.saveAllWorlds();
        }

        getLogger().info("MinevoxelMundos desativado com sucesso!");
    }

    private void loadConfigs() {
        saveDefaultConfig();
        config = new Config(this);
        messages = new Messages(this);
    }

    private void determineServerType() {
        // Determinar tipo de servidor baseado na configuração ou nome do servidor
        String serverName = Bukkit.getServer().getName().toLowerCase();

        if (serverName.contains("lobby")) {
            serverType = "LOBBY";
        } else if (serverName.contains("worlds") || serverName.contains("world")) {
            serverType = "WORLDS";
        } else {
            // Verificar config.yml para o tipo de servidor
            serverType = getConfig().getString("server-type", "UNKNOWN");
            getLogger().warning("Tipo de servidor não detectado automaticamente. Usando valor do config.yml: " + serverType);
        }
    }

    private void initializeManagers() {
        // Inicializar gerenciadores comuns
        playerManager = new PlayerManager(this);
        serverManager = new ServerManager(this);
        teleportManager = new TeleportManager(this);

        // Inicializar gerenciadores específicos do tipo de servidor
        if (serverType.equals("WORLDS")) {
            // Usar MultiverseWorldManager em vez de WorldManager
            worldManager = new MultiverseWorldManager(this);
            worldManager.initialize();
        }

        // Inicializar gerenciador de GUI (comum para ambos os tipos de servidor)
        guiHandler = new GUIHandler(this);
        guiHandler.registerEvents(); // Registrar eventos após a inicialização completa
    }

    private void registerEvents() {
        // Registrar eventos comuns
        getServer().getPluginManager().registerEvents(new PlayerEvents(this), this);

        // Registrar eventos específicos do tipo de servidor
        if (serverType.equals("WORLDS")) {
            getServer().getPluginManager().registerEvents(new WorldEvents(this), this);
        }
    }

    private void setupVelocityConnection() {
        messageChannels = new MessageChannels(this);
        serverConnector = new ServerConnector(this);

        // Registrar canais de plugins
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", serverConnector);

        // Registrar canais personalizados para comunicação entre servidores
        getServer().getMessenger().registerOutgoingPluginChannel(this, "minevoxel:mundos");
        getServer().getMessenger().registerIncomingPluginChannel(this, "minevoxel:mundos", messageChannels);
    }

    private void registerCommands() {
        // Inicializar e registrar o manipulador de comandos
        commandHandler = new CommandHandler(this);

        PluginCommand mundoCommand = getCommand("mundo");
        if (mundoCommand != null) {
            mundoCommand.setExecutor(commandHandler);
            mundoCommand.setTabCompleter(commandHandler);
        }
    }

    // Getters
    public static MinevoxelMundos getInstance() {
        return instance;
    }

    public Config getConfigManager() {
        return config;
    }

    public Messages getMessages() {
        return messages;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MultiverseWorldManager getWorldManager() {
        return worldManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public GUIHandler getGuiHandler() {
        return guiHandler;
    }

    public ServerConnector getServerConnector() {
        return serverConnector;
    }

    public MessageChannels getMessageChannels() {
        return messageChannels;
    }

    public String getServerType() {
        return serverType;
    }

    public boolean isLobbyServer() {
        return "LOBBY".equals(serverType);
    }

    public boolean isWorldsServer() {
        return "WORLDS".equals(serverType);
    }
}