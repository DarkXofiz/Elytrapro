# ElytraPro

Client-side **Fabric** mod for **Minecraft 1.20.1** (and compatible 1.20.x builds). Java 17.

## Features
**Auto Firework**
- Works ONLY while a firework rocket is in your main hand or off hand. Without a rocket in hand nothing happens and everything stays vanilla. Rockets are never taken from the hotbar.
- With a rocket in hand, right-click an already **placed** log / planks / cobweb / bedrock block, right-click anywhere while standing inside a cobweb (`launchInsideCobweb`), or **right-click another player**: you take off with the rocket. Always on, no key needed.
- Already gliding: rocket at once. In the air: wings open + rocket in the same tick. On the ground (`launchFromGround`): you jump, then wings open and the rocket fires.
- Never while sneaking, has a cooldown, skips if a rocket is still boosting you. A block in the main hand = vanilla placement (`triggerWithBlockInHand` changes that).

**Auto Elytra (cobweb only)**
- Press jump while standing inside a cobweb: Elytra opens ONE time per press (waits inside the same press until you are airborne). No retries, no packet spam, no automatic re-open.
- Everywhere else space is completely vanilla.
- The server can still close wings (ground contact, water, anti-cheat). The client cannot override that.

**Fly Hit (new)**
- When a player in open air has 4 hearts or less (`flyHitHealth`, 8 = 4 hearts, 4 = 2 hearts), you take off with the elytra, fly at them (rockets from offhand or hotbar for take-off and speed), switch to the best sword in the hotbar and hit as soon as they are in reach.
- Needs: elytra worn, a sword in the hotbar. Only the selected hotbar slot changes, and it is restored when the hunt ends.
- Toggle in game with **H** (rebind in Controls, category ElytraPro). `flyHit` in the config turns the feature off completely.
- Targets need a free line of sight (open area), skip teammates, creative/spectator players and names in `flyHitIgnoreNames`.

## Install
1. Install Fabric Loader 0.15.11+ and Fabric API 0.92.2+1.20.1.
2. Put `elytrapro-<version>.jar` in `.minecraft/mods`.

## Build
```
gradle wrapper --gradle-version 8.8   # once, creates gradle-wrapper.jar
./gradlew build                        # Windows: gradlew.bat build
```
Output: `build/libs/elytrapro-1.11.0.jar`. Requires JDK 17.
GitHub Actions builds on every push and uploads the JAR as artifact `elytrapro-jar`.

## Config
`.minecraft/config/elytrapro.json` (created on first launch; old configs are reset once on update to v1.1.0):
`flyHit`, `flyHitHealth`, `flyHitRange`, `flyHitReach`, `flyHitMinCooldown`, `flyHitTurnSpeed`, `flyHitUseRockets`, `flyHitRocketCooldown`, `flyHitMinSpeed`, `flyHitRocketMinDistance`, `flyHitNeedLineOfSight`, `flyHitIgnoreNames`,
`autoFirework`, `autoElytra`, `launchFromGround`, `cooldownTicks`, `skipIfBoosting`, `minAirTicks`, `onlyWhenFalling`, `launchInsideCobweb`, `triggerWithBlockInHand`, `triggerBlockTags`, `triggerBlocks`.

## Server rules
Automation mods may be forbidden on some servers. Check the rules before use.

## License
MIT
