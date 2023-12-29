package hexed;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.Tile;

import java.util.LinkedHashMap;
import java.util.Map;

import static mindustry.Vars.world;

public class HexData{
    /** All hexes on the map. No order. */
    private Seq<Hex> hexes = new Seq<>();
    /** Maps world pos -> hex */
    private IntMap<Hex> hexPos = new IntMap<>();
    /** Maps team ID -> player */
    private IntMap<Player> teamMap = new IntMap<>();
    /** Maps team ID -> list of controlled hexes */
    private IntMap<Seq<Hex>> control = new IntMap<>();
    /** Data of specific teams. */
    private HexTeam[] teamData = new HexTeam[256];

    public void updateStats(){
        teamMap.clear();
        for(Player player : Groups.player){
            teamMap.put(player.team().id, player);
        }
        for(Seq<Hex> arr : control.values()){
            arr.clear();
        }

        for(Player player : Groups.player){
            if(player.dead()) continue;

            HexTeam team = data(player);
            Hex newHex = hexes.min(h -> player.dst2(h.wx, h.wy));
            if(team.location != newHex){
                team.location = newHex;
                team.progressPercent = newHex.getProgressPercent(player.team());
                team.lastCaptured = newHex.controller == player.team();
                Events.fire(new HexMoveEvent(player));
            }
            float currPercent = newHex.getProgressPercent(player.team());
            int lp = (int)(team.progressPercent);
            int np = (int)(currPercent);
            team.progressPercent = currPercent;
            if(np != lp){
                Events.fire(new ProgressIncreaseEvent(player, currPercent));
            }

            boolean captured = newHex.controller == player.team();
            if(team.lastCaptured != captured){
                team.lastCaptured = captured;
                if(captured && !newHex.hasCore()){
                    Events.fire(new HexCaptureEvent(player, newHex));
                }
            }
        }

        for(Hex hex : hexes){
            if(hex.controller != null){
                if(!control.containsKey(hex.controller.id)){
                    control.put(hex.controller.id, new Seq<>());
                }
                control.get(hex.controller.id).add(hex);
            }
        }
    }

    public void updateControl(){
        hexes.each(Hex::updateController);
    }

    /** Allocates a new array of players sorted by score, descending. */
    public Seq<Player> getLeaderboard(){
        Seq<Player> players = new Seq<>();
        Groups.player.copy(players);
        players.sort(p -> -getControlled(p).size);
        return players;
    }
    public Map<String, Integer> rankNames(){
        // uuid to rank, 1 is the first rank, tie is allowed
        Map<String, Integer> rankedNames = new LinkedHashMap<>();
        int rank = 0;
        int prevValue = Integer.MIN_VALUE;
        Seq<Player> list = getLeaderboard();
        for (Player p : list) {
            // Assign the rank, incrementing only if the current value is different from the previous value
            if (getControlled(p).size != prevValue) {
                rank++;
                prevValue = getControlled(p).size;
            }
            rankedNames.put(p.uuid(), rank);
        }

        return rankedNames;
    }
    public @Nullable Player getPlayer(Team team){
        return teamMap.get(team.id);
    }

    public Seq<Hex> getControlled(Player player){
        return getControlled(player.team());
    }

    public Seq<Hex> getControlled(Team team){
        if(!control.containsKey(team.id)){
            control.put(team.id, new Seq<>());
        }
        return control.get(team.id);
    }

    public void initHexes(IntSeq ints){
        for(int i = 0; i < ints.size; i++){
            int pos = ints.get(i);
            hexes.add(new Hex(i, Point2.x(pos), Point2.y(pos)));
            hexPos.put(pos, hexes.peek());
        }
    }

    public Seq<Hex> hexes(){
        return hexes;
    }

    public @Nullable Hex getHex(int position){
        return hexPos.get(position);
    }

    public HexTeam data(Team team){
        if(teamData[team.id] == null) teamData[team.id] = new HexTeam();
        return teamData[team.id];
    }

    public HexTeam data(Player player){
        return data(player.team());
    }

    public static class HexTeam{
        public boolean dying;
        public boolean chosen;
        public @Nullable Hex location;
        public float progressPercent;
        public boolean lastCaptured;
        public Timekeeper lastMessage = new Timekeeper(HexedMod.messageTime);
    }

    public static class HexCaptureEvent{
        public final Player player;
        public final Hex hex;

        public HexCaptureEvent(Player player, Hex hex){
            this.player = player;
            this.hex = hex;
            Tile tile = world.tile(hex.x, hex.y);
            tile.setNet(Blocks.coreShard, player.team(), 0);
        }
    }

    public static class HexMoveEvent{
        public final Player player;

        public HexMoveEvent(Player player){
            this.player = player;
        }
    }

    public static class ProgressIncreaseEvent{
        public final Player player;
        public final float percent;

        public ProgressIncreaseEvent(Player player, float percent){
            this.player = player;
            this.percent = percent;
        }
    }
}
