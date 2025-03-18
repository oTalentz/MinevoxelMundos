package br.com.minevoxel.mundos.models;

import org.bukkit.GameMode;
import org.bukkit.WorldType;

import java.util.*;

public class WorldData {

    private int id;
    private String worldName;
    private String displayName;
    private UUID ownerUUID;
    private String ownerName;
    private Date creationDate;
    private Date lastAccessed;

    // Configurações de mundo
    private String worldType = "NORMAL"; // NORMAL, FLAT, AMPLIFIED, etc.
    private String environment = "NORMAL"; // NORMAL, NETHER, THE_END
    private boolean pvp = false;
    private boolean generateStructures = true;
    private long seed = 0;
    private GameMode gameMode = GameMode.SURVIVAL;

    // Configurações de física
    private boolean physics = true;
    private boolean waterFlow = true;
    private boolean lavaFlow = true;
    private boolean fireSpread = true;
    private boolean leafDecay = true;
    private boolean redstone = true;
    private boolean mobSpawning = true;
    private boolean animalSpawning = true;

    // Permissões
    private Map<UUID, String> playerPermissions = new HashMap<>(); // UUID -> OWNER, BUILDER, VISITOR
    private Set<UUID> allowedPlayers = new HashSet<>();
    private boolean isPublic = false;

    // Flags adicionais
    private boolean loaded = false;
    private boolean modified = false;

    public WorldData(int id, String worldName, UUID ownerUUID, String ownerName) {
        this.id = id;
        this.worldName = worldName;
        this.displayName = worldName;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.creationDate = new Date();
        this.lastAccessed = new Date();

        // Adicionar o dono como OWNER nas permissões
        this.playerPermissions.put(ownerUUID, "OWNER");
        this.allowedPlayers.add(ownerUUID);
    }

    // Métodos para configurações de permissão
    public boolean canPlayerBuild(UUID playerUUID) {
        if (playerUUID.equals(ownerUUID)) return true;

        String permission = playerPermissions.getOrDefault(playerUUID, "");
        return "OWNER".equals(permission) || "BUILDER".equals(permission);
    }

    public boolean canPlayerVisit(UUID playerUUID) {
        if (isPublic) return true;
        if (playerUUID.equals(ownerUUID)) return true;

        return allowedPlayers.contains(playerUUID);
    }

    public void addPlayerPermission(UUID playerUUID, String permission) {
        playerPermissions.put(playerUUID, permission);
        allowedPlayers.add(playerUUID);
    }

    public void removePlayerPermission(UUID playerUUID) {
        if (!playerUUID.equals(ownerUUID)) {
            playerPermissions.remove(playerUUID);
            allowedPlayers.remove(playerUUID);
        }
    }

    // Métodos para serialização
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();

        // Dados básicos
        data.put("id", id);
        data.put("worldName", worldName);
        data.put("displayName", displayName);
        data.put("ownerUUID", ownerUUID.toString());
        data.put("ownerName", ownerName);
        data.put("creationDate", creationDate.getTime());
        data.put("lastAccessed", lastAccessed.getTime());

        // Configurações de mundo
        data.put("worldType", worldType);
        data.put("environment", environment);
        data.put("pvp", pvp);
        data.put("generateStructures", generateStructures);
        data.put("seed", seed);
        data.put("gameMode", gameMode.name());

        // Configurações de física
        data.put("physics", physics);
        data.put("waterFlow", waterFlow);
        data.put("lavaFlow", lavaFlow);
        data.put("fireSpread", fireSpread);
        data.put("leafDecay", leafDecay);
        data.put("redstone", redstone);
        data.put("mobSpawning", mobSpawning);
        data.put("animalSpawning", animalSpawning);

        // Permissões
        Map<String, String> serializedPermissions = new HashMap<>();
        for (Map.Entry<UUID, String> entry : playerPermissions.entrySet()) {
            serializedPermissions.put(entry.getKey().toString(), entry.getValue());
        }
        data.put("playerPermissions", serializedPermissions);

        List<String> serializedAllowedPlayers = new ArrayList<>();
        for (UUID uuid : allowedPlayers) {
            serializedAllowedPlayers.add(uuid.toString());
        }
        data.put("allowedPlayers", serializedAllowedPlayers);

        data.put("isPublic", isPublic);

        return data;
    }

    public static WorldData deserialize(Map<String, Object> data) {
        int id = (int) data.get("id");
        String worldName = (String) data.get("worldName");
        UUID ownerUUID = UUID.fromString((String) data.get("ownerUUID"));
        String ownerName = (String) data.get("ownerName");

        WorldData worldData = new WorldData(id, worldName, ownerUUID, ownerName);

        worldData.setDisplayName((String) data.get("displayName"));
        worldData.setCreationDate(new Date((long) data.get("creationDate")));
        worldData.setLastAccessed(new Date((long) data.get("lastAccessed")));

        // Configurações de mundo
        worldData.setWorldType((String) data.get("worldType"));
        worldData.setEnvironment((String) data.get("environment"));
        worldData.setPvp((boolean) data.get("pvp"));
        worldData.setGenerateStructures((boolean) data.get("generateStructures"));
        worldData.setSeed((long) data.get("seed"));
        worldData.setGameMode(GameMode.valueOf((String) data.get("gameMode")));

        // Configurações de física
        worldData.setPhysics((boolean) data.get("physics"));
        worldData.setWaterFlow((boolean) data.get("waterFlow"));
        worldData.setLavaFlow((boolean) data.get("lavaFlow"));
        worldData.setFireSpread((boolean) data.get("fireSpread"));
        worldData.setLeafDecay((boolean) data.get("leafDecay"));
        worldData.setRedstone((boolean) data.get("redstone"));
        worldData.setMobSpawning((boolean) data.get("mobSpawning"));
        worldData.setAnimalSpawning((boolean) data.get("animalSpawning"));

        // Permissões
        Map<String, String> serializedPermissions = (Map<String, String>) data.get("playerPermissions");
        for (Map.Entry<String, String> entry : serializedPermissions.entrySet()) {
            worldData.getPlayerPermissions().put(UUID.fromString(entry.getKey()), entry.getValue());
        }

        List<String> serializedAllowedPlayers = (List<String>) data.get("allowedPlayers");
        for (String uuidString : serializedAllowedPlayers) {
            worldData.getAllowedPlayers().add(UUID.fromString(uuidString));
        }

        worldData.setPublic((boolean) data.get("isPublic"));

        return worldData;
    }

    // Getters e Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public String getWorldType() {
        return worldType;
    }

    public void setWorldType(String worldType) {
        this.worldType = worldType;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isPvp() {
        return pvp;
    }

    public void setPvp(boolean pvp) {
        this.pvp = pvp;
    }

    public boolean isGenerateStructures() {
        return generateStructures;
    }

    public void setGenerateStructures(boolean generateStructures) {
        this.generateStructures = generateStructures;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public boolean isPhysics() {
        return physics;
    }

    public void setPhysics(boolean physics) {
        this.physics = physics;
    }

    public boolean isWaterFlow() {
        return waterFlow;
    }

    public void setWaterFlow(boolean waterFlow) {
        this.waterFlow = waterFlow;
    }

    public boolean isLavaFlow() {
        return lavaFlow;
    }

    public void setLavaFlow(boolean lavaFlow) {
        this.lavaFlow = lavaFlow;
    }

    public boolean isFireSpread() {
        return fireSpread;
    }

    public void setFireSpread(boolean fireSpread) {
        this.fireSpread = fireSpread;
    }

    public boolean isLeafDecay() {
        return leafDecay;
    }

    public void setLeafDecay(boolean leafDecay) {
        this.leafDecay = leafDecay;
    }

    public boolean isRedstone() {
        return redstone;
    }

    public void setRedstone(boolean redstone) {
        this.redstone = redstone;
    }

    public boolean isMobSpawning() {
        return mobSpawning;
    }

    public void setMobSpawning(boolean mobSpawning) {
        this.mobSpawning = mobSpawning;
    }

    public boolean isAnimalSpawning() {
        return animalSpawning;
    }

    public void setAnimalSpawning(boolean animalSpawning) {
        this.animalSpawning = animalSpawning;
    }

    public Map<UUID, String> getPlayerPermissions() {
        return playerPermissions;
    }

    public void setPlayerPermissions(Map<UUID, String> playerPermissions) {
        this.playerPermissions = playerPermissions;
    }

    public Set<UUID> getAllowedPlayers() {
        return allowedPlayers;
    }

    public void setAllowedPlayers(Set<UUID> allowedPlayers) {
        this.allowedPlayers = allowedPlayers;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }
}