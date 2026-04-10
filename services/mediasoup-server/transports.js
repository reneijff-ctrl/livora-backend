const { getOrCreateRoom, getRoom, getLeastLoadedRouter } = require('./rooms');
const transports = new Map(); // transportId -> transport

const createWebRtcTransport = async (roomId, producing = false) => {
  const room = await getOrCreateRoom(roomId);
  
  let router;
  if (producing) {
    router = room.originRouter;
  } else {
    router = await getLeastLoadedRouter(room);
  }

  // announcedIp = the IP clients will use to reach this server.
  // - Local dev: set MEDIASOUP_ANNOUNCED_IP=127.0.0.1 (or your LAN IP for cross-device testing)
  // - Production: set to your public/floating IP (e.g., Hetzner server IP)
  const announcedIp = process.env.MEDIASOUP_ANNOUNCED_IP || "127.0.0.1";

  // listenIp = the interface mediasoup binds to for RTP/RTCP.
  // "0.0.0.0" binds all interfaces — required for LAN and production.
  // Only use "127.0.0.1" if you're certain all clients are on localhost.
  const listenIp = process.env.MEDIASOUP_LISTEN_IP || "0.0.0.0";

  const minPort = parseInt(process.env.RTC_MIN_PORT || '40000');
  const maxPort = parseInt(process.env.RTC_MAX_PORT || '49999');

  console.log("Mediasoup transport config:", { listenIp, announcedIp, ports: `${minPort}-${maxPort}` });

  const transport = await router.createWebRtcTransport({
    listenIps: [
      {
        ip: listenIp,
        announcedIp: announcedIp
      }
    ],

    enableUdp: true,
    enableTcp: true,
    preferUdp: true,

    rtcMinPort: minPort,
    rtcMaxPort: maxPort,

    initialAvailableOutgoingBitrate: 1000000,
    appData: { routerId: router.id }
  });

  // Apply bitrate limits for congestion control
  await transport.setMaxIncomingBitrate(3000000);
  await transport.setMaxOutgoingBitrate(3000000);

  // Store transport in room-based map and global map
  room.transports.set(transport.id, transport);
  transports.set(transport.id, transport);

  // Handle transport closure
  transport.on('dtlsstatechange', (dtlsState) => {
    if (dtlsState === 'closed') {
      transport.close();
    }
  });

  transport.on('@close', () => {
    console.log('Transport closed [id:%s]', transport.id);
    const room = getRoom(roomId);
    if (room) {
      room.transports.delete(transport.id);
    }
    transports.delete(transport.id);
  });

  transport.on('close', () => {
    console.log('Transport closed [id:%s]', transport.id);
    const room = getRoom(roomId);
    if (room) {
      room.transports.delete(transport.id);
    }
    transports.delete(transport.id);
  });

  // ICE servers for NAT traversal:
  // - STUN: free, handles simple NAT (symmetric NAT still fails)
  // - TURN: required for production behind firewalls/symmetric NAT
  const iceServers = [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' },
    { urls: 'stun:stun3.l.google.com:19302' },
    { urls: 'stun:stun4.l.google.com:19302' }
  ];

  // Add TURN server if configured (REQUIRED for production reliability)
  const turnUrl = process.env.TURN_SERVER_URL;
  if (turnUrl && turnUrl.includes(':')) {
    if (!process.env.TURN_USERNAME || !process.env.TURN_CREDENTIAL) {
      console.warn("WEBRTC WARNING: TURN_SERVER_URL is set but TURN_USERNAME or TURN_CREDENTIAL is missing. Falling back to STUN only.");
    } else {
      iceServers.push({
        urls: process.env.TURN_SERVER_URL,
        username: process.env.TURN_USERNAME,
        credential: process.env.TURN_CREDENTIAL
      });
      
      if (process.env.TURN_SERVER_TLS_URL) {
        iceServers.push({
          urls: process.env.TURN_SERVER_TLS_URL,
          username: process.env.TURN_USERNAME,
          credential: process.env.TURN_CREDENTIAL
        });
      }
    }
  } else {
    console.warn("WEBRTC WARNING: No TURN_SERVER_URL configured. WebRTC may fail on mobile networks or restrictive firewalls.");
  }

  console.log("WEBRTC TRANSPORT OPTIONS:", {
    id: transport.id,
    listenIps: [{ ip: listenIp, announcedIp }],
    enableUdp: true,
    enableTcp: true,
    iceCandidates: transport.iceCandidates,
    iceServers: iceServers.map(s => ({ 
      urls: s.urls,
      hasAuth: !!(s.username && s.credential)
    }))
  });

  return {
    id: transport.id,
    iceParameters: transport.iceParameters,
    iceCandidates: transport.iceCandidates,
    dtlsParameters: transport.dtlsParameters,
    iceServers
  };
};

const getTransport = (roomId, transportId) => {
  const actualTransportId = transportId || roomId;
  return transports.get(actualTransportId);
};

const connectTransport = async (roomId, transportId, dtlsParameters) => {
  const transport = getTransport(roomId, transportId);
  if (!transport) {
    throw new Error('Transport not found');
  }
  await transport.connect({ dtlsParameters });
};

const closeTransport = (roomId, transportId) => {
  const transport = getTransport(roomId, transportId);
  if (transport) {
    console.log(`Closing transport: ${transportId} in room: ${roomId}`);
    transport.close();
    // The 'close' event handler will remove it from the room map
  }
};

const restartIce = async (roomId, transportId) => {
  const transport = getTransport(roomId, transportId);
  
  if (!transport) {
    throw new Error('Transport not found');
  }
  const iceParameters = await transport.restartIce();
  return iceParameters;
};

module.exports = {
  createWebRtcTransport,
  getTransport,
  connectTransport,
  closeTransport,
  restartIce,
};
