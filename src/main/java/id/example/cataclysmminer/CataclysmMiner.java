package id.example.cataclysmminer;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class CataclysmMiner extends JavaPlugin implements Listener {

    private final Map<OreKey, OreNode> nodes = new HashMap<>();
    private final Map<OreKey, Long> cooldownUntil = new HashMap<>();
    private final Set<String> allowedWorlds = new HashSet<>();

    private AuraSkillsApi auraSkills;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Plugin aura = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (aura == null || !aura.isEnabled()) {
            getLogger().severe("AuraSkills is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            auraSkills = AuraSkillsApi.get();
        } catch (Throwable t) {
            getLogger().severe("Failed to initialize AuraSkills API: " + t.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("CataclysmMiner enabled. Nodes: " + nodes.size());
    }

    @Override
    public void onDisable() {
        cooldownUntil.clear();
        nodes.clear();
    }

    private void loadData() {
        nodes.clear();
        allowedWorlds.clear();
        allowedWorlds.addAll(getConfig().getStringList("settings.allowed-worlds"));

        var section = getConfig().getConfigurationSection("ores");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "ores." + id;
            String worldName = getConfig().getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);

            if (world == null) {
                getLogger().warning("Skipping " + id + ": world not found: " + worldName);
                continue;
            }

            if (!allowedWorlds.contains(worldName)) {
                getLogger().warning("Skipping " + id + ": world not in allowed-worlds: " + worldName);
                continue;
            }

            int x = getConfig().getInt(path + ".x");
            int y = getConfig().getInt(path + ".y");
            int z = getConfig().getInt(path + ".z");

            Material block = parseMaterial(getConfig().getString(path + ".block"));
            if (block == null || block.isAir()) {
                getLogger().warning("Skipping " + id + ": invalid block.");
                continue;
            }

            long cooldown = Math.max(0L, getConfig().getLong(path + ".cooldown-seconds", 15L));
            int miningXp = Math.max(0, getConfig().getInt(path + ".mining-xp", 500));

            List<Reward> rewards = new ArrayList<>();
            var rewardsSection = getConfig().getConfigurationSection(path + ".rewards");

            if (rewardsSection != null) {
                for (String rewardId : rewardsSection.getKeys(false)) {
                    String rp = path + ".rewards." + rewardId;
                    String mode = getConfig().getString(rp + ".mode", "VANILLA").toUpperCase(Locale.ROOT);
                    int min = Math.max(1, getConfig().getInt(rp + ".min-amount", 1));
                    int max = Math.max(min, getConfig().getInt(rp + ".max-amount", min));
                    double chance = Math.max(0.0, Math.min(100.0, getConfig().getDouble(rp + ".chance", 100.0)));

                    if (mode.equals("VANILLA")) {
                        Material material = parseMaterial(getConfig().getString(rp + ".material"));
                        if (material != null && !material.isAir()) {
                            rewards.add(Reward.vanilla(material, min, max, chance));
                        }
                    } else if (mode.equals("MMOITEMS")) {
                        String type = getConfig().getString(rp + ".type");
                        String itemId = getConfig().getString(rp + ".id");
                        if (type != null && itemId != null && !type.isBlank() && !itemId.isBlank()) {
                            rewards.add(Reward.mmoItems(type, itemId, min, max, chance));
                        }
                    }
                }
            }

            OreKey key = OreKey.from(world, x, y, z);
            nodes.put(key, new OreNode(id, key, block, cooldown, miningXp, rewards));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!allowedWorlds.contains(block.getWorld().getName())) return;

        OreKey key = OreKey.from(block.getLocation());
        OreNode node = nodes.get(key);
        if (node == null) return;

        long now = System.currentTimeMillis();
        long until = cooldownUntil.getOrDefault(key, 0L);

        if (until > now || block.getType() != node.block()) {
            event.setCancelled(true);
            sendMessage(player, "settings.cooldown-message");
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        cooldownUntil.put(key, now + node.cooldownSeconds() * 1000L);
        block.setType(Material.BEDROCK, false);

        if (node.miningXp() > 0) {
            giveMiningXp(player, node.miningXp());
        }

        List<String> rewardNames = new ArrayList<>();

        for (Reward reward : node.rewards()) {
            if (ThreadLocalRandom.current().nextDouble(100.0) > reward.chance()) continue;

            int amount = ThreadLocalRandom.current().nextInt(
                    reward.minAmount(), reward.maxAmount() + 1
            );

            if (reward.mode() == RewardMode.VANILLA) {
                giveSafely(player, new ItemStack(reward.material(), amount));
                rewardNames.add(amount + "x " + pretty(reward.material()));
            } else {
                int given = giveMMOItems(player, reward.mmoType(), reward.mmoId(), amount);
                if (given > 0) {
                    rewardNames.add(given + "x MMOItems:" + reward.mmoType() + ":" + reward.mmoId());
                }
            }
        }

        sendRewardMessage(player, rewardNames, node.miningXp());

        long delay = node.cooldownSeconds() * 20L;
        Bukkit.getScheduler().runTaskLater(this, () -> restore(node), delay);
    }

    private void restore(OreNode node) {
        World world = Bukkit.getWorld(node.key().worldId());
        if (world == null) {
            cooldownUntil.remove(node.key());
            return;
        }

        Block block = world.getBlockAt(node.key().x(), node.key().y(), node.key().z());
        boolean safe = getConfig().getBoolean("settings.safe-restore", true);

        if (!safe || block.getType() == Material.BEDROCK) {
            block.setType(node.block(), false);
        }

        cooldownUntil.remove(node.key());
    }

    private void giveMiningXp(Player player, int amount) {
        try {
            SkillsUser user = auraSkills.getUser(player.getUniqueId());
            if (user != null) {
                user.addSkillXp(Skills.MINING, amount);
            } else {
                getLogger().warning("AuraSkills user unavailable: " + player.getName());
            }
        } catch (Throwable t) {
            getLogger().severe("AuraSkills XP error: " + t.getMessage());
        }
    }

    private int giveMMOItems(Player player, String type, String id, int amount) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (plugin == null || !plugin.isEnabled()) {
            getLogger().warning("MMOItems not installed: " + type + ":" + id);
            return 0;
        }

        try {
            Class<?> clazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object instance = clazz.getField("plugin").get(null);
            Method getItem = clazz.getMethod("getItem", String.class, String.class);
            Object result = getItem.invoke(instance, type, id);

            if (!(result instanceof ItemStack)) {
                getLogger().warning("MMOItems item not found: " + type + ":" + id);
                return 0;
            }

            ItemStack template = (ItemStack) result;
            for (int i = 0; i < amount; i++) giveSafely(player, template.clone());
            return amount;
        } catch (Throwable t) {
            getLogger().severe("MMOItems reward error for " + type + ":" + id + ": " + t.getMessage());
            return 0;
        }
    }

    private void giveSafely(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void sendRewardMessage(Player player, List<String> rewards, int xp) {
        String path = rewards.isEmpty() ? "settings.no-reward-message" : "settings.reward-message";
        String message = getConfig().getString(path, "");
        if (message == null || message.isBlank()) return;

        message = message
                .replace("{reward}", rewards.isEmpty() ? "nothing" : String.join(", ", rewards))
                .replace("{xp}", String.valueOf(xp));

        player.sendMessage(color(message));
    }

    private void sendMessage(Player player, String path) {
        String message = getConfig().getString(path, "");
        if (message != null && !message.isBlank()) player.sendMessage(color(message));
    }

    private static Material parseMaterial(String name) {
        if (name == null) return null;
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("cataclysmminer")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadData();
            sender.sendMessage(color("&aCataclysmMiner reloaded. Nodes: " + nodes.size()));
            return true;
        }

        sender.sendMessage(color("&eUsage: /cataclysmminer reload"));
        return true;
    }

    private enum RewardMode { VANILLA, MMOITEMS }

    private record Reward(
            RewardMode mode,
            Material material,
            String mmoType,
            String mmoId,
            int minAmount,
            int maxAmount,
            double chance
    ) {
        static Reward vanilla(Material material, int min, int max, double chance) {
            return new Reward(RewardMode.VANILLA, material, null, null, min, max, chance);
        }

        static Reward mmoItems(String type, String id, int min, int max, double chance) {
            return new Reward(RewardMode.MMOITEMS, null, type, id, min, max, chance);
        }
    }

    private record OreNode(
            String id,
            OreKey key,
            Material block,
            long cooldownSeconds,
            int miningXp,
            List<Reward> rewards
    ) {}

    private record OreKey(UUID worldId, int x, int y, int z) {
        static OreKey from(Location location) {
            return new OreKey(
                    Objects.requireNonNull(location.getWorld()).getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }

        static OreKey from(World world, int x, int y, int z) {
            return new OreKey(world.getUID(), x, y, z);
        }
    }
}
