# ElytraPro

Client-side **Fabric** mod for **Minecraft 1.20.1** (and compatible 1.20.x builds). Java 17.

## Features
**Auto Firework**
- Right-click with a trigger item (all logs, all planks, cobweb by default) fires a firework rocket instantly, whatever is under the crosshair.
- Already gliding: the rocket is used at once.
- In the air: wings are opened and the rocket is used in the same tick.
- On the ground (`launchFromGround`): you jump, then wings open and the rocket fires on the first tick the server accepts it.
- Never while sneaking, has a cooldown, skips if a rocket is still boosting you.

**Auto Elytra**
- Hold/press jump in the air: Elytra opens automatically (no delay by default).
- Works inside cobwebs too (velocity check is skipped there).
- Wings are never closed by the mod. With `keepOpen` they are re-opened automatically if they drop mid-air.
- Note: the server only accepts "start gliding" while you are airborne **and falling** (vanilla rule). The mod therefore waits for the first falling tick instead of sending a packet the server would answer by closing the wings. Landing, water and anti-cheat can still close the wings; the client cannot override that.

## Install
1. Install Fabric Loader 0.15.11+ and Fabric API 0.92.2+1.20.1.
2. Put `elytrapro-<version>.jar` in `.minecraft/mods`.

## Build
```
gradle wrapper --gradle-version 8.8   # once, creates gradle-wrapper.jar
./gradlew build                        # Windows: gradlew.bat build
```
Output: `build/libs/elytrapro-1.1.0.jar`. Requires JDK 17.
GitHub Actions builds on every push and uploads the JAR as artifact `elytrapro-jar`.

## Config
`.minecraft/config/elytrapro.json` (created on first launch; old configs are reset once on update to v1.1.0):
`autoFirework`, `autoElytra`, `keepOpen`, `launchFromGround`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `retryDelayTicks`, `triggerTags`, `triggerItems`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
