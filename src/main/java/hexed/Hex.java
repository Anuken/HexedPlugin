package hexed;

import arc.math.geom.*;
import arc.util.ArcAnnotate.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;

import java.util.*;

import static mindustry.Vars.*;

public class Hex{
    private float[] counts = new float[256];

    public final static int size = 516;
    public final static int diameter = 74;
    public final static int radius = diameter / 2;
    public final static int spacing = 78;

    public final int x, y;
    public final float wx, wy;
    public final float rad = radius * tilesize;

    public @Nullable Team controller;
    public Timekeeper spawnTime = new Timekeeper(HexedMod.spawnDelay);

    public Hex(int x, int y){
        this.x = x;
        this.y = y;
        wx = x * tilesize;
        wy = y * tilesize;
    }

    public void updateController(){
        controller = findController();
    }

    public @Nullable Team findController(){
        if(world.tile(x, y).getTeam() != Team.derelict && world.tile(x, y).block() instanceof CoreBlock){
            return world.tile(x, y).getTeam();
        }

        Arrays.fill(counts, 0);
        unitGroup.intersect(wx - rad, wy - rad, rad*2, rad*2).each(e -> {
            if(contains(e.x, e.y)){
                counts[e.getTeam().id] += e.health;
            }
        });

        for(int cx = x - radius; cx < x + radius; cx++){
            for(int cy = y - radius; cy < y + radius; cy++){
                Tile tile = world.tile(cx, cy);
                if(tile != null && tile.synthetic() && contains(tile)){
                    counts[tile.getTeam().id] += tile.block().health;
                }
            }
        }

        TeamData data = state.teams.getActive().max(t -> counts[t.team.id]);
        if(data != null && data.team != Team.derelict && counts[data.team.id] >= HexedMod.healthRequirement){
            return data.team;
        }
        return null;
    }

    public boolean contains(float x, float y){
        return Intersector.isInsideHexagon(wx, wy, rad, x, y);
    }

    public boolean contains(Tile tile){
        return contains(tile.worldx(), tile.worldy());
    }
}
