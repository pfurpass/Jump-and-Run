package de.lobbyjump;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LobbyJump extends JavaPlugin {

    private LobbyJumpListener listener;

    @Override
    public void onEnable() {
        listener = new LobbyJumpListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getLogger().info("LobbyJump wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.cleanupAll();
        }
        getLogger().info("LobbyJump wurde deaktiviert!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur für Spieler!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            listener.forceEndGame(player);
            player.sendMessage("§cSpiel beendet.");
            return true;
        }

        player.sendMessage("§eNutzung: /lobbyjump stop");
        return true;
    }
}
