import React from 'react';

const CompliancePage: React.FC = () => {
  const lastUpdated = "February 21, 2026";
  const custodianOfRecords = "[CUSTODIAN_NAME / REGISTERED_AGENT]";
  const recordsLocation = "[STREET_ADDRESS, CITY, STATE, ZIP]";

  return (
    <div className="bg-[#08080A] py-24 min-h-screen font-sans">
      <div className="max-w-4xl mx-auto px-6">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight text-white mb-4">18 U.S.C. § 2257 Compliance</h1>
        <p className="text-zinc-500 mb-16">Last updated: {lastUpdated}</p>
        
        <div className="space-y-16">
          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">1. General Statement</h2>
            <p className="text-zinc-400 leading-relaxed mb-6">
              All models, actors, and other persons who appear in any visual depiction of actual or simulated sexually explicit conduct appearing on JoinLivora (the "Platform") were over the age of 18 years at the time the visual depictions were created.
            </p>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora strictly prohibits the distribution of any content that depicts individuals under the age of 18. We maintain a zero-tolerance policy regarding any violation of age verification requirements and will cooperate fully with law enforcement agencies in any investigation related to such content.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">2. Service Provider Status</h2>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora operates as a "service provider" as defined by 18 U.S.C. § 2257(h)(1). The Platform provides an online venue for independent creators and performers to upload, share, and monetize their own visual content. JoinLivora is not the "producer" (as defined by 18 U.S.C. § 2257) of any content provided by independent creators on the Platform.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">3. Creator Responsibility</h2>
            <p className="text-zinc-400 leading-relaxed mb-6">
              Each independent creator is solely responsible for ensuring that all content they upload to the Platform is in full compliance with 18 U.S.C. § 2257 and all other applicable laws. This includes, but is not limited to, verifying the age of all performers and maintaining all required records of such verification.
            </p>
            <p className="text-zinc-400 leading-relaxed">
              By using the Platform, each creator warrants and represents that they have obtained and will maintain all documentation required to satisfy the record-keeping obligations under 18 U.S.C. § 2257 and its implementing regulations (28 C.F.R. Part 75).
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">4. Records Maintenance and Custodian</h2>
            <p className="text-zinc-400 leading-relaxed mb-6">
              As the service provider, JoinLivora relies on the representations of its independent creators regarding the existence and maintenance of 2257 records. All such records are required to be maintained by the "producer" or the individual creator of the content at their designated location.
            </p>
            <div className="p-8 bg-[#0F0F14] rounded-2xl border border-[#16161D] shadow-xl">
              <h3 className="text-lg font-semibold text-zinc-100 mb-4 uppercase tracking-widest text-[11px]">Custodian of Records (JoinLivora Internal Records)</h3>
              <p className="text-zinc-400 text-sm leading-relaxed mb-4">
                JoinLivora maintains its own corporate records and creator identity verification data at the following location:
              </p>
              <div className="space-y-1">
                <p className="text-zinc-100 font-medium">{custodianOfRecords}</p>
                <p className="text-zinc-500 text-sm">{recordsLocation}</p>
              </div>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">5. Reporting and Compliance Audits</h2>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora reserves the right to request proof of age verification and/or a copy of 2257 records from any creator at any time. Failure to provide such documentation upon request may result in the immediate removal of content and/or termination of the creator's account.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">6. Exemptions</h2>
            <p className="text-zinc-400 leading-relaxed">
              Certain content on the Platform may be exempt from the record-keeping requirements under 18 U.S.C. § 2257 and 28 C.F.R. Part 75. However, JoinLivora requires all creators to verify that any and all performers depicted in any sexually explicit content are at least 18 years of age, regardless of any technical legal exemptions.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">7. Contact Information</h2>
            <p className="text-zinc-400 leading-relaxed">
              Any inquiries regarding 18 U.S.C. § 2257 compliance should be directed to our compliance department at:
            </p>
            <p className="mt-4 text-indigo-400 font-medium">compliance@joinlivora.com</p>
          </section>
        </div>
      </div>
    </div>
  );
};

export default CompliancePage;
