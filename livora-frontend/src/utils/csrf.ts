/**
 * Retrieves the value of a cookie by its name.
 * Uses a robust parsing method that avoids common regex pitfalls.
 * 
 * @param name The name of the cookie to retrieve.
 * @returns The value of the cookie if found, otherwise null.
 */
export const getCookie = (name: string): string | null => {
  if (typeof document === 'undefined') return null;

  const nameLenPlus = name.length + 1;
  return (
    document.cookie
      .split(';')
      .map((c) => c.trim())
      .filter((cookie) => {
        return cookie.substring(0, nameLenPlus) === `${name}=`;
      })
      .map((cookie) => {
        return decodeURIComponent(cookie.substring(nameLenPlus));
      })[0] || null
  );
};

/**
 * Specifically retrieves the CSRF token from the XSRF-TOKEN cookie.
 */
export const getCsrfToken = (): string | null => {
  return getCookie('XSRF-TOKEN');
};
