import React from 'react';

const PrivacyPage: React.FC = () => {
  const lastUpdated = "February 21, 2026";

  return (
    <div className="bg-[#08080A] py-24 min-h-screen">
      <div className="max-w-4xl mx-auto px-6">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight text-white mb-4">Privacy Policy</h1>
        <p className="text-zinc-500 mb-16">Last updated: {lastUpdated}</p>
        
        <div className="space-y-16">
          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">1. Introduction</h2>
            <p className="text-zinc-400 leading-relaxed">
              At JoinLivora, your privacy is our priority. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you visit our platform. We are committed to protecting your personal data and ensuring a secure, discreet, and premium experience.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">2. Data We Collect</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>We collect information that you provide directly to us, as well as data generated automatically through your use of the platform:</p>
              <ul className="list-disc pl-6 space-y-2">
                <li><strong className="text-zinc-200">Account Information:</strong> Email address, username, password, and profile details.</li>
                <li><strong className="text-zinc-200">Identity Verification (Creators):</strong> Government-issued ID, age verification documents, and tax information to comply with legal requirements (e.g., 18 U.S.C. § 2257).</li>
                <li><strong className="text-zinc-200">Payment Data:</strong> We use third-party payment processors to handle transactions. We do not store full credit card numbers on our servers; we only retain transaction references and basic billing information.</li>
                <li><strong className="text-zinc-200">Usage Data:</strong> IP address, browser type, device identifiers, and interaction data (e.g., which creators you follow, tipping history).</li>
              </ul>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">3. Cookies and Tracking</h2>
            <p className="text-zinc-400 leading-relaxed">
              We use cookies and similar tracking technologies to track the activity on our platform and hold certain information. Cookies are files with small amount of data which may include an anonymous unique identifier. You can instruct your browser to refuse all cookies or to indicate when a cookie is being sent. However, if you do not accept cookies, you may not be able to use some portions of our service.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">4. How We Use Your Information</h2>
            <ul className="list-disc pl-6 space-y-2 text-zinc-400 leading-relaxed">
              <li>To provide and maintain our platform.</li>
              <li>To process transactions and send related information, including confirmations and receipts.</li>
              <li>To verify your identity and prevent fraud.</li>
              <li>To improve our services and develop new features.</li>
              <li>To communicate with you regarding updates, security alerts, and support.</li>
              <li>To comply with legal obligations and enforce our Terms of Service.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">5. Data Sharing and Processors</h2>
            <p className="text-zinc-400 leading-relaxed mb-4">
              We do not sell your personal data. We may share information with:
            </p>
            <ul className="list-disc pl-6 space-y-2 text-zinc-400 leading-relaxed">
              <li><strong className="text-zinc-200">Payment Processors:</strong> To handle token purchases and creator payouts.</li>
              <li><strong className="text-zinc-200">Service Providers:</strong> For hosting, analytics, and security monitoring.</li>
              <li><strong className="text-zinc-200">Legal Authorities:</strong> When required by law or to protect the rights and safety of our users and the platform.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">6. Security Measures</h2>
            <p className="text-zinc-400 leading-relaxed">
              We implement robust security measures, including 256-bit SSL encryption, to protect your data. While we strive to use commercially acceptable means to protect your personal information, no method of transmission over the Internet or method of electronic storage is 100% secure.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">7. GDPR and User Rights</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>Under the General Data Protection Regulation (GDPR), users in the European Economic Area (EEA) have specific rights regarding their personal data:</p>
              <ul className="list-disc pl-6 space-y-2">
                <li><strong className="text-zinc-200">Access:</strong> You have the right to request copies of your personal data.</li>
                <li><strong className="text-zinc-200">Correction:</strong> You have the right to request that we correct any information you believe is inaccurate.</li>
                <li><strong className="text-zinc-200">Deletion:</strong> You have the right to request that we erase your personal data, under certain conditions.</li>
                <li><strong className="text-zinc-200">Data Portability:</strong> You have the right to request that we transfer the data that we have collected to another organization.</li>
              </ul>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">8. Data Retention</h2>
            <p className="text-zinc-400 leading-relaxed">
              We will retain your personal information only for as long as is necessary for the purposes set out in this Privacy Policy. We will retain and use your information to the extent necessary to comply with our legal obligations, resolve disputes, and enforce our policies.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">9. Changes to This Policy</h2>
            <p className="text-zinc-400 leading-relaxed">
              We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page and updating the "Last updated" date at the top.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">10. Contact Us</h2>
            <p className="text-zinc-400 leading-relaxed">
              If you have any questions about this Privacy Policy or wish to exercise your data rights, please contact us at:
            </p>
            <p className="mt-4 text-indigo-400 font-medium">privacy@joinlivora.com</p>
          </section>
        </div>
      </div>
    </div>
  );
};

export default PrivacyPage;
