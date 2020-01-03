package hexed;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.core.NetServer.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.gen.*;
import mindustry.net.Packets.*;
import mindustry.plugin.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;

import static arc.util.Log.info;
import static mindustry.Vars.*;

public class HexedMod extends Plugin{
    //in ticks: 20 minutes
    private final static double roundTime = 50 * 60 * 60;
    private final Rules rules = new Rules();
    private IntSet counts = IntSet.with(10, 5, 1, 30);
    private IntSet countdownsUsed = new IntSet();

    private Schematic start;
    private HexedGenerator lastGenerator;
    private ObjectSet<Team> dying = new ObjectSet<>();
    private ObjectSet<Team> chosen = new ObjectSet<>();
    private ObjectSet<String> eliminated = new ObjectSet<>();
    private double counter = 0f;

    //rules add bannedBlocks [ripple, hail]
    @Override
    public void init(){
        rules.pvp = true;
        rules.tags.put("hexed", "true");
        rules.loadout = ItemStack.list(Items.copper, 300, Items.lead, 500, Items.graphite, 150, Items.metaglass, 150, Items.silicon, 150, Items.plastanium, 50);
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 1f / 3f;
        rules.canGameOver = false;
        rules.unitBuildSpeedMultiplier = 1f;
        rules.playerDamageMultiplier = 0.25f;
        rules.enemyCoreBuildRadius = (HexedGenerator.radius - 1) * tilesize / 2f;
        rules.unitDamageMultiplier = 1f;
        rules.playerHealthMultiplier = 1f;

        start = Schematics.readBase64("bXNjaAB4nE2SgY7CIAyGC2yDsXkXH2Tvcq+AkzMmc1tQz/j210JpXDL8hu3/lxYY4FtBs4ZbBLvG1ync4wGO87bvMU2vsCzTEtIlwvCxBW7e1r/43hKYkGY4nFN4XqbfMD+29IbhvmHOtIc1LjCmuIcrfm3X9QH2PofHIyYY5y3FaX3OS3ze4fiRwX7dLa5nDHTPddkCkT3l1DcA/OALihZNq4H6NHnV+HZCVshJXA9VYZC9kfVU+VQGKSsbjVT1lOgp1qO4rGIo9yvnquxH1ORIohap6HVIDbtpaNlDi4cWD80eFJdrNhbJc8W61Jzdqi/3wrRIRii7GYdelvWMZDQs1kNbqtYe9/KuGvDX5zD6d5SML66+5dwRqXgQee5GK3Edxw1ITfb3SJ71OomzUAdjuWsWqZyJavd8Issdb5BqVbaoGCVzJqrddaUGTWSFHPs67m6H5HlaTqbqpFc91Kfn+2eQSp9pr96/Xtx6cevZjeKKDuUOklvvXy9uPGdNZFjZi7IXZS/n8Hyf/wFbjj/q");
        netServer.admins.addChatFilter((player, text) -> {
            for(String swear : CurseFilter.swears){
                text = text.replace(swear, "****");
            }

            return text;
        });

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

                int minsToGo = (int)(roundTime - counter) / 60 / 60;
                if(counts.contains(minsToGo) && !countdownsUsed.contains(minsToGo)){
                    Call.sendMessage("[accent]--- [scarlet] " + minsToGo + "[] minutes until server automatically resets![accent]  ---");
                    countdownsUsed.add(minsToGo);
                }

                counter += Time.delta();

                //kick everyone and restart w/ the script
                if(counter > roundTime){
                    Log.info("&ly--SERVER RESTARTING--");
                    netServer.kickAll(KickReason.serverRestarting);
                    Time.runTask(5f, () -> System.exit(2));
                }
            }else{
                counter = 0;
                countdownsUsed.clear();
            }
        });

        Events.on(PlayerLeave.class, event -> {
            if(active() && event.player.getTeam() != Team.derelict){
                killTiles(event.player.getTeam());
            }
        });

        Events.on(PlayerJoin.class, event -> {
            if(!active() || event.player.getTeam() == Team.derelict) return;

            lastGenerator.hex.shuffle();
            boolean found = false;
            for(int i = 0; i < lastGenerator.hex.size; i++){
                int x = Pos.x(lastGenerator.hex.get(i));
                int y = Pos.y(lastGenerator.hex.get(i));
                int[] health = {0};
                Geometry.circle(x, y, 15, (cx, cy) -> {
                    if(world.tile(cx, cy) != null && world.tile(cx, cy).block().synthetic()){
                        health[0] += world.tile(cx, cy).block().health;
                    }
                });
                if(health[0] > 1300){
                    loadout(event.player, x, y);
                    found = true;
                    Core.app.post(() -> chosen.remove(event.player.getTeam()));
                    break;
                }
            }

            if(!found){
                Call.onInfoMessage(event.player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                event.player.kill();
                event.player.setTeam(Team.derelict);
            }
        });

        TeamAssigner prev = netServer.assigner;
        netServer.assigner = (player, players) -> {
            Array<Player> arr = Array.with(players);
            if(active() && eliminated.contains(player.uuid)){
                Call.onInfoMessage(player.con, "You have been eliminated! Wait until the round ends until connecting again.");
                return Team.derelict;
            }

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

        handler.register("countdown", "Get the hexed restart countdown.", args -> {
            Log.info("Time until round ends: &lc{0} minutes", (int)(roundTime - counter) / 60 / 60);
        });

        handler.register("r", "Restart the server.", args -> System.exit(2));
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("spectate", "Enter spectator mode. This destroys your base.", (args, player) -> {
             if(player.getTeam() == Team.derelict){
                 player.sendMessage("[scarlet]You're already spectating.");
             }else{
                 killTiles(player.getTeam());
                 player.kill();
                 player.setTeam(Team.derelict);
             }
        });
        handler.<Player>register("countdown", "Get the hexed restart countdown.", (args, player)-> {
            player.sendMessage("[scarlet]Time until round ends: [yellow]" + (int)(roundTime - counter) / 60 / 60 + " minutes " + (int)(roundTime - counter) / 60 % 60 + " seconds");
        });
        handler.<Player>register("alliance", "Select a player to be allance.", (args, player) -> {
            if(args.length == 0){
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to alliance: \n");
                for(Player p : playerGroup.all()){
                    if(p.con == null || p == player) continue;

                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(")");
                    if (player.getTeam().isAllies(p.getTeam())){
                        builder.append("[green] /Allies/ \n");
                    }else{
                        builder.append("\n");
                    }
                }
                player.sendMessage(builder.toString());
            }else{
                Player found;
                if(args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))){
                    int id = Strings.parseInt(args[0].substring(1));
                    found = playerGroup.find(p -> p.id == id);
                }else{
                    found = playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));
                }
                if(found != null){
                    if(player.getTeam().isAllies(found.getTeam()) ){
                        player.sendMessage("[scarlet]You already alliance him.");
                        if (!found.getTeam().isAllies(player.getTeam())) player.sendMessage("[scarlet]However, he didn't alliance you as now.");
                    }else{
                        player.getTeam().addAllies(found.getTeam());
                        player.sendMessage("[scarlet]You alliance him.");
                        StringBuilder builder = new StringBuilder();
                        builder.append("[scarlet]").append(player.name).append(" alliance you. If you want alliance him, please do `/alliance #").append(player.id).append("`\n");
                        found.sendMessage(builder.toString());
                    }
                }else{
                    player.sendMessage("[scarlet]No player[orange]'" + args[0] + "'[scarlet] found.");
                }
            }
        });
        handler.<Player>register("broke", "Select ally to break the alliance. If you do, the ally will knows you did.", (args, player) -> {
            if(args.length == 0){
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Allies to broke: \n");
                for(Player p : playerGroup.all()){
                    if(p.con == null || p == player) continue;

                    if(player.getTeam().isAllies(p.getTeam())) {
                        builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(") [green] /Allies/\n");
                    }
                }
                player.sendMessage(builder.toString());
            }else{
                Player found;
                if(args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))){
                    int id = Strings.parseInt(args[0].substring(1));
                    found = playerGroup.find(p -> p.id == id);
                }else{
                    found = playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));
                }
                if(found != null){
                    if(player.getTeam().isAllies(found.getTeam()) ){
                        player.getTeam().removeAllies(found.getTeam());
                        if (!found.getTeam().isAllies(player.getTeam()) ){
                            player.sendMessage("[scarlet]You undo the alliance with \"" + found.name + "\".");
                            found.sendMessage("[yellow]" + player.name + " undo the alliance with you.");   
                        }else{
                            found.getTeam().removeAllies(player.getTeam());
                            player.sendMessage("[scarlet]You break the alliance with \"" + found.name + "\".");
                            found.sendMessage("[yellow]Warning! " + player.name + " breaks alliance with you.");   
                        }
                    }else{
                        player.sendMessage("[scarlet]He didn't alliance you.");
                    }
                }else{
                    player.sendMessage("[scarlet]No player[orange]'" + args[0] + "'[scarlet] found.");
                }
            }
        });
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
        return state.rules.tags.getBool("hexed") && !state.is(State.menu);
    }


}
