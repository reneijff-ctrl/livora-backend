export interface Language {
  code: string;
  label: string;
}

export const LANGUAGES: Language[] = [
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Spanish' },
  { code: 'fr', label: 'French' },
  { code: 'de', label: 'German' },
  { code: 'pt', label: 'Portuguese' },
  { code: 'it', label: 'Italian' },
  { code: 'nl', label: 'Dutch' },
  { code: 'ru', label: 'Russian' },
  { code: 'ja', label: 'Japanese' },
  { code: 'ko', label: 'Korean' },
  { code: 'zh', label: 'Chinese' },
  { code: 'ar', label: 'Arabic' },
  { code: 'hi', label: 'Hindi' },
  { code: 'bn', label: 'Bengali' },
  { code: 'pl', label: 'Polish' },
  { code: 'tr', label: 'Turkish' },
  { code: 'vi', label: 'Vietnamese' },
  { code: 'th', label: 'Thai' },
  { code: 'sv', label: 'Swedish' },
  { code: 'no', label: 'Norwegian' },
  { code: 'da', label: 'Danish' },
  { code: 'fi', label: 'Finnish' },
  { code: 'el', label: 'Greek' },
  { code: 'cs', label: 'Czech' },
  { code: 'ro', label: 'Romanian' },
  { code: 'hu', label: 'Hungarian' },
  { code: 'uk', label: 'Ukrainian' },
  { code: 'he', label: 'Hebrew' },
  { code: 'id', label: 'Indonesian' },
  { code: 'ms', label: 'Malay' },
  { code: 'tl', label: 'Filipino' },
  { code: 'sr', label: 'Serbian' },
  { code: 'hr', label: 'Croatian' },
  { code: 'bg', label: 'Bulgarian' },
  { code: 'sk', label: 'Slovak' },
  { code: 'sl', label: 'Slovenian' },
  { code: 'lt', label: 'Lithuanian' },
  { code: 'lv', label: 'Latvian' },
  { code: 'et', label: 'Estonian' },
  { code: 'fa', label: 'Persian' },
  { code: 'ur', label: 'Urdu' },
  { code: 'sw', label: 'Swahili' },
  { code: 'af', label: 'Afrikaans' },
  { code: 'ca', label: 'Catalan' },
  { code: 'ga', label: 'Irish' },
];

export function getLanguageLabel(code: string): string {
  return LANGUAGES.find(l => l.code === code)?.label ?? code;
}

export function getLanguagesByCode(codes: string[]): Language[] {
  return codes
    .map(code => LANGUAGES.find(l => l.code === code))
    .filter((l): l is Language => l !== undefined);
}
