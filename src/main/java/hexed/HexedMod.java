package hexed;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.core.NetServer.*;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.net.Administration.*;
import mindustry.plugin.*;
import mindustry.world.blocks.storage.*;

import java.io.*;
import java.net.*;

import static arc.util.Log.*;
import static mindustry.Vars.*;

public class HexedMod extends Plugin{
    private final static Rules rules = new Rules(){{
        pvp = true;
        tags.put("hexed", "true");
    }};

    @Override
    public void init(){
        Events.on(Trigger.update, () -> {
            for(Player player : playerGroup.all()){
                if(player.getTeam() != Team.derelict && player.getTeam().cores().isEmpty()){
                    player.kill();
                    for(TileEntity entity : tileGroup.all()){
                        //kill after a delay for dramatic effect
                        Time.run(Mathf.random(60f * 4), entity::kill);
                    }
                    Call.onInfoMessage(player.con, "Your cores have been destroyed. You are defeated.");
                    player.setTeam(Team.derelict);
                }
            }
        });

        Events.on(PlayerJoin.class, event -> {

        });

        TeamAssigner prev = netServer.assigner;
        netServer.assigner = (player, players) -> {
            Array<Player> arr = Array.with(players);
            if(active()){
                //pick first inactive team
                for(Team team : Team.all()){
                    if(team.id > 5 && !team.active() && !arr.contains(p -> p.getTeam() == team)){
                        return team;
                    }
                }
                return Team.derelict;
            }else{
                return prev.assign(player, players);
            }
        };
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("hexed", "Begin hosting with the Hexed gamemode.", args -> {
            logic.reset();
            Log.info("Generating map...");
            world.loadGenerator(new HexedGenerator());
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){

    }

    public boolean active(){
        return state.rules.tags.getBool("hexed");
    }


}
