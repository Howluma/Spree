package dev.howluma.spreecore;

import java.util.ArrayList;
import java.util.List;

/** Small, allocation-light geometry helpers shared by Web Escape and its tests. */
final class WebEscapeMath {
    private WebEscapeMath() {}

    @FunctionalInterface interface BlockTest {
        boolean matches(int x, int y, int z);
    }

    /** Check EVERY occupied block, including head- and edge-level cobwebs. */
    static boolean occupied(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, BlockTest predicate) {
        if(maxX<=minX || maxY<=minY || maxZ<=minZ)return false;
        final double epsilon=0.00001;
        for(int x=(int)Math.floor(minX+epsilon);x<=(int)Math.floor(maxX-epsilon);x++)
            for(int y=(int)Math.floor(minY+epsilon);y<=(int)Math.floor(maxY-epsilon);y++)
                for(int z=(int)Math.floor(minZ+epsilon);z<=(int)Math.floor(maxZ-epsilon);z++)
                    if(predicate.matches(x,y,z))return true;
        return false;
    }

    /**
     * Search horizontal rings closest-first; keep the same altitude before
     * trying one block up or down. Limit the search to five blocks so this
     * remains a short cobweb escape, never a long-range teleport.
     */
    static List<int[]> candidateOffsets(int maxRadius) {
        List<int[]> cells=new ArrayList<>();
        for(int radius=1;radius<=Math.min(5,Math.max(0,maxRadius));radius++)
            for(int dy:new int[]{0,1,-1})
                for(int dx=-radius;dx<=radius;dx++)
                    for(int dz=-radius;dz<=radius;dz++)
                        if(Math.max(Math.abs(dx),Math.abs(dz))==radius)
                            cells.add(new int[]{dx,dy,dz});
        return cells;
    }
}
