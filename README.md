# Face Mocap (Android) — replica

Camera preview + face mesh overlay + TCP streaming of blendshapes, wire format:

```
?FFFFname1_score1|name2_score2|...|
```

- `?` — frame start marker
- `FFFF` — 4-digit zero-padded rolling frame counter
- entries separated by `|`, each as `name_score` (score 0..1, 3 decimals, split on the
  last `_` since blendshape names never contain one)

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
3. The top bar shows the phone's Wi-Fi IP and port (9996 by default) — same info the
   original app displayed. Point your TCP client at that IP:port.
4. Green dots should appear over your face — that's the same landmark set being streamed.

## Known differences from the original app

- **Blendshape** source: uses MediaPipe Face Landmarker's built-in blendshape model (52
  ARKit-style scores, setOutputFaceBlendshapes(true)) instead of ARCore Augmented Faces
  or geometric estimation from landmarks. More accurate than a geometric approximation.
- **Single client only**: like the original, the phone is a TCP server; only one connected
  client is served at a time.
- **No head-pose block on the wire**: matches what we actually captured from your app —
  only the landmark array is sent, no separate 6-number head position/rotation section.

## Files

- `MainActivity.kt` — camera setup, permissions, wiring
- `FaceLandmarkerHelper.kt` — MediaPipe wrapper (LIVE_STREAM mode)
- `OverlayView.kt` — draws landmark dots over the preview
- `TcpStreamer.kt` — TCP server, formats frames on the wire
