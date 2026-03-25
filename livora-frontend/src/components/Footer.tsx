import { Link } from 'react-router-dom';
export default function Footer() {
  return (
    <footer className="bg-black border-t border-white/10 mt-24">
      
      <div className="max-w-[1200px] mx-auto px-8 py-12 flex justify-between items-center">

        {/* LEFT SIDE */}
        <div className="flex flex-col items-start space-y-4">
          
          <img
            src="/icoon_joinlivora.png"
            alt="JoinLivora"
            className="h-[110px] w-auto object-contain"
          />

          <p className="text-white/60 text-sm">
            Premium private live experiences. Direct. Secure. Discreet.
          </p>

          <p className="text-white/40 text-xs">
            18+ Only. All models are 18 years or older.
          </p>

        </div>

        {/* RIGHT SIDE */}
        <div className="flex flex-col items-end space-y-3 text-sm">
          <Link to="/legal/privacy" className="text-white/70 hover:text-white transition-colors">
            Privacy Policy
          </Link>
          <Link to="/legal/terms" className="text-white/70 hover:text-white transition-colors">
            Terms of Service
          </Link>
          <Link to="/legal/dmca" className="text-white/70 hover:text-white transition-colors">
            DMCA Policy
          </Link>
          <Link to="/legal/2257" className="text-white/70 hover:text-white transition-colors">
            2257 Compliance
          </Link>
          <Link to="/contact" className="text-white/70 hover:text-white transition-colors">
            Contact
          </Link>
        </div>

      </div>

      <div className="text-center text-white/40 text-sm pb-6 border-t border-white/10 pt-6">
        © 2026 JoinLivora. All rights reserved.
      </div>

    </footer>
  );
}
