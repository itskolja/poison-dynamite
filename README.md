# Poison Dynamite

A RuneLite plugin for tracking Dynamite(p) usage with hit chance calculations and poison proc timers.

## Demo

[![Demo video](https://img.youtube.com/vi/IR9TIbziBSA/0.jpg)](https://youtu.be/IR9TIbziBSA)

## Features

- **Hit chance overlay** — calculates hit chance and poison probability from your real attack style (stance, prayers and boosts included), equipment, and the NPC's defence
- **Poison immunity** — the poison chance shows "Immune" (from wiki data) for NPCs that cannot be poisoned
- **Countdown timers** — tick-accurate ring overlay above every poisoned NPC showing time remaining for poison to proc, with result indicators (OK / MISS / X); multiple NPCs can be timed at once
- **NPC tracking** — shift-right-click NPCs to track or hide them; tracked NPCs persist across sessions and can be outlined in-world
- **Session stats** — poison procs vs attempts for the current session
- **Supplies** — remaining Dynamite(p) count with a low-supply warning
- **Notifications** — optional system notification when the poison procs
- **Max hit display** — shows your max hit based on Firemaking level

NPC defence stats are fetched from the OSRS Wiki (by name, falling back to NPC ID); failed lookups are shown as "Unavailable" and retried automatically.

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| Show NPC overlay | Countdown ring above poisoned NPCs | On |
| Show info panel | Info panel with target, hit chance, poison chance and max hit | On |
| Highlight tracked NPCs | Outline NPCs on the tracked list | Off |
| Show dynamite count | Remaining Dynamite(p) in the info panel | On |
| Show session stats | Poison procs vs attempts this session | On |
| Notify on poison proc | System notification when poison procs | Off |
