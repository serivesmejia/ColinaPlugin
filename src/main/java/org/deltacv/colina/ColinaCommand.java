package org.deltacv.colina;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.deltacv.colina.arena.Arena;
import org.deltacv.colina.arena.ArenaManager;

public class ColinaCommand implements CommandExecutor {

    ArenaManager arenaManager;

    public ColinaCommand(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        if(!(commandSender instanceof Player)) {
            commandSender.sendMessage("Este comando solo lo pueden usar jugadores.");
            return true;
        }

        if(!commandSender.isOp()) {
            commandSender.sendMessage("No tienes permisos.");
            return true;
        }

        if(args.length < 1) {
            return false;
        }

        switch(args[0].toLowerCase()) {
            case "config":
                // get args after "config" and pass them to arenaManager.config
                String[] configArgs = new String[args.length - 1];
                System.arraycopy(args, 1, configArgs, 0, args.length - 1);

                arenaManager.config((Player) commandSender, configArgs);
                return true;
            case "join":
                if(args.length == 1) {
                    arenaManager.joinPlayer((Player) commandSender);
                } else if(args.length == 2) {
                    // find player by name
                    Player player = commandSender.getServer().getPlayer(args[1]);

                    if(player != null) {
                        arenaManager.joinPlayer(player);
                    } else {
                        commandSender.sendMessage("No se ha encontrado al jugador.");
                    }
                } else {
                    commandSender.sendMessage("Uso: /colina join [player]");
                }
                return true;
            case "tp":
                if(args.length != 2) {
                    commandSender.sendMessage("Uso: /colina tp <arena>");
                    commandSender.sendMessage("Usa: \"/colina arenas\" para ver las arenas disponibles.");
                    return true;
                }

                for(Arena arena : arenaManager.activeArenas.keySet()) {
                    if(arena.gameLocation.getWorld().getName().equals(args[1])) {
                        ((Player) commandSender).teleport(arena.gameLocation);
                        return true;
                    }
                }
            case "arenas":
                commandSender.sendMessage(ChatColor.GREEN + "Arenas disponibles:");
                for(Arena arena : arenaManager.activeArenas.keySet()) {
                    commandSender.sendMessage("- " + arena.gameLocation.getWorld().getName());
                }
                return true;
            case "start":
                if(args.length != 2) {
                    commandSender.sendMessage("Uso: /colina start <arena>");
                    commandSender.sendMessage("Usa: \"/colina arenas\" para ver las arenas disponibles.");
                    return true;
                }

                for(Arena arena : arenaManager.activeArenas.keySet()) {
                    if(arena.gameLocation.getWorld().getName().equals(args[1]) && arena.status == Arena.ArenaStatus.LOBBY) {
                        arena.status = Arena.ArenaStatus.STARTING;
                        commandSender.sendMessage("Se ha iniciado la arena.");
                        return true;
                    }
                }

                commandSender.sendMessage("No se ha encontrado la arena o la arena no está en el estado correcto.");
            case "stop":
                if(args.length != 2) {
                    commandSender.sendMessage("Uso: /colina stop <arena>");
                    commandSender.sendMessage("Usa: \"/colina arenas\" para ver las arenas disponibles.");
                    return true;
                }

                for(Arena arena : arenaManager.activeArenas.keySet()) {
                    if(arena.gameLocation.getWorld().getName().equals(args[1]) && arena.status == Arena.ArenaStatus.PLAYING) {
                        arena.status = Arena.ArenaStatus.END;
                        commandSender.sendMessage("Se ha detenido la arena.");
                        return true;
                    }
                }

                commandSender.sendMessage("No se ha encontrado la arena o la arena no está en el estado correcto.");
        }

        return false;
    }
}
