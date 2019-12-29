package hexed;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.maps.*;
import mindustry.maps.generators.*;
import mindustry.world.*;

import static mindustry.Vars.world;

public class HexedGenerator extends Generator{
    public final static int size = 448;
    public final static int radius = 55;
    public final static int spacing = 70;

    public HexedGenerator() {
        super(size, size);
    }

    @Override
    public void generate(Tile[][] tiles){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                tiles[x][y] = new Tile(x, y, Blocks.stone.id, Blocks.oreCopper.id, Blocks.rocks.id);
            }
        }

        IntArray hex = getHex();
        for(int i = 0; i < hex.size; i++){
            int x = Pos.x(hex.get(i));
            int y = Pos.y(hex.get(i));
            Geometry.circle(x, y, width, height, radius, (cx, cy) -> {
                if(Intersector.isInsideHexagon(x, y, radius, cx, cy)){
                    Tile tile = tiles[cx][cy];
                    tile.setBlock(Blocks.air);
                }
            });
            Angles.circle(3, 360f / 3 / 2f - 90, f -> {
                Tmp.v1.trns(f, radius / 1.5f);
                Bresenham2.line(x, y, x + (int)Tmp.v1.x, y + (int)Tmp.v1.y, (cx, cy) -> {
                    Geometry.circle(cx, cy, width, height, 3, (c2x, c2y) -> tiles[c2x][c2y].setBlock(Blocks.air));
                });
            });
        }

        world.setMap(new Map(StringMap.of("name", "Hex")));
    }

    public IntArray getHex(){
        IntArray array = new IntArray();
        double h = Math.sqrt(3) * spacing/2;
        //base horizontal spacing=1.5w
        //offset = 3/4w
        for(int x = 0; x < width / spacing - 2; x++){
            for(int y = 0; y < height / (h/2) - 2; y++){
                int cx = (int)(x * spacing*1.5 + (y%2)*spacing*3.0/4) + spacing/2;
                int cy = (int)(y * h / 2) + spacing/2;
                array.add(Pos.get(cx, cy));
            }
        }
        return array;
    }
}
