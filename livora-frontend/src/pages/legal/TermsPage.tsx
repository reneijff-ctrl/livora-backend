import React from 'react';

const TermsPage: React.FC = () => {
  const lastUpdated = "February 21, 2026";

  return (
    <div className="bg-[#08080A] py-24 min-h-screen">
      <div className="max-w-4xl mx-auto px-6">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight text-white mb-4">Terms of Service</h1>
        <p className="text-zinc-500 mb-16 italic">Last updated: {lastUpdated}</p>
        
        <div className="space-y-16">
          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">1. Acceptance of Terms</h2>
            <p className="text-zinc-400 leading-relaxed">
              By accessing and using JoinLivora ("Platform", "we", "us", "our"), you agree to be bound by these Terms of Service, our Privacy Policy, and any other policies or guidelines posted on the Platform. If you do not agree to these terms, you are prohibited from using or accessing our services.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">2. Eligibility</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                JoinLivora is an adult-oriented live streaming platform. You must be at least eighteen (18) years of age, or the legal age of majority in your jurisdiction, whichever is greater, to access or use the Platform. 
              </p>
              <p>
                By creating an account, you represent and warrant that you meet these age requirements. Access by minors is strictly prohibited. We reserve the right to request proof of age at any time and may suspend or terminate accounts that cannot be verified.
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">3. User Conduct and Content Restrictions</h2>
            <p className="text-zinc-400 leading-relaxed mb-4">
              You agree not to use the Platform for any unlawful purpose. Prohibited activities include, but are not limited to:
            </p>
            <ul className="list-disc list-inside space-y-3 text-zinc-400 ml-4">
              <li>Uploading or transmitting non-consensual sexual content ("revenge porn").</li>
              <li>Harassment, stalking, or any form of abuse toward Creators or other Users.</li>
              <li>Impersonating any person or entity.</li>
              <li>Using automated scripts to scrape content from the Platform.</li>
              <li>Broadcasting or sharing any content involving minors or any activity that is illegal under local or international law.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">4. Token-Based Transactions and Refund Policy</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                The Platform operates on a virtual token system. Tokens can be purchased to tip Creators, access private shows, or unlock premium features.
              </p>
              <p>
                <strong>No Refunds Policy:</strong> All purchases of tokens are final and non-refundable. Tokens have no cash value outside the Platform and cannot be exchanged for fiat currency by regular Users. 
              </p>
              <p>
                If a transaction is disputed via a third-party payment provider (chargeback), we reserve the right to immediately suspend or terminate your account and take necessary legal action to recover the funds.
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">5. Creator Specific Terms and Payouts</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                Users who register as Creators are subject to additional verification procedures, including identity verification as required by 18 U.S.C. § 2257 and other relevant laws.
              </p>
              <p>
                <strong>Payouts:</strong> Creators earn a share of the tokens received through tips and private shows. Payouts are processed according to our current payout schedule and are subject to minimum balance requirements and fraud verification periods. 
              </p>
              <p>
                Creators are responsible for all taxes and duties associated with their earnings on the Platform.
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">6. Intellectual Property Rights</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                <strong>Ownership:</strong> Content ownership remains with the Creators who produce it. Creators retain all rights, title, and interest in and to their original content.
              </p>
              <p>
                <strong>Platform License:</strong> By broadcasting or uploading content to JoinLivora, you grant us a worldwide, non-exclusive, royalty-free, sublicensable license to host, display, reproduce, and distribute that content solely for the purpose of operating, promoting, and improving the Platform.
              </p>
              <p>
                <strong>Prohibited Use:</strong> Users are strictly prohibited from recording, capturing, or redistributing Creator content without express written permission.
              </p>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">7. Account Termination and Suspension</h2>
            <p className="text-zinc-400 leading-relaxed">
              We reserve the right, in our sole discretion, to suspend or terminate your account and access to the Platform at any time, for any reason, including but not limited to violations of these Terms of Service. Upon termination, any remaining tokens in a User's account may be forfeited at our discretion.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">8. Disclaimer of Warranties</h2>
            <p className="text-zinc-400 leading-relaxed">
              The Platform is provided "as is" and "as available" without any warranties of any kind, either express or implied. We do not guarantee that the Platform will be uninterrupted, secure, or error-free.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">9. Governing Law</h2>
            <p className="text-zinc-400 leading-relaxed">
              These Terms of Service shall be governed by and construed in accordance with the laws of [Insert Jurisdiction/Country], without regard to its conflict of law principles. Any legal action or proceeding arising out of or related to these terms shall be brought exclusively in the courts located in [Insert City, State/Province].
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">10. Contact Information</h2>
            <p className="text-zinc-400 leading-relaxed">
              If you have any questions about these Terms of Service, please contact us at <span className="text-indigo-400">legal@joinlivora.com</span>.
            </p>
          </section>
        </div>
        
        <div className="mt-24 pt-12 border-t border-zinc-800 text-center">
          <p className="text-zinc-600 text-sm">
            © {new Date().getFullYear()} JoinLivora. All rights reserved. 
          </p>
        </div>
      </div>
    </div>
  );
};

export default TermsPage;
