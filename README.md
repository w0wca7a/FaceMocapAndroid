# Face Mocap (Android) — replica

Camera preview + face mesh overlay + TCP streaming of landmarks, in the wire format
reverse-engineered from a real capture of the original Face Mocap app:

```
?FFFF X1_Y1_Z1|X2_Y2_Z2|...|Xn_Yn_Zn|
```

- `?` — frame start marker
- `FFFF` — 4-digit zero-padded rolling frame counter
- points separated by `|`, each point's `X_Y_Z` separated by `_`

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
   original app displayed. Point your TCP client at that IP:port (the `FaceMocapReceiver.cs`
   Stride script from earlier works as-is).
4. Green dots should appear over your face — that's the same landmark set being streamed.

## Known differences from the original app

- **Landmark source**: uses MediaPipe Face Landmarker (468 points) instead of ARCore
  Augmented Faces. Same canonical face topology, actively maintained, works on more
  devices (no ARCore compatibility list).
- **Z / metric scale on the wire**: the original app's values (`Z` around -380..-460)
  look like real depth in millimeters from an ARCore-tracked camera pose. MediaPipe's raw
  landmarks are normalized to the image, not physically metric, so `MainActivity.onFaceResult`
  uses a rough heuristic (`ASSUMED_FACE_WIDTH_MM` / `ASSUMED_DISTANCE_MM`) to put numbers in a
  similar range. It is **not** true depth. If you need accurate per-vertex metric coordinates,
  enable `outputFacialTransformationMatrixes()` (already on) and multiply MediaPipe's
  canonical face model vertices (a fixed 468-vertex array Google publishes) by that 4x4
  matrix per frame — happy to add that if you need real depth accuracy.
- **Single client only**: like the original, the phone is a TCP server; only one connected
  client is served at a time.
- **No head-pose block on the wire**: matches what we actually captured from your app —
  only the landmark array is sent, no separate 6-number head position/rotation section.

## Files

- `MainActivity.kt` — camera setup, permissions, wiring
- `FaceLandmarkerHelper.kt` — MediaPipe wrapper (LIVE_STREAM mode)
- `OverlayView.kt` — draws landmark dots over the preview
- `TcpStreamer.kt` — TCP server, formats frames on the wire
