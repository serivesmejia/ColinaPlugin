package org.deltacv.colina;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.deltacv.colina.arena.Arena;
import org.deltacv.colina.arena.ArenaManager;

import java.util.Objects;

public final class Colina extends JavaPlugin implements Listener {
    ArenaManager arenaManager;

    @Override
    public void onEnable() {
        arenaManager = new ArenaManager(this);
        arenaManager.loadWorldData();
        arenaManager.purgeUnusedArenas();

        Objects.requireNonNull(
                this.getCommand("colina")
        ).setExecutor(new ColinaCommand(arenaManager));

        this.saveDefaultConfig();
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        arenaManager.saveWorldData();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();

        for(Arena arena : arenaManager.activeArenas.keySet()) {
            if(arena.players.contains(p)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
