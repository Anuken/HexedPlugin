package hexed;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
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
    private ObjectSet<Team> chosen = new ObjectSet<>();

    @Override
    public void init(){
        rules.pvp = true;
        rules.tags.put("hexed", "true");
        rules.loadout = ItemStack.list(Items.copper, 300, Items.lead, 500, Items.graphite, 150, Items.metaglass, 150, Items.silicon, 150);
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 1f / 3f;
        rules.unitBuildSpeedMultiplier = 1f;
        rules.enemyCoreBuildRadius = (HexedGenerator.radius + 2) * tilesize / 2f;
        rules.unitDamageMultiplier = 1f;
        rules.playerHealthMultiplier = 2f;

        start = Schematics.readBase64("bXNjaAB4nE2SgY7CIAyGC2yDsXkXH2Tvcq+AkzMmc1tQz/j210JpXDL8hu3/lxYY4FtBs4ZbBLvG1ync4wGO87bvMU2vsCzTEtIlwvCxBW7e1r/43hKYkGY4nFN4XqbfMD+29IbhvmHOtIc1LjCmuIcrfm3X9QH2PofHIyYY5y3FaX3OS3ze4fiRwX7dLa5nDHTPddkCkT3l1DcA/OALihZNq4H6NHnV+HZCVshJXA9VYZC9kfVU+VQGKSsbjVT1lOgp1qO4rGIo9yvnquxH1ORIohap6HVIDbtpaNlDi4cWD80eFJdrNhbJc8W61Jzdqi/3wrRIRii7GYdelvWMZDQs1kNbqtYe9/KuGvDX5zD6d5SML66+5dwRqXgQee5GK3Edxw1ITfb3SJ71OomzUAdjuWsWqZyJavd8Issdb5BqVbaoGCVzJqrddaUGTWSFHPs67m6H5HlaTqbqpFc91Kfn+2eQSp9pr96/Xtx6cevZjeKKDuUOklvvXy9uPGdNZFjZi7IXZS/n8Hyf/wFbjj/q");

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
                boolean[] synth = {false};
                Geometry.circle(x, y, 15, (cx, cy) -> {
                    if(world.tile(x, y).block().synthetic()){
                        synth[0] = true;
                    }
                });
                if(!synth[0]){
                    loadout(event.player, x, y);
                    Core.app.post(() -> chosen.remove(event.player.getTeam()));
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
                    if(team.id > 5 && !team.active() && !arr.contains(p -> p.getTeam() == team) && !dying.contains(team) && !chosen.contains(team)){
                        chosen.add(team);
                        return team;
                    }
                }
                Call.onInfoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                return Team.derelict;
            }else{
                return prev.assign(player, players);
            }
        };
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("hexed", "Begin hosting with the Hexed gamemode.", args -> {
            chosen.clear();
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
            if(tile.block() instanceof CoreBlock){
                for(ItemStack stack : state.rules.loadout){
                    Call.transferItemTo(stack.item, stack.amount, tile.drawx(), tile.drawy(), tile);
                }
            }
        });
    }

    public boolean active(){
        return state.rules.tags.getBool("hexed");
    }


}
