(window as any).global = window;
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { HelmetProvider } from 'react-helmet-async'
import ErrorBoundary from './components/ErrorBoundary'

// Security: Disable console logs in production
if (import.meta.env.PROD || import.meta.env.VITE_APP_ENV === 'production') {
  const noop = () => {};
  console.log = noop;
  console.debug = noop;
  console.info = noop;
  console.warn = noop;
  console.error = noop;
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <HelmetProvider>
        <App />
      </HelmetProvider>
    </ErrorBoundary>
  </React.StrictMode>,
)
