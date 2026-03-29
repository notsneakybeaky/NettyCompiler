import React, { useState, useEffect } from 'react';

function App() {
  const [nodes, setNodes] = useState([]);
  const [newName, setNewName] = useState('');

  // 1. Fetch data from Express when the component loads
  useEffect(() => {
    fetchNodes();
  }, []);

  const fetchNodes = async () => {
    try {
      const response = await fetch('http://localhost:3000/api/nodes');
      const data = await response.json();
      setNodes(data);
    } catch (error) {
      console.error("Error fetching nodes:", error);
    }
  };

  // 2. Send data to Express when you create a new node
  const handleAddNode = async () => {
    if (!newName) return;

    try {
      await fetch('http://localhost:3000/api/nodes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newName })
      });
      
      setNewName(''); // Clear the input
      fetchNodes();   // Refresh the list to show the new node
    } catch (error) {
      console.error("Error saving node:", error);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h1>Platform Nodes</h1>
      
      {/* Control Panel */}
      <div style={{ marginBottom: '20px' }}>
        <input 
          type="text" 
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="New node name..."
          style={{ padding: '8px', marginRight: '10px' }}
        />
        <button onClick={handleAddNode} style={{ padding: '8px 16px' }}>
          Save to Database
        </button>
      </div>

      {/* Node List */}
      <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        {nodes.map((node) => (
          <div key={node._id} style={{ 
            border: '1px solid #ccc', padding: '15px', borderRadius: '8px', width: '200px' 
          }}>
            <h3>{node.name}</h3>
            <p>Status: {node.status}</p>
            <code>{node.code}</code>
          </div>
        ))}
      </div>
    </div>
  );
}

export default App;