import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";

const CookieBanner: React.FC = () => {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const consent = localStorage.getItem("cookie_consent");
    if (!consent) {
      setVisible(true);
    }
  }, []);

  const handleChoice = (choice: string) => {
    localStorage.setItem("cookie_consent", choice);
    setVisible(false);
  };

  if (!visible) return null;

  return (
    <div className="fixed bottom-0 left-0 w-full border-t border-zinc-800 bg-zinc-900 z-50">
      <div className="max-w-7xl mx-auto px-6 py-5 flex flex-col md:flex-row items-center justify-between gap-4">
        
        <p className="text-sm text-zinc-400 max-w-3xl">
          We use cookies to improve your experience, analyze traffic and ensure platform security. 
          By using JoinLivora, you agree to our{" "}
          <Link to="/legal/privacy" className="text-white underline font-medium">
            Privacy Policy
          </Link>.
        </p>

        <div className="flex gap-3">
          <button
            onClick={() => handleChoice("rejected")}
            className="px-6 py-2 text-sm border border-zinc-700 text-zinc-300 hover:bg-zinc-800 transition rounded-lg"
          >
            Reject
          </button>

          <button
            onClick={() => handleChoice("accepted")}
            className="px-6 py-2 text-sm bg-white text-black font-semibold hover:bg-zinc-100 transition rounded-lg shadow-sm"
          >
            Accept
          </button>
        </div>

      </div>
    </div>
  );
};

export default CookieBanner;