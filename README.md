# Poison Dynamite

A RuneLite plugin that tracks Dynamite(p) poison procs, countdown timers, success rates, and NPC immunity.

## Features

- **Countdown infobox** — shows time remaining for poison to proc after detonation, with color-coded status (green = success, red = fail, orange = warning)
- **Session stats overlay** — displays success rate and total poison damage dealt
- **Failure notifications** — alerts you when poison fails to proc
- **Immunity learning** — automatically flags NPCs as immune after repeated failures and warns you before targeting them again

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| Show infobox timer | Countdown infobox after detonation | On |
| Notify on failure | Notification when poison fails to proc | On |
| Show stats overlay | Session success rate and damage overlay | On |
| Track poison damage | Track cumulative poison damage | On |
| Warn if NPC immune | Chat warning when targeting immune NPCs | On |
| Immune NPCs | Learned immune NPC IDs (clear to reset) | — |
