import React from 'react';

const ContactPage: React.FC = () => {
  return (
    <div className="bg-[#08080A] py-24 min-h-screen">
      <div className="max-w-4xl mx-auto px-6">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight text-white mb-8 text-center">Contact Us</h1>
        
        <div className="bg-zinc-900/50 border border-white/10 rounded-2xl p-8 md:p-12">
          <p className="text-zinc-400 text-lg leading-relaxed mb-12 text-center max-w-2xl mx-auto">
            Have questions or need assistance? Our team is here to help. 
            We strive to provide a premium and secure experience for all our users.
          </p>
          
          <div className="grid md:grid-cols-2 gap-8">
            <div className="space-y-4 p-6 bg-white/5 rounded-xl border border-white/5 hover:border-white/10 transition-colors">
              <h2 className="text-xl font-semibold text-white">General Support</h2>
              <p className="text-zinc-400 text-sm">
                For account issues, technical support, or general inquiries.
              </p>
              <a 
                href="mailto:support@joinlivora.com" 
                className="inline-block text-indigo-400 font-medium hover:text-indigo-300 transition-colors"
              >
                support@joinlivora.com
              </a>
            </div>

            <div className="space-y-4 p-6 bg-white/5 rounded-xl border border-white/5 hover:border-white/10 transition-colors">
              <h2 className="text-xl font-semibold text-white">Business Inquiries</h2>
              <p className="text-zinc-400 text-sm">
                For partnerships, marketing, and business development.
              </p>
              <a 
                href="mailto:business@joinlivora.com" 
                className="inline-block text-indigo-400 font-medium hover:text-indigo-300 transition-colors"
              >
                business@joinlivora.com
              </a>
            </div>
          </div>

          <div className="mt-12 pt-12 border-t border-white/5 text-center">
            <p className="text-zinc-500 text-sm">
              We typically respond to all inquiries within 24-48 business hours.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ContactPage;
