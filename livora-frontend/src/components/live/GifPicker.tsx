import React, { useState, useEffect, useCallback, useRef } from 'react';

interface Gif {
  id: string;
  images: {
    fixed_height_small: {
      url: string;
    };
    original: {
      url: string;
    };
  };
  title: string;
}

interface GifPickerProps {
  onSelect: (url: string) => void;
  onClose: () => void;
}

const GIPHY_API_KEY = import.meta.env.VITE_GIPHY_API_KEY || 'dc6zaTOxFJmzC'; // Fallback to public beta key

const GifPicker: React.FC<GifPickerProps> = ({ onSelect, onClose }) => {
  const [gifs, setGifs] = useState<Gif[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const fetchGifs = useCallback(async (query: string) => {
    setLoading(true);
    try {
      const endpoint = query 
        ? `https://api.giphy.com/v1/gifs/search?api_key=${GIPHY_API_KEY}&q=${encodeURIComponent(query)}&limit=20&rating=g`
        : `https://api.giphy.com/v1/gifs/trending?api_key=${GIPHY_API_KEY}&limit=20&rating=g`;
      
      const response = await fetch(endpoint);
      const data = await response.json();
      setGifs(data.data || []);
    } catch (error) {
      console.error('Error fetching GIFs:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchGifs(search);
    }, 500);
    return () => clearTimeout(timer);
  }, [search, fetchGifs]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClose]);

  return (
    <div 
      ref={containerRef}
      className="absolute bottom-full left-0 mb-2 w-72 h-96 bg-[#08080A] border border-[#16161D] rounded-xl shadow-2xl flex flex-col overflow-hidden z-50 animate-in fade-in slide-in-from-bottom-2 duration-200"
    >
      <div className="p-3 border-b border-[#16161D]">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search GIFs..."
          className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-xs text-white outline-none focus:ring-1 focus:ring-zinc-500"
          autoFocus
        />
      </div>
      
      <div className="flex-1 overflow-y-auto p-2 scrollbar-hide">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <div className="w-5 h-5 border-2 border-white/20 border-t-white rounded-full animate-spin" />
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            {gifs.map((gif) => (
              <button
                key={gif.id}
                onClick={() => onSelect(gif.images.original.url)}
                className="relative aspect-video bg-white/5 rounded-md overflow-hidden hover:scale-105 transition-transform active:scale-95 group"
              >
                <img
                  src={gif.images.fixed_height_small.url}
                  alt={gif.title}
                  className="w-full h-full object-cover"
                  loading="lazy"
                />
                <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity" />
              </button>
            ))}
          </div>
        )}
        {!loading && gifs.length === 0 && (
          <div className="text-center py-10 text-zinc-500 text-xs">
            No GIFs found
          </div>
        )}
      </div>
    </div>
  );
};

export default GifPicker;
