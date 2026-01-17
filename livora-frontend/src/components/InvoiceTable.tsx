import React, { useEffect, useState } from 'react';
import paymentService, { Invoice } from '../api/paymentService';

const InvoiceTable: React.FC = React.memo(() => {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadInvoices = async () => {
      try {
        setIsLoading(true);
        const data = await paymentService.fetchInvoices();
        setInvoices(data);
      } catch (err) {
        console.error('Failed to fetch invoices:', err);
        // Toast is shown by the global interceptor
      } finally {
        setIsLoading(false);
      }
    };

    loadInvoices();
  }, []);

  if (isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#666' }}>
        <div className="spinner" style={{ 
          width: '20px', 
          height: '20px', 
          border: '2px solid #f3f3f3', 
          borderTop: '2px solid #6772e5', 
          borderRadius: '50%',
          animation: 'spin 1s linear infinite'
        }} />
        <p>Loading invoice history...</p>
        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  if (invoices.length === 0) {
    return <p>No invoices yet.</p>;
  }

  return (
    <div style={{ marginTop: '2rem', overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid #eee' }}>
            <th style={{ padding: '0.75rem' }}>Date</th>
            <th style={{ padding: '0.75rem' }}>Amount</th>
            <th style={{ padding: '0.75rem' }}>Status</th>
            <th style={{ padding: '0.75rem' }}>Download</th>
          </tr>
        </thead>
        <tbody>
          {invoices.map((invoice) => (
            <tr key={invoice.id} style={{ borderBottom: '1px solid #f9f9f9' }}>
              <td style={{ padding: '0.75rem' }}>
                {new Date(invoice.date).toLocaleDateString()}
              </td>
              <td style={{ padding: '0.75rem' }}>
                {(invoice.amount / 100).toFixed(2)} {invoice.currency.toUpperCase()}
              </td>
              <td style={{ padding: '0.75rem' }}>
                <span style={{ 
                  textTransform: 'capitalize',
                  padding: '0.2rem 0.5rem',
                  borderRadius: '4px',
                  fontSize: '0.85rem',
                  backgroundColor: invoice.status.toLowerCase() === 'paid' ? '#e6fffa' : '#fff5f5',
                  color: invoice.status.toLowerCase() === 'paid' ? '#2c7a7b' : '#c53030'
                }}>
                  {invoice.status}
                </span>
              </td>
              <td style={{ padding: '0.75rem' }}>
                {invoice.pdfUrl && invoice.pdfUrl !== '#' ? (
                  <a 
                    href={invoice.pdfUrl} 
                    target="_blank" 
                    rel="noopener noreferrer"
                    style={{ color: '#6772e5', textDecoration: 'none', fontWeight: 'bold' }}
                  >
                    View PDF
                  </a>
                ) : (
                  <span style={{ color: '#999', fontSize: '0.9rem' }}>Processing...</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
});

export default InvoiceTable;
