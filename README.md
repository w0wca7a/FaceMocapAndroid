# Face Mocap (Android) — replica

Camera preview + face mesh overlay + UDP streaming of a compact binary tracking packet.
Full protocol spec lives in `Proto.kt`; summary:

- **Transport**: UDP (a dropped frame beats added latency for live tracking).
- **Encoding**: Little Endian throughout.
- **Packet** (`PacketType = 0`, Tracking): 16-byte header (`Magic`, `Version`,
  `PacketType`, `FrameId`, `Timestamp`) → 8-byte blendshape bitmask → one `Half` (2 bytes)
  per set bit, in ascending bit order → 3× `Quaternion` (head, left eye, right eye, 16
  bytes each). Max size 176 bytes.
- **BlendShape enum**: fixed 52-entry order, never sent by name over the wire — Android
  and the receiving client (e.g. Stride) must share the exact same enum (see
  `Proto.BlendShape`).
- **Eye quaternions are always identity** from this app — MediaPipe has no gaze output,
  so eye rotation is meant to be reconstructed client-side from the `EyeLookUp/Down/In/
  Out(Left|Right)` blendshape values, which are already in the mask/values section.
- **Head quaternion** is derived on-device from MediaPipe's `facialTransformationMatrixes()`.

## Before building

1. **Download the Face Landmarker model** and place it at
   `app/src/main/assets/face_landmarker.task`:
   https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
   (link taken from Google's MediaPipe docs — if it 404s, get the current one from
   https://developers.google.com/mediapipe/solutions/vision/face_landmarker)

2. Open the project in Android Studio (Jellyfish+ recommended), let it sync Gradle.

3. Check the MediaPipe Tasks Vision version in `app/build.gradle.kts` against the latest
   at the link above — pin whatever is current when you build.

## Running

1. Install on a real device (emulator camera won't give useful face tracking).
2. Grant camera permission when prompted.
3. The top bar shows the phone's Wi-Fi IP and port (9996 by default). Point your UDP
   client at that IP:port. Because UDP is connectionless, the app only knows a client
   exists once it receives *any* datagram from it — send a small heartbeat packet
   periodically (every 1-2s is plenty) to register/stay registered, or the connection
   indicator will show disconnected after 5s of silence even if you're still receiving
   frames fine.
4. Green dots (+ contour lines) should appear over your face — that confirms tracking is
   working. The blendshape scores being streamed are separate from this overlay (the
   overlay uses raw landmarks for drawing; the wire only carries the 52 blendshape values).

## Known differences from the original app

- **Blendshape source**: uses MediaPipe Face Landmarker's built-in blendshape model (52
  ARKit-style scores, `setOutputFaceBlendshapes(true)`) instead of ARCore Augmented Faces
  or geometric estimation from landmarks. More accurate than a geometric approximation.
  Note: MediaPipe has no `tongueOut` score (protocol reserves the bit, it's just never
  set), and MediaPipe's `_neutral` category has no slot in the protocol (dropped).
- **"Connected" is a UDP heartbeat timeout, not a real connection**: see the note under
  Running above.
- **Head pose**: computed on-device from `facialTransformationMatrixes()`, decomposed
  into a quaternion in `MathUtils.kt`. Eye quaternions are always identity by design (see
  protocol summary above).

## Files

- `MainActivity.kt` — camera setup, permissions, wiring
- `FaceLandmarkerHelper.kt` — MediaPipe wrapper (LIVE_STREAM mode)
- `OverlayView.kt` — draws landmark dots + contour lines over the preview
- `Proto.kt` — binary wire protocol: header, blendshape mask/enum, encode/decode
- `UdpStreamer.kt` — UDP transport, client heartbeat/timeout handling
- `MathUtils.kt` — rotation-matrix-to-quaternion conversion for head pose
