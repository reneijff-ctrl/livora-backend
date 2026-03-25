import React from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Footer from './Footer';
import ErrorBoundary from './ErrorBoundary';

/**
 * MainLayout provides the standard application structure with a Navbar and Footer.
 * It is used for the majority of authenticated and public routes.
 */
const MainLayout: React.FC = () => {
  return (
    <div className="flex flex-col min-h-screen bg-[#08080A]">
      <Navbar />
      <main className="flex-1 flex flex-col">
        <ErrorBoundary>
          <Outlet />
        </ErrorBoundary>
      </main>
      <Footer />
    </div>
  );
};

export default MainLayout;
