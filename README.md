# mindustry hex with midgame buffs

## features

- players who join in the midgame gets more items and a better starting core defense.
- simple mmr system to reward better players (requires mongodb atlas account)

## todo

- dont update the mmr for players who joined later than 5 mins into the game.
- collate a history of mmr so that players can see their track record.
- allow players who disconnect to rejoin in their same team (bugged implementation)
- (maybe) allow for team hex. (current code doesnt seem to allow this)

## how to use

- place the jar file into the mods directory.
- to make the game mode loop, run the config: `config startCommands hexed`


## requirements

requires the mongodb key to be present in `config.alex`