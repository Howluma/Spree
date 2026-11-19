package dev.howluma.spreecore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in import of Skript's default CSV variables storage. No hidden migration. */
public final class SkriptImporter {
    private SkriptImporter() {}
    public record Result(int imported, int skipped, int errors, String detail) {
        public String summary() {
            return detail!=null?"&c"+detail:"&aImported "+imported+" Skript stats, skipped "+skipped+", malformed "+errors+".";
        }
    }
    public static Result importDefault(SpreeCorePlugin plugin,boolean force) {
        File file=new File(plugin.getDataFolder().getParentFile(),"Skript/variables.csv");
        if(!file.isFile())return new Result(0,0,0,"Cannot find plugins/Skript/variables.csv. Import only supports Skript CSV variables storage.");
        if(force) {
            File original=new File(plugin.getDataFolder(),"players.yml");
            plugin.data().save();
            if(original.exists()) {
                try {
                    Files.copy(original.toPath(),new File(plugin.getDataFolder(),"players-backup-"+System.currentTimeMillis()+".yml").toPath());
                }catch(IOException ex){return new Result(0,0,0,"Cannot back up players.yml: "+ex.getMessage());}
            }
        }
        int imported=0,skipped=0,errors=0;
        try(BufferedReader reader=Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.isBlank() || line.startsWith("#"))continue;
                List<String> parts=parseCsv(line);
                if(parts.size()<3){errors++;continue;}
                String name=parts.get(0).trim();
                String value=parts.get(2).trim();
                if(!name.startsWith("spree."))continue;
                int separator=name.lastIndexOf("::");
                if(separator<0){skipped++;continue;}
                String key=name.substring(0,separator);
                UUID uuid;
                try{uuid=UUID.fromString(name.substring(separator+2));}
                catch(IllegalArgumentException ignored){errors++;continue;}
                PlayerDataStore.Profile player=plugin.data().get(uuid);
                try {
                    switch(key) {
                        case "spree.streak" -> {
                            int n=Integer.parseInt(value);
                            if(force||player.streak==0){player.streak=SpreeMath.clamp(n,plugin.streaks().cap());imported++;}else skipped++;
                        }
                        case "spree.best" -> {
                            int n=Integer.parseInt(value);
                            if(force||player.best==0){player.best=n;imported++;}else skipped++;
                        }
                        case "spree.kills" -> {
                            int n=Integer.parseInt(value);
                            if(force||player.kills==0){player.kills=n;imported++;}else skipped++;
                        }
                        case "spree.deaths" -> {
                            int n=Integer.parseInt(value);
                            if(force||player.deaths==0){player.deaths=n;imported++;}else skipped++;
                        }
                        case "spree.ended" -> {
                            int n=Integer.parseInt(value);
                            if(force||player.ended==0){player.ended=n;imported++;}else skipped++;
                        }
                        case "spree.name" -> {
                            if(force||player.name.equals("Unknown")){player.name=value;imported++;}else skipped++;
                        }
                        case "spree.hidehud" -> {
                            if(force||!player.hideHud){player.hideHud=Boolean.parseBoolean(value);imported++;}else skipped++;
                        }
                        case "spree.revenge" -> {
                            if(force||player.revengeAgainst==null){
                                player.revengeAgainst=UUID.fromString(value);imported++;
                            }else skipped++;
                        }
                        default -> skipped++;
                    }
                }catch(IllegalArgumentException ex){errors++;}
            }
        }catch(IOException ex){return new Result(imported,skipped,errors,"CSV import failed: "+ex.getMessage());}
        plugin.data().dirty();
        plugin.data().save();
        return new Result(imported,skipped,errors,null);
    }
    /** Comma-separated fields including double-quoted escaped commas and doubled quotes. */
    static List<String> parseCsv(String line) {
        List<String> cells=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        boolean quoted=false;
        for(int i=0;i<line.length();i++) {
            char ch=line.charAt(i);
            if(ch=='"') {
                if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){current.append('"');i++;}
                else quoted=!quoted;
            }else if(ch==',' && !quoted) {cells.add(current.toString());current.setLength(0);}
            else current.append(ch);
        }
        cells.add(current.toString());
        return cells;
    }
}
