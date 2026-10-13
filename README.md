**English** | [Русский](README.ru.md)

# NTM NEXT Blast Doors Addon — blast doors for Minecraft 26.2 / NeoForge

Heavy doors for metro tunnels and bunkers, driven by redstone:

- **Railway Slide Blast Door** — a 3×3 sliding door with a rail running through the opening, like the ones in metro tunnels.
- **Railway Side Blast Door** — a 3×3 swing door on rails: the leaf turns on hinges into a niche beside the track.
- **Station Air-Tight Gates** — a 4×4 slab that lies in the floor as part of it and swings up to seal a
  passage, like the gates at deep metro stations.

All of them have the same stats: they need a diamond pickaxe or better, are twice as hard as obsidian, have a
blast resistance of 450 and cannot be pushed by pistons. Breaking any block of a door dismantles the
whole structure and drops a single item. The items are in the Redstone Blocks tab.

## Railway Slide Blast Door (3×3, on rails)

![Railway Slide Blast Door](docs/screenshots/railway-slide-blast-door.png)

On a redstone signal the leaf slides 3 blocks sideways; when the signal is gone it slides back and
blocks the track.

1. Lay your rails and stand on the track **facing along the rails**.
2. Place the Railway Slide Blast Door **in place of a rail** where the door should be. The block is itself a straight
   rail and sits at the bottom centre of the opening; the other 8 blocks of the 3×3 frame are placed
   automatically. The 3×3 area across the track must be free.
3. Power **any** of the door's 9 blocks with redstone (a lever on the wall next to it, a button,
   a repeater and so on). Signal on — the door is open; signal off — it is closed.
4. **Hold Shift while placing** to make the leaf slide the other way.

Behaviour:

- The leaf takes 6 seconds to travel, with eased start and stop, sounds and animated locking bolts.
- The opening loses its collision when the leaf is about **halfway** open and gets it back at the same point on the way back.
- The door never crushes anyone: while a minecart, player or mob is in the doorway it neither starts to close nor restores the collision — it waits.
- A closed door stops minecarts (collision plus a server-side safeguard), and mobs do not path through it.

Building tip: the leaf slides sideways into a 3×3 "pocket" next to the opening. If there is a wall
there, the door visually disappears into it, as in a real tunnel. The guide rail in the floor is
drawn under both the opening and the pocket.

## Railway Side Blast Door (3×3, on rails)

![Railway Side Blast Door](docs/screenshots/railway-side-blast-door.png)

A swing door: the leaf hangs on vertical hinges at the edge of the opening. Closed, it stands across
the track; open, it stands along the track in a niche beside it, like the tunnel doors of the metro.

1. Next to the track, make a niche for the open leaf: 1 block wide, 3 blocks long and 3 blocks high.
   It starts in line with the door and runs along the track away from you, right beyond the edge of
   the 3×3 opening — by default on your **left**.
2. Stand on the track **facing along the rails** and place the door **in place of a rail**, as with the
   sliding door. The 3×3 area across the track and the niche must be free.
3. Power **any** of the 18 blocks with redstone. Signal on — the leaf swings into the niche; signal
   off — it swings back across the track.
4. **Hold Shift while placing** to put the niche on your right.

Behaviour:

- The leaf takes about 7 seconds to swing, away from you as you placed it.
- Both sides are built from the same oxide-brown sheet steel with riveted frames and belts at the same
  heights, so they read as one door. The front (it faces you when the door is closed, and the track when
  it is open) is shallow and carries the lock drive and a grab rail; the back has deep beams.
- The door is moved by a heavy hydraulic cylinder on the back side. Its mount stands in the corner of
  the niche; the cylinder swings on it and pushes the rod out as the door closes, and lies folded
  between two beams when the door is open.
- Other moving parts: locking bolts on the free edge that pull in before the leaf moves, a drive rod
  and two vertical locking rods that shift with them, and warning lamps on both sides that blink
  while the leaf moves.
- The opening loses its collision at about **halfway**; the leaf becomes solid in the niche only once
  it has swung in completely. Anything standing in the niche at that moment is pushed out towards the track.
- The door never crushes anyone: while somebody is in the opening or in the two blocks in front of it
  on the swing side, it neither leaves the niche nor closes the opening — it waits. Opening does not
  wait, so a minecart parked right in front of the door on that side will be swept through visually.
- A closed door stops minecarts, like the sliding one.
- If there is no room, the game tells you what is needed instead of placing the door.

## Station Air-Tight Gates (4×4)

![Station Air-Tight Gates](docs/screenshots/station-air-tight-gates.png)

A slab one block thick, hinged at floor level. With a redstone signal it lies in the floor and you
walk over it; without a signal it stands upright and seals a 4×4 opening.

1. Dig a pit in the floor: 4 blocks wide, 4 blocks long and 1 block deep. Its first row goes right
   under the opening, the other three continue behind it. The 4×4 opening above the first row must be free.
2. Stand in front of the opening **facing it** and place the gates into the **nearest row** of the pit.
   The structure takes 1 block to the right of the block you placed and 2 blocks to the left.
3. Power **any** of the 32 blocks with redstone. Signal on — the slab lies down and becomes the floor;
   signal off — it rises and closes the opening.

Behaviour:

- The slab takes about 10 seconds to swing.
- Lying down, it is level with the surrounding floor. Its top is a grey deck in a tread-plate frame;
  the underside, which faces away from you when the gates are closed, is an oxide-red riveted frame
  with braces and chamfered top corners, as on the real gates.
- Moving parts besides the slab itself: two hydraulic cylinders that swing on their mounts and extend
  as the slab rises; a service platform whose deck stays level while its railing folds flat against
  the slab; warning lamps in the pit that blink while the slab moves.
- The opening becomes passable when the slab is about **halfway** down. The slab turns into a solid floor only once it has lain down completely; until then there is a pit under it.
- It never crushes anyone: while somebody is standing on it or in the opening it neither starts to rise nor closes the opening again — it waits.
- While the gates are closed, a pit one block deep is left where the slab was. Whatever is in the pit
  when the slab comes back down ends up on top of it, not inside.
- If there is no room, the game tells you what is needed instead of placing the gates.

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

## Building and running

You need **JDK 25** (Mojang ships Java 25 with 26.2).

```bash
./gradlew runClient     # start the game with the mod
./gradlew build         # build the jar → build/libs/blastdoor-1.7.3.jar
```

On Windows use `gradlew.bat`. Put the built jar into the `mods` folder of a NeoForge 26.2 client.
Versions are set in `gradle.properties` (`neo_version=26.2.0.90`); newer NeoForge builds are listed at
https://projects.neoforged.net/neoforged/neoforge.

## Code layout

| File | What it does |
|---|---|
| `BlastDoorMod.java` | Registers blocks, items and block entities; creative tab entries |
| `blockentity/AbstractDoorBlockEntity.java` | Logic shared by all doors: animation progress, opening and closing on redstone, sounds, sync |
| `item/BlastDoorItem.java` | Door item: the "Blast Resistance" tooltip line |
| `compat/NtmCompat.java` | NTM NEXT compatibility: registers closed doors as radiation shielding |
| `client/BoxMesh.java` | Builds textured boxes; all leaves are drawn from them |
| `client/DoorRumbleSound.java` | The low rumble that plays on the client while a leaf moves |
| `block/AbstractRailDoorBlock.java` | Shared by the two rail doors: a straight rail (`BaseRailBlock`) as controller, placement, collision, redstone |
| `blockentity/AbstractRailDoorBlockEntity.java` | Shared by the two rail doors: stopping minecarts, keeping the rail shape aligned |
| **Railway Slide Blast Door** | |
| `block/BlastDoorBlock.java` | Controller: the rail plus the list of the 8 parts around it |
| `block/BlastDoorPartBlock.java` | Invisible parts of the 3×3 frame: collision, redstone input, removal of the whole structure |
| `block/DoorGeometry.java` | Part positions, collision shapes, doorway volume |
| `blockentity/BlastDoorBlockEntity.java` | The 3×3 structure and the "doorway is clear" check |
| `client/BlastDoorRenderer.java` | Draws the leaf (sheet, frame, ribs, handwheel, valves, bolts, rollers) and the guide rail |
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
