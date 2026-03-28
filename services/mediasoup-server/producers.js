const { getRoom } = require('./rooms');

const createProducer = async (roomId, transport, kind, rtpParameters, appData) => {
  console.log('NEW PRODUCER: kind=%s, roomId=%s', kind, roomId);
  const producer = await transport.produce({ kind, rtpParameters, appData });
  console.log('PRODUCER CREATED: id=%s, kind=%s, roomId=%s', producer.id, producer.kind, roomId);
  
  const room = getRoom(roomId);
  if (room) {
    room.producers.set(producer.id, producer);
    if (!room.producer) {
      room.producer = producer;
    }
  }

  // PLI / FIR keyframe request throttling
  let lastKeyframeRequest = 0;
  const KEYFRAME_THROTTLE_MS = 1000;

  producer.on('requestkeyframe', () => {
    const now = Date.now();
    if (now - lastKeyframeRequest > KEYFRAME_THROTTLE_MS) {
      console.log('Requesting keyframe for producer [id:%s]', producer.id);
      lastKeyframeRequest = now;
      producer.requestKeyFrame()
        .catch((error) => console.warn('Failed to request keyframe: %o', error));
    } else {
      console.log('Ignoring repeated keyframe request for producer [id:%s] (throttled)', producer.id);
    }
  });

  producer.on('transportclose', () => {
    console.log('Producer transport closed, closing producer [id:%s]', producer.id);
    producer.close();
    const room = getRoom(roomId);
    if (room) {
      room.producers.delete(producer.id);
    }
  });

  producer.on('close', () => {
    console.log('Producer closed [id:%s]', producer.id);
    const room = getRoom(roomId);
    if (room) {
      room.producers.delete(producer.id);
    }
  });

  return {
    id: producer.id,
    kind: producer.kind,
  };
};

const getProducer = (roomId, producerId) => {
  const room = getRoom(roomId);
  return room ? room.producers.get(producerId) : null;
};

module.exports = {
  createProducer,
  getProducer,
};
