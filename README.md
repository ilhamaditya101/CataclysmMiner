# CataclysmMiner

Paper 1.20.1 / Java 17.

## Build

```bash
./gradlew clean build
```

Output:

```text
build/libs/CataclysmMiner.jar
```

## Server dependencies

Required:
- Paper 1.20.1
- Java 17
- AuraSkills 2.2.0

Optional:
- MMOItems, when using `mode: MMOITEMS` rewards.

## Behavior

Configured mining nodes:
1. Player breaks the configured block.
2. The exact coordinate gets a GLOBAL cooldown.
3. The block immediately becomes BEDROCK.
4. AuraSkills Mining receives the configured XP.
5. Configured Vanilla/MMOItems rewards are rolled.
6. After the node's cooldown, BEDROCK becomes the configured block again.

Each coordinate has an independent cooldown.

Worlds not listed in `settings.allowed-worlds` are ignored by the plugin.

Use `/cataclysmminer reload` after changing config.yml.
