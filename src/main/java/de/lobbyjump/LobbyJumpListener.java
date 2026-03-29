package de.lobbyjump;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class LobbyJumpListener implements Listener {

    private static final int      TELEPORT_HEIGHT = 20;
    private static final int      FALL_KILL_Y     = 15;
    private static final Material WOOL            = Material.LIME_WOOL;
    private static final Material POINT           = Material.EMERALD_BLOCK;

    private static final int FLAT_MIN = 2;
    private static final int FLAT_MAX = 5;
    private static final int TILT_MIN = 2;
    private static final int TILT_MAX = 4;

    private final LobbyJump plugin;
    private final Random random = new Random();
    private final Map<UUID, GameSession> sessions = new HashMap<>();

    public LobbyJumpListener(LobbyJump plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPressurePlate(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL)                           return;
        if (e.getClickedBlock() == null)                                return;
        if (e.getClickedBlock().getType()
                != Material.LIGHT_WEIGHTED_PRESSURE_PLATE)              return;
        Player player = e.getPlayer();
        if (sessions.containsKey(player.getUniqueId()))                 return;
        startGame(player, e.getClickedBlock().getLocation());
    }

    private void startGame(Player player, Location plateLoc) {
        Location firstBlock = plateLoc.clone().add(0, TELEPORT_HEIGHT, 0);
        firstBlock.getBlock().setType(POINT);

        Location spawnLoc = firstBlock.clone().add(0.5, 1, 0.5);
        spawnLoc.setYaw(player.getLocation().getYaw());
        spawnLoc.setPitch(0);
        player.teleport(spawnLoc);

        Location secondBlock = findNextBlock(firstBlock);
        secondBlock.getBlock().setType(WOOL);
        BlockDisplay glowEntity = spawnGlowDisplay(secondBlock, WOOL);

        GameSession session = new GameSession(player, plateLoc.clone(), firstBlock, secondBlock, glowEntity);
        sessions.put(player.getUniqueId(), session);

        session.task = plugin.getServer().getScheduler().runTaskTimer(plugin,
            () -> tickSession(player, session), 2L, 2L);

        showTargetParticles(secondBlock);
        player.sendMessage("§a§lLobbyJump §fgestartet!");
        player.sendMessage("§7/lobbyjump stop §7zum Beenden.");
        player.playSound(spawnLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    // ── Spawnt ein leuchtendes BlockDisplay minimal größer als der Block ──
    // Skalierung 1.02 + Offset -0.01 → kein Z-Fighting mit dem echten Block
    private BlockDisplay spawnGlowDisplay(Location blockLoc, Material mat) {
        float scale  = 0.99f;
        float offset = -(scale - 1f) / 2f; // zentriert: -0.01

        Location displayLoc = blockLoc.clone();
        displayLoc.setYaw(0);
        displayLoc.setPitch(0);

        BlockData data = Bukkit.createBlockData(mat);
        BlockDisplay display = blockLoc.getWorld().spawn(displayLoc, BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setGlowing(true);
            bd.setPersistent(false);
            bd.setVisibleByDefault(true);
            bd.setTransformation(new Transformation(
                new Vector3f(offset, offset, offset),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
            ));
        });
        return display;
    }

    private void removeGlowDisplay(BlockDisplay display) {
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private void tickSession(Player player, GameSession session) {
        if (!player.isOnline()) { endGame(player.getUniqueId(), false); return; }

        if (player.getLocation().getY() < session.currentBlock.getY() - FALL_KILL_Y) {
            endGame(player.getUniqueId(), true);
            return;
        }

        if (session.waitingForRemove) return;

        Block underFeet = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

        boolean onNext =
            underFeet.getX() == session.nextBlock.getBlockX() &&
            underFeet.getY() == session.nextBlock.getBlockY() &&
            underFeet.getZ() == session.nextBlock.getBlockZ();

        if (onNext && !session.triggeredThisBlock) {
            session.triggeredThisBlock = true;
            session.waitingForRemove   = true;
            session.score++;
            onLandOnNextBlock(player, session);
        }

        session.particleTick++;
        if (session.particleTick >= 4) {
            session.particleTick = 0;
            showPulseParticle(session.nextBlock);
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§eScore§8: §a" + session.score));
    }

    private void onLandOnNextBlock(Player player, GameSession session) {
        Location oldBlock    = session.currentBlock.clone();
        Location landedBlock = session.nextBlock.clone();
        BlockDisplay oldGlow = session.glowDisplay; // Glow-Entity des gelandeten Blocks entfernen

        player.playSound(player.getLocation(), Sound.ENTITY_SLIME_SQUISH_SMALL, 1f, 1f);
        landedBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
            landedBlock.clone().add(0.5, 0.8, 0.5), 15, 0.3, 0.2, 0.3, 0);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Glow-Display des gelandeten Blocks entfernen (Spieler steht drauf, kein Glow nötig)
            removeGlowDisplay(oldGlow);

            // Alten Block entfernen
            oldBlock.getBlock().setType(Material.AIR);
            oldBlock.getWorld().spawnParticle(Particle.POOF,
                oldBlock.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);

            session.currentBlock = landedBlock;
            session.currentBlock.getBlock().setType(POINT);

            // Neuen Zielblock + Glow-Display spawnen
            Location newTarget = findNextBlock(landedBlock);
            newTarget.getBlock().setType(WOOL);
            BlockDisplay newGlow = spawnGlowDisplay(newTarget, WOOL);

            session.nextBlock    = newTarget;
            session.glowDisplay  = newGlow;

            session.triggeredThisBlock = false;
            session.waitingForRemove   = false;

            showTargetParticles(newTarget);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
        }, 5L);
    }

    private Location findNextBlock(Location from) {
        World world = from.getWorld();

        for (int attempt = 0; attempt < 50; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int dy = random.nextInt(3) - 1;

            int minDist = (dy == 0) ? FLAT_MIN : TILT_MIN;
            int maxDist = (dy == 0) ? FLAT_MAX : TILT_MAX;
            int dist    = minDist + random.nextInt(maxDist - minDist + 1);

            int nx = from.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int ny = from.getBlockY() + dy;
            int nz = from.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            if (world.getBlockAt(nx, ny,     nz).getType() == Material.AIR &&
                world.getBlockAt(nx, ny + 1, nz).getType() == Material.AIR &&
                world.getBlockAt(nx, ny + 2, nz).getType() == Material.AIR) {
                return new Location(world, nx, ny, nz);
            }
        }
        return from.clone().add(2, 0, 0);
    }

    private void showTargetParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD,
            loc.clone().add(0.5, 1.0, 0.5), 25, 0.2, 0.5, 0.2, 0.08);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
            loc.clone().add(0.5, 0.8, 0.5), 8, 0.2, 0.2, 0.2, 0);
    }

    private void showPulseParticle(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD,
            loc.clone().add(0.5, 1.1, 0.5), 2, 0.1, 0.05, 0.1, 0.02);
    }

    private void endGame(UUID uuid, boolean fell) {
        GameSession session = sessions.remove(uuid);
        if (session == null) return;
        if (session.task != null) session.task.cancel();

        // Glow-Entity aufräumen
        removeGlowDisplay(session.glowDisplay);

        if (session.currentBlock.getBlock().getType() == POINT)
            session.currentBlock.getBlock().setType(Material.AIR);
        if (session.nextBlock.getBlock().getType() == WOOL)
            session.nextBlock.getBlock().setType(Material.AIR);

        Player player = session.player;
        if (!player.isOnline()) return;

        if (fell) {
            Location back = session.startPlate.clone().add(0.5, 1, 0.5);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> player.teleport(back), 5L);
            player.sendMessage("§c§lAbgestürzt! §eBlöcke geschafft: §6" + session.score);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
        }
    }

    public void forceEndGame(Player player) { endGame(player.getUniqueId(), false); }
    public void cleanupAll() { new HashSet<>(sessions.keySet()).forEach(uuid -> endGame(uuid, false)); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { endGame(e.getPlayer().getUniqueId(), false); }

    private static class GameSession {
        final Player   player;
        final Location startPlate;
        Location     currentBlock;
        Location     nextBlock;
        BlockDisplay glowDisplay;   // Glow-Entity des aktuellen Zielblocks
        BukkitTask   task;
        boolean triggeredThisBlock = false;
        boolean waitingForRemove   = false;
        int     particleTick       = 0;
        int     score              = 0;

        GameSession(Player p, Location plate, Location cur, Location next, BlockDisplay glow) {
            this.player       = p;
            this.startPlate   = plate;
            this.currentBlock = cur;
            this.nextBlock    = next;
            this.glowDisplay  = glow;
        }
    }
}
