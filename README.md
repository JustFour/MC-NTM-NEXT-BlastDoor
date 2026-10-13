**English** | [Русский](README.ru.md)

# NTM NEXT Blast Doors Addon — blast doors for Minecraft 26.2 / NeoForge

Heavy doors for metro tunnels and bunkers, driven by redstone:

- **Railway Slide Blast Door** — a 3×3 sliding door with a rail running through the opening, like the ones in metro tunnels.
- **Railway Side Blast Door** — a 3×3 swing door on rails: the leaf turns on hinges into a niche beside the track.
- **Station Air-Tight Gates** — a 4×4 slab that lies in the floor as part of it and swings up to seal a
  passage, like the gates at deep metro stations.

All doors follow the same rules. A redstone signal on any block of a door opens it; no signal closes
it. The opening becomes passable about halfway through the travel, and a door waits rather than crush
whoever is in its way. They need a diamond pickaxe or better, are twice as hard as obsidian, have a
blast resistance of 450 and cannot be pushed by pistons. Breaking any block drops the whole door as
one item. The items are in the Redstone Blocks tab.

## Railway Slide Blast Door (3×3, on rails)

![Railway Slide Blast Door](docs/screenshots/railway-slide-blast-door.png)

The leaf slides 3 blocks sideways in 6 seconds. Closed, it blocks the track and stops minecarts.

1. Stand on the track **facing along the rails** and place the door **in place of a rail**.
   The 3×3 area across the track must be free.
2. **Hold Shift while placing** to make the leaf slide the other way.

The leaf slides into a 3×3 "pocket" beside the opening; if there is a wall there, the door
disappears into it. The locking bolts, the handwheel and the warning lamps are animated.

## Railway Side Blast Door (3×3, on rails)

![Railway Side Blast Door](docs/screenshots/railway-side-blast-door.png)

A swing door: in about 7 seconds the leaf turns on vertical hinges from across the track into a
niche beside it. A hydraulic cylinder on the back pushes it shut. Closed, it stops minecarts.

1. Beside the track make a niche for the open leaf: 1 block wide, 3 long, 3 high. It starts in line
   with the door and runs along the track away from you — on your **left**, or on your right if you
   **hold Shift** while placing.
2. Stand on the track **facing along the rails** and place the door **in place of a rail**. The 3×3
   area across the track and the niche must be free; if they are not, the game tells you.

The door waits to close while anyone is in the opening or within two blocks in front of it on the
niche side. Opening does not wait, so the leaf passes visually through a minecart parked at the door.

## Station Air-Tight Gates (4×4)

![Station Air-Tight Gates](docs/screenshots/station-air-tight-gates.png)

A slab hinged at floor level. With a signal it lies in the floor and you walk over it; without one
it rises in about 10 seconds and seals a 4×4 opening.

1. Dig a pit 4 blocks wide, 4 long and 1 deep: the first row right under the opening, the rest behind it.
2. Stand in front of the opening **facing it** and place the gates into the **nearest row** of the pit.
   They take 1 block to the right of that block and 2 to the left; if there is no room, the game tells you.

The slab becomes a solid floor only once it has fully lain down; until then, and while the gates are
closed, there is a pit in its place. Whatever is in the pit when the slab comes down ends up on top
of it. The pit holds a pump station, a control cabinet and hydraulic piping; the hydraulic cylinders,
a folding service platform and the warning lamps are animated.

## Crafting

With NTM NEXT installed, the doors are built in the **Assembly Machine** (200 ticks each):

| Material | Railway Slide Blast Door | Railway Side Blast Door | Station Air-Tight Gates |
|---|---|---|---|
| steel plate | 16 | 18 | 24 |
| lead plate | 6 | 6 | 8 |
| rubber (any kind) | 4 | 4 | 4 |
| high-speed steel bolt | 8 | 8 | 12 |
| motor | 2 | 1 | 2 |
| rail | 1 | 1 | — |

Without NTM NEXT fallback crafting-table recipes apply (they are disabled when NTM is present):

```
Railway Slide Blast Door      Railway Side Blast Door      Station Air-Tight Gates

I I I                         I I I                        I I I        I — block of iron
I P I                         P I O                        I I I        P — piston
O R O                         I R I                        P O P        O — obsidian, R — rail
```

## NTM NEXT compatibility

With [NTM NEXT](https://github.com/Warfactory-Official/ntm-next) (mod id `hbm`) installed, a closed
door or gate blocks radiation, just like NTM's own doors, and lets it through from the same halfway point at which it stops blocking movement. Without NTM
the mod works as usual: the dependency is optional and is not bundled into the jar.

The blast resistance (450) equals that of NTM ducrete bricks and is higher than that of concrete
bricks (96). NTM nuclear explosions use the same number.

## Traincraft variant (the `-TC` jar)

There are two builds of the mod. In the regular one the rail doors sit on a vanilla rail;
[Traincraft](https://github.com/matisiekpl/traincraft) trains can ride it, but the doorway shows a
vanilla rail and a train that rams a closed door is wrecked.

The **`-TC` build** is made for Traincraft track and needs Traincraft 0.0.5 or newer:

- The rail block of both rail doors is real Traincraft track, drawn by Traincraft itself.
  Place the door into a one-block gap between two pieces of Traincraft track.
- Trains heading for a closed door brake within 16 blocks of it, like at a red signal, and stop one
  block short of the leaf instead of crashing.
- Vanilla minecarts cannot ride these doors. The station gates are the same in both builds.

Use one build per world: do not switch builds on a world that already has rail doors.

## Building and running

You need **JDK 25** (Mojang ships Java 25 with 26.2).

```bash
./gradlew runClient     # start the game with the mod
./gradlew build         # build the regular jar → build/libs/blastdoor-1.8.1.jar
./gradlew build -Ptc    # build the Traincraft variant → build/libs/blastdoor-1.8.1-TC.jar
```

On Windows use `gradlew.bat`. Put the built jar into the `mods` folder of a NeoForge 26.2 client.
Versions are set in `gradle.properties` (`neo_version=26.2.0.90`); newer NeoForge builds are listed at
https://projects.neoforged.net/neoforged/neoforge.

The Traincraft variant is compiled against Traincraft itself, which is not bundled and not in any
public repository: put its jar at `libs/tc.jar` first (the path is `tc_jar` in `gradle.properties`).

## Code layout

| File | What it does |
|---|---|
| `BlastDoorMod.java` | Registers blocks, items and block entities; creative tab entries |
| `blockentity/AbstractDoorBlockEntity.java` | Logic shared by all doors: animation progress, opening and closing on redstone, sounds, sync |
| `item/BlastDoorItem.java` | Door item: the "Blast Resistance" tooltip line |
| `compat/NtmCompat.java` | NTM NEXT compatibility: registers closed doors as radiation shielding |
| `client/BoxMesh.java` | Builds textured boxes; all leaves are drawn from them |
| `client/DoorRumbleSound.java` | The low rumble that plays on the client while a leaf moves |
| `block/AbstractRailDoorBlock.java` | Shared by the two rail doors: the controller block, placement, collision, redstone. Two versions: a vanilla rail in `src/vanilla`, Traincraft track in `src/tc` |
| `blockentity/AbstractRailDoorBlockEntity.java` | Shared by the two rail doors: stopping minecarts (`src/vanilla`) or braking Traincraft trains (`src/tc`) |
| `blockentity/DoorBlockEntityBase.java` | Base of every door's block entity; in `src/tc` it is Traincraft's track block entity |
| `client/RailTrackVisual.java` | Draws the rail under a door: nothing in `src/vanilla`, Traincraft's track in `src/tc` |
| **Railway Slide Blast Door** | |
| `block/BlastDoorBlock.java` | Controller: the rail plus the list of the 8 parts around it |
| `block/BlastDoorPartBlock.java` | Invisible parts of the 3×3 frame: collision, redstone input, removal of the whole structure |
| `block/DoorGeometry.java` | Part positions, collision shapes, doorway volume |
| `blockentity/BlastDoorBlockEntity.java` | The 3×3 structure and the "doorway is clear" check |
| `client/BlastDoorRenderer.java` | Draws the leaf (sheet, riveted frame and ribs, round handwheel, warning lamps, bolts, rollers) and the guide rail |
| **Railway Side Blast Door** | |
| `block/SideDoorBlock.java` | Controller: the rail plus the list of opening and niche parts |
| `block/SideDoorPartBlock.java` | Invisible opening and niche parts: collision, redstone input, removal of the whole structure |
| `block/SideDoorGeometry.java` | Niche positions, the niche volume, the zone the leaf swings through |
| `blockentity/SideDoorBlockEntity.java` | Waiting for a clear swing zone, pushing entities out of the niche |
| `client/SideDoorRenderer.java` | Draws the leaf (plate, frames, hinges, lock drive, back beams, bolts, lamps) and the hydraulic cylinder that moves it |
| **Station Air-Tight Gates** | |
| `block/StationGateBlock.java` | Controller: a floor block in the nearest row of the pit |
| `block/StationGatePartBlock.java` | Invisible floor and opening parts: collision, redstone input, removal of the whole structure |
| `block/StationGateGeometry.java` | Part positions, the pit, the volume the slab sweeps |
| `blockentity/StationGateBlockEntity.java` | Waiting for a clear path, lifting entities out of the pit |
| `client/StationGateRenderer.java` | Draws the slab (deck, frame, braces), the hydraulic cylinders, the folding platform and the pit with its warning lamps |

Things that are easy to tweak:

- speed — `TRAVEL_TICKS` in `BlastDoorBlockEntity`, `SideDoorBlockEntity` and `StationGateBlockEntity`;
- looks — the `draw…` methods in the renderers and the textures in `assets/blastdoor/textures/entity/`;
- sounds — the start and stop sounds are the `playSound(...)` calls in `AbstractDoorBlockEntity`; the low rumble while a leaf moves is `client/DoorRumbleSound`.
