package hexed;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import hexed.HexData.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.core.NetServer.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Schematic.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.net.Packets.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.storage.*;
import org.bson.Document;

import java.util.*;

import static arc.util.Log.*;
import static com.mongodb.client.model.Updates.*;
import static com.mongodb.client.model.Updates.push;
import static java.lang.Math.max;
import static mindustry.Vars.*;

public class HexedMod extends Plugin{
    //in seconds
    public static final float spawnDelay = 60 * 4;
    //health requirement needed to capture a hex; no longer used
    public static final float healthRequirement = 3500;
    //item requirement to captured a hex
    public static final int itemRequirement = 5510;

    public static final int messageTime = 1;
    //in ticks: 60 minutes
    private final static int roundTime = 60 * 60 * 40;
    //in ticks: 3 minutes
    private final static int leaderboardTime = 60 * 60 * 2;

    private final static int updateTime = 60 * 2;

    private final static int winCondition = 25;

    private final static int timerBoard = 0, timerUpdate = 1, timerWinCheck = 2;

    private final Rules rules = new Rules();
    private Interval interval = new Interval(5);

    private HexData data;
    private boolean restarting = false, registered = false;

    private Schematic start,start1,midgamestart,worldblock;
    private double counter = 0f;
    private int lastMin;
    public HashMap<String, Integer> PlayersWhoLeft;
    //public MMR_config MMRsystem;

    public ObjectSet<String> joinedPlayers = new ObjectSet<>();
    private List<Long> allMMR = new ArrayList<>();
    public HashMap<String, Long> PlayersMMR = new HashMap<>();

    public MMR_mongo mmrmongo;
    private String mongoURL = "";

    @Override
    public void init(){
        rules.pvp = true;
        rules.tags.put("hexed", "true");
        rules.canGameOver = false;
        rules.polygonCoreProtection = false;
        rules.enemyCoreBuildRadius = 35f*8;
        rules.bannedBlocks = new ObjectSet<>();
        rules.bannedBlocks.addAll(Blocks.ripple,Blocks.breach);
        // default rules to run in console:
        //rules add bannedBlocks [switch ,message ,micro-processor ,logic-processor ,hyper-processor ,logic-display ,large-logic-display ,canvas]
        rules.bannedBlocks.addAll(Blocks.diffuse ,Blocks.sublimate ,Blocks.titan ,Blocks.disperse ,Blocks.afflict ,Blocks.lustre ,Blocks.scathe ,Blocks.smite ,Blocks.malign);
        // diffuse sublimate titan disperse afflict lustre scathe smite malign
        rules.bannedBlocks.addAll(Blocks.buildTower,Blocks.regenProjector,Blocks.shockwaveTower,Blocks.coreBastion,
                Blocks.coreAcropolis,Blocks.coreCitadel,Blocks.reinforcedContainer,Blocks.reinforcedVault);
        // build tower, regen projector, shockwave tower, bastion, citadel, acropolis, reinforced container , reinforced vault

        rules.bannedBlocks.addAll(Blocks.ventCondenser, Blocks.cliffCrusher,Blocks.plasmaBore, Blocks.largePlasmaBore,
                Blocks.impactDrill,Blocks.eruptionDrill);
        // vent condenser, cliff crusher , plasma bore, large plasma bore, impact drill, eruption drill
        rules.bannedBlocks.addAll(Blocks.beamNode,Blocks.beamTower,Blocks.turbineCondenser,Blocks.chemicalCombustionChamber,
                Blocks.pyrolysisGenerator,Blocks.fluxReactor,Blocks.neoplasiaReactor);
        // beam node, beam tower, turbine condenser, chemical combustion chamber, pyro lysis generator, flux reactor, neoplasia reactor
        //
        rules.bannedBlocks.addAll(Blocks.message,Blocks.switchBlock , Blocks.microProcessor,Blocks.logicProcessor,Blocks.hyperProcessor,
                Blocks.memoryCell,Blocks.memoryBank,Blocks.logicDisplay,Blocks.largeLogicDisplay,Blocks.canvas,Blocks.reinforcedMessage
        );
        rules.bannedBlocks.addAll(Blocks.tankAssembler,Blocks.tankFabricator,Blocks.tankRefabricator,
                Blocks.shipAssembler,Blocks.shipFabricator,Blocks.shipRefabricator,
                Blocks.mechAssembler,Blocks.mechFabricator,Blocks.mechRefabricator,
                Blocks.primeRefabricator,Blocks.basicAssemblerModule,Blocks.unitRepairTower,Blocks.reinforcedPayloadConveyor,Blocks.reinforcedPayloadRouter,
                Blocks.payloadMassDriver,Blocks.largePayloadMassDriver,Blocks.deconstructor,Blocks.smallDeconstructor,Blocks.constructor,Blocks.smallDeconstructor,Blocks.payloadLoader,
                Blocks.payloadUnloader,Blocks.largeConstructor);
        rules.planet = Planets.serpulo;
        //rules.bannedBlocks = new ObjectSet<>({Blocks.foreshadow,Blocks.foreshadow});
        rules.coreDestroyClear = true;
        rules.coreCapture = true;
        rules.hideBannedBlocks = true;
        rules.loadout = ItemStack.list(Items.copper, 600, Items.lead, 600, Items.graphite, 100, Items.metaglass, 100, Items.silicon, 50, Items.thorium,20,Items.plastanium, 20,Items.titanium, 20,Items.phaseFabric,5,Items.surgeAlloy,1);
        //for further configuration, use `ruless add <name> <value...>`
        /*
        rules.loadout = ItemStack.list(Items.copper, 300, Items.lead, 500, Items.graphite, 150, Items.metaglass, 150, Items.silicon, 150, Items.plastanium, 50);
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 1f / 2f;
        rules.blockHealthMultiplier = 1.2f;
        rules.unitBuildSpeedMultiplier = 1f;
        rules.unitDamageMultiplier = 1.1f;
        */
        rules.unitDamageMultiplier = 1.5f;
        //old
        // start = Schematics.readBase64("bXNjaAB4nE2SgY7CIAyGC2yDsXkXH2Tvcq+AkzMmc1tQz/j210JpXDL8hu3/lxYY4FtBs4ZbBLvG1ync4wGO87bvMU2vsCzTEtIlwvCxBW7e1r/43hKYkGY4nFN4XqbfMD+29IbhvmHOtIc1LjCmuIcrfm3X9QH2PofHIyYY5y3FaX3OS3ze4fiRwX7dLa5nDHTPddkCkT3l1DcA/OALihZNq4H6NHnV+HZCVshJXA9VYZC9kfVU+VQGKSsbjVT1lOgp1qO4rGIo9yvnquxH1ORIohap6HVIDbtpaNlDi4cWD80eFJdrNhbJc8W61Jzdqi/3wrRIRii7GYdelvWMZDQs1kNbqtYe9/KuGvDX5zD6d5SML66+5dwRqXgQee5GK3Edxw1ITfb3SJ71OomzUAdjuWsWqZyJavd8Issdb5BqVbaoGCVzJqrddaUGTWSFHPs67m6H5HlaTqbqpFc91Kfn+2eQSp9pr96/Xtx6cevZjeKKDuUOklvvXy9uPGdNZFjZi7IXZS/n8Hyf/wFbjj/q");
        start1 = Schematics.readBase64("bXNjaAF4nFWSXW6kMBCE2zZgG8j+aC+QC/CSi+wZHMZaITH2CJhEOXnewnZ15yEZifmwu6rcNNBAPy01JV0zdc9pz49PNFzyPm/L7VhqIXqgP8dypLLcr9NrWtdpTdu/TA/fNinMtbzkt7qRS9tMv271NW9TqZf8qR/2yjfTLZW80rjlW1p4VZdykN/ndBx5o3GuW57KfV7zfaffXxyfId01lwsLw72sNeHOP4v1jYj+8kWGrGE4slg1ZMGOZOkVgeQXVTmocsQKa+sYjpxliM+oz8CH2kAOyh8wwGobVINjmWVBPE/qTSv3rmM0KmzRitVIq5EWkahxKzD3aMRqK1YPcNhpEUUOaMgFjvIkTYqkgSQin2zPq4bMwH99yxqD/ZHJGyJuIR4ZHAT0eJhWax1qA6OBlUsdXwb7UvbSHMOgW4/W0EiPxjym0jBGnoM9z/Pj/GC+G9jEjaELZA6BQwVeEbTGc+gYfGrLkEEHtUc9POJwnrEMhCHvN2pY1LCIMNSiKgdVjpoiYT0+DysPKPCKoDX+IP4DZRpQpQ==");
        midgamestart =Schematics.readBase64("bXNjaAF4nE2Ra27CMBCEN05InAe09AS9QM7SM5hg0UjBRiYUcfH+LN3JgESkZLz2zpfdtbTyZqQI7uil27mz/zyO+wOidu/PQxpP8xiDyFq6+Tum8XLsr26a5OM16ieXDl628zi7gM0hhh9/i0lylwaphtswxeDl/RSvPvUh7v3D0p6jLvqTC36SLvmTGzWKY5ilG2LyfbgMk7+cZfuS+PCWRx/2Pom9hCk6rKqdm2efbiLyJXyyx6tPzlUhYlRKSkWxTKmZ21BaSicZdP3EKSjDa1QWSkZKRkoGSqFxiwhwWeHULqa81LVqpVKgJKOn+jFEGaIMUUZRJlNZP+pH2TnskEKZaq6ETaF6/ChvNFJ0q58GbIP9jlkblrN0tQIBsrS74lnJxkspYFVWqcc59jfCRoGx3KpgheikUEhns/vf/TfHWBeaZXNWcw2koliMxaK5SqUBynLalr+pn9ex4hAWe017DTtSaoykhg9RR8OG92coOaWgLLCGsIawhrU0SllE7f9tPU+n");
        worldblock = Schematics.readBase64("bXNjaAF4nAGrA1T8AAIAAQMABG5hbWUACnRlc3RibG9ja3MAC2Rlc2NyaXB0aW9uAAAABmxhYmVscwACW10BAA93b3JsZC1wcm9jZXNzb3IAAAACAAAAAAAOAAABpniclZRLbsIwEIbppoucYsQBKps0CWHVTW/QTYVYuGBB2jihsSm0p6/jt4jDIwtgZj7/88844mEyeXzfd1UjYArqmSaMck62FFjFedU2kCZHUgnAieGWHd2slrAaJdETyhzcdqTZUsnfeoALInr8Vh7uQ+9iL8JFlnAq4I92LbyIitGk3QM/fOiM+sAIIZvtCc2pWtBo2iOs3cC6btdfGlQne/mTWfg0+TywPaQp1NLQ2440Bs88Z3Y9iuJAU615nPSqyl21qX6AB9Zsjsls7sOdTOiwn4e7Yh8xV9MoRhLHrrzDLtTKss6COsMu1G5knQd1jnWoJspKoN8HUqsuJvdcQtOKV5OOZVksyWVS39XJfEtNd3t2hdm5uAHQIgIwZcoSUeCKAo8p5H5oO0ieRqYLk7Hp/HALr7ztKBG0U+9I1P+AiYxQWIfMX0uRns09yA49ssgNFNf2V1zb37xw7qyN+eyyjdCF+bnwapfblTPTjvtllCg8NGjII3OXub11J5KH7yKKpTmO+Jk7P5HGvu/Iv6LSmKVA6iP5lbuSGhP5/AOQCe/lAAAAAQAADgAAAad4nJWSMW7cQAxFN0i3pyCcYhvDTU4QOy5cpIkDpDBcUCNKS2g0HHCoVXSbHDWcSWykCCBHjSBo5v1P/v/ucHj/Mysng6vvFIPMBCbw9GF4Pn3lC8ZTgTtZkpGiGYbp9PR8c3WcqRQcCZIYDxt8PK7I5q8/pFsaRAlWgo5GTtcQycDJ9CNH5ARnWauKf2xgZy4wY96n3kVC9fNOjRKmAk4aVPyXDLDJUpGjCzXNxGkEpSKLBir78M9UTGVr+BIUM6wYY6nMJVU5mOtIL0Qwjm/BftOtInyvHSdfQKSZkpXqe4ZH0rxEAUw93CtNrNBhoR4kVR/+if0Fkzn/DVK3C8e+sZY8Kvbkqm63ynMKSk72UfxGPfI6RzHRCg2YMbBt+zpfcCIYRXowwnkVnRoxiGjPCY3dfUe2EiXPfO5Im4e5XvOUtFAcAGF0R9YQ+5IP6eLx8IhGL93p/8qrc2XS34HdwINXjcezs6uisfeu+ovia/eiFAzKA/9XKTCB5CzJo2usRn7NplKbjbrh2sPRO74P/+Sg5MO0VcYlTP+64vHk48GfXwmERjkAZGK/uA==");
        //MMRsystem = MMR_config.getInstance();
        org.json.JSONObject configData = configReader.get("config.alex");
        assert configData != null;
        if (configData.has("mongoURL")) {
            mongoURL = configData.getString("mongoURL");
            mmrmongo = new MMR_mongo(mongoURL);
        }
        Events.run(Trigger.update, () -> {
            if(active()){
                data.updateStats();

                for(Player player : Groups.player){
                    if(player.team() != Team.derelict && player.team().cores().isEmpty()){
                        player.clearUnit();
                        killTiles(player.team());
                        Call.sendMessage("[yellow](!)[] [accent]" + player.name + "[lightgray] has been eliminated![yellow] (!)");
                        Call.infoMessage(player.con, "Your cores have been destroyed. You are defeated.");
                        player.team(Team.derelict);
                    }

                    if(player.team() == Team.derelict){
                        player.clearUnit();
                    }else if(data.getControlled(player).size == data.hexes().size){
                        endGame();
                        break;
                    }
                }

                int minsToGo = (int)(roundTime - counter) / 60 / 60;
                if(minsToGo != lastMin){
                    lastMin = minsToGo;
                }

                if(interval.get(timerBoard, leaderboardTime)){
                    Call.infoToast(getLeaderboard(), 6f);
                }

                if(interval.get(timerUpdate, updateTime)){
                    data.updateControl();
                }

                if(interval.get(timerWinCheck, 60 * 2)){
                    Seq<Player> players = data.getLeaderboard();
                    //if(!players.isEmpty() && data.getControlled(players.first()).size >= winCondition && players.size > 1 && data.getControlled(players.get(1)).size <= 5){
                    if(!players.isEmpty() && data.getControlled(players.first()).size >= winCondition && players.size > 1 ){
                        endGame();
                    }
                }

                counter += Time.delta;

                //kick everyone and restart w/ the script
                if(counter > roundTime && !restarting){
                    endGame();
                }
            }else{
                counter = 0;
            }
        });

        Events.on(BlockDestroyEvent.class, event -> {
            //reset last spawn times so this hex becomes vacant for a while.
            if(event.tile.block() instanceof CoreBlock){
                Hex hex = data.getHex(event.tile.pos());

                if(hex != null){
                    //update state
                    hex.spawnTime.reset();
                    hex.updateController();
                    clearTilesInHex(hex);
                    // todo destroy half the units in the block
                }
            }
        });

        Events.on(PlayerLeave.class, event -> {
            if(active() && event.player.team() != Team.derelict){
                // old ver
                killTiles(event.player.team());
                //PlayersWhoLeft.put(event.player.uuid(),event.player.team().id);
            }
        });

        Events.on(PlayerJoin.class, event -> {
//            String playeruuid = event.player.uuid();
//            if(active() && PlayersWhoLeft.containsKey(playeruuid)){
//                int prevTeamid = PlayersWhoLeft.get(playeruuid);
//                Team prevTeam = Team.get(prevTeamid);
//                if (prevTeam==Team.derelict){
//                    PlayersWhoLeft.remove(playeruuid);
//                    return;
//                }
//                event.player.unit().kill();
//                event.player.team(prevTeam);
//                event.player.sendMessage("Welcome back");
//                return;
//            }
            if(!active() || event.player.team() == Team.derelict) return;

            Seq<Hex> copy = data.hexes().copy();
            // filter for the hexes at the edges
            copy.shuffle();
            Hex hex = copy.find(h -> (h.controller == null) && (h.spawnTime.get()) && h_id_is_edge(h.id));

            if(hex != null){
                loadout(event.player, hex.x, hex.y);
                Core.app.post(() -> data.data(event.player).chosen = false);
                hex.findController();
                // this is for local mode Long mmr = MMRsystem.getPlayerMMR(event.player.uuid());
                Long mmr = mmrmongo.read_hexdataV7(event.player,event.player.uuid());
                if (!joinedPlayers.contains(event.player.uuid())){
                    joinedPlayers.add(event.player.uuid());
                    allMMR.add(mmr);
                    PlayersMMR.put(event.player.uuid(),mmr);
                    Call.infoMessage(event.player.con,"Welcome to [red]A[yellow]L[teal]E[blue]X [white]| HEX [green](PRE-ALPHA).[]\n\n[lime]Capture cores by:[]\n- Building on empty tiles\n- Eliminating enemies.\n\n[lime]Objective: []Most Hexes in 40mins or First to 25 Hexes.\n\n[accent]Note: []BuildSpeed X10, Damage X2\n[sky]GL HF [sky]Your current MMR: []"+mmr);
                }
                else{
                    Call.infoMessage(event.player.con,"Welcome back. Your core was erased, and now you have a new one.");
                }
                Long avgMMR = calculateAverageMMR(allMMR);
                Call.sendMessage("[red]A[yellow]L[teal]E[blue]X [white]| HEX [green](PRE-ALPHA)[]: [sky]Average MMR this game is[] "+avgMMR+".");
            }else{
                Call.infoMessage(event.player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                event.player.unit().kill();
                event.player.team(Team.derelict);
            }

            data.data(event.player).lastMessage.reset();
        });

        Events.on(ProgressIncreaseEvent.class, event -> updateText(event.player));

        Events.on(HexCaptureEvent.class, event -> updateText(event.player));
        Events.on(HexMoveEvent.class, event -> updateText(event.player));

        TeamAssigner prev = netServer.assigner;
        netServer.assigner = (player, players) -> {
            Seq<Player> arr = Seq.with(players);

            if(active()){
                //pick first inactive team
                for(Team team : Team.all){
                    if(team.id > 5 && !team.active() && !arr.contains(p -> p.team() == team) && !data.data(team).dying && !data.data(team).chosen){
                        data.data(team).chosen = true;
                        return team;
                    }
                }
                Call.infoMessage(player.con, "There are currently no empty hex spaces available.\nAssigning into spectator mode.");
                return Team.derelict;
            }else{
                return prev.assign(player, players);
            }
        };
    }

    private boolean h_id_is_edge(int id) {
        int[] array = new int[]{ 1,4,10,26,40,54,51,45,29,15};
        for (int a : array) {
            if (a==id) return true;
        }
        return false;
    }

    void updateText(Player player){
        HexTeam team = data.data(player);
        int minsleft=(int)(roundTime - counter) / 60 / 60;
        StringBuilder message = new StringBuilder("[white]Hex #" + team.location.id + " [teal]( "+minsleft+"mins left )\n");

        if(!team.lastMessage.get()) return;

        if(team.location.controller == null){
            if(team.progressPercent > 0){
                message.append("[lightgray]Capture progress: [accent]").append((int)(team.progressPercent)).append("%");
            }else{
                message.append("[lightgray][[Empty]");
            }
        }else if(team.location.controller == player.team()){
            message.append("[yellow][[Captured]");
        }else if(team.location != null && team.location.controller != null && data.getPlayer(team.location.controller) != null){
            message.append("[#").append(team.location.controller.color).append("]Captured by ").append(data.getPlayer(team.location.controller).name);
        }else{
            message.append("<Unknown>");
        }

        Call.setHudText(player.con, message.toString());
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("hexed", "Begin hosting with the Hexed gamemode.", args -> {
            if(!state.is(State.menu)){
                Log.err("Stop the server first.");
                return;
            }

            data = new HexData();

            logic.reset();
            Log.info("Generating map...");
            HexedGenerator generator = new HexedGenerator();
            world.loadGenerator(Hex.size, Hex.size, generator);
            data.initHexes(generator.getHex());
            info("Map generated.");
            state.rules = rules.copy();
            logic.play();
            netServer.openServer();
            // set tiles here? (27,5) (28,5)
            Log.info("world tile setting");
            initWorldBlock();
            Log.info("world tile set");
        });

        handler.register("countdown", "Get the hexed restart countdown.", args -> {
            Log.info("Time until round ends: &lc@ minutes", (int)(roundTime - counter) / 60 / 60);
        });

        handler.register("end", "End the game.", args -> endGame());

        handler.register("r", "Restart the server.", args -> System.exit(2));
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        if(registered) return;
        registered = true;

        handler.<Player>register("spectate", "Enter spectator mode. This destroys your base.", (args, player) -> {
             if(player.team() == Team.derelict){
                 player.sendMessage("[scarlet]You're already spectating.");
             }else{
                 killTiles(player.team());
                 player.unit().kill();
                 player.team(Team.derelict);
             }
        });

        handler.<Player>register("captured", "Dispay the number of hexes you have captured.", (args, player) -> {
            if(player.team() == Team.derelict){
                player.sendMessage("[scarlet]You're spectating.");
            }else{
                player.sendMessage("[lightgray]You've captured[accent] " + data.getControlled(player).size + "[] hexes.");
            }
        });

        handler.<Player>register("leaderboard", "Display the leaderboard", (args, player) -> {
            player.sendMessage(getLeaderboard());
        });

        handler.<Player>register("hexstatus", "Get hex status at your position.", (args, player) -> {
            Hex hex = data.data(player).location;
            if(hex != null){
                hex.updateController();
                StringBuilder builder = new StringBuilder();
                builder.append("| [lightgray]Hex #").append(hex.id).append("[]\n");
                builder.append("| [lightgray]Owner:[] ").append(hex.controller != null && data.getPlayer(hex.controller) != null ? data.getPlayer(hex.controller).name : "<none>").append("\n");
                for(TeamData data : state.teams.getActive()){
                    if(hex.getProgressPercent(data.team) > 0){
                        builder.append("|> [accent]").append(this.data.getPlayer(data.team).name).append("[lightgray]: ").append((int)hex.getProgressPercent(data.team)).append("% captured\n");
                    }
                }
                player.sendMessage(builder.toString());
            }else{
                player.sendMessage("[scarlet]No hex found.");
            }
        });
    }

    void endGame(){
        if(restarting) return;

        restarting = true;
        Seq<Player> players = data.getLeaderboard();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < players.size && i < 3; i++){
            if(data.getControlled(players.get(i)).size > 1){
                builder.append("[yellow]").append(i + 1).append(".[accent] ").append(players.get(i).name)
                .append("[lightgray] (x").append(data.getControlled(players.get(i)).size).append(")[]\n");
            }
        }
        Date currDate = new Date();
        if(!players.isEmpty()){
            boolean dominated = data.getControlled(players.first()).size == data.hexes().size;

            for(Player player : Groups.player){
                Call.infoMessage(player.con, "[accent]--ROUND OVER--\n\n[lightgray]"
                + (player == players.first() ? "[accent]You[] were" : "[yellow]" + players.first().name + "[lightgray] was") +
                " victorious, with [accent]" + data.getControlled(players.first()).size + "[lightgray] hexes conquered."
                + (dominated ? "" : "\n\nFinal scores:\n" + builder)
                );
            }
            List<WriteModel<Document>> bulkOperations = new ArrayList<>();
            Map<String, Integer> uuid_to_rank = data.rankNames();
            Long avgMMR = calculateAverageMMR(allMMR);
            for( String muuid : joinedPlayers){
                Long oldMMR = PlayersMMR.get(muuid);
                Integer rank = uuid_to_rank.get(muuid);
                if (rank==null) rank = 0;
                Player p = Groups.player.find(pp -> Objects.equals(pp.uuid(), muuid));
                int hexescontrolled = p == null ? 0 : data.getControlled(p).size;
                Long newMMR = getNewMMR(oldMMR,avgMMR,uuid_to_rank,muuid);
                bulkOperations.add(
                    new UpdateOneModel<>(
                        new Document("muuid", muuid), // Filter
                        combine(
                                set("currMMR", newMMR), // Increment field1 by 1
                                push("dates", currDate), // Append "value2" to field2
                                push("losswinrank",rank ),
                                push("hexescontrolled",hexescontrolled),
                                push("MMRs",newMMR)
                        )
                    )
                );
                if (p!=null && p.con!=null){
                    Call.infoMessage(p.con,"[sky]Your previous MMR:[] "+oldMMR+"\n[sky]Your new MMR:[] "+newMMR+"\n[sky]Average MMR in this match:[] "+avgMMR);
                }
            }
            if(!bulkOperations.isEmpty()){
                this.mmrmongo.bulkWritehexdataV7(bulkOperations);
                Log.info("MMR sent to db, update size: "+bulkOperations.size());
            }
        }


        Log.info("&ly--SERVER RESTARTING--");
        Time.runTask(60f * 10f, () -> {
            Log.info("&ly--running kick task--");
            netServer.kickAll(KickReason.serverRestarting);
            Log.info("&ly--finish kick task--");
            Time.runTask(5f, () -> {
                Log.info("&ly--system exit--");
                System.exit(2);
            });
        });
    }

    private Long getNewMMR(Long currMMR, Long avgMMR,Map<String, Integer> uuid_to_rank,String muuid) {
        Long newMMR;
        int rank = uuid_to_rank.get(muuid)==null? 10 : uuid_to_rank.get(muuid);

        int rankpoints = 11 - rank;
        if(rankpoints>=9) { //first two ranks
            if (currMMR > avgMMR) {
                newMMR = currMMR + rankpoints/2;
            } else {
                newMMR = currMMR + rankpoints;
            }
        }else{ // worse than 2 ranks
            if (currMMR > avgMMR) {
                newMMR = currMMR - rankpoints;
            } else {
                newMMR = currMMR - rankpoints/2;
            }
        }
        return max(100L,newMMR); // lowest is 100 MMR...
    }

    public static Long calculateAverageMMR(List<Long> values) {
        if (values == null || values.isEmpty()) {
            //throw new IllegalArgumentException("List must not be null or empty");
            return 1000L;
        }

        Long sum = 0L;
        for (Long value : values) {
            sum += value;
        }

        // Truncates the decimal part
        return sum / values.size();
    } 
    String getLeaderboard(){
        StringBuilder builder = new StringBuilder();
        builder.append("[accent]Leaderboard\n[scarlet]").append(lastMin).append("[lightgray] mins. remaining\n\n");
        int count = 0;
        for(Player player : data.getLeaderboard()){
            builder.append("[yellow]").append(++count).append(".[white] ")
                    .append(player.name).append("[orange] (").append(data.getControlled(player).size).append(" hexes, ").append(PlayersMMR.get(player.uuid())).append(" MMR)\n[white]");

            if(count > 4) break;
        }
        return builder.toString();
    }

    void killTiles(Team team){
        data.data(team).dying = true;
        Time.runTask(8f, () -> data.data(team).dying = false);
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile.build != null && tile.team() == team){
                    Time.run(Mathf.random(60f * 6), tile.build::kill);
                }
            }
        }
    }
    // add kill tiles in hex
    void clearTilesInHex(Hex hex){
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if( tile.build != null && (!(tile.block() instanceof CoreBlock)) && hex.contains(tile)){
                    Time.run(Mathf.random(60f * 6), tile.build::kill);
                }
            }
        }
    }
    void loadout(Player player, int x, int y){
        boolean midgamemode;
        if (lastMin <= roundTime/60/60/2){ // half of roundTime
            start = midgamestart;
            midgamemode=true;
        }else{
            midgamemode = false;
            start=start1;
        }
        Stile coreTile = start.tiles.find(s -> s.block instanceof CoreBlock);
        if(coreTile == null) throw new IllegalArgumentException("Schematic has no core tile. Exiting.");
        int ox = x - coreTile.x, oy = y - coreTile.y;
        start.tiles.each(st -> {
            Tile tile = world.tile(st.x + ox, st.y + oy);
            if(tile == null) return;

            if(tile.block() != Blocks.air){
                tile.removeNet();
            }

            tile.setNet(st.block, player.team(), st.rotation);

            if(st.config != null){
                tile.build.configureAny(st.config);
            }
            if(tile.block() instanceof CoreBlock){
                for(ItemStack stack : state.rules.loadout){
                    if(midgamemode){
                        Call.setItem(tile.build, stack.item, stack.amount*5+500);
                    }else {
                        Call.setItem(tile.build, stack.item, stack.amount);
                    }
                }
            }
        });
    }
    void initWorldBlock(){
        worldblock.tiles.each(st->{
            Tile tile = world.tile(st.x + 27, st.y + 5);
            if(tile == null) return;
            if(tile.block() != Blocks.air){
                tile.removeNet();
            }
            tile.setNet(st.block, Team.blue, st.rotation);
            if(st.config != null){
                tile.build.configureAny(st.config);
            }
        });
    }

    public boolean active(){
        return state.rules.tags.getBool("hexed") && !state.is(State.menu);
    }


}
