# ElytraPro

Client-side **Fabric** mod for **Minecraft 1.20.1** (and compatible 1.20.x builds). Java 17.

## Features
**Auto Firework**
- Right-click while holding a trigger item (all logs, all planks, cobweb by default) fires a firework rocket from your offhand/hotbar instantly.
- Works even if another player/entity is under the crosshair, or you are above/over a player.
- If Elytra is not open yet, it is opened first, then the rocket is used in the same tick.
- Never fires on the ground, never while sneaking, has a cooldown, and skips if a rocket is still boosting you (no wasted rockets).

**Auto Elytra**
- Press/hold jump in the air: Elytra opens automatically.
- The mod never sends a "stop gliding" action; pressing jump again does not close the wings, and it re-opens them if they drop mid-air while jump is held.
- Note: a server can still force-close wings (landing, water, anti-cheat); the client cannot override the server.

**Logging**: no debug output, no chat messages. Only real errors (config read/write) are logged.

## Install
1. Install Fabric Loader 0.15.11+ and Fabric API 0.92.2+1.20.1.
2. Put `elytrapro-<version>.jar` in `.minecraft/mods`.

## Build
```
gradle wrapper --gradle-version 8.8   # once, creates gradle-wrapper.jar
./gradlew build                        # Windows: gradlew.bat build
```
Output: `build/libs/elytrapro-1.0.0.jar`. Requires JDK 17.
GitHub Actions builds on every push and uploads the JAR as artifact `elytrapro-jar`.

## Config
`.minecraft/config/elytrapro.json` (created on first launch):
`autoFirework`, `autoElytra`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `retryDelayTicks`, `triggerTags`, `triggerItems`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
