import React from 'react';

const DMCAPage: React.FC = () => {
  const lastUpdated = "February 21, 2026";
  const dmcaEmail = "dmca@joinlivora.com";

  return (
    <div className="bg-[#08080A] py-24 min-h-screen">
      <div className="max-w-4xl mx-auto px-6">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight text-white mb-4">DMCA Policy</h1>
        <p className="text-zinc-500 mb-16">Last updated: {lastUpdated}</p>
        
        <div className="space-y-16">
          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">1. Introduction</h2>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora respects the intellectual property rights of others and expects our users to do the same. In accordance with the Digital Millennium Copyright Act (DMCA), we have adopted the following policy toward copyright infringement. We reserve the right to remove any content that allegedly infringes another person's copyright and to terminate the accounts of repeat infringers.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">2. Reporting Infringement</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                If you are a copyright owner or an agent thereof and believe that any content on JoinLivora infringes upon your copyrights, you may submit a notification pursuant to the DMCA by providing our Copyright Agent with the following information in writing:
              </p>
              <ul className="list-disc pl-6 space-y-3">
                <li><strong className="text-zinc-200">Electronic or physical signature:</strong> Of the person authorized to act on behalf of the owner of the copyright interest.</li>
                <li><strong className="text-zinc-200">Identification of the copyrighted work:</strong> A description of the copyrighted work that you claim has been infringed.</li>
                <li><strong className="text-zinc-200">Identification of the infringing material:</strong> A description of where the material that you claim is infringing is located on our platform (including specific URLs).</li>
                <li><strong className="text-zinc-200">Contact Information:</strong> Your address, telephone number, and email address.</li>
                <li><strong className="text-zinc-200">Good Faith Statement:</strong> A statement by you that you have a good faith belief that the disputed use is not authorized by the copyright owner, its agent, or the law.</li>
                <li><strong className="text-zinc-200">Statement of Accuracy:</strong> A statement by you, made under penalty of perjury, that the above information in your notice is accurate and that you are the copyright owner or authorized to act on the copyright owner's behalf.</li>
              </ul>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">3. Dedicated DMCA Agent</h2>
            <p className="text-zinc-400 leading-relaxed">
              Please send all DMCA notices to our designated Copyright Agent at the following email address:
            </p>
            <p className="mt-4 text-indigo-400 font-medium">{dmcaEmail}</p>
            <p className="mt-4 text-zinc-500 text-sm">
              Please note that this email address is strictly for copyright infringement notices. General support inquiries will not receive a response from this address.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">4. Counter-Notification</h2>
            <div className="space-y-4 text-zinc-400 leading-relaxed">
              <p>
                If you believe that your content was removed (or to which access was disabled) is not infringing, or that you have the authorization from the copyright owner, the copyright owner's agent, or pursuant to the law, to post and use the material in your content, you may send a counter-notice containing the following information to the Copyright Agent:
              </p>
              <ul className="list-disc pl-6 space-y-3">
                <li>Your physical or electronic signature.</li>
                <li>Identification of the content that has been removed or to which access has been disabled and the location at which the content appeared before it was removed or disabled.</li>
                <li>A statement that you have a good faith belief that the content was removed or disabled as a result of mistake or a misidentification of the content.</li>
                <li>Your name, address, telephone number, and email address, a statement that you consent to the jurisdiction of the federal court in the judicial district in which your address is located (or if you are outside the US, the judicial district in which JoinLivora is located), and that you will accept service of process from the person who provided notification of the alleged infringement.</li>
              </ul>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">5. Repeat Infringer Policy</h2>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora takes copyright protection seriously. We maintain a policy for the termination, in appropriate circumstances, of users who are repeat infringers of intellectual property rights. This may include disabling the accounts of creators who repeatedly post content that is the subject of valid DMCA notices.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">6. Third Party Content</h2>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora is a platform for creators to share their own content. We are not responsible for the content posted by our users, but we will take swift action to address any reported infringements in accordance with this policy.
            </p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-zinc-100 mb-6">7. Modifications</h2>
            <p className="text-zinc-400 leading-relaxed">
              JoinLivora reserves the right to modify, alter or update this DMCA Policy at any time. We will post the revised policy on this page and update the "Last updated" date.
            </p>
          </section>
        </div>
      </div>
    </div>
  );
};

export default DMCAPage;
