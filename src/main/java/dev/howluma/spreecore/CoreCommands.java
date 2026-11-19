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
            case "spree" -> {Player p=requirePlayer(sender);if(p!=null)plugin.menus().openMain(p);}
            case "spreehud" -> {
                Player p=requirePlayer(sender);
                if(p!=null){var profile=plugin.data().get(p);profile.hideHud=!profile.hideHud;plugin.data().dirty();
                    message(p,"&7Killstreak action bar "+(profile.hideHud?"&cOFF":"&aON"));
                    if(profile.hideHud)Text.action(p," ");}
            }
            case "killstreak" -> {
                PlayerDataStore.Profile p=args.length>0?lookup(args[0]):sender instanceof Player player?plugin.data().get(player):null;
                if(p==null)message(sender,"&cPlayer not found.");
                else message(sender,"&f"+p.name+" &7killstreak: &9"+plugin.streaks().display(p.streak));
            }
            case "stats" -> {
                PlayerDataStore.Profile p=args.length>0?lookup(args[0]):sender instanceof Player player?plugin.data().get(player):null;
                if(p==null)message(sender,"&cPlayer not found.");
                else {
                    Text.send(sender,"&8&m------------------------------");
                    Text.send(sender,"&9&lSPREE STATS &f"+p.name);
                    Text.send(sender,"&7Current: &9"+plugin.streaks().display(p.streak)+" &7| Best: &9"+p.best);
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
                    Text.send(sender,"&b#"+(index+1)+" &f"+profile.name+" &7- &9"+plugin.streaks().display(profile.streak));
                }
            }
            case "spreeinfo" -> showInfo(sender);
            case "spreeadmin" -> {if(admin(sender))adminCommand(sender,args);}
            case "spreeevent" -> {if(admin(sender))eventCommand(sender,args);}
            case "legendary" -> legendaryCommand(sender,args);
            default -> {return false;}
        }
        return true;
    }
    private PlayerDataStore.Profile lookup(String input) {
        try {UUID id=UUID.fromString(input);return plugin.data().get(id);}catch(IllegalArgumentException ignored){}
        Player online=Bukkit.getPlayerExact(input);
        if(online!=null)return plugin.data().get(online);
        return plugin.data().all().stream().filter(p->p.name.equalsIgnoreCase(input)).findFirst().orElse(null);
    }
    private void showInfo(CommandSender sender) {
        Text.send(sender,"&9&lSPREE &7- Killstreak Mechanics");
        Text.send(sender,"&7Cap: &f"+plugin.streaks().cap()+" &7| Recovery state after natural death at zero");
        Text.send(sender,"&7Extra health: +1 heart at streaks 3, 5, 7, ... up to &f"+
                plugin.getConfig().getInt("killstreak.max-health-hearts",20)+" hearts");
        String root="killstreak.restrictions.";
        for (String key:List.of("totem","netherite-sword","netherite-axe","enchanted-golden-apple","netherite-helmet","ender-pearl"))
            Text.send(sender,"&b"+key.replace('-', ' ')+" &7unlocks at &f"+plugin.getConfig().getInt(root+key));
        Text.send(sender,"&7Hunt: &f"+plugin.events().mode()+" &7| Timed: &f"+plugin.events().timed());
    }
    private Integer number(CommandSender sender,String s) {
        try {return Integer.parseInt(s);}catch(NumberFormatException e){message(sender,"&cInvalid number: "+s);return null;}
    }
    private void adminHelp(CommandSender s) {
        Text.send(s,"&9&lSPREE ADMIN &7- GUI: /spreeadmin");
        for(String line:List.of("set <player> <value>","add <player> <value>","remove <player> <value>",
                "reset <player>","recovery <player>","setall <value>","resetall (twice to confirm)",
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
            case "reload" -> {plugin.reloadSpreeConfiguration();message(sender,"&aConfiguration reloaded. UI task intervals need a restart to change.");}
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
                plugin.streaks().safetyInventoryCheck();
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
                if(args.length<2){message(sender,"&cUsage: /spreeadmin setall <amount>");return;}
                Integer value=number(sender,args[1]);if(value==null)return;
                for(var profile:List.copyOf(plugin.data().all()))plugin.streaks().setStreak(profile.id,value,true);
                message(sender,"&aUpdated all saved player streaks.");
            }
            case "update" -> {
                if(args.length>=2){
                    Player p=Bukkit.getPlayerExact(args[1]);
                    if(p==null){message(sender,"&cTarget must be online.");return;}
                    plugin.streaks().update(p);plugin.streaks().enforceInventory(p);
                }else{plugin.streaks().refreshOnline();plugin.streaks().safetyInventoryCheck();}
                message(sender,"&aRecalculated players.");
            }
            case "set","add","remove","reset","recovery" -> {
                if(args.length<2){message(sender,"&cUsage: /spreeadmin "+cmd+" <player> [amount]");return;}
                PlayerDataStore.Profile p=lookup(args[1]);
                if(p==null){message(sender,"&cPlayer unknown. They must have joined before, or pass a UUID.");return;}
                int value=0;
                if(!cmd.equals("reset") && !cmd.equals("recovery")) {
                    if(args.length<3){message(sender,"&cMissing amount.");return;}
                    Integer parsed=number(sender,args[2]);if(parsed==null)return;
                    value=switch(cmd){case "set" -> parsed;case "add" -> p.streak+parsed;default -> p.streak-parsed;};
                }
                if(cmd.equals("recovery"))plugin.streaks().setStreak(p.id,-1,false);
                else plugin.streaks().setStreak(p.id,value,true);
                message(sender,"&aUpdated &f"+p.name+"&a: " + plugin.streaks().display(p.streak));
            }
            default -> adminHelp(sender);
        }
    }
    private void eventHelp(CommandSender sender) {
        Text.send(sender,"&9&lSPREE EVENTS &7- /spreeevent <type> <on|off>");
        Text.send(sender,"&7Types: &flocator, hunt, juggernaut, bloodsbane, bloodmoon, surge, chaos, race");
        Text.send(sender,"&7Hunt/Juggernaut = compass modes; other four = timed 5-min events.");
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
                if(on)plugin.events().setTimed(EventManager.Timed.valueOf(type.toUpperCase(Locale.ROOT)));
                else plugin.events().setTimed(EventManager.Timed.NONE);
            }
            default -> {eventHelp(sender);return;}
        }
        message(sender,"&aEvent updated.");
    }
    private void legendaryHelp(CommandSender sender) {
        Text.send(sender,"&9&lLEGENDARY &7- 7 weapons, 4 craftable forges");
        Text.send(sender,"&f/legendary list &7| &f/legendary status");
        Text.send(sender,"&f/legendary give <weapon> [player] &7(admin)");
        Text.send(sender,"&f/legendary forge end <weapon> &7(admin)");
        Text.send(sender,"&f/legendary forge reset <weapon> &7(admin)");
        Text.send(sender,"&f/legendary refresh &7(refresh held weapon lore)");
        Text.send(sender,"&7Weapons: &f"+String.join(", ",LegendaryType.ids()));
    }
    private void legendaryCommand(CommandSender sender,String[] args) {
        if(args.length==0){
            if(sender instanceof Player p)plugin.menus().openLegendary(p);else legendaryHelp(sender);
            return;
        }
        String cmd=args[0].toLowerCase(Locale.ROOT);
        switch(cmd) {
            case "list" -> legendaryHelp(sender);
            case "status" -> {
                for(LegendaryType t:LegendaryType.values())Text.send(sender,"&b"+t.display+" &7- "+plugin.forges().status(t));
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
        if(root.equals("spreeadmin") && sender.hasPermission("spree.admin")) {
            if(args.length==1)options=List.of("set","add","remove","reset","recovery","setall","resetall","update","reload","migrateitems","importskript","help");
            else if(args.length==2 && List.of("set","add","remove","reset","recovery","update").contains(args[0]))
                options=plugin.data().all().stream().map(p->p.name).sorted().toList();
            else if(args.length==2 && args[0].equalsIgnoreCase("importskript"))options=List.of("force");
        }else if(root.equals("spreeevent") && sender.hasPermission("spree.admin")) {
            if(args.length==1)options=List.of("locator","hunt","juggernaut","bloodsbane","bloodmoon","surge","chaos","race");
            else if(args.length==2)options=List.of("on","off");
        }else if(root.equals("legendary")) {
            if(args.length==1)options=List.of("list","status","refresh","give","forge");
            else if(args.length==2 && args[0].equalsIgnoreCase("give"))options=LegendaryType.ids();
            else if(args.length==2 && args[0].equalsIgnoreCase("forge"))options=List.of("end","reset");
            else if(args.length==3 && args[0].equalsIgnoreCase("forge"))options=LegendaryType.ids();
            else if(args.length==3 && args[0].equalsIgnoreCase("give"))options=Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }else if(List.of("stats","killstreak").contains(root) && args.length==1)
            options=plugin.data().all().stream().map(p->p.name).toList();
        String current=args.length>0?args[args.length-1].toLowerCase(Locale.ROOT):"";
        return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(current)).limit(60).toList();
    }
}
