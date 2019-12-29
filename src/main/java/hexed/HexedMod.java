package hexed;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.NetServer.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.gen.*;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;

import static arc.util.Log.info;
import static mindustry.Vars.*;

public class HexedMod extends Plugin{
    private final Rules rules = new Rules();

    private Schematic start;
    private HexedGenerator lastGenerator;
    private ObjectSet<Team> dying = new ObjectSet<>();

    @Override
    public void init(){
        rules.pvp = true;
        rules.tags.put("hexed", "true");
        rules.loadout = ItemStack.list(Items.copper, 300, Items.lead, 300, Items.graphite, 100, Items.metaglass, 100, Items.silicon, 100);
        rules.buildCostMultiplier = 0.5f;
        rules.unitBuildSpeedMultiplier = 2f;
        rules.enemyCoreBuildRadius = (HexedGenerator.radius + 1) * tilesize;

        start = Schematics.readBase64("bXNjaAB4nE2SXW7DIBCE12Awa+elB/FLr9FTEAtVkVzbIj9Vbl8wu6NaMkzMzjdLgCaaOuq3+JPIXeM9fTJNy34cKc+/cV3p49+PeY35O1FY9u2V3nsmG/NC030v3+cjbmklv8ZtSZkuy57TvD2XNT3vdMnpiLdSs9+2Bw3X+Hik/Cair/JSRzqaU9Wxh3JQHnUD6cMydzLbok5tTZmV0oHSCaU6uBmKGiXCyFp/VtbVqozUmTqcZAOyAdmAbFp/1hUV4NUMiwyLDCsZvtAcdqKOHo6+OUx/dqjfAhSjUr1KG4pq3kBO0qpq/dVVhkO9HjSPXI9cj1yPXA+vnpArSnc5NEdXFbcjKEodgfQJ+J9D27upykF5OAYhB5ADyAFkBpmll3r2etcYGYwMloxaN8hpMO4LgzzKvaunq5QRlBGdjtLDH6ERK0o=");

        Events.on(Trigger.update, () -> {
            if(active()){
                for(Player player : playerGroup.all()){
                    if(player.getTeam() != Team.derelict && player.getTeam().cores().isEmpty()){
                        player.kill();
                        killTiles(player.getTeam());
                        Call.onInfoMessage(player.con, "Your cores have been destroyed. You are defeated.");
                        player.setTeam(Team.derelict);
                    }
                }
            }
        });

        Events.on(PlayerLeave.class, event -> {
            if(event.player.getTeam() != Team.derelict){
                killTiles(event.player.getTeam());
            }
        });

        Events.on(PlayerJoin.class, event -> {
            lastGenerator.hex.shuffle();
            for(int i = 0; i < lastGenerator.hex.size; i++){
                int x = Pos.x(lastGenerator.hex.get(i));
                int y = Pos.y(lastGenerator.hex.get(i));
                if(!world.tile(x, y).block().synthetic()){
                    loadout(event.player, x, y);
                    break;
                }
            }
        });

        TeamAssigner prev = netServer.assigner;
        netServer.assigner = (player, players) -> {
            Array<Player> arr = Array.with(players);
            if(active()){
                //pick first inactive team
                for(Team team : Team.all()){
                    if(team.id > 5 && !team.active() && !arr.contains(p -> p.getTeam() == team) && !dying.contains(team)){
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
            world.loadGenerator(lastGenerator = new HexedGenerator());
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();
        });

        handler.register("r", "Restart the server.", args -> System.exit(2));
    }

    @Override
    public void registerClientCommands(CommandHandler handler){

    }

    private void killTiles(Team team){
        dying.add(team);
        Time.runTask(8f, () -> dying.remove(team));
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.entity != null && tile.getTeam() == team){
                    Time.run(Mathf.random(60f * 6), tile.entity::kill);
                }
            }
        }
    }

    private void loadout(Player player, int x, int y){

        Stile coreTile = start.tiles.find(s -> s.block instanceof CoreBlock);
        if(coreTile == null) throw new IllegalArgumentException("Schematic has no core tile. Exiting.");
        int ox = x - coreTile.x, oy = y - coreTile.y;
        start.tiles.each(st -> {
            Tile tile = world.tile(st.x + ox, st.y + oy);
            if(tile == null) return;

            Call.onConstructFinish(tile, st.block, -1, st.rotation, player.getTeam(), true);
            if(st.block.posConfig){
                tile.configureAny(Pos.get(tile.x - st.x + Pos.x(st.config), tile.y - st.y + Pos.y(st.config)));
            }else{
                tile.configureAny(st.config);
            }

            //if(st.block instanceof Drill){
            //    tile.getLinkedTiles(t -> t.setOverlay(Blocks.oreCopper));
           // }
        });
    }

    public boolean active(){
        return state.rules.tags.getBool("hexed");
    }


}
