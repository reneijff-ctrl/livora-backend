import React, { useState, useRef, useEffect, useMemo } from 'react';
import { COUNTRIES, getCountryLabel } from '../../data/countries';

interface CountrySelectProps {
  value: string;
  onChange: (code: string) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}

const CountrySelect: React.FC<CountrySelectProps> = ({
  value,
  onChange,
  placeholder = 'Select country...',
  style,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const filtered = useMemo(() => {
    if (!search) return COUNTRIES;
    const lower = search.toLowerCase();
    return COUNTRIES.filter(
      c => c.label.toLowerCase().includes(lower) || c.code.toLowerCase().includes(lower)
    );
  }, [search]);

  useEffect(() => {
    setHighlightedIndex(0);
  }, [filtered]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setSearch('');
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (isOpen && listRef.current) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement;
      if (item) {
        item.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [highlightedIndex, isOpen]);

  const handleSelect = (code: string) => {
    onChange(code);
    setIsOpen(false);
    setSearch('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!isOpen) {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setIsOpen(true);
        return;
      }
    }

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setHighlightedIndex(prev => Math.min(prev + 1, filtered.length - 1));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setHighlightedIndex(prev => Math.max(prev - 1, 0));
        break;
      case 'Enter':
        e.preventDefault();
        if (filtered[highlightedIndex]) {
          handleSelect(filtered[highlightedIndex].code);
        }
        break;
      case 'Escape':
        setIsOpen(false);
        setSearch('');
        break;
    }
  };

  const displayValue = value ? getCountryLabel(value) : '';

  return (
    <div ref={containerRef} style={{ position: 'relative', ...style }}>
      <div
        style={styles.trigger}
        onClick={() => {
          setIsOpen(!isOpen);
          if (!isOpen) {
            setTimeout(() => inputRef.current?.focus(), 0);
          }
        }}
      >
        {isOpen ? (
          <input
            ref={inputRef}
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={displayValue || placeholder}
            style={styles.searchInput}
            autoFocus
          />
        ) : (
          <span style={{ color: displayValue ? '#F4F4F5' : '#71717A' }}>
            {displayValue || placeholder}
          </span>
        )}
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="#A1A1AA"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{
            flexShrink: 0,
            transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
            transition: 'transform 0.2s',
          }}
        >
          <path d="M6 9l6 6 6-6" />
        </svg>
      </div>

      {isOpen && (
        <ul ref={listRef} style={styles.dropdown}>
          {filtered.length === 0 ? (
            <li style={styles.noResults}>No countries found</li>
          ) : (
            filtered.map((country, index) => (
              <li
                key={country.code}
                onClick={() => handleSelect(country.code)}
                onMouseEnter={() => setHighlightedIndex(index)}
                style={{
                  ...styles.option,
                  backgroundColor:
                    index === highlightedIndex
                      ? 'rgba(99, 102, 241, 0.15)'
                      : country.code === value
                      ? 'rgba(99, 102, 241, 0.08)'
                      : 'transparent',
                }}
              >
                <span>{country.label}</span>
                <span style={styles.countryCode}>{country.code}</span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  trigger: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0.875rem 1.25rem',
    backgroundColor: '#08080A',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    color: '#F4F4F5',
    fontSize: '1rem',
    cursor: 'pointer',
    width: '100%',
    boxSizing: 'border-box',
    minHeight: '3.25rem',
  },
  searchInput: {
    background: 'none',
    border: 'none',
    outline: 'none',
    color: '#F4F4F5',
    fontSize: '1rem',
    width: '100%',
    padding: 0,
  },
  dropdown: {
    position: 'absolute',
    top: '100%',
    left: 0,
    right: 0,
    marginTop: '4px',
    backgroundColor: '#18181B',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    maxHeight: '240px',
    overflowY: 'auto',
    zIndex: 50,
    listStyle: 'none',
    padding: '4px',
    margin: 0,
  },
  option: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0.625rem 1rem',
    cursor: 'pointer',
    borderRadius: '8px',
    color: '#F4F4F5',
    fontSize: '0.95rem',
    transition: 'background-color 0.15s',
  },
  countryCode: {
    fontSize: '0.8rem',
    color: '#71717A',
    fontFamily: 'monospace',
  },
  noResults: {
    padding: '1rem',
    textAlign: 'center',
    color: '#71717A',
    fontSize: '0.9rem',
  },
};

export default CountrySelect;
