package dev.howluma.spreecore;

import java.util.Locale;

/** Pure, bounded duration logic for the server-wide killstreak freeze. */
final class StreakFreezeMath {
    static final long MAX_SECONDS = 7L * 24 * 60 * 60;
    private StreakFreezeMath() {}

    /**
     * Accepts "30" (minutes), "90s", "15m", "2h" or "1d".
     * Returns -1 if invalid/out of bounds. Largest allowed freeze is 7 days.
     */
    static long parseSeconds(String input) {
        if(input==null) return -1;
        String token=input.trim().toLowerCase(Locale.ROOT);
        if(!token.matches("[0-9]{1,9}[smhd]?")) return -1;
        char suffix=token.charAt(token.length()-1);
        long multiple=switch(suffix) {
            case 's' -> 1;
            case 'h' -> 3600;
            case 'd' -> 86400;
            default -> 60; // m or bare integer
        };
        String digits=Character.isDigit(suffix)?token:
                token.substring(0,token.length()-1);
        long amount;
        try { amount=Long.parseLong(digits); }
        catch(NumberFormatException ex) {return -1;}
        if(amount<=0 || amount>MAX_SECONDS/multiple) return -1;
        return amount*multiple;
    }

    static long endAt(long nowMillis,long seconds) {
        if(seconds<=0 || seconds>MAX_SECONDS) throw new IllegalArgumentException("Freeze must be between 1 second and 7 days.");
        return Math.addExact(nowMillis,Math.multiplyExact(seconds,1000));
    }

    /** The only exception to a global freeze is an explicit staff score edit. */
    static boolean blocksAutomaticChange(boolean frozen,boolean explicitAdminOverride) {
        return frozen && !explicitAdminOverride;
    }

    static boolean active(long endMillis,long nowMillis) {
        return endMillis>nowMillis;
    }

    static long secondsRemaining(long endMillis,long nowMillis) {
        if(!active(endMillis,nowMillis)) return 0;
        long difference=endMillis-nowMillis;
        return difference/1000+(difference%1000>0?1:0);
    }

    static String format(long seconds) {
        if(seconds<=0)return "0s";
        long days=seconds/86400;
        long hours=(seconds%86400)/3600;
        long minutes=(seconds%3600)/60;
        long remainder=seconds%60;
        StringBuilder out=new StringBuilder();
        if(days>0)out.append(days).append("d ");
        if(hours>0)out.append(hours).append("h ");
        if(minutes>0)out.append(minutes).append("m ");
        if(remainder>0 || out.length()==0)out.append(remainder).append("s ");
        return out.toString().trim();
    }
}
