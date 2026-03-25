require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { rateLimit } = require('express-rate-limit');
const { initMediasoup, getWorkerStats } = require('./mediasoupService');
const { createWebRtcTransport, connectTransport, getTransport, closeTransport, restartIce } = require('./transports');
const { createConsumer, resumeConsumer } = require('./consumers');
const { getRoom, getOrCreateRoom, getAllRooms, removeRoom, getRoomStats, getGlobalStats } = require('./rooms');
const { createProducer } = require('./producers');

const app = express();
const port = process.env.MEDIASOUP_PORT || 4000;
const INTERNAL_SECRET = process.env.MEDIASOUP_INTERNAL_SECRET;

app.use(express.json());
app.use(cors());

// Internal Authentication Middleware
const authMiddleware = (req, res, next) => {
  if (!INTERNAL_SECRET) {
    console.error('MEDIASOUP_INTERNAL_SECRET is not set in environment!');
    return res.status(500).json({ error: 'Server misconfigured: missing secret' });
  }

  const authHeader = req.headers.authorization;
  if (!authHeader || authHeader !== `Bearer ${process.env.MEDIASOUP_INTERNAL_SECRET}`) {
    console.warn(`Unauthorized REST request from ${req.ip}`);
    return res.status(401).json({ error: 'Unauthorized' });
  }
  next();
};

// Apply auth middleware to all REST endpoints
app.use(authMiddleware);

// Strict Allowlist Middleware
const allowedPrefixes = ['/rooms', '/transports', '/connect', '/produce', '/consume'];
app.use((req, res, next) => {
  const isAllowed = allowedPrefixes.some(prefix => 
    req.path === prefix || req.path.startsWith(prefix + '/')
  );

  if (!isAllowed) {
    console.warn(`Security Audit: Rejected request to unknown route: ${req.method} ${req.path} from ${req.ip}`);
    return res.status(404).json({ error: 'Not Found' });
  }
  next();
});

// Rate Limiting: 1 minute window, max 100 requests per IP
const apiRateLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  limit: 100, // 100 requests per IP
  standardHeaders: 'draft-7', // Draft-7: combined `RateLimit` header
  legacyHeaders: false, // Disable the `X-RateLimit-*` headers.
  skip: (req) => {
    // Skip if internal secret is provided (backend requests)
    const authHeader = req.headers.authorization;
    if (INTERNAL_SECRET && authHeader === `Bearer ${INTERNAL_SECRET}`) {
      return true;
    }

    // Exclude localhost and internal Docker network
    const ip = req.ip;
    return (
      ip === '127.0.0.1' ||
      ip === '::1' ||
      ip === '::ffff:127.0.0.1' ||
      ip.startsWith('172.') || // Common Docker network range
      ip.startsWith('10.') ||   // Internal network range
      ip.startsWith('192.168.') // Typical local network
    );
  },
  handler: (req, res, next, options) => {
    console.warn(`Rate limit violation: IP ${req.ip} exceeded limit for ${req.method} ${req.path}`);
    res.status(options.statusCode).json({ error: options.message });
  }
});

// Apply rate limiting to all REST routes
app.use(apiRateLimiter);

// Mediasoup Router Capabilities Endpoint
app.get('/rooms/capabilities', async (req, res) => {
  const { roomId } = req.query;
  if (!roomId) {
    return res.status(400).json({ error: 'roomId is required' });
  }

  try {
    const room = await getOrCreateRoom(roomId);
    res.json(room.originRouter.rtpCapabilities);
  } catch (err) {
    console.error('Failed to get router capabilities:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Create Transport Endpoint
app.post('/transports', async (req, res) => {
  const { roomId, producing } = req.body;
  
  if (!roomId) {
    return res.status(400).json({ error: 'roomId is required' });
  }

  try {
    const transportOptions = await createWebRtcTransport(roomId, producing);
    res.json(transportOptions);
  } catch (err) {
    console.error('Failed to create Mediasoup transport:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Restart ICE Endpoint
app.post('/transports/restart-ice', async (req, res) => {
  const { roomId, transportId } = req.body;
  try {
    const iceParameters = await restartIce(roomId, transportId);
    res.json(iceParameters);
  } catch (err) {
    console.error('Failed to restart ICE:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Connect Transport Endpoint
app.post('/connect', async (req, res) => {
  const { roomId, transportId, dtlsParameters } = req.body;
  try {
    await connectTransport(roomId, transportId, dtlsParameters);
    res.json({ success: true });
  } catch (err) {
    console.error('Failed to connect Mediasoup transport:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Consume Endpoint
app.post('/consume', async (req, res) => {
  const { roomId, transportId, producerId, rtpCapabilities, appData } = req.body;
  try {
    const transport = getTransport(roomId, transportId);
    if (!transport) throw new Error('Transport not found');
    
    const consumerData = await createConsumer(roomId, transport, producerId, rtpCapabilities, appData);
    res.json(consumerData);
  } catch (err) {
    console.error('Failed to create Mediasoup consumer:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Resume Consumer Endpoint
app.post('/consume/resume', async (req, res) => {
  const { roomId, consumerId } = req.body;
  try {
    await resumeConsumer(roomId, consumerId);
    res.json({ success: true });
  } catch (err) {
    console.error('Failed to resume Mediasoup consumer:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Diagnostic Endpoint: Get all active rooms
app.get('/rooms', (req, res) => {
  res.json({ rooms: getAllRooms() });
});

// Mediasoup Global Statistics Endpoint
app.get('/rooms/stats', async (req, res) => {
  try {
    const workerStats = await getWorkerStats();
    const globalStats = getGlobalStats();
    
    res.json({
      global: {
        routers: globalStats.numRouters,
        transports: globalStats.numTransports,
        producers: globalStats.numProducers,
        consumers: globalStats.numConsumers
      },
      workers: workerStats.map(w => ({
        workerId: w.pid.toString(),
        cpuUsage: 0, // Placeholder
        memoryUsage: Math.round((w.resourceUsage.ru_maxrss || 0) / 1024) // KB to MB
      }))
    });
  } catch (err) {
    console.error('Failed to get Mediasoup stats:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Delete Room Endpoint
app.delete('/rooms/:roomId', (req, res) => {
  const { roomId } = req.params;
  try {
    removeRoom(roomId);
    res.json({ success: true });
  } catch (err) {
    console.error('Failed to delete Mediasoup room:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Delete Transport Endpoint
app.delete('/rooms/:roomId/transports/:transportId', (req, res) => {
  const { roomId, transportId } = req.params;
  try {
    closeTransport(roomId, transportId);
    res.json({ success: true });
  } catch (err) {
    console.error('Failed to delete Mediasoup transport:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Get Room Stats Endpoint
app.get('/rooms/:roomId/stats', async (req, res) => {
  const { roomId } = req.params;
  try {
    const stats = await getRoomStats(roomId);
    if (!stats) return res.status(404).json({ error: 'Room not found' });
    res.json(stats);
  } catch (err) {
    console.error('Failed to get Mediasoup room stats:', err);
    res.status(500).json({ error: err.message });
  }
});

// Mediasoup Get Producers in Room Endpoint
app.get('/rooms/:roomId/producers', (req, res) => {
  const { roomId } = req.params;
  const room = getRoom(roomId);
  if (!room) {
    return res.json({ producers: [] });
  }
  
  const producers = Array.from(room.producers.values()).map(producer => ({
    id: producer.id,
    kind: producer.kind
  }));
  
  res.json({ producers });
});

// Mediasoup Produce Endpoint
app.post('/produce', async (req, res) => {
  const { roomId, transportId, kind, rtpParameters, appData } = req.body;
  try {
    const transport = getTransport(roomId, transportId);
    if (!transport) throw new Error('Transport not found');
    
    const producerData = await createProducer(roomId, transport, kind, rtpParameters, appData);
    res.json(producerData);
  } catch (err) {
    console.error('Failed to create Mediasoup producer:', err);
    res.status(500).json({ error: err.message });
  }
});

// Initialization
const startServer = async () => {
  try {
    await initMediasoup();
    console.log('Mediasoup initialized');
    
    app.listen(port, () => {
      console.log(`Mediasoup server listening at http://localhost:${port}`);
    });
  } catch (err) {
    console.error('Failed to start Mediasoup server:', err);
    process.exit(1);
  }
};

startServer();
