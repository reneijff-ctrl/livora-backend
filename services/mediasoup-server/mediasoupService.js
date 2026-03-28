const mediasoup = require('mediasoup');
const os = require('os');

const mediaCodecs = [
  {
    kind: 'audio',
    mimeType: 'audio/opus',
    clockRate: 48000,
    channels: 2,
  },
  {
    kind: 'video',
    mimeType: 'video/VP8',
    clockRate: 90000,
    parameters: {},
  },
];

const workers = [];
let nextWorkerIdx = 0;

const createWorker = async () => {
  const worker = await mediasoup.createWorker({
    rtcMinPort: parseInt(process.env.RTC_MIN_PORT || '40000'),
    rtcMaxPort: parseInt(process.env.RTC_MAX_PORT || '49999'),
  });

  worker.on('died', () => {
    console.error('Mediasoup worker died [pid:%d]', worker.pid);
    const idx = workers.indexOf(worker);
    if (idx !== -1) {
      workers.splice(idx, 1);
    }
  });

  return worker;
};

const initMediasoup = async () => {
  const numWorkers = os.cpus().length;
  for (let i = 0; i < numWorkers; i++) {
    const worker = await createWorker();
    workers.push(worker);
  }
  console.log("Mediasoup workers started:", workers.length);
};

const getNextWorker = () => {
  if (workers.length === 0) {
    throw new Error('No Mediasoup workers available');
  }

  const worker = workers[nextWorkerIdx % workers.length];
  nextWorkerIdx = (nextWorkerIdx + 1) % workers.length;
  return worker;
};

const getWorkerCount = () => {
  return workers.length;
};

const getWorkerStats = async () => {
  const stats = [];
  for (const worker of workers) {
    const resourceUsage = await worker.getResourceUsage();
    stats.push({
      pid: worker.pid,
      resourceUsage,
      // Routers are not directly tracked per worker in Mediasoup Node.js API, 
      // but they can be inferred by counting them externally if needed.
    });
  }
  return stats;
};

const createRouter = async () => {
  const worker = getNextWorker();
  const router = await worker.createRouter({ mediaCodecs });
  router.appData.consumerCount = 0;
  return router;
};

module.exports = {
  initMediasoup,
  getWorkerStats,
  createRouter,
  getWorkerCount,
};
