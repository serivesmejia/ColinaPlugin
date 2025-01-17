package org.deltacv.colina.arena;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Objects;

public class Arena extends BukkitRunnable {

    ArenaManager manager;

    Location lobbyLocation;
    Location gameLocation;

    public ArrayList<Player> players = new ArrayList<>();
    ArenaStatus status = ArenaStatus.LOBBY;

    long creationTimestamp = System.currentTimeMillis();
    long startingCountdownTimestamp = -1;
    long gameStartTimestamp = -1;

    boolean hasSentStartWithMinPlayersMessage = false;

    int lastStartingCountdownSeconds = -1;

    public Arena(ArenaManager manager, Location lobbyLocation, Location gameLocation) {
        this.manager = manager;

        this.lobbyLocation = lobbyLocation;
        this.gameLocation = gameLocation;
    }

    @Override
    public void run() {
        PotionEffect healthEffect = new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 10000, false, false, false);
        PotionEffect saturationEffect = new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 10000, false, false, false);

        for(Player player : players) {
            player.addPotionEffect(healthEffect);
            player.addPotionEffect(saturationEffect);
            player.setGameMode(GameMode.ADVENTURE);
        }

        switch(status) {
            case LOBBY -> {
                int lobbyDelayHalf = manager.getLobbyDelaySeconds() / 2;

                if(players.size() >= manager.getMinPlayersPerArena() && elapsedSecondsSinceCreation() >= lobbyDelayHalf && !hasSentStartWithMinPlayersMessage) {
                    hasSentStartWithMinPlayersMessage = true;

                    for(Player player : players) {
                        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "La partida empezará en " + lobbyDelayHalf + " segundos si hay suficientes jugadores.");
                    }
                } else if(players.size() >= manager.getMinPlayersPerArena() && elapsedSecondsSinceCreation() >= manager.getLobbyDelaySeconds()) {
                    status = ArenaStatus.STARTING;
                    break;
                } else if(players.size() == manager.getMaxPlayersPerArena()) {
                    // if arena is full, start countdown
                    status = ArenaStatus.STARTING;
                    break;
                } else if(players.size() < manager.getMinPlayersPerArena()) {
                    hasSentStartWithMinPlayersMessage = false;
                    creationTimestamp = System.currentTimeMillis();
                }

                for(Player player : players) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.YELLOW + "" + ChatColor.BOLD + "Esperando jugadores... (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")"));
                }
            }

            case STARTING -> {
                // count from five to zero and tp to game

                if(startingCountdownTimestamp == -1) {
                    startingCountdownTimestamp = System.currentTimeMillis();
                }

                int seconds = 10 - (int) elapsedSecondsSinceStartingCountdown();

                if(seconds == 0) {
                    status = ArenaStatus.BEGIN;
                } else if(seconds != lastStartingCountdownSeconds) {
                    // broadcast countdown message
                    for(Player player : players) {
                        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "Empezando en " + seconds + "...");
                        player.playSound(player.getLocation(), "block.note_block.pling", 1, 1);

                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.RED + "" + ChatColor.BOLD + "Todos listos !"));
                    }
                }

                lastStartingCountdownSeconds = seconds;
            }

            case BEGIN -> {
                manager.log.info("Begin game in arena " + newWorldName);
                gameStartTimestamp = System.currentTimeMillis();

                // give stick with knockback
                ItemStack stick = new ItemStack(Material.STICK);

                ItemMeta im = Objects.requireNonNull(stick.getItemMeta());
                im.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Empuja-nator 2000");
                stick.setItemMeta(im);

                // give enchantment to stick
                stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 6);

                for(Player player : players) {
                    // give stick with knockback
                    player.getInventory().addItem(stick);
                    player.teleport(gameLocation);
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "A pelear !");
                    player.playSound(player.getLocation(), "entity.ender_dragon.growl", 1, 1);
                }

                status = ArenaStatus.PLAYING;
            }

            case PLAYING -> {
            }
        }
    }

    public void addPlayer(Player player) {
        players.add(player);

        player.getInventory().clear();
        player.teleport(lobbyLocation);

        for(Player p : players) {
            p.sendMessage(ChatColor.YELLOW + player.getName() + " se ha unido a la partida. (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")");
        }
    }

    public double elapsedSecondsSinceCreation() {
        return (System.currentTimeMillis() - creationTimestamp) / 1000.0;
    }

    public double elapsedSecondsSinceStartingCountdown() {
        return (System.currentTimeMillis() - startingCountdownTimestamp) / 1000.0;
    }

    enum ArenaStatus {
        LOBBY, STARTING,
        BEGIN,
        PLAY_COUNTDOWN, PLAYING
    }

}
