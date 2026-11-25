# Changelog

## 0.1.28
- Settings: Added a Targets tab in LunaLib with a switch for each kind of target: planets, colonies, stars, stations, abandoned stations, research stations, mining stations, orbital habitats, derelict ships, caches and cargo pods, debris fields, Domain-era derelicts, relays, buoys and arrays, stable locations, warning beacons, everything else, jump points, gates, star systems and map points, hostile fleets and other fleets.
- Settings: Defaults are unchanged. Planets, colonies, stars, jump points, gates and star systems are on. The rest are off.
- Targets: Stations, salvage, fleets and other objects that are switched on are flown to like planets. Only stars, black holes, star systems and map points stop outside a corona.
- Without LunaLib: The same switches are the ba_t keys in data/config/better_autopilot.json.

## 0.1.27
- Compatibility: Updated for Starsector 0.98.5a-RC9.
- Jump points: Jumps without the dialog charge the game's jump pay, as the dialog does.
- Wormholes: The transit applies the game's jump pay rules, as the jump dialog does.

## 0.1.26
- Transverse Jump: After a Transverse Jump the player used, the course stays laid in and the fleet waits until the player resumes it. The pulsar beam escape jump is not held.
- Settings: Added "Wait after a Transverse Jump" (on by default).

## 0.1.25
- Sustained Burn: Uses RAT's Abyssal Burn in the Abyssal Depths. Elsewhere the order is Enhanced Sustained Burn, the game's Sustained Burn, then Abyssal Burn.
- Sustained Burn: Abyssal Burn is turned off while the steering slows down for storms or asteroids. Sustained Burn comes back on after.
- Sustained Burn: No second Sustained Burn kind is turned on while one is on.
- Emergency Burn: Uses SEEKER's Enhanced Emergency Burn when the fleet has it.

## 0.1.24
- Sustained Burn: Fixed the game's Sustained Burn being switched on in place of SEEKER's Enhanced Sustained Burn. The fleet's Enhanced Sustained Burn is used when it has one.
- Sustained Burn: Normal burn out of coronas and slipstream exits turn off whichever Sustained Burn is on.
- Asteroids: No asteroid slow-down while Enhanced Sustained Burn is on. It stops asteroid impacts.

## 0.1.23
- Compatibility: Updated for Starsector 0.98.5a-RC8.

## 0.1.22
- Wormholes: Gate routes also use wormhole termini when the player can use wormholes. Entries are stable termini, exits are their linked termini.
- Wormholes: The transit skips the jump dialog and costs the game's wormhole fuel. The dialog opens when the transponder is off and the exit system has markets.
- Wormholes: Termini in pulsar systems are skipped with "Skip gates in pulsar systems" on.
- Settings: Added "Use wormholes" (on by default).

## 0.1.21
- Gates: Gate routes skip gates, gate beacons and Bifrost gates in star systems with a pulsar beam.
- Gates: A route under way does not transit when its entry or exit gate is in a pulsar system. The course to the target is laid in again.
- Settings: Added "Skip gates in pulsar systems" (on by default).

## 0.1.20
- Coronas and pulsar beams: Added normal burn when a corona, event horizon or pulsar beam holds the fleet for 4 seconds. Sustained Burn is turned off and stays off inside.
- Coronas and pulsar beams: Sustained Burn is turned off at once inside one within 2000 units of the target.
- Coronas and pulsar beams: Added Emergency Burn when the fleet is still held. Only when every ship has the CR it costs.
- Pulsar beams: Transverse Jump comes after normal burn and Emergency Burn.
- Settings: Added "Burn out of coronas and pulsar beams" (on by default).

## 0.1.19
- Pulsar beams: Added Transverse Jump out of a pulsar beam when the fleet flies itself and makes no progress toward the target for 6 seconds. Same fuel and CR cost as a manual jump.
- Pulsar beams: When the target is in the same system, the course is cleared once the fleet is in hyperspace.
- Settings: Added "Transverse Jump out of pulsar beams" (on by default).

## 0.1.18
- Settings: Rewrote the 23 LunaLib setting descriptions in the author's wording. Same facts, numbers and setting names.
- Mod info: Rewrote the launcher description in the author's wording.
- Settings file: Rewrote the 2 comment lines in data/config/better_autopilot.json.
- README: Rewrote the docs in the author's wording. Same facts.

## 0.1.17
- Reverse Polarity: Fixed Reverse Polarity turned on by the mod never being turned off inside a slipstream.
- Reverse Polarity: The limit of 2 paid switches per slipstream also counts switches at the stream's faint edges.
- Storms: No storm slow-down or detour inside a slipstream, also with every slipstream setting off.
- Slipstreams: Fixed a fleet without Reverse Polarity entering a wrong-way slipstream when the target lies past a bend of the stream.
- Slipstreams: "Slipstream least saving" is at least 5 percent.
- Sustained Burn: With a star or black hole as the target, the trip length counts to the stop point outside it.
- Sustained Burn: With "Use Sustained Burn" off, Sustained Burn turned off for a slipstream is not turned back on for the next target.
- Steering: Slipstream crossings, stream entries and detour sides are planned again after the player steers by hand.
- Hazards: The fleet stays at the stop point outside a corona after the course ends there.
- Terrain: A modded terrain that throws errors is skipped. Steering goes on in that location.
- Jump points: Jump points with their own dialog (a market, an objective, a station or the interaction dialog tag) keep it.
- Jump points: The dialog is shown when the game offers no jump in it.
- Jump points: The dialog is kept while the game setting oneClickJumpPoints is on.
- Gates: A dialog at the entry gate that closes without the transit ends the route. The course to the target is laid in.
- Gates: A route taken up from a save without a course lays in the course to its target.
- Gates: Fixed a new course waiting 5 seconds before gate planning after a course was cleared during a transit.
- Bifrost: Gate routes use only gates of active Bifrost sections, as AoTD's destination list does.
- Bifrost: The way to a Bifrost entry gate is steered.
- Memory: The mod no longer holds the last save's sector after quitting to the menu or while another save loads.
- Docs: Removed "crew jump pay". A jump costs fuel only.

## 0.1.16
- Gates: Gate routes can end at a gate beacon, such as a Random Assortment of Things settlement with a Gate Beacon. A beacon is an exit only, as in the gate dialog.
- Targets: A course to a gate beacon is steered, as a course to a gate is.

## 0.1.15
- Forum: Added the forum topic to the README and the Version Checker file.

## 0.1.14
- Name: Renamed from Better Autopilot to Auto-er Pilot. The mod id, settings and saves are unchanged.
- Log: Log lines start with "Auto-er Pilot:".
- Version Checker: Added a version file.

## 0.1.13
- Jump points: Added jumping into and out of star systems without the jump dialog. The dialog still opens when the game warns of hostile fleets at the exit, when it asks about the transponder, when fuel is short, and for wormholes, unstable jump points and jump points with more than one exit.
- Jump points: Fuel and crew jump pay are charged as in the dialog.
- Gates: Added gate routes. A laid-in course goes through two gates when that way is at least 10 percent and one light year shorter. The transit skips the gate dialog and costs the game's fuel.
- Gates: Used once the game offers "Travel through the Gate": the Janus Device works and At the Gates is finished. Only scanned, active gates.
- Bifrost: Added AoTD Bifrost gates to gate routes, with a Bifrost Control Center on a colony. Only gates that are supplied and not cooling down. Same costs and cooldown as a manual Bifrost transit.
- Gates: With hostile fleets near the exit gate, too little fuel or a gate that can not be used, the gate dialog opens and the course to the target is laid in again.
- Gates: A route under way continues after a save load.
- Gates: Laying in the course to the same target again flies direct.
- Gates: Off while the Autopilot With Gates ability is on.
- Settings: Added "Jump without the dialog", "Route through gates", "Use the Bifrost network" and "Gate route least saving (percent)".

## 0.1.12
- Compatibility: Built for Starsector 0.98.5a-RC7.

## 0.1.11
- Errors: Fixed an error outside the route planning ending the game. Any error is caught, logged once and pauses the steering for 5 seconds.
- Pulsar beams: Fixed them being treated as a solid disc as wide as the whole beam. Pulsar beams are not steered around. The neutron star's corona is.
- Hazards: Terrain from other mods with a radius that is not a number or over 20000 units is ignored.
- Hazardous targets: The way to the stop point outside a star or black hole goes around other hazards and stronger fleets. The course is cleared only when it leads to that target.
- Go Dark: The steering keeps working while Go Dark is on. Sensor Burst and Interdiction Pulse hand control back while they run.
- Reverse Polarity: At most 2 paid switches per stay in a slipstream. It is switched back only when the other way does not flow away from the target.
- Reverse Polarity: Turned off after the slipstream also when the mod or its slipstream settings were turned off meanwhile.
- Reverse Polarity: With the player's own Reverse Polarity on and the mod's use of it off, wrong-way streams are judged by the reversed flow.
- Sustained Burn: Not turned on with a stronger hostile fleet within 8 seconds of reach, also when it does not see you yet or fleet steering is off.
- Sustained Burn: Turned off by the game within 3 seconds of being turned on: the next try waits 60 seconds.
- Sustained Burn: Turned off for a slipstream crossing with "Use Sustained Burn" off: turned back on after.
- Sustained Burn: Shift, Ctrl, Alt and Space no longer count as the player turning it off.
- Hostile fleets: Overlapping fleets are not joined into one circle when that circle would hold you or the target.
- Hostile fleets: Small circles (low clearance setting) no longer put the next route point beside or behind the fleet.
- Slipstream drift: The drift correction no longer starts after a jump or after flying out of a stream by hand.
- Settings: "Keep out of wrong-way slipstreams" works with "Use slipstreams" and "Correct drift" both off. A setting that is not a number uses its default. better_autopilot.json is read again on every save load.
- Memory: The mod no longer keeps a slipstream from the previous save after loading another.

## 0.1.10
- Sustained Burn in slipstreams: Turned on while riding a slipstream with its flow, when the fleet is lined up with the flow within 30 degrees, away from the edges, and the trip takes more than 10 seconds. The fleet keeps its speed and rides faster.
- Sustained Burn in slipstreams: Turned off when leaving a slipstream and back on after the drift correction. Not turned on again inside the stream it is leaving.

## 0.1.9
- Sustained Burn: Fixed it never being turned on unless it had been on earlier in the session. It is turned on for trips longer than 2000 units and 11 seconds at the current speed.
- Sustained Burn: Not turned on with a stronger hostile fleet within 8 seconds of reach. Turned off by you: left off until the next target.
- Storms and asteroids: The braking distance with Sustained Burn on uses its braking acceleration.

## 0.1.8
- Slipstreams without Reverse Polarity: Added. A slipstream on the way that does not flow toward the target is kept out of. Target on this side: the fleet goes along its edge. Target across: it lines up and crosses straight across at a gap or the weakest place within 4000 units, with Sustained Burn off. Without the ability, or when it is on cooldown or short of fuel.
- Sustained Burn: Turned off while crossing or leaving such a slipstream, and back on after.
- Reverse Polarity: The mod remembers across saving and loading that it turned Reverse Polarity on, so it still turns it off after the slipstream.

## 0.1.7
- Hazardous targets: Added. A course to a star, a black hole or a point inside a corona or event horizon stops outside it and ends there.
- Targets: Fixed a course to a place (a point in space, "unknown location") being left to the game's autopilot, which flew into the star at that place.

## 0.1.6
- Targets: Steers only on the way to a planet, star, jump point, gate or star system. Stations, fleets and other objects are never steered. Sustained Burn and Reverse Polarity follow the same rule.
- Reverse Polarity inside a slipstream: Turned on at once when the stream flows more than 110 degrees away from the target. Waited for a 10 percent time saving before.
- Reverse Polarity: Also turned on before crossing a slipstream on the way that flows more than 110 degrees away from the target.
- Debug log: Fixed storm slow-downs logged as asteroids in hyperspace.

## 0.1.5
- Reverse Polarity: Added. Turned on before entering a slipstream that flows away from the target when riding it reversed saves time, and off after leaving. Free outside a slipstream.
- Reverse Polarity inside a slipstream: Added. Turned on when a slipstream carries the fleet away from the target and riding it reversed beats leaving. Costs fuel and CR. Skipped when a ship's CR is below the cost.
- Sustained Burn: Turned back on when the game or another ability turned it off and the trip is long enough. Left off when the player turned it off.
- Slipstreams: Reversed riding uses the game's 75 percent speed.
- Storms and asteroids: A slow-down is kept for 1 second after its last trigger. Fixed it flickering on and off after leaving a slipstream.

## 0.1.4
- Asteroids: Slows down inside asteroid belts and fields, starting at braking distance before the edge. On by default. The detour around them is removed.
- Settings: "Avoid asteroid belts and fields" and "Asteroid detour limit" replaced by "Slow down in asteroid belts and fields".

## 0.1.3
- Asteroids: Goes around asteroid belts and fields when the way dips into one and the detour adds at most 25 percent (setting). Otherwise moves slowly through them. On by default.
- Settings: "Slow down in asteroid fields" replaced by "Avoid asteroid belts and fields" and "Asteroid detour limit".
- Routes: Fixed a star inside its own asteroid belt hiding the belt from the route.
- Debug log: Added the obstacles found for each target, with their radius and how close the straight line passes.

## 0.1.2
- Hand-off: Fixed holding Shift (speed up time) stopping the steering. Keys no longer stop it. The game's go-slow key still does, with any key binding.

## 0.1.1
- Steering: Fixed steering not running in game. It stopped after any click and stayed off for the rest of the trip.
- Steering: Also runs when the fleet flies to a clicked planet, station, jump point or fleet. A click on empty space stays manual.
- Hand-off: Mouse control counts only while a button is held, as in the game.
- Debug log: Added the reason when it does not steer and a status line every 10 seconds.

## 0.1.0
- Storms: Added. Moves slowly inside and ahead of storming or signaling cells.
- Storm detour: Added. Goes around a storm when the detour adds at most the set share of the leg (default 15 percent).
- Slipstream use: Added. Enters slipstreams that flow toward the target and save time; leaves at the point that gives the shortest trip.
- Slipstream drift: Added. Cancels the sideways speed after leaving a slipstream.
- Hazards: Added. Steers around star coronas, black hole event horizons, pulsar beams and stars.
- Hostile fleets: Added. Steers around visible hostile fleets that see the player and are stronger (strength margin setting, default 1.0).
- Asteroids: Added, off by default. Moves slowly in asteroid belts and fields.
- Settings: LunaLib menu, or `data/config/better_autopilot.json` without LunaLib.
- Log: Debug log setting writes steering changes to `starsector.log`.
