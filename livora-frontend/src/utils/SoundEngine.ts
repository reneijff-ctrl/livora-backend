/**
 * SoundEngine - A high-performance singleton for audio management in the frontend.
 * This class handles preloading, volume control, and playback of common app sounds
 * without triggering React re-renders or creating unnecessary DOM elements.
 */
export class SoundEngine {
  private static instance: SoundEngine;
  private sounds: Map<string, HTMLAudioElement> = new Map();
  private globalVolume: number = 0.5;
  private currentProfile: string = "cinematic";

  /**
   * Sound banks for different profiles.
   */
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

  /**
   * Default sound names and their corresponding public paths.
   * Add new sound files to public/sounds/ and register them here.
   */
  private readonly SOUND_CONFIG: Record<string, string> = {
    'tip': '/sounds/tip.mp3',
    'gift': '/sounds/gift.mp3',
    'unlock': '/sounds/unlock.mp3',
    'notification': '/sounds/notification.mp3',
    'success': '/sounds/success.mp3',
    'error': '/sounds/error.mp3',
    // Animation specific sounds
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

  private constructor() {
    this.preload();
  }

  /**
   * Returns the single instance of the SoundEngine.
   */
  public static getInstance(): SoundEngine {
    if (!SoundEngine.instance) {
      SoundEngine.instance = new SoundEngine();
    }
    return SoundEngine.instance;
  }

  /**
   * Pre-initializes all audio elements for rapid playback.
   */
  private preload() {
    if (typeof window === 'undefined') return; // Guard for SSR contexts

    // Preload global sounds
    Object.entries(this.SOUND_CONFIG).forEach(([name, path]) => {
      try {
        const audio = new Audio(path);
        audio.preload = 'auto';
        audio.volume = this.globalVolume;
        this.sounds.set(name, audio);
      } catch (err) {
        console.warn(`SoundEngine: Failed to initialize sound "${name}" at ${path}`, err);
      }
    });

    // Preload bank sounds
    Object.entries(this.SOUND_BANKS).forEach(([profile, bank]) => {
      Object.entries(bank).forEach(([rarity, path]) => {
        try {
          const audio = new Audio(path);
          audio.preload = 'auto';
          audio.volume = this.globalVolume;
          this.sounds.set(`${profile}-${rarity}`, audio);
        } catch (err) {
          console.warn(`SoundEngine: Failed to initialize bank sound "${profile}-${rarity}" at ${path}`, err);
        }
      });
    });
  }

  /**
   * Sets the active sound profile.
   */
  public setProfile(profile: string) {
    this.currentProfile = profile;
  }

  /**
   * Plays a registered sound from the beginning.
   * Resets the playback head if the sound is already playing.
   * 
   * @param soundName - The key name of the sound or rarity to play (e.g. 'tip', 'gift').
   */
  public play(soundName: string) {
    // Priority: 1. Profile-based bank sound, 2. Global soundName
    const bankKey = `${this.currentProfile}-${soundName}`;
    const audio = this.sounds.get(bankKey) || this.sounds.get(soundName);
    
    if (!audio) {
      console.warn(`SoundEngine: Sound "${soundName}" (profile: ${this.currentProfile}) not found.`);
      return;
    }

    try {
      // Reset playhead to start for immediate replay
      audio.currentTime = 0;
      
      // Attempt playback
      // Note: Browsers may block this until the first user interaction (click/keydown).
      const playPromise = audio.play();

      if (playPromise !== undefined) {
        playPromise.catch(error => {
          // We suppress the "NotAllowedError" as it's a standard browser policy
          if (error.name !== 'NotAllowedError') {
            console.warn(`SoundEngine: Playback of "${soundName}" failed:`, error.message);
          }
        });
      }
    } catch (err) {
      console.warn(`SoundEngine: Unexpected error playing "${soundName}":`, err);
    }
  }

  /**
   * Globally updates the volume for all current and future sounds.
   * 
   * @param volume - A value between 0.0 (silent) and 1.0 (max volume).
   */
  public setVolume(volume: number) {
    this.globalVolume = Math.max(0, Math.min(1, volume));
    
    this.sounds.forEach(audio => {
      audio.volume = this.globalVolume;
    });
  }

  /**
   * Retrieves the current global volume setting.
   */
  public getVolume(): number {
    return this.globalVolume;
  }

  /**
   * Manually reloads all audio files if necessary.
   */
  public refresh() {
    this.sounds.clear();
    this.preload();
  }
}

/**
 * Export the singleton instance for application-wide use.
 */
export const soundEngine = SoundEngine.getInstance();
