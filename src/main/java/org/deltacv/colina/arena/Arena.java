package org.deltacv.colina.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.w3c.dom.css.RGBColor;

import java.util.*;
import java.util.stream.Collectors;


public class Arena extends BukkitRunnable {

    ArenaManager manager;

    Location lobbyLocation;
    public Location gameLocation;

    ArrayList<Player> playersToRemove = new ArrayList<>();

    public ArrayList<Player> players = new ArrayList<>();
    public ArenaStatus status = ArenaStatus.LOBBY;

    long creationTimestamp = System.currentTimeMillis();
    long startingCountdownTimestamp = -1;
    long gameHaltTimestamp = -1;
    long gameStartTimestamp = -1;

    int previousRemainingTimeSecs = -1;
    long lastZoneAwardTimestamp = -1;
    long resultsTimestamp = -1;
    long resultsLastFireworkTimestamp = System.currentTimeMillis();

    Player winningPlayer = null;

    HashMap<Player, Integer> playerScores = new HashMap<>();
    HashMap<Player, Boolean> playerZoneStatuses = new HashMap<>();

    PotionEffect blindnessEffect = new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 10000, false, false, false);
    PotionEffect slownessEffect = new PotionEffect(PotionEffectType.SLOWNESS, 20 * 10, 10000, false, false, false);
    PotionEffect invisibilityEffect = new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 10, 1, false, false, false);

    Sidebar sidebar;

    boolean hasSentStartWithMinPlayersMessage = false;

    int lastStartingCountdownSeconds = -1;

    public Arena(ArenaManager manager, Location lobbyLocation, Location gameLocation) {
        this.manager = manager;

        sidebar = manager.scoreboardLibrary.createSidebar();

        sidebar.title(Component.text(ChatColor.YELLOW + "" + ChatColor.BOLD + "Rey de la Colina"));
        sidebar.line(0, Component.empty());
        sidebar.line(1, Component.text(ChatColor.GRAY + "Esperando..."));
        sidebar.line(2, Component.empty());

        this.lobbyLocation = lobbyLocation;
        this.gameLocation = gameLocation;
    }

    @Override
    public void run() {
        playersToRemove.clear();

        for (Player player : players) {
            player.setHealth(20);
            player.setSaturation(20);

            if(!player.isOnline()) {
                playersToRemove.add(player);
            }
        }

        for (Player player : playersToRemove) {
            players.remove(player);
            sidebar.removePlayer(player);

            for (Player p : players) {
                p.sendMessage(ChatColor.YELLOW + player.getName() + " ha abandonado la partida. (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")");
            }
        }

        switch (status) {
            case LOBBY -> {
                int lobbyDelayHalf = manager.getLobbyDelaySeconds() / 2;

                sidebar.line(3, Component.text(ChatColor.GRAY + "" + players.size() + "/" + manager.getMaxPlayersPerArena() + " jugadores"));

                if (players.size() >= manager.getMinPlayersPerArena() && elapsedSecondsSinceCreation() >= lobbyDelayHalf && !hasSentStartWithMinPlayersMessage) {
                    hasSentStartWithMinPlayersMessage = true;

                    for (Player player : players) {
                        player.sendMessage(ChatColor.GRAY + "La partida empezará en " + lobbyDelayHalf + " segundos si hay suficientes jugadores.");
                    }
                } else if (players.size() >= manager.getMinPlayersPerArena() && elapsedSecondsSinceCreation() >= manager.getLobbyDelaySeconds()) {
                    status = ArenaStatus.STARTING;
                    break;
                } else if (players.size() == manager.getMaxPlayersPerArena()) {
                    // if arena is full, start countdown
                    status = ArenaStatus.STARTING;
                    break;
                } else if (players.size() < manager.getMinPlayersPerArena()) {
                    hasSentStartWithMinPlayersMessage = false;
                    creationTimestamp = System.currentTimeMillis();
                }

                for (Player player : players) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.YELLOW + "" + ChatColor.BOLD + "Esperando jugadores... (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")"));
                    player.setGameMode(GameMode.ADVENTURE);
                }
            }

            case STARTING -> {
                if(players.size() < manager.getMinPlayersPerArena()) {
                    startingCountdownTimestamp = -1;
                    status = ArenaStatus.LOBBY;

                    for(Player player : players) {
                        player.sendMessage(ChatColor.RED + "No hay suficientes jugadores para empezar la partida.");
                    }

                    break;
                }

                // count from five to zero and tp to game
                sidebar.line(1, Component.text(ChatColor.GREEN + "Iniciando..."));

                if (startingCountdownTimestamp == -1) {
                    startingCountdownTimestamp = System.currentTimeMillis();
                }

                int seconds = 5 - (int) elapsedSecondsSinceStartingCountdown();

                if (seconds == 0) {
                    for(Player player : players) {
                        for(int i = 0; i < 20; i++) {
                            player.sendMessage("");
                        }
                    }
                    status = ArenaStatus.BEGIN;
                } else if (seconds != lastStartingCountdownSeconds) {
                    // broadcast countdown message
                    for (Player player : players) {
                        player.sendMessage(ChatColor.YELLOW + "Empezando en " + seconds + "...");
                        player.playSound(player.getLocation(), "block.note_block.pling", 1, 1);

                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.RED + "" + ChatColor.BOLD + "Todos listos !"));
                    }
                }

                lastStartingCountdownSeconds = seconds;
            }

            case BEGIN -> {
                manager.log.info("Begin game in arena " + gameLocation.getWorld().getName());

                spawnZoneBorder();

                for (Player player : players) {
                    player.sendMessage(ChatColor.GREEN + "La partida se acabará con el primer jugador que obtenga " + manager.getPointsToWin() + " puntos !");
                    player.teleport(gameLocation);
                }

                gameHaltTimestamp = System.currentTimeMillis();
                status = ArenaStatus.PLAY_HALT;
            }

            case PLAY_HALT -> {
                if (System.currentTimeMillis() - gameHaltTimestamp >= 3000) {
                    gameStartTimestamp = System.currentTimeMillis();
                    status = ArenaStatus.PLAYING;

                    // give stick with knockback
                    ItemStack stick = new ItemStack(Material.STICK);

                    ItemMeta im = Objects.requireNonNull(stick.getItemMeta());
                    im.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Empuja-nator 2000");
                    stick.setItemMeta(im);

                    // give enchantment to stick
                    stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 6);

                    for (Player player : players) {
                        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "A pelear !");
                        player.playSound(player.getLocation(), "entity.generic.explode", 100000, 1);

                        // give stick with knockback
                        player.getInventory().addItem(stick);

                        playerZoneStatuses.put(player, true);

                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        player.removePotionEffect(PotionEffectType.SLOWNESS);
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);

                        double randomX = (Math.random() * 2 - 1) * 4;
                        double randomZ = (Math.random() * 2 - 1) * 4;

                        player.setVelocity(new Vector(
                                (float) randomX,
                                1f,
                                (float) randomZ
                        ));
                    }
                } else {
                    for (Player player : players) {
                        player.addPotionEffect(blindnessEffect);
                        player.addPotionEffect(slownessEffect);
                        player.addPotionEffect(invisibilityEffect);

                        player.teleport(gameLocation);
                    }
                }
            }

            case PLAYING -> {
                long remainingTimeSecs = manager.getGameDurationSeconds() - (System.currentTimeMillis() - gameStartTimestamp) / 1000;
                if (previousRemainingTimeSecs == -1) {
                    previousRemainingTimeSecs = (int) remainingTimeSecs;
                }

                if (previousRemainingTimeSecs != (int) remainingTimeSecs) {
                    if (remainingTimeSecs == 30 || remainingTimeSecs == 15) {
                        for (Player player : players) {
                            player.sendMessage(ChatColor.YELLOW + "Quedan " + remainingTimeSecs + " segundos !");
                            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1, 10);
                        }
                    } else if (remainingTimeSecs <= 10 && remainingTimeSecs > 0) {
                        for (Player player : players) {
                            player.sendMessage(ChatColor.RED + "El juego se acaba en " + remainingTimeSecs + " segundos.");
                            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1, 1);
                        }
                    }
                }

                if (remainingTimeSecs <= 0 || players.size() <= 1) {
                    status = ArenaStatus.RESULTS;
                    break;
                }

                previousRemainingTimeSecs = (int) remainingTimeSecs;

                int remainingMinutes = (int) (remainingTimeSecs) / 60;
                int remainingSeconds = (int) (remainingTimeSecs) % 60;

                sidebar.line(1, Component.text(ChatColor.GRAY + "Tiempo restante: " + ChatColor.YELLOW + remainingMinutes + "m " + remainingSeconds + "s"));

                sidebar.line(3, Component.text(ChatColor.YELLOW + "Puntos para ganar: " + ChatColor.GREEN + manager.getPointsToWin()));
                sidebar.line(4, Component.empty());
                sidebar.line(5, Component.text(ChatColor.GRAY + "Top puntajes:"));

                List<Map.Entry<Player, Integer>> topScores = playerScores.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(5)
                        .toList();

                // grab at most the top 3
                for (int i = 0; i < Math.min(3, topScores.size()); i++) {
                    Map.Entry<Player, Integer> entry = topScores.get(i);
                    // sidebar with #n player name and score
                    sidebar.line(6 + i, Component.text(ChatColor.GRAY + "#" + (i + 1) + " " + ChatColor.YELLOW + entry.getKey().getName() + ChatColor.GRAY + " - " + ChatColor.YELLOW + entry.getValue() + " puntos"));
                }

                for (Player player : players) {
                    boolean wasInsideZone = playerZoneStatuses.getOrDefault(player, false);

                    boolean isInsideZone = isInsideZone(player.getLocation(), gameLocation.getBlockX(), gameLocation.getBlockZ(), manager.getZoneSize() - 0.2f, manager.getZoneSize() - 0.2f);

                    if (!isInsideZone) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.RED + "" + ChatColor.BOLD + "¡Saliste de la zona!" + ChatColor.GRAY + " - " + playerScores.getOrDefault(player, 0) + " puntos"));

                        if (wasInsideZone) {
                            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1, 1);
                        }
                    } else {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Estás en la zona!" + ChatColor.GRAY + " - " + playerScores.getOrDefault(player, 0) + " puntos"));

                        if (!wasInsideZone) {
                            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1, 1);
                        }
                    }

                    playerZoneStatuses.put(player, isInsideZone);

                    if (System.currentTimeMillis() - lastZoneAwardTimestamp > 1000) {
                        if (isInsideZone) {
                            int score = playerScores.getOrDefault(player, 0) + 1;
                            playerScores.put(player, score);
                            // noteblock tick
                            player.playSound(player.getLocation(), "block.note_block.hat", 1, 1);
                        }
                    }

                    if (playerScores.getOrDefault(player, 0) >= manager.getPointsToWin()) {
                        status = ArenaStatus.RESULTS;
                        break;
                    }
                }

                if (System.currentTimeMillis() - lastZoneAwardTimestamp > 1000) {
                    lastZoneAwardTimestamp = System.currentTimeMillis();
                }
            }

            case RESULTS -> {
                if(players.isEmpty()) {
                    status = ArenaStatus.END;
                    break;
                }

                if (resultsTimestamp == -1) {
                    resultsTimestamp = System.currentTimeMillis();
                    // get winning player
                    winningPlayer = playerScores.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);

                    for (Player player : players) {
                        player.getInventory().clear();
                        player.playSound(player.getLocation(), "entity.player.levelup", 1, 1);
                        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "¡" + winningPlayer.getName() + " ha ganado la partida!");
                    }

                    manager.econ.depositPlayer(winningPlayer, 1);
                    winningPlayer.sendMessage(ChatColor.GREEN + "Has ganado 1 punto por ganar la partida.");
                } else {
                    for (Player player : players) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.YELLOW + "" + ChatColor.BOLD + "¡" + winningPlayer.getName() + " ha ganado la partida!"));
                    }

                    sidebar.line(1, Component.text(ChatColor.GREEN + "¡Victoria para " + winningPlayer.getName() + "!"));

                    // fireworks
                    if (System.currentTimeMillis() - resultsLastFireworkTimestamp >= 1000) {
                        resultsLastFireworkTimestamp = System.currentTimeMillis();

                        winningPlayer.getWorld().spawn(winningPlayer.getLocation(), Firework.class, firework -> {
                            FireworkMeta fm = firework.getFireworkMeta();
                            fm.addEffect(FireworkEffect.builder()
                                    .withColor(Color.WHITE)
                                    .withColor(Color.BLUE)
                                    .with(FireworkEffect.Type.BURST)
                                    .withFlicker()
                                    .withTrail()
                                    .build()
                            );
                            fm.setPower(0);
                            firework.setFireworkMeta(fm);
                        });
                    }

                    if (System.currentTimeMillis() - resultsTimestamp >= 12000) {
                        status = ArenaStatus.END;
                    }
                }
            }

            case END -> {
                for(Player player : players) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(""));
                }

                sidebar.removePlayers(players);
                manager.ceaseArena(this);
            }
        }
    }

    public void addPlayer(Player player) {
        players.add(player);

        player.getInventory().clear();
        player.teleport(lobbyLocation);

        sidebar.addPlayer(player);

        for (Player p : players) {
            p.sendMessage(ChatColor.YELLOW + player.getName() + " se ha unido a la partida. (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")");
        }
    }

    private boolean isInsideZone(Location loc, float centerX, float centerZ, float sizeX, float sizeZ) {
        float halfSizeX = sizeX / 2f;
        float halfSizeZ = sizeZ / 2f;

        return loc.getBlockX() >= centerX - halfSizeX && loc.getBlockX() <= centerX + halfSizeX &&
                loc.getBlockZ() >= centerZ - halfSizeZ && loc.getBlockZ() <= centerZ + halfSizeZ;
    }

    public void spawnZoneBorder() {
        // north
        gameLocation.getWorld().spawn(gameLocation.clone().subtract(
                (manager.getZoneSize() / 2f),
                0,
                (-manager.getZoneSize() / 2f)
        ), BlockDisplay.class, entity -> {
            entity.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());

            entity.setTransformationMatrix(new Matrix4f()
                    .scale(new Vector3f(manager.getZoneSize(), manager.getZoneHeight(), 0.1f))
            );

            entity.setGlowing(true);
        });

        // south
        gameLocation.getWorld().spawn(gameLocation.clone().subtract(
                (manager.getZoneSize() / 2f),
                0,
                (manager.getZoneSize() / 2f)
        ), BlockDisplay.class, entity -> {
            entity.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());

            entity.setTransformationMatrix(new Matrix4f()
                    .scale(new Vector3f(manager.getZoneSize(), manager.getZoneHeight(), 0.1f))
            );

            entity.setGlowing(true);
        });


        // east
        gameLocation.getWorld().spawn(gameLocation.clone().subtract(
                (manager.getZoneSize() / 2f),
                0,
                (manager.getZoneSize() / 2f)
        ), BlockDisplay.class, entity -> {
            entity.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());

            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f((float) Math.toRadians(-90), 0, 1, 0),
                    new Vector3f(new Vector3f(manager.getZoneSize(), manager.getZoneHeight(), 0.1f)),
                    new AxisAngle4f(0, 0, 0, 0)
            ));

            entity.setGlowing(true);
        });

        // west
        gameLocation.getWorld().spawn(gameLocation.clone().subtract(
                (-manager.getZoneSize() / 2f),
                0,
                (manager.getZoneSize() / 2f)
        ), BlockDisplay.class, entity -> {
            entity.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());

            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f((float) Math.toRadians(-90), 0, 1, 0),
                    new Vector3f(new Vector3f(manager.getZoneSize(), manager.getZoneHeight(), 0.1f)),
                    new AxisAngle4f(0, 0, 0, 0)
            ));

            entity.setGlowing(true);
        });
    }

    public double elapsedSecondsSinceCreation() {
        return (System.currentTimeMillis() - creationTimestamp) / 1000.0;
    }

    public double elapsedSecondsSinceStartingCountdown() {
        return (System.currentTimeMillis() - startingCountdownTimestamp) / 1000.0;
    }

    public enum ArenaStatus {
        LOBBY, STARTING,
        BEGIN,
        PLAY_HALT, PLAYING,
        RESULTS, END
    }

}
