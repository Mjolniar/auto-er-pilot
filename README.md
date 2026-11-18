# Auto-er Pilot

> [!WARNING]
> AI-generated. The mod, docs and build checks were written by AI coding agents. If you hit a bug, open an [issue](https://github.com/Mjolniar/auto-er-pilot/issues) with `starsector-core/starsector.log` and your modlist.

- Scope: It works while your fleet is flying itself to a planet, star, jump point, gate, gate beacon, star system or a point in space (a course to a place), from a laid-in course or a click. It never steers for stations, fleets or other objects, so fights and close maneuvers stay manual. Clicking on empty space is you flying by hand.
- Taking control back: Holding a mouse button, the go-slow key, Sensor Burst, Interdiction Pulse, a dialog, a battle or a jump gives you control back for that moment. The speed-up key and Go Dark don't. It only changes your course at a hazardous target and for a gate route (below).
- Storms: It slows down inside and just ahead of storming or signaling hyperspace cells, then burns back up after. The game doesn't strike fleets that are moving slowly.
- Storm detour: If going around a storm adds 15 percent of the leg or less, it goes around. Otherwise it just slows down and goes through.
- Slipstream use: It hops on a slipstream within 4000 units when the flow is within 45 degrees of the target and the trip takes at least 10 percent less time, and gets off at the point that makes the trip shortest. Slipsurges work too.
- Slipstream drift: After leaving a slipstream, it fights the sideways speed first and then turns to the target.
- Reverse Polarity: If you have the ability, it turns it on before entering a slipstream that flows away from the target (when riding it reversed saves time), and before crossing a slipstream on the way that flows more than 110 degrees away from the target. It turns it off after leaving. Outside a slipstream that's free.
- Reverse Polarity inside a slipstream: When a stream is carrying you more than 110 degrees away from the target, or riding it reversed beats leaving, it turns Reverse Polarity on right there (not at the stream's edge). That costs fuel and CR like doing it by hand, so it's skipped when a ship's CR is below the cost. It does at most 2 switches per stay in a stream, and it only turns off Reverse Polarity that it turned on itself.
- Slipstreams without Reverse Polarity: If you don't have it, or it's on cooldown or you're short on fuel, it stays out of a slipstream on the way that doesn't flow toward the target. If the target's on this side, it goes along the edge. If it's across, it lines up on this side and crosses straight over at a gap or the weakest spot within 4000 units, with Sustained Burn off, fighting the push along the stream.
- Sustained Burn: It turns it on for trips longer than 2000 units and 11 seconds at your current speed, and back on if the game or another ability turned it off. It won't turn it on while slowing down, near a slipstream, during the drift correction after one, with a stronger hostile fleet within 8 seconds of reach (even one that hasn't seen you yet), or with an ability on that the game doesn't allow with it (Go Dark, Emergency Burn). If you turn it off yourself, it stays off until your next target. If the game turns it off within 3 seconds of it going on, the next try waits 60 seconds.
- Sustained Burn in slipstreams: It also turns it on while riding a slipstream with the flow, lined up within 30 degrees and away from the edges, for trips longer than 10 seconds. In a stream it starts without stopping you, and the stream carries you faster. It goes off when you leave the stream.
- Hazards: It steers around star coronas, black hole event horizons and stars, 300 units plus your fleet's radius out from the edge of the corona or event horizon. If the target is inside one, that one's skipped. Pulsar beams aren't steered around.
- Hazardous targets: If your target is a star, a black hole, or a point inside a corona or event horizon, the fleet stops outside it (plus the hazard margin) and the course ends there (it's only cleared when it leads to that target). The way to the stop point still goes around other hazards and fleets. A planet, jump point or gate inside a corona still gets flown to.
- Hostile fleets: It steers around hostile fleets that you can see, that can see you and that are stronger than you (effective strength), keeping 800 units plus both radii. It flies straight past weaker ones, stations and fleets that are in a battle.
- Asteroids: It slows down inside asteroid belts and fields, starting at braking distance before the edge, then burns back up after. Slow-moving fleets don't take asteroid impacts. There's no detour, since most of the time you can't really avoid them anyway.
- Pulsar beams: If a pulsar beam is blowing the fleet off course so it can't get any closer to the target for 6 seconds, it uses Transverse Jump to get out into hyperspace (same fuel and CR cost as doing it by hand). If your target is in that same system, the course gets cleared once you're out, so it doesn't fly you straight back into the beam. It only does this while the fleet is flying itself and Transverse Jump is usable.
- Final approach: The last 400 units plus both radii to the target are left to the game.
- Jump points: It jumps into and out of star systems without the jump dialog. You'll still get the dialog when the game would warn you about hostile fleets at the exit, ask about your transponder, or you're short on fuel, and for wormholes, unstable jump points and jump points with more than one exit. Fuel is the same as a normal jump.
- Gate routes: A laid-in course goes through two gates of one network when that way is at least 10 percent and one light year shorter (each jump and the transit count as 1000 units). The fleet flies to the entry gate, goes through without the gate dialog at the network's own cost, and lays your course back in on the other side.
- Gates: These get used once the game offers "Travel through the Gate", so the Janus Device has to be working and At the Gates done. Only gates you've scanned that are active.
- Gate beacons: These are places the gate dialog lists as a destination that aren't gates, like a Random Assortment of Things settlement with a Gate Beacon. They're used as the exit of a gate route, never the entry, and a course to one gets steered.
- Bifrost (AoTD Vaults of Knowledge): With a Bifrost Control Center on a colony, gate routes also use Bifrost gates of active sections (the same ones AoTD's own destination list shows) that are supplied and not cooling down. The way to a Bifrost entry gate gets steered. A Bifrost transit costs no fuel and puts both gates on the 30-day cooldown, same as doing it by hand.
- Gate routes, exceptions: With hostile fleets near the exit gate, not enough fuel or a gate that can't be used, it skips the transit and lays your course to the target back in. If you lay in the same course again during a gate route, it flies direct. A route that's under way carries on after a save load.
- Autopilot With Gates: Works alongside it. While its ability is on, its gate routes are followed point by point and this mod's gate routes stay off.
- Settings: They're in the LunaLib menu (F3) if you have LunaLib, otherwise in `data/config/better_autopilot.json`. Every behavior has its own switch.
- Log: Nothing shows up on screen. With Debug log on, `starsector.log` gets each steering change, why it isn't steering, the obstacles it found for each target and a status line every 10 seconds.
- Forum: [topic 36441](https://fractalsoftworks.com/forum/index.php?topic=36441.0).
- Patch notes: [changelog](CHANGELOG.md).

## Install

1. Download the zip from the [latest release](https://github.com/Mjolniar/auto-er-pilot/releases/latest).
2. Extract it into `Starsector/mods/`. To update, extract the new zip over `Starsector/mods/Auto-er Pilot`.
3. Enable Auto-er Pilot in the launcher. LunaLib is optional.

- Game version: Starsector 0.98.5a.
- Saves: You can add it to or remove it from an existing save. The only things it stores are on the player fleet: one memory flag while Reverse Polarity is on because of this mod, and the target and gates of a gate route that's under way.

## Permissions

- License: Modpacks, reuploads, forks, copies and derivative work are all fine without asking or credit. Just don't sell it. See [LICENSE](LICENSE).

## Building

If you want to build it yourself:

- Requirements: JDK 17, with the sources in `src/`.
- Command: `javac --release 17 -cp "<core jars>;<LunaLib jars>" -d build <all .java files under src>`, then `jar cf jars/betterautopilot.jar -C build data`.
- Core jars (`starsector-core`): `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`.
- Optional jars: LunaLib (with LazyLib's Kotlin runtime). Only `BALuna` uses them.
