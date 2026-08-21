# PICO Spatial Tennis

## Current behavior

- Native Android/Kotlin PICO Spatial SDK 0.13.3 app.
- `DefaultStage` uses `StageStyle.Mixed` for a passthrough tabletop tennis arena.
- Single-player quick match and three-round Star Cup against local AI.
- PICO controller input: left thumbstick moves; a physical right-controller swing or right trigger hits; A attacks, B lobs, X pauses, and Y returns to menu. Left/right pose availability is checked independently, and only two tracked controllers produce the ready state. Right-only mode falls back to the right thumbstick for movement and thumbstick press for pause; left-only mode keeps trigger safe shots. Buttons remain a deterministic fallback for emulator and accessibility use.
- Motion swings use a short velocity- and direction-consistent path instead of a one-frame distance threshold. Slow repositioning, one-sample tracking jumps and reversing jitter are rejected, with a 320 ms cooldown after a valid swing.
- Player swings are buffered for 0.42 seconds to make controller timing forgiving. Centered returns earn `PERFECT` feedback and bonus energy; full energy makes the next successful hit an automatic skill shot.
- AI mistakes produce a slower, centered opportunity ball instead of an unexplained instant point. Match results grade the player on victory, score margin, and best rally.
- Pause restores the exact previous phase, and Star Cup completion is persisted once per result.
- UI uses the `ISLAND RALLY` direction: forest glass, warm cream, honey yellow and one accent per animal opponent. The menu is a narrow island match-card surface with three stacked challenger rows, leaving the 3D lineup visible on its right. Opponent selection remains a deliberate two-step controller flow: select a row first, then use the explicit quick-match action; the selected row gains a stronger outline and the matching 3D preview moves forward and scales up.
- The match HUD is a low-chrome 620×112 score ribbon with contextual feedback and a compact skill-energy meter. First-time players receive a three-step controller tutorial (move, swing, A/B shot direction) that advances from real input and is persisted once completed. Pause/result cards use the same broadcast hierarchy and PicoTheme roles.
- Characters, court, rackets, ball, props and effects are procedural Spatial ECS geometry. The player is the balanced tiger 泰格虎; the AI roster is the agile rabbit 露比兔, power bear 柏鲁熊 and trickster fox 菲妮狐. Species silhouettes use distinct ears, muzzles, tails, fur colors and proportions; the tiger also carries face stripes. Every athlete is authored toward local +Z; during a match the near-court player root uses 180° yaw and the far-court AI uses 0° yaw, guaranteeing both face the net and each other. Racket roots remain located at the grip and anchored to the computed right-hand endpoint, so facing and swing rotation carry the full hand/racket assembly together. Character movement includes lateral lean, idle motion and eased racket swings.
- The court and jersey use local generated textures (`assets/textures/island_clay_v3.png` and `athlete_knit_v3.png`) with shared PBR surface profiles. The arena is a cozy tabletop grass island with a terracotta clay court, cream markings, wood net hardware, tiered bleachers, six lightweight animal spectators, four clustered trees and a small club kiosk. It is an original storybook life-sim direction rather than a direct copy of another game's assets.
- Presentation feedback includes a sampled ball trail, hit-impact ring, landing target, skill-ready player ring, rally/quality tags, best-rally tracking, point cards, and a three-star result grade.
- The menu shows all four animal athletes: a full-detail player tiger on the near half and three lightweight procedural AI preview rigs together on the far half. During a match all previews are hidden and only the player plus selected full-detail AI rig remain visible. Cold start creates only the playable court, player and core effects synchronously; opponent previews, bleachers, spectators, kiosk and trees are appended in small 48 ms-spaced groups after the first compositor handoff. Canonical primitive meshes/materials remain shared and entity-scaled.
- The four optional character GLB files are intentionally excluded. Networking is intentionally excluded.

## Structure

- `app/src/main/java/com/example/spatialtennis/Main.kt`: thin `DefaultStage` entry.
- `platform/`: Spatial `Application` and launcher Activity.
- `domain/model/`: game, cup and input models.
- `domain/usecase/TickTennisGameUseCase.kt`: deterministic local match simulation.
- `data/repository/`: local Star Cup progress persistence.
- `spatial/input/`: `ControllerTrackingProvider` adapter.
- `spatial/scene/TennisScene.kt`: procedural 3D arena and presentation updates.
- `ui/game/`: MVI-lite ViewModel and SpatialUI stage screen.
- `ui/game/components/`: HUD and menu/pause/result overlays.

## UI rule

All 2D UI must use `com.pico.spatial.ui.design.*` components inside `PicoTheme`. Do not add Material or Material3. Custom interaction must use SpatialUI hover, indication and controller haptics rules.

## Commands

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
pico-cli device list --format json
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.spatialtennis --activity com.example.spatialtennis.platform.LaunchActivity
```

## Next likely work

- Tune world scale and attachment positions on a PICO device.
- Add hit haptics and spatial audio keyed from game events.
- Tune controller-pose racket offsets and the current motion-threshold swing recognition on a physical PICO controller without changing deterministic hit rules.
- Confirm selected-opponent depth and HUD physical text size on a PICO headset; the emulator verifies layout but cannot automate volumetric controller-ray interaction.
