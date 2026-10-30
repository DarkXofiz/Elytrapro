# ElytraPro

Client-side **Fabric** mod for **Minecraft 1.20.1** (and compatible 1.20.x builds). Java 17.

## Features
**Auto Firework (placed blocks only)**
- Right-click an already **placed** log / planks / cobweb block: a firework rocket (offhand or hotbar) is fired and you take off.
- Already gliding: rocket at once. In the air: wings open + rocket in the same tick. On the ground (`launchFromGround`): you jump, then wings open and the rocket fires.
- Items do nothing special: you can place blocks normally. A block in hand = vanilla placement (`triggerWithBlockInHand` changes that). A rocket in hand and every other block behave like vanilla.
- Never while sneaking, has a cooldown, skips if a rocket is still boosting you.

**Auto Elytra**
- Hold/press jump in the air: Elytra opens (no delay, no waiting for falling).
- Inside a cobweb: wings open **once per space press**. No retry loop, no auto re-open (that loop caused the open/close flicker).
- Outside webs `keepOpen` re-opens the wings if they drop mid-air.
- The server can still close wings (ground contact, water, anti-cheat). The client cannot override that.

## Install
1. Install Fabric Loader 0.15.11+ and Fabric API 0.92.2+1.20.1.
2. Put `elytrapro-<version>.jar` in `.minecraft/mods`.

## Build
```
gradle wrapper --gradle-version 8.8   # once, creates gradle-wrapper.jar
./gradlew build                        # Windows: gradlew.bat build
```
Output: `build/libs/elytrapro-1.2.0.jar`. Requires JDK 17.
GitHub Actions builds on every push and uploads the JAR as artifact `elytrapro-jar`.

## Config
`.minecraft/config/elytrapro.json` (created on first launch; old configs are reset once on update to v1.1.0):
`autoFirework`, `autoElytra`, `keepOpen`, `launchFromGround`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `retryDelayTicks`, `onlyWhenFalling`, `triggerWithBlockInHand`, `triggerBlockTags`, `triggerBlocks`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
