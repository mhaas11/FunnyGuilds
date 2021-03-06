package net.dzikoysk.funnyguilds.command.util;

import net.dzikoysk.funnyguilds.data.Messages;
import net.dzikoysk.funnyguilds.data.configs.MessagesConfig;
import net.dzikoysk.funnyguilds.data.configs.PluginConfig.Commands.FunnyCommand;
import net.dzikoysk.funnyguilds.util.FunnyLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExecutorCaller implements CommandExecutor, TabExecutor {

    private static final List<ExecutorCaller> ecs = new ArrayList<>();

    private Executor executor;
    private boolean enabled;
    private boolean playerOnly;
    private String overriding;
    private String permission;
    private String[] secondary;
    private List<String> aliases;
    private List<ExecutorCaller> executors = new ArrayList<>();

    public ExecutorCaller(Executor exc, String perm, FunnyCommand command, boolean playerOnly) {
        this(exc, perm, command.name, command.aliases, command.enabled, playerOnly);
    }
    
    public ExecutorCaller(Executor exc, String command, List<String> aliases, boolean enabled, boolean playerOnly) {
        this(exc, "funnyguilds.admin", command, aliases, enabled, playerOnly);
    }
    
    public ExecutorCaller(Executor exc, String perm, String command, List<String> aliases, boolean enabled, boolean playerOnly) {
        if (exc == null || command == null) {
            return;
        }

        this.executor = exc;
        this.permission = perm;
        this.enabled = enabled;
        this.playerOnly = playerOnly;
        
        if (aliases != null && aliases.size() > 0) {
            this.aliases = aliases;
        } else {
            this.aliases = null;
        }

        String[] splited = command.split("\\s+");
        this.overriding = splited[0];
        if (splited.length > 1) {
            this.secondary = new String[splited.length - 1];
            System.arraycopy(splited, 1, this.secondary, 0, splited.length - 1);
        } else {
            this.secondary = null;
        }

        for (ExecutorCaller ec : ecs) {
            if (ec.overriding.equalsIgnoreCase(this.overriding)) {
                ec.executors.add(this);
                return;
            }
        }
        
        this.register();
        executors.add(this);
        ecs.add(this);
    }

    private boolean call(CommandSender sender, Command cmd, String[] args) {
        if (!cmd.getName().equalsIgnoreCase(this.overriding)) {
            return false;
        }
        
        ExecutorCaller main = null;
        for (ExecutorCaller ec : this.executors) {
            if (ec.secondary != null) {
                if (ec.secondary.length > args.length) {
                    continue;
                }
                
                boolean sec = false;
                for (int i = 0; i < ec.secondary.length; i++) {
                    if (!ec.secondary[i].equalsIgnoreCase(args[i])) {
                        sec = true;
                        break;
                    }
                }
                
                if (sec) {
                    continue;
                }
                
                args = Arrays.copyOfRange(args, ec.secondary.length, args.length);
            } else {
                main = ec;
                continue;
            }
            
            if (sender instanceof Player) {
                if (ec.permission != null && !sender.hasPermission(ec.permission)) {
                    sender.sendMessage(Messages.getInstance().permission);
                    return true;
                }
            }
            
            ec.executor.execute(sender, args);
            return true;
        }
        
        main.executor.execute(sender, args);
        return true;
    }

    private void register() {
        try {
            Performer p = new Performer(this.overriding);
            if (this.aliases != null) {
                p.setAliases(this.aliases);
            }
            
            p.setPermissionMessage(Messages.getInstance().permission);
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            
            CommandMap cmap = (CommandMap) f.get(Bukkit.getServer());
            cmap.register("", p);
            p.setExecutor(this);
        } catch (Exception e) {
            if (FunnyLogger.exception(e.getCause())) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        MessagesConfig messages = Messages.getInstance();
        
        if (!this.enabled) {
            sender.sendMessage(messages.generalCommandDisabled);
            return true;
        }
        
        if (this.playerOnly && !(sender instanceof Player)) {
            sender.sendMessage(messages.playerOnly);
            return true;
        }
        
        return call(sender, cmd, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        if (this.secondary != null) {
            return Arrays.asList(this.secondary);
        } else {
            return null;
        }
    }
}
