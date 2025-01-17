package org.deltacv.colina;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
                arenaManager.joinPlayer((Player) commandSender);
                return true;
        }

        return false;
    }
}
