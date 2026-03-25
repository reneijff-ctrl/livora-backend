import React from 'react';

const RemovalPage: React.FC = () => {
  const lastUpdated = "February 21, 2026";
  const safetyEmail = "safety@joinlivora.com";

  return (
    <div className="bg-[#08080A] py-24 min-h-screen font-sans">
      <div className="max-w-4xl mx-auto px-6">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight text-white mb-4">Content Removal Request</h1>
        <p className="text-zinc-500 mb-16">Last updated: {lastUpdated}</p>
        
        <div className="space-y-16">
          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">1. Our Commitment to Consent and Safety</h2>
            <p className="text-zinc-400 leading-relaxed mb-6">
              JoinLivora prioritizes the safety, privacy, and consent of all individuals. We maintain a zero-tolerance policy for the distribution of non-consensual sexual material (NCSM), content depicting minors, or any material that violates our community guidelines.
            </p>
            <p className="text-zinc-400 leading-relaxed">
              If you believe content on our platform depicts you or someone else without their consent, we will take immediate action to investigate and, where appropriate, remove the material.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">2. How to Request Content Removal</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                To request the removal of content from JoinLivora, please send a detailed email to our Safety Team. Your request should include the following information:
              </p>
              <ul className="list-disc pl-6 space-y-3">
                <li><strong className="text-zinc-200">Specific URL(s):</strong> Direct links to the page or content you wish to have removed.</li>
                <li><strong className="text-zinc-200">Reason for Request:</strong> A clear explanation of why you are requesting the removal (e.g., non-consensual, privacy violation, copyright infringement).</li>
                <li><strong className="text-zinc-200">Your Contact Information:</strong> Your full name and a reliable email address for follow-up communications.</li>
              </ul>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">3. Identity Verification Requirement</h2>
            <div className="p-8 bg-[#0F0F14] rounded-2xl border border-[#16161D] shadow-xl mb-6">
              <p className="text-zinc-400 leading-relaxed">
                To protect our users and prevent fraudulent removal requests, we require identity verification for all requests related to personal privacy or non-consensual material.
              </p>
              <p className="mt-4 text-zinc-400 leading-relaxed">
                You may be asked to provide a <strong className="text-zinc-200">valid government-issued photo ID</strong> or other documentation to verify that you are the person depicted in the content or are authorized to act on their behalf. This information will be used solely for the purpose of processing your removal request and will be handled in accordance with our Privacy Policy.
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">4. Processing Timeframe</h2>
            <p className="text-zinc-400 leading-relaxed">
              Upon receipt of a complete removal request, including any required verification, our Safety Team will review the material promptly. We aim to process all valid requests within <strong className="text-zinc-200">24 to 48 hours</strong>. You will receive a notification once your request has been reviewed and a decision has been made.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">5. False Claims and Legal Warning</h2>
            <div className="bg-red-950/20 border border-red-500/20 p-8 rounded-2xl">
              <p className="text-red-400 leading-relaxed font-medium">
                WARNING: Submission of false, misleading, or fraudulent removal requests is a serious matter.
              </p>
              <p className="mt-4 text-zinc-400 text-sm leading-relaxed">
                Purposely misrepresenting that material is infringing or non-consensual may lead to legal consequences, including civil liability for damages. JoinLivora reserves the right to take action against individuals who abuse our removal process, including termination of their access to the platform.
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">6. Reporting via Platform Tools</h2>
            <p className="text-zinc-400 leading-relaxed">
              For rapid reporting of content that violates our terms, users can also use the "Report" button located directly on creator profiles and content pages. This flag sends an immediate alert to our moderation team for review.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">7. Contact Safety Team</h2>
            <p className="text-zinc-400 leading-relaxed">
              For all safety-related matters and formal removal requests, please contact us at:
            </p>
            <p className="mt-4 text-indigo-400 font-medium">{safetyEmail}</p>
          </section>
        </div>
      </div>
    </div>
  );
};

export default RemovalPage;
