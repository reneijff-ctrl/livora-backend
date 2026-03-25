import React, { useState, useRef, useEffect } from 'react';

interface CountryDropdownProps {
  value: string;
  onChange: (value: string) => void;
}

const CountryDropdown: React.FC<CountryDropdownProps> = ({ value, onChange }) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const options = ["ALL", "NL", "DE", "US", "UK", "ES"];

  // Click outside to close
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = (option: string) => {
    onChange(option);
    setIsOpen(false);
  };

  const getCountryName = (code: string) => {
    switch (code) {
      case 'ALL': return 'All Countries';
      case 'NL': return 'Netherlands';
      case 'DE': return 'Germany';
      case 'US': return 'United States';
      case 'UK': return 'United Kingdom';
      case 'ES': return 'Spain';
      default: return code;
    }
  };

  return (
    <div className="relative w-full md:w-1/4" ref={dropdownRef}>
      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="
          w-full
          flex items-center justify-between
          px-4 py-3
          rounded-xl
          bg-[#121218]
          border border-white/10
          text-white
          text-sm
          transition-all
          duration-300
          hover:border-white/20
          focus:outline-none
          focus:border-indigo-500
          focus:ring-2 focus:ring-indigo-500/30
          shadow-lg
          backdrop-blur-md
        "
      >
        <span className={!value || value === "ALL" ? "text-white/40" : "text-white"}>
          {getCountryName(value)}
        </span>
        <svg
          className={`w-4 h-4 text-white/40 transition-transform duration-300 ${isOpen ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Dropdown Panel */}
      <div
        className={`
          absolute
          z-50
          mt-2
          w-full
          bg-[#121218]
          border border-white/10
          rounded-xl
          overflow-hidden
          shadow-[0_10px_40px_rgba(0,0,0,0.6)]
          transition-all
          duration-200
          origin-top
          ${isOpen ? 'opacity-100 scale-y-100 visible' : 'opacity-0 scale-y-95 invisible pointer-events-none'}
        `}
      >
        <div className="py-1">
          {options.map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => handleSelect(option)}
              className={`
                w-full
                text-left
                px-4 py-3
                text-sm
                transition-colors
                duration-200
                hover:bg-indigo-600/20
                ${value === option ? "text-indigo-400 bg-white/5" : "text-white/70"}
              `}
            >
              {getCountryName(option)}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

export default CountryDropdown;
