package dev.howluma.spreecore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** One consistent admin API replacing ten separate /weapon and /reset commands. */
public final class CoreCommands implements CommandExecutor, TabCompleter {
    private final SpreeCorePlugin plugin;
    CoreCommands(SpreeCorePlugin plugin){this.plugin=plugin;}
    private void message(CommandSender to,String msg){Text.send(to,plugin.prefix()+msg);}
    private boolean admin(CommandSender sender) {
        if(sender.hasPermission("spree.admin"))return true;
        message(sender,"&cNo permission.");return false;
    }
    private Player requirePlayer(CommandSender sender) {
        if(sender instanceof Player p)return p;
        message(sender,"&cThis command requires a player.");return null;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String alias,String[] args) {
        switch(command.getName().toLowerCase(Locale.ROOT)) {
            case "spree" -> {
                if (args.length > 0 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("setall"))) {
                    if (admin(sender)) adminCommand(sender, args);
                    return true; // Console admins can set killstreaks, too.
                }
                Player p=requirePlayer(sender);
                if(p!=null) {
                    if(args.length>0 && args[0].equalsIgnoreCase("admin")) {
                        if(p.hasPermission("spree.admin"))plugin.menus().openAdmin(p);
                        else message(p,"&cOnly admins can access the control panel.");
                    } else if(args.length>0 && args[0].equalsIgnoreCase("settings")) {
                        if(p.hasPermission("spree.admin"))plugin.menus().openSettings(p);
                        else message(p,"&cOnly admins can change plugin settings.");
                    } else if(args.length>0 && args[0].equalsIgnoreCase("perks")) {
                        if (args.length != 1) message(p, "&cUsage: /spree perks");
                        else {
                            Text.send(p, "&9&lYOUR ACTIVE PERKS");
                            for (String lore : plugin.perks().activeLore(plugin.data().get(p))) Text.send(p, lore);
                        }
                    } else if(args.length>0 && args[0].equalsIgnoreCase("withdraw")) {
                        withdraw(p, args);
                    } else if(args.length>0 && args[0].equalsIgnoreCase("value")) {
                        if(args.length<2 || !plugin.menus().submitInput(p,String.join(" ",Arrays.copyOfRange(args,1,args.length))))
                            message(p,"&cSelect a setting in /spree settings before entering a value.");
                    } else if(args.length>0 && args[0].equalsIgnoreCase("cancel")) {
                        if(!plugin.menus().submitInput(p,"cancel"))message(p,"&7Nothing to cancel.");
                    } else plugin.menus().openMain(p);
                }
            }
            case "spreesettings" -> {
                if (admin(sender)) {
                    Player player = requirePlayer(sender);
                    if (player != null) plugin.menus().openSettings(player);
                }
            }
            case "spreehud" -> {
                Player p=requirePlayer(sender);
                if(p!=null){var profile=plugin.data().get(p);profile.hideHud=!profile.hideHud;plugin.data().dirty();
                    message(p,"&7Killstreak action bar "+(profile.hideHud?"&cOFF":"&aON"));
                    if(profile.hideHud)Text.action(p," ");}
            }
            case "killstreak" -> {
                PlayerDataStore.Profile p=args.length>0?lookup(args[0]):sender instanceof Player player?plugin.data().get(player):null;
                if(p==null)message(sender,"&cPlayer not found.");
                else message(sender,"&f"+p.name+" &7killstreak: "+plugin.indicators().streakColor(p.streak)+plugin.streaks().display(p.streak));
            }
            case "stats" -> {
                PlayerDataStore.Profile p=args.length>0?lookup(args[0]):sender instanceof Player player?plugin.data().get(player):null;
                if(p==null)message(sender,"&cPlayer not found.");
                else {
                    Text.send(sender,"&8&m------------------------------");
                    Text.send(sender,"&9&lSPREE STATS &f"+p.name);
                    Text.send(sender,"&7Current: "+plugin.indicators().streakColor(p.streak)+plugin.streaks().display(p.streak)+" &7| Best: "+plugin.indicators().streakColor(p.best)+p.best);
                    Text.send(sender,"&7Kills: &f"+p.kills+" &7Deaths: &f"+p.deaths+" &7Streaks ended: &f"+p.ended);
                    Text.send(sender,"&8&m------------------------------");
                }
            }
            case "leaderboard" -> {
                Text.send(sender,"&9&lTOP 10 KILLSTREAKS");
                List<PlayerDataStore.Profile> leaders=plugin.data().leaders(10);
                if(leaders.isEmpty())Text.send(sender,"&7No players yet.");
                for(int index=0;index<leaders.size();index++) {
                    var profile=leaders.get(index);
                    Text.send(sender,"&9#"+(index+1)+" &f"+profile.name+" &7- "+plugin.indicators().streakColor(profile.streak)+plugin.streaks().display(profile.streak));
                }
            }
            case "spreeinfo" -> showInfo(sender);
            case "spreeadmin" -> {if(admin(sender))adminCommand(sender,args);}
            case "spreeset" -> {
                if (!admin(sender)) return true;
                if (args.length != 2) message(sender, "&cUsage: /spreeset <player> <value>");
                else adminCommand(sender, new String[]{"set", args[0], args[1]});
            }
            case "spreesetall" -> {
                if (!admin(sender)) return true;
                if (args.length != 1) message(sender, "&cUsage: /spreesetall <value>");
                else adminCommand(sender, new String[]{"setall", args[0]});
            }
            case "spreeevent" -> {if(admin(sender))eventCommand(sender,args);}
            case "legendary" -> legendaryCommand(sender,args);
            default -> {return false;}
        }
        return true;
    }
    private void withdraw(Player player, String[] args) {
        if (!player.hasPermission("spree.withdraw")) {
            message(player, "&cYou cannot withdraw Spree.");
            return;
        }
        if (!plugin.getConfig().getBoolean("killstreak.withdrawal.enabled", true)) {
            message(player, "&cSpree withdrawal is disabled.");
            return;
        }
        if (args.length > 2) {
            message(player, "&cUsage: /spree withdraw [amount|all]");
            return;
        }
        int streak = plugin.streaks().streak(player);
        if (streak <= 0) {
            message(player, "&cYou must have a positive killstreak to withdraw.");
            return;
        }
        int amount = streak;
        if (args.length == 2 && !args[1].equalsIgnoreCase("all")) {
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) {
                message(player, "&cUsage: /spree withdraw [amount|all]");
                return;
            }
        }
        StreakManager.WithdrawalResult result = plugin.streaks().withdrawSpree(player, amount);
        if (!result.success()) { message(player, "&c" + result.message()); return; }
        message(player, "&aWithdrew &9&l" + result.amount() + " &aSpree item(s). &7Remaining killstreak: &9&l"
                + result.remaining());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, .85f);
    }

    private PlayerDataStore.Profile lookup(String input) {
        try {UUID id=UUID.fromString(input);return plugin.data().get(id);}catch(IllegalArgumentException ignored){}
        Player online=Bukkit.getPlayerExact(input);
        if(online!=null)return plugin.data().get(online);
        return plugin.data().all().stream().filter(p->p.name.equalsIgnoreCase(input)).findFirst().orElse(null);
    }
    private void showInfo(CommandSender sender) {
        Text.send(sender,"&9&lSPREE &7- Killstreak Mechanics");
        Text.send(sender,"&7Cap: &f"+plugin.streaks().cap()+" &7| Fractured Killstreak after an environmental death at zero");
        Text.send(sender,"&7Health: +" + ConfigEditor.format(plugin.getConfig().getDouble("killstreak.health-per-level-hearts", 0.5))
                + " heart per positive level; Fractured has the normal base health.");
        Text.send(sender,"&7Mace attacks require a claimed &9Mace Wielder &7Privilege.");
        Text.send(sender,"&7Base PvP kill reward: &a+" +
                plugin.getConfig().getInt("killstreak.kill-rewards.base-reward", 1) +
                " &7plus &a+" + plugin.getConfig().getInt("killstreak.kill-rewards.extra-per-tier", 1) +
                " &7per &f" + plugin.getConfig().getInt("killstreak.kill-rewards.levels-per-tier", 5) +
                " &7victim killstreak levels (before death).");
        Text.send(sender,"&7Use &9/spree &7> &bProgression &7to claim Privileges, Talents and Infusions.");
        Text.send(sender,"&7Perks roll automatically; a crafted Fate Sigil rerolls saved upgrades.");
    }
    private Integer number(CommandSender sender,String s) {
        try {return Integer.parseInt(s);}catch(NumberFormatException e){message(sender,"&cInvalid number: "+s);return null;}
    }
    private Integer exactSpreeValue(CommandSender sender, String input) {
        Integer value = number(sender, input);
        if (value == null) return null;
        if (value < -1 || value > plugin.streaks().cap()) {
            message(sender, "&cAllowed killstreak values: -1 (Fractured) through " + plugin.streaks().cap() + ".");
            return null;
        }
        return value;
    }
    private List<String> spreeSuggestions() {
        return java.util.stream.Stream.of("-1", "0", "1", "5", "10", "15", "20",
                Integer.toString(plugin.streaks().cap())).distinct().toList();
    }
    private void adminHelp(CommandSender s) {
        Text.send(s,"&9&lSPREE ADMIN &7- GUI: /spreeadmin");
        Text.send(s,"&bShortcuts: &f/spreeset <player> <value> &7and &f/spreesetall <value>");
        for(String line:List.of("set <player> <value>","add <player> <value>","remove <player> <value>",
                "reset <player>","fracture <player>","giveitem <player> [amount]","setall <value>","resetall (twice to confirm)",
                "update [player]","reload","migrateitems","importskript [force]"))
            Text.send(s,"&7/spreeadmin &f"+line);
    }
    private final Map<String,Long> adminResets=new HashMap<>();
    private void adminCommand(CommandSender sender,String[] args) {
        if(args.length==0) {
            if(sender instanceof Player p)plugin.menus().openAdmin(p);else adminHelp(sender);
            return;
        }
        String cmd=args[0].toLowerCase(Locale.ROOT);
        switch(cmd) {
            case "help" -> adminHelp(sender);
            case "reload" -> {plugin.reloadSpreeConfiguration();message(sender,"&aConfiguration, effects and schedules reloaded.");}
            case "settings" -> {
                Player p=requirePlayer(sender);
                if(p!=null)plugin.menus().openSettings(p);
            }
            case "migrateitems" -> {
                int count=0;
                for(Player p:Bukkit.getOnlinePlayers())count+=plugin.legendaries().migrate(p);
                message(sender,"&aTagged "+count+" old Skript relic items with secure IDs. Disable legacy lore once all migrated.");
            }
            case "importskript" -> {
                boolean force=args.length>1 && args[1].equalsIgnoreCase("force");
                SkriptImporter.Result result=SkriptImporter.importDefault(plugin,force);
                message(sender,result.summary());
                plugin.streaks().refreshOnline();
                plugin.legendaries().syncBloodsBane();
            }
            case "resetall" -> {
                String id=sender instanceof Player p?p.getUniqueId().toString():"console";
                long time=System.currentTimeMillis();
                if(adminResets.getOrDefault(id,0L)<time) {
                    adminResets.put(id,time+10000);
                    message(sender,"&cRepeat /spreeadmin resetall within 10 seconds to confirm.");
                    return;
                }
                adminResets.remove(id);
                for(var profile:List.copyOf(plugin.data().all()))plugin.streaks().setStreak(profile.id,0,true);
                Bukkit.broadcastMessage(Text.c("&9&lSPREE &7All killstreaks reset by an admin."));
            }
            case "setall" -> {
                if(args.length!=2){message(sender,"&cUsage: /spreesetall <value>");return;}
                Integer value=exactSpreeValue(sender,args[1]);if(value==null)return;
                for (Player player : Bukkit.getOnlinePlayers()) plugin.data().get(player);
                List<PlayerDataStore.Profile> targets = List.copyOf(plugin.data().all());
                for(var profile:targets)plugin.streaks().setStreak(profile.id,value,value>=0);
                plugin.data().save();
                plugin.getLogger().info(sender.getName() + " set all " + targets.size() + " stored killstreaks to " + value);
                message(sender,"&aSet " + targets.size() + " saved player killstreaks to &f"
                        + plugin.streaks().display(value) + "&a (online and offline).");
            }
            case "update" -> {
                if(args.length>=2){
                    Player p=Bukkit.getPlayerExact(args[1]);
                    if(p==null){message(sender,"&cTarget must be online.");return;}
                    plugin.streaks().update(p);
                }else{plugin.streaks().refreshOnline();}
                message(sender,"&aRecalculated players.");
            }
            case "giveitem" -> {
                if (args.length < 2) { message(sender,"&cUsage: /spreeadmin giveitem <player> [amount]"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { message(sender,"&cThat player must be online to receive the item."); return; }
                int amount = 1;
                if (args.length >= 3) {
                    Integer parsed = number(sender, args[2]);
                    if (parsed == null) return;
                    amount = parsed;
                }
                if (amount < 1 || amount > 64) { message(sender,"&cGive between 1 and 64 items."); return; }
                plugin.streaks().giveSpreeItem(target, amount);
                message(sender,"&aGave &9" + amount + " &aSpree item(s) to &f" + target.getName() + "&a.");
            }
            case "set","add","remove","reset","recovery","fracture" -> {
                if(args.length<2){message(sender,"&cUsage: /spreeadmin "+cmd+" <player> [amount]");return;}
                PlayerDataStore.Profile p=lookup(args[1]);
                if(p==null){message(sender,"&cPlayer unknown. They must have joined before, or pass a UUID.");return;}
                int value=0;
                if(!cmd.equals("reset") && !cmd.equals("recovery") && !cmd.equals("fracture")) {
                    if(args.length<3){message(sender,"&cMissing amount.");return;}
                    Integer parsed=cmd.equals("set")
                            ?exactSpreeValue(sender,args[2]):number(sender,args[2]);
                    if(parsed==null)return;
                    value=switch(cmd){case "set" -> parsed;case "add" -> p.streak+parsed;default -> p.streak-parsed;};
                }
                if(cmd.equals("fracture") || cmd.equals("recovery"))plugin.streaks().setStreak(p.id,-1,false);
                else plugin.streaks().setStreak(p.id,value,value>=0);
                plugin.data().save();
                message(sender,"&aUpdated &f"+p.name+"&a: " + plugin.streaks().display(p.streak));
            }
            default -> adminHelp(sender);
        }
    }
    private void eventHelp(CommandSender sender) {
        Text.send(sender,"&9&lSPREE EVENTS &7- /spreeevent <type> <on|off>");
        Text.send(sender,"&7Types: &flocator, hunt, juggernaut, bloodsbane, bloodmoon, surge, chaos, race");
        Text.send(sender,"&7Hunt/Juggernaut = compass modes; Spree Surge is temporarily paused.");
    }
    private void eventCommand(CommandSender sender,String[] args) {
        if(args.length==0){
            if(sender instanceof Player p)plugin.menus().openEvents(p,true);else eventHelp(sender);
            return;
        }
        if(args.length<2){eventHelp(sender);return;}
        String type=args[0].toLowerCase(Locale.ROOT);
        boolean on=args[1].equalsIgnoreCase("on");
        if(!on && !args[1].equalsIgnoreCase("off")) {eventHelp(sender);return;}
        switch(type) {
            case "locator" -> plugin.events().setLocator(on);
            case "bloodsbane" -> plugin.events().setBloodsBane(on);
            case "hunt" -> plugin.events().setMode(on?EventManager.Mode.HUNT:EventManager.Mode.NONE);
            case "juggernaut", "jug" -> plugin.events().setMode(on?EventManager.Mode.JUGGERNAUT:EventManager.Mode.NONE);
            case "bloodmoon", "surge", "chaos", "race" -> {
                if (on && type.equals("surge")) {
                    message(sender, "&7Spree Surge is paused until the new killstreak progression is designed.");
                    return;
                }
                if(on)plugin.events().setTimed(EventManager.Timed.valueOf(type.toUpperCase(Locale.ROOT)));
                else plugin.events().setTimed(EventManager.Timed.NONE);
            }
            default -> {eventHelp(sender);return;}
        }
        message(sender,"&aEvent updated.");
    }
    private void legendaryHelp(CommandSender sender) {
        Text.send(sender,"&9&lLEGENDARY &7- 7 weapons, 4 craftable forges");
        Text.send(sender,"&f/legendary list &7| &f/legendary status &7(admin)");
        Text.send(sender,"&f/legendary give <weapon> [player] &7(admin)");
        Text.send(sender,"&f/legendary forge end <weapon> &7(admin)");
        Text.send(sender,"&f/legendary forge reset <weapon> &7(admin)");
        Text.send(sender,"&f/legendary refresh &7(refresh held weapon lore)");
        Text.send(sender,"&7Weapons: &f"+String.join(", ",LegendaryType.ids()));
    }
    private void legendaryCommand(CommandSender sender,String[] args) {
        // Players can safely refresh their held weapon without seeing unreleased content.
        if(args.length>0 && args[0].equalsIgnoreCase("refresh")) {
            Player p=requirePlayer(sender);
            if(p!=null)plugin.legendaries().refreshHeld(p);
            return;
        }
        if(!admin(sender))return;
        if(args.length==0){
            if(sender instanceof Player p)plugin.menus().openLegendary(p);else legendaryHelp(sender);
            return;
        }
        String cmd=args[0].toLowerCase(Locale.ROOT);
        switch(cmd) {
            case "list" -> legendaryHelp(sender);
            case "status" -> {
                for(LegendaryType t:LegendaryType.values())Text.send(sender,"&9"+t.display+" &7- "+plugin.forges().status(t));
            }
            case "refresh" -> {
                Player p=requirePlayer(sender);if(p!=null)plugin.legendaries().refreshHeld(p);
            }
            case "give" -> {
                if(!sender.hasPermission("spree.legendary.give")) {message(sender,"&cNo permission.");return;}
                if(args.length<2){message(sender,"&cUsage: /legendary give <weapon> [player]");return;}
                LegendaryType t=LegendaryType.byId(args[1]);
                if(t==null){message(sender,"&cUnknown weapon.");return;}
                Player recipient=args.length>2?Bukkit.getPlayerExact(args[2]):sender instanceof Player p?p:null;
                if(recipient==null){message(sender,"&cRecipient not online.");return;}
                Map<Integer,ItemStack> unused=recipient.getInventory().addItem(plugin.legendaries().create(t,t==LegendaryType.BLOODSBANE));
                for(ItemStack stack:unused.values())recipient.getWorld().dropItemNaturally(recipient.getLocation(),stack);
                message(sender,"&aGiven &f"+t.display+" &ato &f"+recipient.getName());
            }
            case "forge" -> {
                if(!admin(sender))return;
                if(args.length<3){message(sender,"&cUsage: /legendary forge <end|reset> <weapon>");return;}
                LegendaryType t=LegendaryType.byId(args[2]);
                if(t==null || !t.forgeable()){message(sender,"&cUnknown craftable legendary.");return;}
                boolean success=switch(args[1].toLowerCase(Locale.ROOT)){
                    case "end" -> plugin.forges().end(t);
                    case "reset" -> plugin.forges().reset(t);
                    default -> false;
                };
                message(sender,success?"&aForge updated: &f"+t.display:"&cNo matching active forge, or invalid action.");
            }
            default -> legendaryHelp(sender);
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        String root=command.getName().toLowerCase(Locale.ROOT);
        List<String> options=new ArrayList<>();
        if (root.equals("spree")) {
            if (args.length == 1) {
                options = new ArrayList<>();
                options.add("perks");
                if (sender.hasPermission("spree.withdraw")
                        && plugin.getConfig().getBoolean("killstreak.withdrawal.enabled", true))
                    options.add("withdraw");
                if (sender.hasPermission("spree.admin"))
                    options.addAll(List.of("settings", "admin", "set", "setall", "value", "cancel"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set")
                    && sender.hasPermission("spree.admin")) {
                options=plugin.data().all().stream().map(profile -> profile.name).sorted().toList();
            } else if (sender.hasPermission("spree.admin")
                    && ((args.length==3 && args[0].equalsIgnoreCase("set"))
                    || (args.length==2 && args[0].equalsIgnoreCase("setall")))) {
                options=spreeSuggestions();
            } else if (args.length == 2 && args[0].equalsIgnoreCase("withdraw")
                    && sender instanceof Player p && sender.hasPermission("spree.withdraw")) {
                int n = plugin.streaks().streak(p);
                if (n > 0) options = List.of("all", "1", "5", "10", Integer.toString(n));
            }
        }
        else if(root.equals("spreeadmin") && sender.hasPermission("spree.admin")) {
            if(args.length==1)options=List.of("set","add","remove","reset","fracture","giveitem","setall","resetall","update","reload","settings","migrateitems","importskript","help");
            else if(args.length==2 && List.of("set","add","remove","reset","fracture","recovery","update","giveitem").contains(args[0]))
                options=plugin.data().all().stream().map(p->p.name).sorted().toList();
            else if(args.length==3 && args[0].equalsIgnoreCase("giveitem")) options=List.of("1","5","16","32","64");
            else if((args.length==3 && args[0].equalsIgnoreCase("set"))
                    || (args.length==2 && args[0].equalsIgnoreCase("setall"))) options=spreeSuggestions();
            else if(args.length==2 && args[0].equalsIgnoreCase("importskript"))options=List.of("force");
        }else if(root.equals("spreeset") && sender.hasPermission("spree.admin")) {
            if(args.length==1) options=plugin.data().all().stream().map(profile -> profile.name).sorted().toList();
            else if(args.length==2) options=spreeSuggestions();
        }else if(root.equals("spreesetall") && sender.hasPermission("spree.admin")) {
            if(args.length==1) options=spreeSuggestions();
        }else if(root.equals("spreeevent") && sender.hasPermission("spree.admin")) {
            if(args.length==1)options=List.of("locator","hunt","juggernaut","bloodsbane","bloodmoon","surge","chaos","race");
            else if(args.length==2)options=List.of("on","off");
        }else if(root.equals("legendary")) {
            if(args.length==1)options=sender.hasPermission("spree.admin")
                    ? List.of("list","status","refresh","give","forge") : List.of("refresh");
            else if(sender.hasPermission("spree.admin") && args.length==2 && args[0].equalsIgnoreCase("give"))options=LegendaryType.ids();
            else if(sender.hasPermission("spree.admin") && args.length==2 && args[0].equalsIgnoreCase("forge"))options=List.of("end","reset");
            else if(sender.hasPermission("spree.admin") && args.length==3 && args[0].equalsIgnoreCase("forge"))options=LegendaryType.ids();
            else if(sender.hasPermission("spree.admin") && args.length==3 && args[0].equalsIgnoreCase("give"))options=Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }else if(List.of("stats","killstreak").contains(root) && args.length==1)
            options=plugin.data().all().stream().map(p->p.name).toList();
        String current=args.length>0?args[args.length-1].toLowerCase(Locale.ROOT):"";
        return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(current)).limit(60).toList();
    }
}
