import { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * User-friendly ErrorBoundary
 * Phase E Design Compliant: Minimalist, Zinc theme, Tailwind CSS.
 */
class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false
  };

  public static getDerivedStateFromError(_: Error): State {
    return { hasError: true };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Dev logs stay intact
    console.error('Uncaught application error:', error, errorInfo);
  }

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    window.location.href = '/';
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-[#08080A] p-6 font-sans">
          <div className="w-full max-w-md flex flex-col items-center text-center">
            
            {/* Icon Section */}
            <div className="mb-8">
              <div className="w-16 h-16 bg-[#0F0F14] rounded-2xl flex items-center justify-center border border-[#16161D] shadow-2xl">
                <svg 
                  width="32" 
                  height="32" 
                  viewBox="0 0 24 24" 
                  fill="none" 
                  stroke="currentColor" 
                  strokeWidth="2.5" 
                  strokeLinecap="round" 
                  strokeLinejoin="round"
                  className="text-zinc-500"
                >
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </div>
            </div>

            {/* Content Section */}
            <div className="mb-10">
              <h1 className="text-2xl font-bold tracking-tight text-white mb-3">
                Something went wrong
              </h1>
              <p className="text-zinc-400 leading-relaxed">
                The application encountered an unexpected error. 
                Don't worry, your data is safe. Please try refreshing the page.
              </p>
            </div>

            {/* Actions Section */}
            <div className="w-full space-y-3">
              <button 
                onClick={this.handleReload}
                className="w-full py-3.5 px-6 bg-indigo-600 text-white font-bold rounded-full hover:bg-indigo-700 transition-all active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 shadow-lg shadow-indigo-600/20"
              >
                Reload Page
              </button>
              <button 
                onClick={this.handleGoHome}
                className="w-full py-3.5 px-6 bg-white/5 text-zinc-400 font-bold rounded-full border border-[#16161D] hover:bg-white/10 hover:text-white transition-all active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
              >
                Go to Home
              </button>
            </div>

            {/* Dev Note */}
            {import.meta.env.DEV && (
              <div className="mt-12 p-4 bg-[#0F0F14] rounded-xl border border-[#16161D]">
                <p className="text-[10px] font-bold uppercase tracking-widest text-zinc-500">
                  Developer Note
                </p>
                <p className="mt-1 text-xs text-zinc-500">
                  Check the browser console for technical details.
                </p>
              </div>
            )}

            {/* Footer */}
            <p className="mt-12 text-[10px] font-bold uppercase tracking-[0.2em] text-zinc-600">
              Livora Systems
            </p>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
