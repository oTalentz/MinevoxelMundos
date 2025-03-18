package br.com.minevoxel.mundos.managers;

import br.com.minevoxel.mundos.MinevoxelMundos;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;

public class ServerManager {

    private final MinevoxelMundos plugin;

    public ServerManager() {
        this.plugin = MinevoxelMundos.getInstance();
    }

    public ServerManager(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    /**
     * Conecta um jogador ao servidor de lobby
     *
     * @param player Jogador a ser conectado
     */
    public void connectToLobbyServer(Player player) {
        String lobbyServer = plugin.getConfigManager().getLobbyServer();
        connectToServer(player, lobbyServer);
    }

    /**
     * Conecta um jogador ao servidor de mundos
     *
     * @param player Jogador a ser conectado
     */
    public void connectToWorldsServer(Player player) {
        String worldsServer = plugin.getConfigManager().getWorldsServer();
        connectToServer(player, worldsServer);
    }

    /**
     * Conecta um jogador ao servidor de mundos e indica o mundo para teleporte
     *
     * @param player Jogador a ser conectado
     * @param worldName Nome do mundo para teleporte após conexão
     */
    public void connectToWorldsServer(Player player, String worldName) {
        // Primeiro, enviar mensagem para teleportar o jogador quando chegar ao servidor de mundos
        plugin.getMessageChannels().sendTeleportMessage(player, worldName);

        // Então, conectar o jogador ao servidor de mundos
        String worldsServer = plugin.getConfigManager().getWorldsServer();
        connectToServer(player, worldsServer);
    }

    /**
     * Conecta um jogador a um servidor específico
     *
     * @param player Jogador a ser conectado
     * @param server Nome do servidor
     */
    public void connectToServer(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    /**
     * Obtém o nome do servidor atual
     *
     * @return Nome do servidor atual
     */
    public String getCurrentServerName() {
        return plugin.getServer().getName();
    }

    /**
     * Verifica se o servidor atual é o servidor de lobby
     *
     * @return true se o servidor atual é o servidor de lobby, false caso contrário
     */
    public boolean isLobbyServer() {
        return plugin.isLobbyServer();
    }

    /**
     * Verifica se o servidor atual é o servidor de mundos
     *
     * @return true se o servidor atual é o servidor de mundos, false caso contrário
     */
    public boolean isWorldsServer() {
        return plugin.isWorldsServer();
    }
}