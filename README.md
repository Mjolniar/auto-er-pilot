# Auto-er Pilot

> [!WARNING]
> AI-generated. AI coding agents wrote the mod, the docs and the build checks.

When your fleet flies itself somewhere (a laid-in course, or a click on a planet, star, jump point or gate), it steers more like the NPC fleets do. Stations and fleets are left to the game, so fights stay manual.

- Storms: It slows down in and just ahead of storm cells, since slow fleets don't get struck. If a detour adds 15 percent or less, it goes around.
- Slipstreams: It rides a slipstream going your way if that saves at least 10 percent of the trip, and it gets off at the best point. Then it fixes the sideways drift.
- Reverse Polarity: If you have it, it turns it on for a wrong-way slipstream when that's faster, and off after. Inside a stream that costs fuel and CR, so it's skipped when a ship's CR is too low.
- Without Reverse Polarity: It stays out of wrong-way streams, or it crosses them at a gap or the weakest spot.
- Sustained Burn: It turns it on for long trips and while riding a stream, unless a stronger hostile fleet is close. If you turn it off, it stays off until your next target. If you have SEEKER's Enhanced Sustained Burn, it uses that one, and it doesn't slow down for asteroids while it's on, since it stops the impacts. With RAT's Abyssal Burn, it uses that one in the Abyssal Depths and the others everywhere else, and it turns Abyssal Burn off when it has to slow down for a storm or asteroids, since Abyssal Burn cancels the slow-down.
- Hazards: It goes around coronas, black holes and stars. If a star or black hole is the target, it stops outside the corona and ends the course there.
- Coronas and pulsar beams: If a corona, a black hole or a pulsar beam holds the fleet back for 4 seconds, it turns Sustained Burn off for full acceleration, and then it uses Emergency Burn if every ship has the CR for it. Close to the target, Sustained Burn goes off right away. If a pulsar beam still holds the fleet, it uses Transverse Jump to get out into hyperspace (same cost as doing it yourself), and if the target was in that system, the course is cleared. With SEEKER's Enhanced Emergency Burn, that's the one it uses.
- Hostile fleets: It goes around hostile fleets that are stronger than you and can see you, and it flies past weaker ones.
- Asteroids: It slows down in asteroid belts and fields so you don't take hits. There's no detour, since you usually can't avoid them anyway.
- Jump points: It jumps without the dialog. The dialog still shows for hostiles at the exit, transponder questions, low fuel, wormholes and unstable jump points. Fuel costs the same.
- Transverse Jump: After you use Transverse Jump yourself, your course stays laid in, but the fleet waits where it comes out until you resume it.
- Gates: Once you have the Janus Device and At the Gates done, a course goes through two gates when that's at least 10 percent and one light year shorter. It jumps through without the dialog at the normal fuel cost and sets your course again on the other side. Lay in the same course again to fly direct. Gates in a system with a pulsar are skipped, so you don't come out in the beam (you can turn that off).
- Gate beacons: A Random Assortment of Things settlement with a Gate Beacon can be the exit of a gate route.
- Bifrost: With AoTD Vaults of Knowledge and a Bifrost Control Center, gate routes use Bifrost gates too (no fuel, same 30-day cooldown).
- Wormholes: With the Wormhole Scanner, gate routes also use your wormhole termini when that's shorter. It goes through without the dialog at the game's wormhole fuel cost, and it skips a terminus that's still unstable.
- Taking control back: Holding a mouse button or the go-slow key gives you control back, and so do dialogs, battles and jumps.
- Settings: Everything has its own switch in LunaLib (F3), or in `data/config/better_autopilot.json` without LunaLib. Nothing shows on screen, and the Debug log setting writes the details to `starsector.log`.
- Compatibility: It works with Autopilot With Gates and leaves gate routes to it while its ability is on.

## Install

1. Download the zip from the [latest release](https://github.com/Mjolniar/auto-er-pilot/releases/latest).
2. Extract it into `Starsector/mods/`. To update, extract the new zip over the old folder.
3. Enable it in the launcher. LunaLib is optional.

It needs Starsector 0.98.5a, and you can add it to or remove it from a save at any time. The patch notes are in the [changelog](CHANGELOG.md), and the forum thread is [topic 36441](https://fractalsoftworks.com/forum/index.php?topic=36441.0). Report bugs in the [issues](https://github.com/Mjolniar/auto-er-pilot/issues) with your `starsector.log` and modlist.

## Permissions

Modpacks, reuploads, forks and changes are all fine without asking or credit. Just don't sell it. See the [license](LICENSE).

## Building

Build it with JDK 17 (`javac --release 17`) against the `starsector-core` jars (`starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`) and, optionally, LunaLib, which only `BALuna` uses. Then pack the classes into `jars/betterautopilot.jar`.
