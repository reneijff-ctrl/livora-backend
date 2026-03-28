import React, { useState, useRef, useEffect, useMemo } from 'react';
import { LANGUAGES, getLanguageLabel } from '../../data/languages';

interface LanguageSelectProps {
  value: string[];
  onChange: (codes: string[]) => void;
  placeholder?: string;
  style?: React.CSSProperties;
}

const LanguageSelect: React.FC<LanguageSelectProps> = ({
  value,
  onChange,
  placeholder = 'Select languages...',
  style,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const filtered = useMemo(() => {
    const available = LANGUAGES.filter(l => !value.includes(l.code));
    if (!search) return available;
    const lower = search.toLowerCase();
    return available.filter(
      l => l.label.toLowerCase().includes(lower) || l.code.toLowerCase().includes(lower)
    );
  }, [search, value]);

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
    onChange([...value, code]);
    setSearch('');
    setTimeout(() => inputRef.current?.focus(), 0);
  };

  const handleRemove = (code: string) => {
    onChange(value.filter(c => c !== code));
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !search && value.length > 0) {
      onChange(value.slice(0, -1));
      return;
    }

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

  return (
    <div ref={containerRef} style={{ position: 'relative', ...style }}>
      <div
        style={styles.trigger}
        onClick={() => {
          setIsOpen(true);
          setTimeout(() => inputRef.current?.focus(), 0);
        }}
      >
        <div style={styles.tagsContainer}>
          {value.map(code => (
            <span key={code} style={styles.tag}>
              {getLanguageLabel(code)}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleRemove(code);
                }}
                style={styles.tagRemove}
              >
                ×
              </button>
            </span>
          ))}
          <input
            ref={inputRef}
            type="text"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              if (!isOpen) setIsOpen(true);
            }}
            onKeyDown={handleKeyDown}
            placeholder={value.length === 0 ? placeholder : ''}
            style={styles.searchInput}
          />
        </div>
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
            <li style={styles.noResults}>
              {value.length === LANGUAGES.length ? 'All languages selected' : 'No languages found'}
            </li>
          ) : (
            filtered.map((lang, index) => (
              <li
                key={lang.code}
                onClick={() => handleSelect(lang.code)}
                onMouseEnter={() => setHighlightedIndex(index)}
                style={{
                  ...styles.option,
                  backgroundColor:
                    index === highlightedIndex
                      ? 'rgba(99, 102, 241, 0.15)'
                      : 'transparent',
                }}
              >
                <span>{lang.label}</span>
                <span style={styles.langCode}>{lang.code}</span>
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
    padding: '0.5rem 1rem',
    backgroundColor: '#08080A',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    color: '#F4F4F5',
    fontSize: '1rem',
    cursor: 'pointer',
    width: '100%',
    boxSizing: 'border-box',
    minHeight: '3.25rem',
    gap: '8px',
  },
  tagsContainer: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '4px',
    flex: 1,
    alignItems: 'center',
  },
  tag: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    padding: '2px 8px',
    backgroundColor: 'rgba(99, 102, 241, 0.2)',
    border: '1px solid rgba(99, 102, 241, 0.3)',
    borderRadius: '6px',
    fontSize: '0.85rem',
    color: '#C7D2FE',
    whiteSpace: 'nowrap',
  },
  tagRemove: {
    background: 'none',
    border: 'none',
    color: '#A5B4FC',
    cursor: 'pointer',
    fontSize: '1rem',
    lineHeight: 1,
    padding: '0 2px',
  },
  searchInput: {
    background: 'none',
    border: 'none',
    outline: 'none',
    color: '#F4F4F5',
    fontSize: '1rem',
    flex: 1,
    minWidth: '60px',
    padding: '4px 0',
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
  langCode: {
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

export default LanguageSelect;
