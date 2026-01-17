import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import SEO from '../components/SEO';
import HasRole from '../components/HasRole';
import contentService, { ContentItem } from '../api/contentService';
import ContentCard from '../components/ContentCard';

const Home = () => {
  const { isAuthenticated } = useAuth();
  const [publicContent, setPublicContent] = useState<ContentItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchPublicContent = async () => {
      try {
        const data = await contentService.getPublicContent();
        setPublicContent(data);
      } catch (err) {
        console.error('Failed to fetch public content', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchPublicContent();
  }, []);

  return (
    <div style={{ padding: 'clamp(1rem, 3vw, 2rem)', fontFamily: 'sans-serif', maxWidth: '1200px', margin: '0 auto' }}>
      <SEO 
        title="Welcome" 
        description="Livora is a modern platform with advanced subscription features and premium content."
        canonical="/"
      />
      <header style={{ textAlign: 'center', marginBottom: 'clamp(2rem, 8vw, 4rem)' }}>
        <h1 style={{ fontSize: 'clamp(2rem, 6vw, 3.5rem)', margin: '0 0 0.5rem 0' }}>Welcome to Livora</h1>
        <p style={{ fontSize: 'clamp(1rem, 3vw, 1.25rem)', color: '#666' }}>Premium Content for Everyone</p>
        <nav style={{ marginTop: '2rem', display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link to="/pricing" style={{ padding: '0.5rem 1rem', textDecoration: 'none', color: '#6772e5', fontWeight: 'bold' }}>Pricing</Link>
          {isAuthenticated ? (
            <>
              <Link to="/dashboard" style={{ padding: '0.5rem 1rem', backgroundColor: '#6772e5', color: 'white', textDecoration: 'none', borderRadius: '8px', fontWeight: 'bold' }}>Go to Dashboard</Link>
              <HasRole role="ADMIN">
                <Link to="/admin" style={{ padding: '0.5rem 1rem', color: 'red', fontWeight: 'bold', textDecoration: 'none' }}>Admin Panel</Link>
              </HasRole>
            </>
          ) : (
            <>
              <Link to="/login" style={{ padding: '0.5rem 1rem', textDecoration: 'none', color: '#6772e5', fontWeight: 'bold' }}>Login</Link>
              <Link to="/register" style={{ padding: '0.5rem 1rem', backgroundColor: '#6772e5', color: 'white', textDecoration: 'none', borderRadius: '8px', fontWeight: 'bold' }}>Register</Link>
            </>
          )}
        </nav>
      </header>

      <main>
        <section>
          <h2 style={{ marginBottom: '1.5rem', fontSize: 'clamp(1.5rem, 4vw, 2rem)' }}>Explore Free Content</h2>
          {isLoading ? (
            <p>Loading content...</p>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '2rem', marginTop: '1rem' }}>
              {publicContent.length === 0 ? (
                <p>No public content available yet.</p>
              ) : (
                publicContent.map(item => <ContentCard key={item.id} content={item} />)
              )}
            </div>
          )}
        </section>
        
        <section style={{ marginTop: '4rem', padding: 'clamp(1.5rem, 5vw, 3rem)', backgroundColor: '#f8f9ff', borderRadius: '16px', textAlign: 'center' }}>
          <h2 style={{ fontSize: 'clamp(1.5rem, 4vw, 2.25rem)', marginBottom: '1rem' }}>Ready for more?</h2>
          <p style={{ fontSize: 'clamp(1rem, 2.5vw, 1.15rem)', color: '#444', maxWidth: '600px', margin: '0 auto 2rem' }}>Join our premium community to unlock exclusive content and support your favorite creators.</p>
          <Link to="/pricing" style={{ display: 'inline-block', padding: '14px 32px', backgroundColor: '#6772e5', color: 'white', textDecoration: 'none', borderRadius: '8px', fontWeight: 'bold', fontSize: '1.1rem' }}>View Pricing</Link>
        </section>
      </main>
    </div>
  )
}

export default Home
