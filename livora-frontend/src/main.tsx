import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import router from './router'
import './index.css'
import { HelmetProvider } from 'react-helmet-async'
import { AuthProvider } from './auth/AuthContext'
import { WsProvider } from './ws/WsContext'
import { WalletProvider } from './wallet/WalletContext'
import ErrorBoundary from './components/ErrorBoundary'
import healthStore from './store/healthStore'

console.log("[MAIN] Module loaded. Date:", new Date().toISOString());

// Trigger backend health check once on app start
healthStore.checkHealth();

// Polyfill for global and process - though Vite define usually handles this, 
// some libraries explicitly check for window.global or process
(window as any).global = (window as any).global || window;
(window as any).process = (window as any).process || { env: { NODE_ENV: import.meta.env.MODE } };

// Security: Disable verbose logs in production, but KEEP errors/warnings 
// for troubleshooting blank screens.
if (import.meta.env.PROD || import.meta.env.VITE_APP_ENV === 'production') {
  const noop = () => {};
  console.log = noop;
  console.debug = noop;
  console.info = noop;
  // console.warn and console.error are kept intentionally
}

// Global error handling for early diagnosis of blank screens
window.onerror = (message, source, lineno, colno, error) => {
  console.error('Global error caught:', { message, source, lineno, colno, error });
};

window.onunhandledrejection = (event) => {
  console.error('Unhandled promise rejection:', event.reason);
};

const container = document.getElementById('root');

if (!container) {
  const errorMsg = "Failed to find the root element. Ensure index.html has <div id='root'></div>";
  console.error(errorMsg);
  // Show a visible error on the screen if possible
  document.body.innerHTML = `<div style="padding: 20px; color: red; font-family: sans-serif;"><h1>Critical Error</h1><p>${errorMsg}</p></div>`;
  throw new Error(errorMsg);
}

try {
  console.log("[MAIN] Initializing React root and rendering...");
  const root = createRoot(container);

  root.render(
    <ErrorBoundary>
      <HelmetProvider>
        <AuthProvider>
          <WsProvider>
            <WalletProvider>
              <RouterProvider 
                router={router} 
              />
            </WalletProvider>
          </WsProvider>
        </AuthProvider>
      </HelmetProvider>
    </ErrorBoundary>,
  );
} catch (error) {
  console.error("Failed to render the application:", error);
  container.innerHTML = `<div style="padding: 20px; color: red; font-family: sans-serif;"><h1>Failed to render</h1><p>${error instanceof Error ? error.message : String(error)}</p></div>`;
}
