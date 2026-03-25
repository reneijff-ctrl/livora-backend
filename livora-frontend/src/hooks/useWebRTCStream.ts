import { useEffect, useRef } from 'react';
import webRtcService, { SignalingMessage, SignalingType } from '@/websocket/webRtcService';
import { webSocketService } from '@/websocket/webSocketService';
import type { Transport, Consumer } from 'mediasoup-client/lib/types';

interface UseWebRTCStreamProps {
  creatorId: number | undefined;
  streamId: string;
  user: any;
  availability: "ONLINE" | "LIVE" | "OFFLINE" | null;
  hasAccess: boolean | null;
  room: any;
  error: string | null;
  videoRef: React.RefObject<HTMLVideoElement>;
  setHasAccess: (access: boolean) => void;
  setAvailability: (availability: "ONLINE" | "LIVE" | "OFFLINE" | null) => void;
  setError: (error: string | null) => void;
  setLoading: (loading: boolean) => void;
  setNeedsInteraction: (needs: boolean) => void;
}

export function useWebRTCStream({
  creatorId,
  streamId,
  user,
  availability,
  hasAccess,
  room,
  error,
  videoRef,
  setHasAccess,
  setAvailability,
  setError,
  setLoading,
  setNeedsInteraction
}: UseWebRTCStreamProps) {
  const instanceId = useRef(Math.random().toString(36).substring(2, 9)).current;
  const remoteStreamRef = useRef<MediaStream>(new MediaStream());
  const recvTransport = useRef<Transport | null>(null);
  const consumers = useRef<Map<string, Consumer>>(new Map());
  const hasInitializedRef = useRef(false);
  const joinedCreatorRef = useRef<number | null>(null);
  const joiningRef = useRef(false);
  const consumedProducersRef = useRef(new Set<string>());
  const deferredProducersRef = useRef<{ id: string, kind: string }[]>([]);
  const signalingUnsubRef = useRef<(() => void) | null>(null);
  const errorsUnsubRef = useRef<(() => void) | null>(null);
  const activeSessionIdRef = useRef<string | null>(null);
  const prevHasAccessRef = useRef<boolean | null>(null);
  const userIdRef = useRef(user?.id);
  userIdRef.current = user?.id;

  const log = (msg: string, data?: any) => {
    console.log(`[WATCH-HOOK-${streamId}] ${new Date().toISOString()} - ${msg}`, data || "");
  };

  const performWebRTCCleanup = () => {
    if (webSocketService.isConnected() && joinedCreatorRef.current && userIdRef.current && room?.streamRoomId) {
      log(`[${instanceId}] WebRTC Cleanup: Sending LEAVE signal`);
      webSocketService.send('/app/webrtc.signal', {
        type: SignalingType.LEAVE,
        senderId: Number(userIdRef.current),
        roomId: room.streamRoomId,
        streamId: streamId
      });
    }

    hasInitializedRef.current = false;
    
    if (signalingUnsubRef.current) {
      log(`[${instanceId}] WebRTC Cleanup: Executing signaling unsubscribe`);
      signalingUnsubRef.current();
      signalingUnsubRef.current = null;
    }
    
    if (errorsUnsubRef.current) {
      log(`[${instanceId}] WebRTC Cleanup: Executing errors unsubscribe`);
      errorsUnsubRef.current();
      errorsUnsubRef.current = null;
    }
    
    if (recvTransport.current) {
      recvTransport.current.close();
      recvTransport.current = null;
    }
    webRtcService.cleanup();
    consumers.current.clear();
    consumedProducersRef.current.clear();
    deferredProducersRef.current = [];
    if (remoteStreamRef.current) {
      remoteStreamRef.current.getTracks().forEach(t => {
        t.stop();
        remoteStreamRef.current.removeTrack(t);
      });
    }
    joinedCreatorRef.current = null;
  };

  const consumeProducer = async (data: any, bypassSetCheck: boolean = false, origin: string = 'unknown', active: boolean) => {
    const { producerId, id, kind } = data || {};
    const actualProducerId = producerId || id;
    const actualKind = kind || 'video';

    console.log(
      "[WATCH-HOOK] consumeProducer called",
      {
        producerId: actualProducerId,
        kind: actualKind,
        origin,
        consumedSetSize: consumedProducersRef.current.size,
        time: Date.now()
      }
    );

    if (!actualProducerId) {
      log(`[${instanceId}] consumeProducer: missing producerId`, data);
      return;
    }

    if (!bypassSetCheck && consumedProducersRef.current.has(actualProducerId)) {
      log(`[${instanceId}] Producer already consumed: ${actualProducerId}`);
      return;
    }

    consumedProducersRef.current.add(actualProducerId);

    if (!recvTransport.current || !webRtcService.getDevice() || !room?.streamRoomId) {
      log(`[${instanceId}] consumeProducer deferred: transport or device not ready. Queuing producer ${actualProducerId}`);
      deferredProducersRef.current.push({ id: actualProducerId, kind: actualKind });
      return;
    }

    if ([...consumers.current.values()].some(c => c.producerId === actualProducerId)) {
      log(`[${instanceId}] consumeProducer: already consuming ${actualProducerId}`);
      return;
    }

    try {
      const response = await webRtcService.sendRequest(SignalingType.CONSUME, room.streamRoomId, {
        transportId: recvTransport.current.id,
        producerId: actualProducerId,
        rtpCapabilities: webRtcService.getDevice()!.rtpCapabilities
      });
      
      if (!active || !recvTransport.current) return;
      
      const { id: consumerId, kind: cKind, rtpParameters, appData } = response;

      const consumer = await recvTransport.current.consume({
        id: consumerId,
        producerId: actualProducerId,
        kind: cKind,
        rtpParameters,
        appData,
      });

      if (!active) {
        consumer.close();
        return;
      }

      consumers.current.set(consumerId, consumer);
      webRtcService.addConsumer(consumer);
      
      const { track } = consumer;
      track.enabled = true;
      
      if (cKind === 'video') {
        remoteStreamRef.current.getVideoTracks().forEach(t => {
          t.stop();
          remoteStreamRef.current.removeTrack(t);
        });
      } else {
        remoteStreamRef.current.getAudioTracks().forEach(t => {
          t.stop();
          remoteStreamRef.current.removeTrack(t);
        });
      }
      remoteStreamRef.current.addTrack(track);

      if (videoRef.current) {
        if (videoRef.current.srcObject !== remoteStreamRef.current) {
          videoRef.current.srcObject = remoteStreamRef.current;
        }
        
        videoRef.current.playsInline = true;
        videoRef.current.autoplay = true;
        videoRef.current.setAttribute("preload", "auto");
        
        track.onunmute = async () => {
          try {
            if (videoRef.current) {
              await videoRef.current.play();
            }
          } catch (err: any) {
            log(`[${instanceId}] Autoplay blocked:`, err.message);
            setNeedsInteraction(true);
          }
        };
      }

      await webRtcService.sendRequest(SignalingType.RESUME_CONSUMER, room.streamRoomId, { consumerId });
      if (!active) return;
      
      consumer.resume();
      log(`[${instanceId}] Consumer resumed: ${consumerId}`);

      if (cKind === 'video') {
        consumer.setPreferredLayers({ temporalLayer: 2 });
        consumer.appData.currentLayer = 2;
      }

      // Adaptive Simulcast Layer Switching
      if (cKind === 'video') {
        webRtcService.registerStatsInterval(consumer, (statsReport) => {
          let packetLoss = 0;
          let rtt = 0;
          let bytesReceived = 0;

          statsReport.forEach((report) => {
            if (report.type === "inbound-rtp" && report.kind === "video") {
              packetLoss = report.fractionLost || 0;
              bytesReceived = report.bytesReceived || 0;
            }

            if (report.type === "remote-outbound-rtp") {
              rtt = report.roundTripTime || 0;
            }
          });

          let targetLayer;
          if (packetLoss > 0.10 || rtt > 0.3) {
            targetLayer = 0; // 240p
          } else if (packetLoss > 0.03 || rtt > 0.15) {
            targetLayer = 1; // 480p
          } else {
            targetLayer = 2; // 720p
          }

          if (consumer.appData.currentLayer !== targetLayer) {
            consumer.setPreferredLayers({ spatialLayer: targetLayer, temporalLayer: 2 });
            consumer.appData.currentLayer = targetLayer;
            log(`[${instanceId}] Adaptive Switching: Consumer ${consumerId} moved to layer ${targetLayer}`, { packetLoss, rtt, bytesReceived });
          }
        });
      }
    } catch (error) {
      log(`[${instanceId}] Mediasoup: consume failed`, error);
    }
  };

  const initJoin = async (active: boolean) => {
    if (joiningRef.current) return;

    try {
      if (!active) return;
      if (joinedCreatorRef.current === creatorId) return;

      joiningRef.current = true;
      log(`[${instanceId}] JOIN Flow: Starting Mediasoup Consume sequence`);

      const joinRes = await webRtcService.sendRequest(SignalingType.JOIN, room.streamRoomId, {
        streamId: streamId
      });
      if (!active) return;
      
      const { routerRtpCapabilities, producers: existingProducers } = joinRes;

      const device = await webRtcService.initDevice(routerRtpCapabilities);
      if (!active) return;

      const transportData = await webRtcService.sendRequest(SignalingType.CREATE_TRANSPORT, room.streamRoomId, {
        direction: 'recv'
      });
      if (!active) return;

      const transport = device.createRecvTransport(transportData);
      if (!active) {
        transport.close();
        return;
      }
      recvTransport.current = transport;

      transport.on('connect', async ({ dtlsParameters }, callback, errback) => {
        try {
          if (!active) return;
          await webRtcService.sendRequest(SignalingType.CONNECT_TRANSPORT, room.streamRoomId, {
            transportId: transport.id,
            dtlsParameters
          });
          callback();
        } catch (error: any) {
          errback(error);
        }
      });

      const attemptIceRestart = async () => {
        if (!active || transport.closed) return;

        if (!transport.appData.restartAttempts) {
          transport.appData.restartAttempts = 0;
        }

        if ((transport.appData.restartAttempts as number) >= 3) {
          log(`[${instanceId}] RecvTransport: Max ICE restart attempts reached, stopping.`);
          return;
        }

        transport.appData.restartAttempts =
          (transport.appData.restartAttempts ?? 0) + 1;
        const attempt = transport.appData.restartAttempts;

        try {
          // Add 3-6 second randomized delay between restart attempts to prevent signaling storms
          const jitterDelay = Math.floor(Math.random() * 3000) + 3000;
          await new Promise((resolve) => setTimeout(resolve, jitterDelay));
          if (!active || transport.closed) return;

          log(`[${instanceId}] RecvTransport: Initiating ICE restart (attempt ${attempt}) after ${jitterDelay}ms delay...`);

          const response = await webRtcService.sendRequest(SignalingType.RESTART_ICE, room.streamRoomId, {
            transportId: transport.id
          });

          await transport.restartIce({
            iceParameters: response.iceParameters
          });
          log(`[${instanceId}] RecvTransport: ICE restart success, gathering candidates...`);
        } catch (error) {
          log(`[${instanceId}] RecvTransport: ICE restart failed`, error);
        }
      };

      transport.on('connectionstatechange', async (state) => {
        if (state === 'failed') {
          log(`[${instanceId}] RecvTransport: connection failed, initiating immediate restart...`);
          attemptIceRestart();
        } else if (state === 'disconnected') {
          const jitterDelay = Math.floor(Math.random() * 3000) + 3000;
          log(`[${instanceId}] RecvTransport: connection disconnected, waiting ${jitterDelay}ms before restart check...`);
          setTimeout(() => {
            if (transport.connectionState === 'disconnected') {
              log(`[${instanceId}] RecvTransport: Still disconnected after ${jitterDelay}ms, initiating restart...`);
              attemptIceRestart();
            } else {
              log(`[${instanceId}] RecvTransport: Recovered from disconnected state automatically.`);
            }
          }, jitterDelay);
        }
      });

      if (deferredProducersRef.current.length > 0) {
        const toProcess = [...deferredProducersRef.current];
        deferredProducersRef.current = [];
        for (const p of toProcess) {
          await consumeProducer(p, true, 'deferred_replay', active);
        }
      }

      if (existingProducers && Array.isArray(existingProducers)) {
        for (const producer of existingProducers) {
          await consumeProducer({ id: producer.id, kind: producer.kind || 'video' }, false, 'existing_producers', active);
        }
      }

      joinedCreatorRef.current = creatorId || null;
      setLoading(false);
    } catch (err) {
      log(`[${instanceId}] JOIN Flow: ERROR occurred (Mediasoup)`, err);
      setError("Failed to connect to stream.");
    } finally {
      joiningRef.current = false;
    }
  };

  const initSignalingAndJoin = async (active: boolean) => {
    if (!room?.streamRoomId) return;
    
    try {
      if (!webSocketService.isConnected()) {
        await webSocketService.waitForConnection();
        if (!active) return;
      }

      const errorsUnsub = webSocketService.subscribe('/user/queue/errors', (msg) => {
        try {
          const signal: SignalingMessage = JSON.parse(msg.body);
          if (signal.type === 'ERROR') {
            const errorCode = signal.sdp;
            if (errorCode === 'PAID_ACCESS_REQUIRED') {
              setHasAccess(false);
            } else if (errorCode === 'STREAM_OFFLINE') {
              setAvailability('OFFLINE');
            } else if (errorCode === 'ACCESS_DENIED') {
              setError("Access denied. Please check your permissions.");
            } else if (errorCode === 'INSUFFICIENT_TOKENS') {
              setError("Insufficient tokens to watch this stream.");
            } else {
              setError(errorCode || "A signaling error occurred.");
            }
            setLoading(false);
          }
        } catch (e) { log("Error parsing error message", e); }
      });

      webRtcService.setCurrentUserId(Number(user?.id));
      
      await webRtcService.connect(room.streamRoomId, (signal) => {
        if (signal.type === SignalingType.NEW_PRODUCER || signal.type === 'NEW_PRODUCER') {
          if (!signal.data?.producerId) return;
          consumeProducer(signal.data, false, 'NEW_PRODUCER_signal', active);
        }
      });

      if (!active) return;

      errorsUnsubRef.current = () => {
        errorsUnsub();
        errorsUnsubRef.current = null;
      };

      signalingUnsubRef.current = () => {
        webRtcService.leaveStream();
        signalingUnsubRef.current = null;
      };
      
      initJoin(active);
    } catch (err) {
      log("Signaling: Initialization failed", err);
    }
  };

  useEffect(() => {
    let active = true;
    
    if (availability === null || error) return;

    if (joinedCreatorRef.current !== creatorId) {
      joinedCreatorRef.current = null;
    }

    if (availability === "OFFLINE") {
      performWebRTCCleanup();
    }

    if (availability !== "LIVE" || !creatorId || !user || hasAccess !== true || error) {
      if (recvTransport.current && (availability !== "LIVE" || hasAccess !== true || error)) {
        performWebRTCCleanup();
      }
      hasInitializedRef.current = false;
      prevHasAccessRef.current = hasAccess;
      return;
    }

    const transitionToTrue = prevHasAccessRef.current === false && hasAccess === true;
    const sessionChanged = activeSessionIdRef.current !== streamId;
    const creatorChanged = joinedCreatorRef.current !== creatorId;

    if (hasInitializedRef.current && !transitionToTrue && !sessionChanged && !creatorChanged) {
      prevHasAccessRef.current = hasAccess;
      return;
    }

    hasInitializedRef.current = true;
    activeSessionIdRef.current = streamId;
    prevHasAccessRef.current = hasAccess;

    void initSignalingAndJoin(active);

    return () => {
      active = false;
      joiningRef.current = false;
      if (signalingUnsubRef.current) signalingUnsubRef.current();
      if (errorsUnsubRef.current) errorsUnsubRef.current();

      const isActuallyChanging = (joinedCreatorRef.current !== creatorId) || 
                                 (activeSessionIdRef.current !== streamId) || 
                                 (availability !== "LIVE") || 
                                 (error !== null);
      
      if (isActuallyChanging) {
        performWebRTCCleanup();
      }
    };
  }, [availability, creatorId, hasAccess, error, room, streamId, user?.id]);

  useEffect(() => {
    return () => {
      performWebRTCCleanup();
      webRtcService.cleanupStatsIntervals();
      webRtcService.leaveStream();
    };
  }, [instanceId]);

  return {
    recvTransport,
    consumers,
    remoteStreamRef,
    performWebRTCCleanup
  };
}
