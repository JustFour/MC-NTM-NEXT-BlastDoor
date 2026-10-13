**English** | [Русский](README.ru.md)

# Blast Door — a mod for Minecraft 26.2 / NeoForge

A 3×3 sliding blast door with a rail running through the opening, like the ones in metro tunnels.
On a redstone signal the leaf slides 3 blocks sideways; when the signal is gone it slides back and
blocks the track.

## How to use it in game

1. Lay your rails and stand on the track **facing along the rails**.
2. Place the Blast Door (Redstone Blocks tab) **in place of a rail** where the door should be.
   The block is itself a straight rail and sits at the bottom centre of the opening; the other
   8 blocks of the 3×3 frame are placed automatically. The 3×3 area across the track must be free.
3. Power **any** of the door's 9 blocks with redstone (a lever on the wall next to it, a button,
   a repeater and so on). Signal on — the door is open; signal off — it is closed.
4. **Hold Shift while placing** to make the leaf slide the other way.

Behaviour:

- The leaf takes 2.5 seconds to travel, with eased start and stop, sounds and animated locking bolts.
- The opening becomes passable only when the leaf has slid away **completely**.
- The door closes only when the doorway is empty: it waits instead of crushing a minecart, player or mob.
- A closed door stops minecarts (collision plus a server-side safeguard), and mobs do not path through it.
- It needs a diamond pickaxe or better, is twice as hard as obsidian, has a blast resistance of 450,
  and cannot be pushed by pistons.
- Breaking any block of the door dismantles the whole structure and drops a single item.

Building tip: the leaf slides sideways into a 3×3 "pocket" next to the opening. If there is a wall
there, the door visually disappears into it, as in a real tunnel. The guide rail in the floor is
drawn under both the opening and the pocket.

## Crafting

With NTM NEXT installed, the door is built in the **Assembly Machine** (200 ticks):

| Amount | Material |
|---|---|
| 16 | steel plate |
| 6 | lead plate |
| 4 | rubber (any kind) |
| 8 | high-speed steel bolt |
| 2 | motor |
| 1 | rail |

Without NTM NEXT a fallback crafting-table recipe applies (it is disabled when NTM is present):

```
I I I    I — block of iron
I P I    P — piston
O R O    O — obsidian, R — rail
```

## NTM NEXT compatibility

With [NTM NEXT](https://github.com/Warfactory-Official/ntm-next) (mod id `hbm`) installed, a closed
blast door blocks radiation, just like NTM's own doors. A fully open door lets radiation through.
Without NTM the mod works as usual: the dependency is optional and is not bundled into the jar.

The door's blast resistance (450) equals that of NTM ducrete bricks and is higher than that of
concrete bricks (96). NTM nuclear explosions use the same number.

## Building and running

You need **JDK 25** (Mojang ships Java 25 with 26.2).

```bash
./gradlew runClient     # start the game with the mod
./gradlew build         # build the jar → build/libs/blastdoor-1.1.0.jar
```

On Windows use `gradlew.bat`. Put the built jar into the `mods` folder of a NeoForge 26.2 client.
Versions are set in `gradle.properties` (`neo_version=26.2.0.90`); newer NeoForge builds are listed at
https://projects.neoforged.net/neoforged/neoforge.

## Code layout

| File | What it does |
|---|---|
| `BlastDoorMod.java` | Registers the blocks, the item and the block entity; creative tab entry |
| `block/BlastDoorBlock.java` | Controller: a straight rail (`BaseRailBlock`) plus the closed door's collision and redstone |
| `block/BlastDoorPartBlock.java` | Invisible parts of the 3×3 frame: collision, redstone input, removal of the whole structure |
| `block/DoorGeometry.java` | Part positions, collision shapes, doorway volume |
| `blockentity/BlastDoorBlockEntity.java` | Logic: animation progress, opening and closing, stopping minecarts, sounds, sync |
| `item/BlastDoorItem.java` | The door item: the "Blast Resistance" tooltip line |
| `compat/NtmCompat.java` | NTM NEXT compatibility: registers the closed door as radiation shielding |
| `client/BlastDoorRenderer.java` | Draws the leaf (sheet, frame, ribs, handwheel, valves, bolts, rollers) and the guide rail |
| `assets/.../textures/entity/blast_door.png` | Leaf texture, 64×64 (front face plus material swatches) |

Things that are easy to tweak:

- speed — `BlastDoorBlockEntity.TRAVEL_TICKS`;
- looks — the `drawLeaf`, `drawHandwheel` and `drawValve` methods in the renderer, and the `blast_door.png` texture;
- sounds — the `playSound(...)` calls in the block entity.
