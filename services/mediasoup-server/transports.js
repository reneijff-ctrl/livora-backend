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

  console.log("Mediasoup transport created with announcedIp:", process.env.MEDIASOUP_ANNOUNCED_IP);
  const minPort = parseInt(process.env.RTC_MIN_PORT || '40000');
  const maxPort = parseInt(process.env.RTC_MAX_PORT || '49999');
  console.log("Mediasoup transport ports:", `${minPort}-${maxPort}`);

  const transport = await router.createWebRtcTransport({
    listenIps: [
      {
        ip: process.env.MEDIASOUP_LISTEN_IP || "127.0.0.1",
        announcedIp: process.env.MEDIASOUP_ANNOUNCED_IP || "127.0.0.1"
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

  return {
    id: transport.id,
    iceParameters: transport.iceParameters,
    iceCandidates: transport.iceCandidates,
    dtlsParameters: transport.dtlsParameters
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
