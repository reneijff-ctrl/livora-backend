import React from 'react';
import { Helmet } from 'react-helmet-async';

interface SEOProps {
  title?: string;
  description?: string;
  canonical?: string;
  type?: string;
}

const SEO: React.FC<SEOProps> = React.memo(({ 
  title, 
  description, 
  canonical, 
  type = 'website' 
}) => {
  const siteTitle = 'Livora';
  const fullTitle = title ? `${title} | ${siteTitle}` : siteTitle;
  const defaultDescription = 'Livora - Modern subscription platform with premium features.';
  const metaDescription = description || defaultDescription;
  const baseUrl = import.meta.env.VITE_APP_URL;
  const canonicalUrl = canonical ? `${baseUrl}${canonical}` : baseUrl;

  return (
    <Helmet>
      {/* Basic metadata */}
      <title>{fullTitle}</title>
      <meta name="description" content={metaDescription} />
      <link rel="canonical" href={canonicalUrl} />

      {/* Open Graph / Facebook */}
      <meta property="og:type" content={type} />
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={metaDescription} />
      <meta property="og:url" content={canonicalUrl} />

      {/* Twitter */}
      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:title" content={fullTitle} />
      <meta name="twitter:description" content={metaDescription} />
    </Helmet>
  );
});

export default SEO;
