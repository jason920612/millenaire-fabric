# Millénaire — Minecraft 26.2 (Fabric) clean-room rewrite

A from-scratch rewrite of the **Millénaire** mod (千年村莊 — living NPC villages loosely based on
11th-century cultures) for **Minecraft 26.2** on the **Fabric** loader and **Java 25**.

> **Status: early work in progress.** A vertical slice runs end-to-end — content data → generated
> village → buildings constructed block-by-block → living villagers — but this is far from feature
> parity with the original. See [`PLAN.md`](PLAN.md) for the honest per-layer status.

## What works today (server-verified)
- **Toolchain**: Fabric / MC 26.2 / Loom 1.17 / Java 25, official Mojang mappings.
- **Content loader (L1)**: parses the original Millénaire DSLs + layered-PNG building schematics;
  loads all five cultures; maps logical blocks to 26.2 block states (1.12→26.2 flattening).
- **World (L2 + backbone)**: `MillWorld` / `TownHall` aggregate persisted via `SavedData`
  (single source of truth, survives reload), duplicate-village prevention, proximity active/inactive.
- **Construction (L3)**: active villages build their buildings **gradually**, two-pass
  (structure then doors/torches/…), with a persistent cursor that resumes after a reload.
- **NPCs (L4, slice)**: a custom villager entity spawns in villages and wanders.

## Design docs
- [`PLAN.md`](PLAN.md) — layered plan (L0–L7) and current status.
- [`docs/INTENT.md`](docs/INTENT.md) + `docs/intent/01–06` — design-intent analysis of the original.
- [`docs/DEV.md`](docs/DEV.md) — how to build/run (incl. headless fake-player testing).
- [`docs/API-NOTES-26.2.md`](docs/API-NOTES-26.2.md) — 26.2 API specifics found via decompilation.

## Build
Requires JDK 25. `./gradlew build` (or `runClient` / `runServer`).

## Credits & licensing
This is a **clean-room** rewrite: all Java here is written from scratch and contains **no** decompiled
Minecraft code and **no** code copied from the original Millénaire. The original mod is by **Kinniken**
(<https://millenaire.org/>). Reuse of the original's art and content data, and any redistribution of
those, is **pending license clarification** — see [`LICENSE`](LICENSE). Until then all rights are
reserved and no original assets are included in this repository.

🤖 Scaffolding assisted by [Claude Code](https://claude.com/claude-code).
