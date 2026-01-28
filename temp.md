

## Milestone 6: Riding Integration
### Phase A: Physics Locking
- [ ] **Task 6.1:** Parent Player Model transform to the `BoardRig` transform with a vertical offset.
- [ ] **Task 6.2:** Implement "Snap to Board" logic to prevent the player from drifting off during high-speed turns.

### Phase B: Animation Posing
- [ ] **Task 6.3:** Load and apply a static "Ride" pose (knees bent, arms out).
- [ ] **Task 6.4:** Implement **Procedural Spine Lean** tied to LS steering input.

### Phase C: The Push Mechanic
- [ ] **Task 6.5:** Implement a `PlayerState` manager.
- [ ] **Task 6.6:** Create the "Push" animation logic (trigger physics impulse based on animation frame).

## Milestone 7: Character Controller & Camera
### 7.1 State Management
- [ ] Implement `PlayerState` (Walking vs. Riding).
- [ ] Map Gamepad **Y Button** to state toggle with position offsets.

### 7.2 On-Foot Locomotion
- [ ] Implement Left Joystick movement relative to Camera Forward.
- [ ] Implement **A Button** Jump logic for Walking state.
- [ ] Add `WALK` and `JUMP` animation triggers.

### 7.3 Advanced Camera System
- [ ] Implement Right Stick **Orbit Camera** (Yaw/Pitch control).
- [ ] Create `CameraSettings` data class (FOV, Offset, Distance).
- [ ] Implement **Smooth Transition** between 'Walking' and 'Riding' presets.

### 7.4 World Alignment
- [ ] Ensure Skater and Board maintain proper Y-offset (Centimeters to Meters conversion).
- [ ] Implement Raycast-based floor snapping for the Walking state.


