export const SIMULCAST_ENCODINGS = [
  {
    rid: "r0",
    maxBitrate: 150000,
    scaleResolutionDownBy: 3.0,
    maxFramerate: 15,
    scalabilityMode: "L1T3"
  },
  {
    rid: "r1",
    maxBitrate: 500000,
    scaleResolutionDownBy: 1.5,
    maxFramerate: 24,
    scalabilityMode: "L1T3"
  },
  {
    rid: "r2",
    maxBitrate: 2500000,
    scaleResolutionDownBy: 1.0,
    scalabilityMode: "L1T3"
  }
];

export const VIDEO_CONSTRAINTS = {
  width: { ideal: 1280 },
  height: { ideal: 720 },
  frameRate: { ideal: 30 }
};
