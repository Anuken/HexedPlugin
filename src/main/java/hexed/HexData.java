package hexed;

import arc.struct.*;
import arc.util.ArcAnnotate.*;
import mindustry.*;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.world.*;

public class HexData{
    /** All hexes on the map. No order. */
    private Array<Hex> hexes = new Array<>();
    /** Maps world pos -> hex */
    private IntMap<Hex> hexPos = new IntMap<>();
    /** Maps team ID -> player */
    private IntMap<Player> teamMap = new IntMap<>();
    /** Maps team ID -> list of controlled hexes */
    private IntMap<Array<Hex>> control = new IntMap<>();
    /** Data of specific teams. */
    private HexTeam[] teamData = new HexTeam[256];

    public void updateStats(){
        teamMap.clear();
        for(Array<Hex> arr : control.values()){
            arr.clear();
        }

        for(Player player : Vars.playerGroup.all()){
            teamMap.put(player.getTeam().id, player);
        }

        for(Hex hex : hexes){
            if(hex.controller != null){
                if(!control.containsKey(hex.controller.id)){
                    control.put(hex.controller.id, new Array<>());
                }
                control.get(hex.controller.id).add(hex);
            }
        }
    }

    public void updateControl(){
        hexes.each(Hex::updateController);
    }

    /** Allocates a new array of players sorted by score, descending. */
    public Array<Player> getLeaderboard(){
        Array<Player> players = Vars.playerGroup.all().copy();
        players.sort(p -> -getControlled(p).size);
        return players;
    }

    public @Nullable Player getPlayer(Team team){
        return teamMap.get(team.id);
    }

    public Array<Hex> getControlled(Player player){
        return getControlled(player.getTeam());
    }

    public Array<Hex> getControlled(Team team){
        if(!control.containsKey(team.id)){
            control.put(team.id, new Array<>());
        }
        return control.get(team.id);
    }

    public void initHexes(IntArray ints){
        for(int i = 0; i < ints.size; i++){
            int pos = ints.get(i);
            hexes.add(new Hex(Pos.x(pos), Pos.y(pos)));
            hexPos.put(pos, hexes.peek());
        }
    }

    public Array<Hex> hexes(){
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
        return data(player.getTeam());
    }

    public static class HexTeam{
        public boolean dying;
        public boolean chosen;
    }
}
