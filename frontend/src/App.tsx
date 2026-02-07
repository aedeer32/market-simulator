import React from 'react';
import MarketDashboard from './MarketDashboard';

const App: React.FC = () => {
  return (
    <div>
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 10,
          background: '#ffffff',
          borderBottom: '1px solid #e0e0e0',
          padding: '10px 12px',
          textAlign: 'center',
          fontWeight: 700,
        }}
      >
        📈 Market Simulator
      </header>
      <div style={{ paddingTop: 12 }}>
        <MarketDashboard />
      </div>
    </div>
  );
};

export default App;
