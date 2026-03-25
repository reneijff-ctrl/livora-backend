/**
 * SpatialSoundEngine - A high-performance spatial audio engine using Web Audio API.
 * Provides singleton access, preloading, volume control, and stereo panning.
 */
export class SpatialSoundEngine {
  private static instance: SpatialSoundEngine;
  private context: AudioContext | null = null;
  private buffers: Map<string, AudioBuffer> = new Map();
  private masterGain: GainNode | null = null;
  private globalVolume: number = 0.5;
  private isMuted: boolean = false;
  private currentProfile: string = "cinematic";
  private initialized: boolean = false;

  private readonly SOUND_BANKS: Record<string, Record<string, string>> = {
    cinematic: {
      common: '/sounds/soft_hit.mp3',
      rare: '/sounds/coin.mp3',
      epic: '/sounds/firework.mp3',
      legendary: '/sounds/deep_boom.mp3'
    },
    arcade: {
      common: '/sounds/coin8bit.mp3',
      rare: '/sounds/coin.mp3',
      epic: '/sounds/firework.mp3',
      legendary: '/sounds/powerup.mp3'
    },
    minimal: {
      common: '/sounds/small.mp3',
      rare: '/sounds/coin.mp3',
      epic: '/sounds/firework.mp3',
      legendary: '/sounds/legendary.mp3'
    },
    luxury: {
      common: '/sounds/small.mp3',
      rare: '/sounds/coin.mp3',
      epic: '/sounds/firework.mp3',
      legendary: '/sounds/legendary.mp3'
    }
  };

  private readonly SOUND_CONFIG: Record<string, string> = {
    'tip': '/sounds/tip.mp3',
    'gift': '/sounds/gift.mp3',
    'unlock': '/sounds/unlock.mp3',
    'notification': '/sounds/notification.mp3',
    'success': '/sounds/success.mp3',
    'error': '/sounds/error.mp3',
    'small-hearts': '/sounds/small.mp3',
    'floatHearts': '/sounds/small.mp3',
    'golden-coin-burst': '/sounds/coin.mp3',
    'fireworks': '/sounds/firework.mp3',
    'mega-explosion': '/sounds/legendary.mp3',
    'goldenRain': '/sounds/coin.mp3',
    'cosmicStorm': '/sounds/legendary.mp3',
    'meteorFall': '/sounds/legendary.mp3',
    'goldPulseBorder': '/sounds/gift.mp3',
    // Global defaults for rarities
    'common': '/sounds/small.mp3',
    'rare': '/sounds/coin.mp3',
    'epic': '/sounds/firework.mp3',
    'legendary': '/sounds/legendary.mp3'
  };

  private static readonly LS_VOLUME_KEY = 'livora-audio-volume';
  private static readonly LS_MUTED_KEY = 'livora-audio-muted';

  private constructor() {
    if (typeof window !== 'undefined') {
      this.restorePreferences();
      this.init();
      this.setupAutoResume();
    }
  }

  private restorePreferences() {
    try {
      const savedVolume = localStorage.getItem(SpatialSoundEngine.LS_VOLUME_KEY);
      if (savedVolume !== null) {
        this.globalVolume = Math.max(0, Math.min(1, parseFloat(savedVolume)));
      }
      const savedMuted = localStorage.getItem(SpatialSoundEngine.LS_MUTED_KEY);
      if (savedMuted !== null) {
        this.isMuted = savedMuted === 'true';
      }
    } catch (_) { /* localStorage unavailable */ }
  }

  private persistPreferences() {
    try {
      localStorage.setItem(SpatialSoundEngine.LS_VOLUME_KEY, String(this.globalVolume));
      localStorage.setItem(SpatialSoundEngine.LS_MUTED_KEY, String(this.isMuted));
    } catch (_) { /* localStorage unavailable */ }
  }

  public static getInstance(): SpatialSoundEngine {
    if (!SpatialSoundEngine.instance) {
      SpatialSoundEngine.instance = new SpatialSoundEngine();
    }
    return SpatialSoundEngine.instance;
  }

  private init() {
    try {
      this.context = new (window.AudioContext || (window as any).webkitAudioContext)();
      this.masterGain = this.context.createGain();
      this.masterGain.gain.value = this.isMuted ? 0 : this.globalVolume;
      this.masterGain.connect(this.context.destination);
      this.preloadAll();
      this.initialized = true;
    } catch (e) {
      console.warn('SpatialSoundEngine: Web Audio API not supported', e);
    }
  }

  private setupAutoResume() {
    const resume = () => {
      if (this.context && this.context.state === 'suspended') {
        this.context.resume();
      }
      // Remove listeners once resumed successfully
      if (this.context && this.context.state === 'running') {
        window.removeEventListener('click', resume);
        window.removeEventListener('keydown', resume);
        window.removeEventListener('pointerdown', resume);
      }
    };

    window.addEventListener('click', resume);
    window.addEventListener('keydown', resume);
    window.addEventListener('pointerdown', resume);
  }

  private async preloadAll() {
    // Preload global sounds
    const globalLoadPromises = Object.entries(this.SOUND_CONFIG).map(async ([name, path]) => {
      try {
        const response = await fetch(path);
        const arrayBuffer = await response.arrayBuffer();
        if (this.context) {
          const audioBuffer = await this.context.decodeAudioData(arrayBuffer);
          this.buffers.set(name, audioBuffer);
        }
      } catch (err) {
        console.warn(`SpatialSoundEngine: Failed to load sound "${name}" from ${path}`, err);
      }
    });

    // Preload bank sounds
    const bankLoadPromises: Promise<void>[] = [];
    Object.entries(this.SOUND_BANKS).forEach(([profile, bank]) => {
      Object.entries(bank).forEach(([rarity, path]) => {
        bankLoadPromises.push((async () => {
          try {
            const response = await fetch(path);
            const arrayBuffer = await response.arrayBuffer();
            if (this.context) {
              const audioBuffer = await this.context.decodeAudioData(arrayBuffer);
              this.buffers.set(`${profile}-${rarity}`, audioBuffer);
            }
          } catch (err) {
            console.warn(`SpatialSoundEngine: Failed to load bank sound "${profile}-${rarity}" from ${path}`, err);
          }
        })());
      });
    });

    await Promise.all([...globalLoadPromises, ...bankLoadPromises]);
  }

  /**
   * Sets the active sound profile.
   * @param profile - Profile name (cinematic, arcade, etc.)
   */
  public setProfile(profile: string) {
    this.currentProfile = profile;
  }

  /**
   * Plays a sound with optional spatial panning.
   * @param soundName - Name of the sound or rarity to play.
   * @param pan - Panning value from -1 (left) to 1 (right). Default is 0 (center).
   */
  public play(soundName: string, pan: number = 0) {
    if (!this.initialized || !this.context || !this.masterGain) return;

    // Priority: 1. Profile-based bank sound, 2. Global soundName
    const bankKey = `${this.currentProfile}-${soundName}`;
    const buffer = this.buffers.get(bankKey) || this.buffers.get(soundName);

    if (!buffer) {
      console.warn(`SpatialSoundEngine: Sound "${soundName}" (profile: ${this.currentProfile}) not found.`);
      return;
    }

    // Auto-resume if needed (fallback for some browsers)
    if (this.context.state === 'suspended') {
      this.context.resume();
    }

    const source = this.context.createBufferSource();
    source.buffer = buffer;

    try {
      // Use StereoPannerNode with clamped value between -1 and 1
      const panner = this.context.createStereoPanner();
      panner.pan.value = Math.max(-1, Math.min(1, pan));

      source.connect(panner);
      panner.connect(this.masterGain);
    } catch (e) {
      // Fallback for browsers without StereoPannerNode support
      source.connect(this.masterGain);
    }
    
    source.start(0);
  }

  /**
   * Sets the global volume.
   * @param value - Volume from 0 to 1.
   */
  public setVolume(value: number) {
    this.globalVolume = Math.max(0, Math.min(1, value));
    if (this.masterGain && !this.isMuted) {
      this.masterGain.gain.setTargetAtTime(this.globalVolume, this.context?.currentTime || 0, 0.1);
    }
    this.persistPreferences();
  }

  /**
   * Mutes or unmutes the audio engine.
   * @param mute - True to mute, false to unmute.
   */
  public mute(mute: boolean) {
    this.isMuted = mute;
    if (this.masterGain) {
      const targetVolume = mute ? 0 : this.globalVolume;
      this.masterGain.gain.setTargetAtTime(targetVolume, this.context?.currentTime || 0, 0.1);
    }
    this.persistPreferences();
  }

  public getIsMuted(): boolean {
    return this.isMuted;
  }

  public getVolume(): number {
    return this.globalVolume;
  }
}

export const spatialSoundEngine = SpatialSoundEngine.getInstance();
