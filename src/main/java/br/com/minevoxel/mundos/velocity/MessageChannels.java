package br.com.minevoxel.mundos.velocity;

import br.com.minevoxel.mundos.MinevoxelMundos;
import br.com.minevoxel.mundos.models.WorldData;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.UUID;

public class MessageChannels implements PluginMessageListener {

    private final MinevoxelMundos plugin;

    public MessageChannels(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("minevoxel:mundos")) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        plugin.getLogger().info("Recebida mensagem no canal " + channel + ", subchannel: " + subchannel);

        switch (subchannel) {
            case "WorldCreated":
                handleWorldCreatedMessage(in);
                break;
            case "WorldTeleport":
                handleWorldTeleportMessage(in);
                break;
            case "WorldStatus":
                handleWorldStatusMessage(in);
                break;
            case "WorldUpdate":
                handleWorldUpdateMessage(in);
                break;
            default:
                plugin.getLogger().warning("Subchannel desconhecido: " + subchannel);
                break;
        }
    }

    private void handleWorldCreatedMessage(ByteArrayDataInput in) {
        String worldName = in.readUTF();
        int worldId = in.readInt();

        // Verificar se estamos no servidor Worlds
        if (plugin.isWorldsServer()) {
            // Carregar os dados do mundo do banco de dados
            plugin.getDatabaseManager().getWorld(worldId).thenAccept(worldData -> {
                if (worldData != null) {
                    // Carregar ou criar o mundo
                    plugin.getWorldManager().loadWorld(worldData).thenAccept(world -> {
                        plugin.getLogger().info("Mundo criado com sucesso: " + worldName);
                    }).exceptionally(ex -> {
                        plugin.getLogger().severe("Erro ao carregar mundo: " + ex.getMessage());
                        ex.printStackTrace();
                        return null;
                    });
                } else {
                    plugin.getLogger().warning("Dados do mundo não encontrados: " + worldId);
                }
            });
        }
    }

    private void handleWorldTeleportMessage(ByteArrayDataInput in) {
        String playerUUID = in.readUTF();
        String worldName = in.readUTF();

        // Verificar se estamos no servidor Worlds
        if (plugin.isWorldsServer()) {
            // Buscar o jogador pelo UUID
            Player player = Bukkit.getPlayer(UUID.fromString(playerUUID));

            if (player != null) {
                // Teleportar o jogador para o mundo
                plugin.getTeleportManager().teleportToWorld(player, worldName);
            } else {
                plugin.getLogger().warning("Jogador não encontrado para teleporte: " + playerUUID);
            }
        }
    }

    private void handleWorldStatusMessage(ByteArrayDataInput in) {
        String requestId = in.readUTF();
        String worldName = in.readUTF();

        // Verificar se estamos no servidor Worlds
        if (plugin.isWorldsServer()) {
            // Verificar status do mundo
            boolean isLoaded = plugin.getWorldManager().isWorldLoaded(worldName);
            String status = isLoaded ? "LOADED" : "UNLOADED";

            // Enviar resposta
            sendWorldResponseMessage(requestId, status);
        }
    }

    private void handleWorldUpdateMessage(ByteArrayDataInput in) {
        int worldId = in.readInt();

        // Verificar se estamos no servidor Worlds
        if (plugin.isWorldsServer()) {
            // Buscar dados atualizados do mundo
            plugin.getDatabaseManager().getWorld(worldId).thenAccept(worldData -> {
                if (worldData != null) {
                    // Atualizar configurações do mundo
                    plugin.getWorldManager().updateWorldSettings(worldData);
                }
            });
        }
    }

    // Métodos para enviar mensagens

    public void sendCreateWorldMessage(WorldData worldData) {
        // Verificar se há jogadores online para enviar a mensagem
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("Não foi possível enviar mensagem: nenhum jogador online");
            return;
        }

        // Pegar um jogador para enviar a mensagem
        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos");

        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);

        try {
            // Escrever dados da mensagem
            msgout.writeUTF("WorldCreated");
            msgout.writeUTF(worldData.getWorldName());
            msgout.writeInt(worldData.getId());

            // Finalizar mensagem
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            plugin.getLogger().info("Mensagem de criação de mundo enviada: " + worldData.getWorldName());

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendTeleportMessage(Player player, String worldName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos");

        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);

        try {
            // Escrever dados da mensagem
            msgout.writeUTF("WorldTeleport");
            msgout.writeUTF(player.getUniqueId().toString());
            msgout.writeUTF(worldName);

            // Finalizar mensagem
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            plugin.getLogger().info("Mensagem de teleporte enviada: " + player.getName() + " -> " + worldName);

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendWorldUpdateMessage(int worldId) {
        // Verificar se há jogadores online para enviar a mensagem
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("Não foi possível enviar mensagem: nenhum jogador online");
            return;
        }

        // Pegar um jogador para enviar a mensagem
        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos");

        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);

        try {
            // Escrever dados da mensagem
            msgout.writeUTF("WorldUpdate");
            msgout.writeInt(worldId);

            // Finalizar mensagem
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            plugin.getLogger().info("Mensagem de atualização de mundo enviada: ID " + worldId);

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendWorldResponseMessage(String requestId, String response) {
        // Verificar se há jogadores online para enviar a mensagem
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("Não foi possível enviar resposta: nenhum jogador online");
            return;
        }

        // Pegar um jogador para enviar a mensagem
        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos");

        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);

        try {
            // Escrever dados da mensagem
            msgout.writeUTF("WorldResponse");
            msgout.writeUTF(requestId);
            msgout.writeUTF(response);

            // Finalizar mensagem
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            plugin.getLogger().info("Resposta enviada: " + requestId + " -> " + response);

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar resposta: " + e.getMessage());
            e.printStackTrace();
        }
    }
}