# Krovex Client

A custom client for Minecraft: Java Edition 1.21.11: a Fabric mod (in-game menu, HUD modules, skies, shaders, music
overlay) and a Windows launcher (Java Swing) that manages instances and installs the mod.

Independent project, not affiliated with Mojang or Microsoft. Downloads: see the Krovex website.

## Layout
- `mod/`: the Fabric mod (Gradle, Fabric Loom).
- `shared-theme/`: theme model shared by the mod and the launcher.
- `launcher/`: the launcher sources (`com/krovex/...`), built with `javac` + `jar` (see `launcher/Build.txt`).
- `tools/`: font generation script and an offline sky preview renderer.

## Building the mod
Needs JDK 25 and Gradle 9.x (Fabric Loom 1.19):

    gradle :mod:build

The jar ends up in `mod/build/libs/`.

## Microsoft sign-in
The launcher uses the Microsoft device / PKCE flow with a client ID that is **not** included in this repository. Forks must
register their own Azure application and Minecraft API access (see `launcher/Public Microsoft Sign-In.txt`).

## Licence
GPL-3.0 (see `LICENSE`). The Krovex name and logo are not licensed: see `NOTICE.md`.