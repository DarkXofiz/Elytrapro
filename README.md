# ElytraPro V1

Client-side **Fabric** mod for **Minecraft 1.20.1** (and compatible 1.20.x builds). Java 17.

## Features
**Auto Firework**
- Works ONLY while a firework rocket is in your main hand or off hand. Without a rocket in hand (or with any other item) nothing happens and everything stays vanilla. Rockets are never taken from the hotbar.
- With a rocket in hand, right-click an already **placed** log / planks / cobweb / bedrock block, or right-click anywhere while standing inside a cobweb (`launchInsideCobweb`): you take off with the rocket.
- Already gliding: rocket at once. In the air: wings open + rocket in the same tick. On the ground (`launchFromGround`): you jump, then wings open and the rocket fires.
- Shift never blocks the rocket (cobweb, gliding, trigger blocks), has a cooldown, skips if a rocket is still boosting you. A block in the main hand = vanilla placement (`triggerWithBlockInHand` changes that).

**Auto Elytra (cobweb only)**
- Press Shift while inside a cobweb: Elytra opens at once, ONE time per press. On the ground the mod does one small hop first (`cobwebHop`), because vanilla cannot glide from the ground. No retries, no packet spam, no automatic re-open.
- Key is configurable with `cobwebOpenKey`: SNEAK (default), JUMP or BOTH.
- Everywhere else Shift and Space are completely vanilla.
- The server can still close wings (ground contact, water, anti-cheat). The client cannot override that.

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
`.minecraft/config/elytrapro.json` (created on first launch; old configs are reset once on update to V1):
`autoFirework`, `autoElytra`, `launchFromGround`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `onlyWhenFalling`, `launchInsideCobweb`, `triggerWithBlockInHand`, `cobwebOpenKey`, `cobwebHop`, `allowWhileSneaking`, `triggerBlockTags`, `triggerBlocks`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
