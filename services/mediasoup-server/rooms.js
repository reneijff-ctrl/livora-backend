const { createRouter, getWorkerCount } = require('./mediasoupService');

const rooms = new Map(); // roomId -> { originRouter, edgeRouters, pipes, producer, ... }

const getOrCreateRoom = async (roomId) => {
  if (rooms.has(roomId)) {
    return rooms.get(roomId);
  }

  console.log(`Creating room: ${roomId}`);
  const router = await createRouter();

  const room = {
    originRouter: router,
    edgeRouters: [],
    pipes: {}, // routerId -> boolean (is piped)
    producer: null, // We can store the main producer here if needed, or just use the producers Map
    transports: new Map(), // transportId -> transport
    producers: new Map(), // producerId -> producer
    consumers: new Map(), // consumerId -> consumer
    scalingInProgress: false,
    lastActivity: Date.now()
  };

  rooms.set(roomId, room);
  return room;
};

const getRoom = (roomId) => {
  const room = rooms.get(roomId);
  if (room) {
    room.lastActivity = Date.now();
  }
  return room;
};

const getLeastLoadedRouter = async (room) => {
  const allRouters = [room.originRouter, ...room.edgeRouters];
  
  // Scaling logic: If all existing routers have at least 10 consumers, create a new edge router
  const SCALING_THRESHOLD = 10;
  const MAX_ROUTERS_PER_ROOM = getWorkerCount();
  const allBusy = allRouters.every(r => (r.appData.consumerCount || 0) >= SCALING_THRESHOLD);
  
  if (allBusy && room.edgeRouters.length < MAX_ROUTERS_PER_ROOM - 1 && !room.scalingInProgress) {
    console.log(`Scaling: all routers busy, creating new edge router for room. Current routers: ${allRouters.length}/${MAX_ROUTERS_PER_ROOM}`);
    room.scalingInProgress = true;
    try {
      const newRouter = await createRouter();
      room.edgeRouters.push(newRouter);
      console.log(`Scaling completed: new edge router ${newRouter.id} created for room`);
      return newRouter;
    } finally {
      room.scalingInProgress = false;
    }
  }

  let leastLoaded = allRouters[0];
  for (const r of allRouters) {
    const currentCount = r.appData.consumerCount || 0;
    const leastLoadedCount = leastLoaded.appData.consumerCount || 0;
    
    if (currentCount < leastLoadedCount) {
      leastLoaded = r;
    }
  }

  return leastLoaded;
};

const ensureProducerOnRouter = async (room, router, producerId) => {
  if (router === room.originRouter) {
    return;
  }

  const pipeKey = `${router.id}:${producerId}`;
  if (room.pipes[pipeKey]) {
    return;
  }

  console.log(`Piping producer ${producerId} to router ${router.id}`);

  const { pipeProducer, pipeConsumer } = await room.originRouter.pipeToRouter({
    producerId: producerId,
    router: router,
  });

  room.pipes[pipeKey] = { pipeProducer, pipeConsumer };
};

const removeRoom = (roomId) => {
  const room = rooms.get(roomId);
  if (room) {
    console.log(`Closing room: ${roomId}`);
    
    // Close all pipes (pipeProducer/pipeConsumer)
    for (const pipeKey in room.pipes) {
      const { pipeProducer, pipeConsumer } = room.pipes[pipeKey];
      pipeProducer.close();
      pipeConsumer.close();
    }
    
    // Close origin router
    room.originRouter.close();
    
    // Close all edge routers
    for (const edgeRouter of room.edgeRouters) {
      edgeRouter.close();
    }
    
    rooms.delete(roomId);
    console.log(`Room ${roomId} successfully removed from Map`);
  } else {
    console.log(`Room ${roomId} not found for removal`);
  }
};

const getAllRooms = () => {
  return Array.from(rooms.entries()).map(([roomId, room]) => ({
    roomId,
    producers: room.producers.size,
    consumers: room.consumers.size,
    transports: room.transports.size
  }));
};

const getGlobalStats = () => {
  let totalProducers = 0;
  let totalConsumers = 0;
  let totalTransports = 0;
  let totalRouters = 0;
  
  for (const room of rooms.values()) {
    totalProducers += room.producers.size;
    totalConsumers += room.consumers.size;
    totalTransports += room.transports.size;
    totalRouters += 1 + room.edgeRouters.length;
  }
  
  return {
    numRooms: rooms.size,
    numRouters: totalRouters,
    numTransports: totalTransports,
    numProducers: totalProducers,
    numConsumers: totalConsumers
  };
};

const getRoomStats = async (roomId) => {
  const room = rooms.get(roomId);
  if (!room) return null;

  const producerStats = [];
  for (const producer of room.producers.values()) {
    const stats = await producer.getStats();
    producerStats.push({ id: producer.id, kind: producer.kind, paused: producer.paused, stats });
  }

  const consumerStats = [];
  for (const consumer of room.consumers.values()) {
    const stats = await consumer.getStats();
    consumerStats.push({ id: consumer.id, kind: consumer.kind, paused: consumer.paused, stats });
  }

  const transportStats = [];
  for (const transport of room.transports.values()) {
    const stats = await transport.getStats();
    transportStats.push({ id: transport.id, iceState: transport.iceState, dtlsState: transport.dtlsState, stats });
  }

  return { producerStats, consumerStats, transportStats };
};

// Background task to clean up idle rooms
setInterval(() => {
  const now = Date.now();
  const IDLE_TIMEOUT = 5 * 60 * 1000; // 5 minutes

  for (const [roomId, room] of rooms.entries()) {
    const hasProducers = room.producers.size > 0;
    const hasConsumers = room.consumers.size > 0;

    if (hasProducers || hasConsumers) {
      room.lastActivity = now;
      continue;
    }

    if (now - room.lastActivity > IDLE_TIMEOUT) {
      console.log("Removing idle Mediasoup room:", roomId);
      removeRoom(roomId);
    }
  }
}, 60 * 1000); // Check every 60 seconds

module.exports = {
  getOrCreateRoom,
  getRoom,
  removeRoom,
  getAllRooms,
  getGlobalStats,
  getRoomStats,
  ensureProducerOnRouter,
  getLeastLoadedRouter,
};
