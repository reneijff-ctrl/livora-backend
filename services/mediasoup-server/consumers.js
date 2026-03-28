const { getRoom, ensureProducerOnRouter } = require('./rooms');
const { getProducer } = require('./producers');

const createConsumer = async (roomId, transport, producerId, rtpCapabilities, appData) => {
  const room = getRoom(roomId);
  if (!room) {
    throw new Error(`Room ${roomId} not found`);
  }

  const producer = getProducer(roomId, producerId);
  if (!producer) {
    throw new Error(`Producer ${producerId} not found in room ${roomId}`);
  }

  // 1. Resolve the router from the transport's appData to guarantee
  //    consumer and transport share the same router (avoids mismatch
  //    when edge routers are created between transport and consume calls).
  const transportRouterId = transport.appData && transport.appData.routerId;
  if (!transportRouterId) {
    throw new Error('Transport is missing routerId in appData');
  }

  const allRouters = [room.originRouter, ...room.edgeRouters];
  const router = allRouters.find(r => r.id === transportRouterId);
  if (!router) {
    throw new Error(`Router ${transportRouterId} not found in room ${roomId}`);
  }

  // 2. Ensure the producer is piped to this router (no-op for origin router)
  await ensureProducerOnRouter(room, router, producerId);

  // 3. Create the consumer on the transport's router.
  if (!transport.consume) {
     throw new Error('Invalid transport provided');
  }

  if (!router.canConsume({ producerId, rtpCapabilities })) {
    throw new Error('Cannot consume producer with provided capabilities');
  }

  const consumer = await transport.consume({
    producerId,
    rtpCapabilities,
    paused: true, // consumers should be created as paused
    appData: { ...appData, routerId: router.id },
  });

  console.log("CONSUMER CREATED:", consumer.kind, "id:", consumer.id, "producerId:", producerId, "roomId:", roomId);

  // Increment consumer count for load balancing
  router.appData.consumerCount++;

  room.consumers.set(consumer.id, consumer);

  consumer.on('transportclose', () => {
    console.log('Consumer transport closed, closing consumer [id:%s]', consumer.id);
    consumer.close();
    room.consumers.delete(consumer.id);
  });

  consumer.on('producerclose', () => {
    console.log('Consumer producer closed, closing consumer [id:%s]', consumer.id);
    consumer.close();
    room.consumers.delete(consumer.id);
  });

  consumer.on('close', () => {
    console.log('Consumer closed [id:%s]', consumer.id);
    room.consumers.delete(consumer.id);
    // Decrement consumer count for load balancing
    if (router.appData.consumerCount > 0) {
      router.appData.consumerCount--;
    }
  });

  return {
    id: consumer.id,
    producerId: consumer.producerId,
    kind: consumer.kind,
    rtpParameters: consumer.rtpParameters,
  };
};

const resumeConsumer = async (roomId, consumerId) => {
  const room = getRoom(roomId);
  if (!room) {
    throw new Error(`Room ${roomId} not found`);
  }

  const consumer = room.consumers.get(consumerId);
  if (!consumer) {
    throw new Error('Consumer not found');
  }
  await consumer.resume();
};

module.exports = {
  createConsumer,
  resumeConsumer,
};
