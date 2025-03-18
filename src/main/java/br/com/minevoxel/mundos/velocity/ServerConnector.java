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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ServerConnector implements PluginMessageListener {

    private final MinevoxelMundos plugin;
    private Map<UUID, CompletableFuture<String>> pendingResponses = new HashMap<>();

    public ServerConnector(MinevoxelMundos plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord") && !channel.equals("minevoxel:mundos")) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        switch (subchannel) {
            case "WorldCreated":
                handleWorldCreatedMessage(in);
                break;
            case "WorldTeleport":
                handleWorldTeleportMessage(in, player);
                break;
            case "WorldResponse":
                handleWorldResponseMessage(in);
                break;
            default:
                // Ignorar outros subcanais que não são relevantes
                break;
        }
    }

    private void handleWorldCreatedMessage(ByteArrayDataInput in) {
        // Usado no servidor Worlds para receber notificação de que um mundo foi criado
        String worldName = in.readUTF();
        int worldId = in.readInt();

        // Verificar se estamos no servidor de mundos
        if (!plugin.isWorldsServer()) {
            plugin.getLogger().warning("Recebido mensagem WorldCreated mas não estamos no servidor de Worlds!");
            return;
        }

        // Buscar dados completos do mundo no banco de dados
        plugin.getDatabaseManager().getWorld(worldId).thenAccept(worldData -> {
            if (worldData != null) {
                // Carregar ou criar o mundo
                plugin.getWorldManager().loadWorld(worldData);
                plugin.getLogger().info("Mundo " + worldName + " (ID: " + worldId + ") criado com sucesso!");
            } else {
                plugin.getLogger().warning("Falha ao carregar dados do mundo ID: " + worldId);
            }
        });
    }

    private void handleWorldTeleportMessage(ByteArrayDataInput in, Player player) {
        // Usado no servidor Worlds para teleportar um jogador para um mundo específico
        String worldName = in.readUTF();

        // Verificar se estamos no servidor de mundos
        if (!plugin.isWorldsServer()) {
            plugin.getLogger().warning("Recebido mensagem WorldTeleport mas não estamos no servidor de Worlds!");
            return;
        }

        // Teleportar o jogador para o mundo
        plugin.getTeleportManager().teleportToWorld(player, worldName);
    }

    private void handleWorldResponseMessage(ByteArrayDataInput in) {
        // Resposta para uma solicitação anterior
        UUID requestId = UUID.fromString(in.readUTF());
        String response = in.readUTF();

        CompletableFuture<String> future = pendingResponses.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    // Métodos para enviar mensagens

    public void sendToServer(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    public void sendCreateWorldMessage(WorldData worldData) {
        // Enviar mensagem para criar um mundo no servidor Worlds
        // Esta mensagem é enviada do servidor Lobby para o servidor Worlds

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("Não foi possível enviar mensagem: nenhum jogador online");
            return;
        }

        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos"); // Canal personalizado

        // Criar stream de dados para o payload
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        try {
            // Formato da mensagem
            msgOut.writeUTF("WorldCreated");
            msgOut.writeUTF(worldData.getWorldName());
            msgOut.writeInt(worldData.getId());

            // Finalizar mensagem
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar mensagem de criação de mundo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendTeleportRequest(Player player, String worldName) {
        // Enviar mensagem para teleportar um jogador para um mundo específico
        // Esta mensagem é enviada do servidor Lobby para o servidor Worlds

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos"); // Canal personalizado

        // Criar stream de dados para o payload
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        try {
            // Formato da mensagem
            msgOut.writeUTF("WorldTeleport");
            msgOut.writeUTF(player.getUniqueId().toString());
            msgOut.writeUTF(worldName);

            // Finalizar mensagem
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar mensagem de teleporte: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<String> sendWorldStatusRequest(String worldName) {
        // Enviar mensagem para verificar o status de um mundo no servidor Worlds
        // Esta mensagem é enviada do servidor Lobby para o servidor Worlds

        UUID requestId = UUID.randomUUID();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingResponses.put(requestId, future);

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().warning("Não foi possível enviar mensagem: nenhum jogador online");
            future.complete("ERROR");
            return future;
        }

        Player player = Bukkit.getOnlinePlayers().iterator().next();

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // Enviar para todos os servidores
        out.writeUTF("minevoxel:mundos"); // Canal personalizado

        // Criar stream de dados para o payload
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);

        try {
            // Formato da mensagem
            msgOut.writeUTF("WorldStatus");
            msgOut.writeUTF(requestId.toString());
            msgOut.writeUTF(worldName);

            // Finalizar mensagem
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());

            // Enviar mensagem
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

            // Definir timeout após 5 segundos
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                CompletableFuture<String> pendingFuture = pendingResponses.remove(requestId);
                if (pendingFuture != null && !pendingFuture.isDone()) {
                    pendingFuture.complete("TIMEOUT");
                }
            }, 100L); // 5 segundos (100 ticks)

        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao enviar mensagem de status de mundo: " + e.getMessage());
            e.printStackTrace();
            future.complete("ERROR");
        }

        return future;
    }
}