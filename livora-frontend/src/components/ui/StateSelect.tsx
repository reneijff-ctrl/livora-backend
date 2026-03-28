import React, { useState, useRef, useEffect, useMemo } from 'react';
import { getStatesForCountry } from '../../data/states';

interface StateSelectProps {
  countryCode: string;
  value: string;
  onChange: (state: string) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}

const StateSelect: React.FC<StateSelectProps> = ({
  countryCode,
  value,
  onChange,
  placeholder = 'Select state/province...',
  style,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const states = useMemo(() => getStatesForCountry(countryCode), [countryCode]);

  const filtered = useMemo(() => {
    if (!search) return states;
    const lower = search.toLowerCase();
    return states.filter(s => s.toLowerCase().includes(lower));
  }, [search, states]);

  const disabled = !countryCode || states.length === 0;

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

  const handleSelect = (state: string) => {
    onChange(state);
    setIsOpen(false);
    setSearch('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return;

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
          handleSelect(filtered[highlightedIndex]);
        }
        break;
      case 'Escape':
        setIsOpen(false);
        setSearch('');
        break;
    }
  };

  return (
    <div ref={containerRef} style={{ position: 'relative', ...style }}>
      <div
        style={{
          ...styles.trigger,
          opacity: disabled ? 0.5 : 1,
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
        onClick={() => {
          if (disabled) return;
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
            placeholder="Search..."
            style={styles.searchInput}
          />
        ) : (
          <span style={{ color: value ? '#F4F4F5' : '#71717A' }}>
            {disabled
              ? (countryCode ? 'No states available' : 'Select country first')
              : (value || placeholder)}
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

      {isOpen && !disabled && (
        <ul ref={listRef} style={styles.dropdown}>
          {filtered.length === 0 ? (
            <li style={styles.noResults}>No states found</li>
          ) : (
            filtered.map((state, index) => (
              <li
                key={state}
                onClick={() => handleSelect(state)}
                onMouseEnter={() => setHighlightedIndex(index)}
                style={{
                  ...styles.option,
                  backgroundColor:
                    index === highlightedIndex
                      ? 'rgba(99, 102, 241, 0.15)'
                      : state === value
                      ? 'rgba(99, 102, 241, 0.08)'
                      : 'transparent',
                }}
              >
                {state}
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
    padding: '0.625rem 1rem',
    cursor: 'pointer',
    borderRadius: '8px',
    color: '#F4F4F5',
    fontSize: '0.95rem',
    transition: 'background-color 0.15s',
  },
  noResults: {
    padding: '1rem',
    textAlign: 'center',
    color: '#71717A',
    fontSize: '0.9rem',
  },
};

export default StateSelect;
