import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const Dashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return <div>Loading...</div>;
  }

  const isCreator = user.role === 'CREATOR' || (user.role as string) === 'ROLE_CREATOR';

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h1>Welcome</h1>
      <div style={{ marginBottom: '20px' }}>
        <p><strong>Email:</strong> {user.email}</p>
        <p><strong>Role:</strong> {user.role}</p>
      </div>
      
      <div style={{ display: 'flex', gap: '10px' }}>
        {isCreator && (
          <button 
            onClick={() => navigate('/creator')}
            style={{
              padding: '10px 20px',
              backgroundColor: '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Creator Dashboard
          </button>
        )}
        <button 
          onClick={logout}
          style={{
            padding: '10px 20px',
            backgroundColor: '#dc3545',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
        >
          Logout
        </button>
      </div>
    </div>
  );
};

export default Dashboard;
