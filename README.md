<div align='center'>

# PigMod

### Turn ordinary pigs into powerful, trainable companions.

A Minecraft mod about taming, upgrading and mutating pigs. Build a bond with your pig, develop its abilities, and take it from a simple companion to a powerful ally.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62A34A?style=flat-square)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/Mod%20Loader-NeoForge-orange?style=flat-square)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)](https://www.oracle.com/java/)

[Features](#features) · [Installation](#installation) · [Building from source](#building-from-source)

</div>

---

## Features

- **Tameable companion pigs** — tame pigs with a special Pig Treat and bring them along on your adventures.
- **Progression and stats** — pigs gain experience and levels, earn skill points, and have trackable health, hunger, damage, armor and speed.
- **31 unique mutations** — discover mutations that grant combat powers, defensive bonuses, mobility, utility skills and special abilities.
- **Genetic engineering** — use DNA tubes, a Genetic Injector and **four tiers of Genetic Extractors** to research and apply mutations.
- **Pig Control Tablet** — manage your pigs from one interface: inspect their stats, issue commands, configure work areas, bind a storage chest and manage mob whitelists.
- **Useful companion abilities** — depending on their mutations, pigs can help with tree chopping, farming, mining, fishing and item delivery.
- **Combat abilities** — train pigs to defend you with ranged attacks, powerful projectiles and other mutation-specific powers.
- **JEI integration** — mutation recipes can be viewed in Just Enough Items (JEI). JEI is optional.

## Requirements

| Component | Version |
|---|---|
| Minecraft | **1.21.1** |
| Mod loader | **NeoForge** |
| Java | **21** |
| JEI | Optional — for in-game recipe viewing |

This project is built against NeoForge **21.1.234** for Minecraft 1.21.1.

## Installation

1. Install **Minecraft 1.21.1** with **NeoForge**.
2. Download the PigMod `.jar` from the page where you found the mod (Modrinth, CurseForge or GitHub Releases).
3. Place the `.jar` file into your Minecraft `mods` folder.
4. Launch the game using your NeoForge installation.

JEI is not required to run PigMod. Install a compatible JEI version only if you want to browse mutation recipes in-game.

## Building from source

Make sure Java 21 is installed, then clone the repository:

```bash
git clone https://github.com/den04109/PigMod.git
cd PigMod
```

Build the mod with Gradle:

**Windows**
```bat
gradlew.bat build
```

**Linux / macOS**
```bash
./gradlew build
```

The built mod JAR will be available in `build/libs/`.

## Languages

PigMod currently includes English and Russian translations.

## Links

- **GitHub:** [den04109/PigMod](https://github.com/den04109/PigMod)
- **Discord:** `dxn1zz`

## License

PigMod is distributed under **All Rights Reserved**. All rights are reserved by the author.