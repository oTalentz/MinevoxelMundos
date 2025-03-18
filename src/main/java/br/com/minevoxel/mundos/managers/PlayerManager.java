package br.com.minevoxel.mundos.managers;

import br.com.minevoxel.mundos.MinevoxelMundos;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {

    private final MinevoxelMundos plugin;
    private final Map<UUID, Integer> playerWorldCount = new HashMap<>();

    public PlayerManager() {
        this.plugin = MinevoxelMundos.getInstance();
    }

    public PlayerManager(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    /**
     * Verifica se o jogador pode criar um novo mundo
     *
     * @param player O jogador a ser verificado
     * @return true se o jogador pode criar um novo mundo, false caso contrário
     */
    public boolean canCreateWorld(Player player) {
        // Administradores sempre podem criar mundos
        if (player.hasPermission(plugin.getConfigManager().getWorldAdminPermission())) {
            return true;
        }

        // Verificar se o jogador tem a permissão básica para criar mundos
        if (!player.hasPermission(plugin.getConfigManager().getWorldCreationPermission())) {
            return false;
        }

        // Verificar limite de mundos do jogador
        int maxWorlds = plugin.getConfigManager().getMaxWorldsPerPlayer();

        // Se maxWorlds for 0, não há limite
        if (maxWorlds <= 0) {
            return true;
        }

        // Jogadores com permissão de bypass de limite não têm limite
        if (player.hasPermission(plugin.getConfigManager().getWorldLimitBypassPermission())) {
            return true;
        }

        // Verificar quantos mundos o jogador já tem
        int currentWorlds = getPlayerWorldCount(player.getUniqueId());

        return currentWorlds < maxWorlds;
    }

    /**
     * Obtém a contagem de mundos do jogador
     *
     * @param playerUUID UUID do jogador
     * @return Número de mundos que o jogador possui
     */
    public int getPlayerWorldCount(UUID playerUUID) {
        // Verificar cache primeiro
        if (playerWorldCount.containsKey(playerUUID)) {
            return playerWorldCount.get(playerUUID);
        }

        // Buscar do banco de dados (assíncrono)
        plugin.getDatabaseManager().getWorldsByOwner(playerUUID).thenAccept(worlds -> {
            int count = worlds.size();
            playerWorldCount.put(playerUUID, count);
        });

        // Retornar 0 enquanto aguarda a resposta do banco de dados
        return 0;
    }

    /**
     * Incrementa a contagem de mundos do jogador
     *
     * @param playerUUID UUID do jogador
     */
    public void incrementWorldCount(UUID playerUUID) {
        int currentCount = getPlayerWorldCount(playerUUID);
        playerWorldCount.put(playerUUID, currentCount + 1);
    }

    /**
     * Decrementa a contagem de mundos do jogador
     *
     * @param playerUUID UUID do jogador
     */
    public void decrementWorldCount(UUID playerUUID) {
        int currentCount = getPlayerWorldCount(playerUUID);
        if (currentCount > 0) {
            playerWorldCount.put(playerUUID, currentCount - 1);
        }
    }

    /**
     * Atualiza a contagem de mundos do jogador com o valor correto do banco de dados
     *
     * @param playerUUID UUID do jogador
     */
    public void refreshWorldCount(UUID playerUUID) {
        playerWorldCount.remove(playerUUID);
        getPlayerWorldCount(playerUUID);
    }

    /**
     * Limpa o cache de contagem de mundos do jogador
     *
     * @param playerUUID UUID do jogador
     */
    public void clearPlayerCache(UUID playerUUID) {
        playerWorldCount.remove(playerUUID);
    }
}