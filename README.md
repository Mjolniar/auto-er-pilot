# Auto-er Pilot

> [!WARNING]
> AI-generated. The mod, docs and build checks were written by AI coding agents. Checked offline (compile, bytecode checks, steering math against a copy of the game's fleet movement). Tested in game: storm slow-down and detour, slipstream rides, Reverse Polarity before a slipstream crossing, Sustained Burn on long trips. Not yet tested in game: jumps without the dialog, gate, gate beacon and Bifrost routes, the stop outside stars, crossing a slipstream without Reverse Polarity. Report bugs in the [issues](https://github.com/Mjolniar/auto-er-pilot/issues) with `starsector-core/starsector.log` and the modlist.

- Scope: Works while the fleet flies itself to a planet, star, jump point, gate, gate beacon, star system or a point in space (a course to a place), from a laid-in course or a click. Stations, fleets and other objects are never steered, so fights and close maneuvers stay manual. A click on empty space is manual flying.
- Hand-off: A held mouse button, the go-slow key, Sensor Burst, Interdiction Pulse, a dialog, a battle or a jump gives control back for that moment. The speed-up key and Go Dark do not. The target is never changed. The course is changed only at a hazardous target and for a gate route (below).
- Storms: Moves slowly inside and ahead of storming or signaling hyperspace cells, then resumes burn. The game does not strike slow-moving fleets.
- Storm detour: Goes around a storm on the way to the target when the detour adds at most 15 percent of the leg. Otherwise moves slowly through it.
- Slipstream use: Enters a slipstream within 4000 units when its flow is within 45 degrees of the target and the trip takes at least 10 percent less time. Leaves it at the point that gives the shortest trip. Works for slipsurges.
- Slipstream drift: After leaving a slipstream, accelerates against the sideways speed first, then turns to the target.
- Reverse Polarity: With the ability, turns it on before entering a slipstream that flows away from the target when riding it reversed saves time, and before crossing a slipstream on the way that flows more than 110 degrees away from the target. Turns it off after leaving. Outside a slipstream this costs nothing.
- Reverse Polarity inside a slipstream: When a slipstream carries the fleet more than 110 degrees away from the target, or riding it reversed is faster than leaving, turns Reverse Polarity on there at once. Not at the stream's edge. Costs fuel and CR as a manual use. Skipped when a ship's CR is below the cost. At most 2 switches per stay in a stream. Only turns off Reverse Polarity it turned on itself.
- Slipstreams without Reverse Polarity: Without the ability, or when it is on cooldown or short of fuel, a slipstream on the way that does not flow toward the target is kept out of. Target on this side: the fleet goes along its edge. Target across: it lines up on this side and crosses straight across at a gap or the weakest place within 4000 units, with Sustained Burn off, fighting the push along the stream.
- Sustained Burn: Turned on for trips longer than 2000 units and 11 seconds at the current speed, and back on when the game or another ability turned it off. Not turned on while slowing down, near a slipstream, during the drift correction after one, with a stronger hostile fleet within 8 seconds of reach (also one that does not see you yet), or with an ability active that the game does not allow with it (Go Dark, Emergency Burn). Turned off by you: left off until the next target. Turned off by the game within 3 seconds of being turned on: the next try waits 60 seconds.
- Sustained Burn in slipstreams: Turned on while riding a slipstream with its flow, lined up within 30 degrees and away from the edges, for trips longer than 10 seconds. In a stream it starts without stopping the fleet, and the stream carries the fleet faster. Turned off when leaving the stream.
- Hazards: Steers around star coronas, black hole event horizons and stars, 300 units plus the fleet's radius out from the edge of the corona or event horizon. Skipped when the target is inside one. Pulsar beams are not steered around.
- Hazardous targets: A star, a black hole, or a point inside a corona or event horizon: the fleet stops outside the corona or event horizon (plus the hazard margin) and the course ends there (cleared only when it leads to that target). The way to the stop point goes around other hazards and fleets. A planet, jump point or gate inside a corona is still flown to.
- Hostile fleets: Steers around hostile fleets that you see, that see you and that are stronger than your fleet (effective strength). Keeps 800 units plus both radii. Flies straight past weaker ones, stations and fleets in a battle.
- Asteroids: Moves slowly inside asteroid belts and fields, starting at braking distance before the edge, then resumes burn. Slow-moving fleets take no asteroid impacts. No detour around them.
- Final approach: The last 400 units plus both radii to the target are the game's own.
- Jump points: Jumps into and out of star systems without the jump dialog. The dialog still opens when the game warns of hostile fleets at the exit, when it asks about the transponder, when fuel is short, and for wormholes, unstable jump points and jump points with more than one exit. Fuel is charged as in the dialog.
- Gate routes: A laid-in course goes through two gates of one network when that way is at least 10 percent and one light year shorter. Each jump and the transit count as 1000 units. The fleet flies to the entry gate, transits without the gate dialog at the network's own cost, and the course to the target is laid in again at the exit gate.
- Gates: Used once the game offers "Travel through the Gate": the Janus Device works and At the Gates is finished. Only gates you scanned that are active.
- Gate beacons: A place the gate dialog lists as a destination that is not a gate, such as a Random Assortment of Things settlement with a Gate Beacon. Used as the exit of a gate route, never as the entry. A course to a gate beacon is steered.
- Bifrost (AoTD Vaults of Knowledge): Used with a Bifrost Control Center on a colony. Only Bifrost gates of active sections (as AoTD's own destination list) that are supplied and not cooling down. The way to a Bifrost entry gate is steered. A Bifrost transit costs no fuel and puts both gates on the 30-day cooldown, as a manual one does.
- Gate routes, exceptions: With hostile fleets near the exit gate, too little fuel or a gate that can not be used there is no transit, and the course to the target is laid in again. Laying in the course to the same target again during a gate route flies direct. A route under way continues after a save load.
- Autopilot With Gates: Compatible. While its ability is on, its gate routes are followed point by point and this mod's gate routes are off.
- Settings: In the LunaLib menu when LunaLib is enabled, otherwise `data/config/better_autopilot.json`. Every behavior has its own switch.
- Log: Nothing on screen. With Debug log on, `starsector.log` gets each steering change, the reason when it does not steer, the obstacles found for each target and a status line every 10 seconds.
- Forum: [topic 36441](https://fractalsoftworks.com/forum/index.php?topic=36441.0).
- Patch notes: [changelog](CHANGELOG.md).

## Install

1. Download the zip from the [latest release](https://github.com/Mjolniar/auto-er-pilot/releases/latest).
2. Extract it into `Starsector/mods/`. To update, extract the new zip over `Starsector/mods/Auto-er Pilot`.
3. Enable Auto-er Pilot in the launcher. LunaLib is optional.

- Game version: Starsector 0.98.5a.
- Saves: Can be added to or removed from an existing save. Stored on the player fleet: one memory flag while Reverse Polarity is on by this mod, and the target and gates of a gate route under way.

## Permissions

- License: Modpacks, reuploads, forks, copies and derivative work are allowed without asking or credit. Selling is not allowed. See [LICENSE](LICENSE).

## Building

- Requirements: JDK 17, sources in `src/`.
- Command: `javac --release 17 -cp "<core jars>;<LunaLib jars>" -d build <all .java files under src>`, then `jar cf jars/betterautopilot.jar -C build data`.
- Core jars (`starsector-core`): `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`.
- Optional jars: LunaLib (with LazyLib's Kotlin runtime). Only `BALuna` uses them.
