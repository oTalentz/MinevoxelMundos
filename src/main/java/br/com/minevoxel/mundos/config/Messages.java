package br.com.minevoxel.mundos.config;

import br.com.minevoxel.mundos.MinevoxelMundos;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

public class Messages {

    private final MinevoxelMundos plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix;

    public Messages() {
        this.plugin = MinevoxelMundos.getInstance();
        loadMessages();
    }

    public Messages(MinevoxelMundos plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        // Verificar se o arquivo existe
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        // Carregar arquivo
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Comparar com o arquivo padrão para adicionar mensagens faltantes
        matchMessages();

        // Carregar prefixo
        prefix = ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("prefix", "&a[MinevoxelMundos] &r"));
    }

    private void matchMessages() {
        try {
            boolean hasUpdated = false;
            InputStream is = plugin.getResource("messages.yml");

            if (is != null) {
                YamlConfiguration defMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));

                for (String key : defMessages.getKeys(true)) {
                    if (!messagesConfig.contains(key)) {
                        messagesConfig.set(key, defMessages.get(key));
                        hasUpdated = true;
                    }
                }

                if (hasUpdated) {
                    messagesConfig.save(messagesFile);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao atualizar messages.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reloadMessages() {
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("prefix", "&a[MinevoxelMundos] &r"));
    }

    public String getPrefix() {
        return prefix;
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public String getRawMessage(String path) {
        String message = messagesConfig.getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String formatMessage(String path, Object... args) {
        String message = getRawMessage(path);
        return prefix + MessageFormat.format(message, args);
    }
}