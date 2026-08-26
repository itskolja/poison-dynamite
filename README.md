# Poison Dynamite

A RuneLite plugin for tracking Dynamite(p) usage with hit chance calculations and poison proc timers.

## Demo

[![Poison tracker demo](https://img.youtube.com/vi/XALXfeWzRfU/0.jpg)](https://youtu.be/XALXfeWzRfU)

Earlier demo: [hit chance and proc timer](https://youtu.be/IR9TIbziBSA)

## Features

- **Hit chance overlay** — calculates hit chance from your real attack style (stance, prayers and boosts included), equipment, and the NPC's defence
- **Poison immunity** — NPCs that cannot be poisoned (from wiki data) are flagged in the info panel, and a throw at one shows the X result immediately instead of running a countdown
- **Countdown timers** — tick-accurate ring overlay above every poisoned NPC showing time remaining for poison to proc, with result indicators (OK / MISS / X); multiple NPCs can be timed at once
- **Poison tracker** — once the poison procs, tracks the target's remaining poison hits and damage, time until the poison wears off, and time until it kills the target
- **Session stats** — poison procs vs attempts for the current session
- **Supplies** — remaining Dynamite(p) count with a low-supply warning
- **Notifications** — optional system notification when the poison procs

NPC defence stats are fetched from the OSRS Wiki (by name, falling back to NPC ID); failed lookups are shown as "Unavailable" and retried automatically.

## Configuration

Settings are grouped into sections in the plugin's config panel.

| Section | Setting | Description | Default |
|---------|---------|-------------|---------|
| Overlays | NPC countdown ring | Countdown ring above poisoned NPCs | On |
| Overlays | Info panel | Info panel with target and hit chance | On |
| Info panel | Target stats | Target's defence level, hit chance and poison immunity | On |
| Info panel | Poison tracker | Remaining poison damage, time until it wears off and time until it kills the target | On |
| Info panel | Dynamite count | Remaining Dynamite(p) in the info panel | On |
| Info panel | Session stats | Poison procs vs attempts this session | On |
| Notifications | Notify on poison proc | System notification when poison procs | Off |
