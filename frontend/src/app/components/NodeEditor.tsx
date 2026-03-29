import { useCallback, useState, memo, useMemo } from 'react';
import ReactFlow, {
  Node,
  Edge,
  addEdge,
  Connection,
  useNodesState,
  useEdgesState,
  Controls,
  Background,
  MiniMap,
  Panel,
  Handle,
  Position,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { Settings, Trash2, Save } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Textarea } from './ui/textarea';

// Custom Node Component - memoized and defined outside
const CustomNode = memo(({ data, selected }: any) => {
  return (
    <div
      className={`px-4 py-3 shadow-lg rounded-lg border-2 bg-white transition-all ${
        selected ? 'border-blue-500 shadow-xl' : 'border-gray-300'
      }`}
      style={{ minWidth: 150 }}
    >
      <Handle 
        type="target" 
        position={Position.Left} 
        className="w-3 h-3"
        id="target"
      />
      <div className="flex items-center justify-between mb-2">
        <div className="text-sm font-semibold text-gray-700">{data.label}</div>
        {data.type && (
          <div className="text-xs text-gray-500 bg-gray-100 px-2 py-1 rounded">
            {data.type}
          </div>
        )}
      </div>
      {data.value !== undefined && (
        <div className="text-xs text-gray-600 mt-1">
          <span className="font-mono">{data.value}</span>
        </div>
      )}
      <Handle 
        type="source" 
        position={Position.Right} 
        className="w-3 h-3"
        id="source"
      />
    </div>
  );
});

CustomNode.displayName = 'CustomNode';

// Define nodeTypes outside component to avoid recreation
const nodeTypes = {
  custom: CustomNode,
};

const initialNodes: Node[] = [
  {
    id: '1',
    type: 'custom',
    position: { x: 100, y: 100 },
    data: { label: 'Input Node', type: 'input', value: 'x = 10' },
  },
  {
    id: '2',
    type: 'custom',
    position: { x: 300, y: 100 },
    data: { label: 'Process Node', type: 'function', value: 'multiply(x, 2)' },
  },
  {
    id: '3',
    type: 'custom',
    position: { x: 500, y: 100 },
    data: { label: 'Output Node', type: 'output', value: 'result' },
  },
];

const initialEdges: Edge[] = [
  { 
    id: 'e1-2', 
    source: '1', 
    target: '2', 
    sourceHandle: 'source',
    targetHandle: 'target',
    animated: true 
  },
  { 
    id: 'e2-3', 
    source: '2', 
    target: '3',
    sourceHandle: 'source',
    targetHandle: 'target',
    animated: true 
  },
];

interface NodeEditorProps {
  onNodeSelect?: (nodeId: string | null) => void;
  onBackendUpdate?: (data: any) => void;
}

export function NodeEditor({ onNodeSelect, onBackendUpdate }: NodeEditorProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [nodeLabel, setNodeLabel] = useState('');
  const [nodeValue, setNodeValue] = useState('');
  const [nodeType, setNodeType] = useState('');
  const [lastSaved, setLastSaved] = useState<Date | null>(null);

  // Memoize nodeTypes to prevent recreation warning
  const memoizedNodeTypes = useMemo(() => nodeTypes, []);

  // Simulate backend sync
  const syncToBackend = useCallback((updatedNodes: Node[], updatedEdges: Edge[]) => {
    const backendData = {
      nodes: updatedNodes.map(n => ({
        id: n.id,
        type: n.data.type,
        label: n.data.label,
        value: n.data.value,
        position: n.position,
      })),
      edges: updatedEdges.map(e => ({
        source: e.source,
        target: e.target,
      })),
      timestamp: new Date().toISOString(),
    };
    
    onBackendUpdate?.(backendData);
    setLastSaved(new Date());
    console.log('Synced to backend:', backendData);
  }, [onBackendUpdate]);

  const onConnect = useCallback(
    (params: Connection) => {
      const newEdges = addEdge(params, edges);
      setEdges(newEdges);
      syncToBackend(nodes, newEdges);
    },
    [edges, nodes, setEdges, syncToBackend]
  );

  const onNodeClick = useCallback(
    (_event: any, node: Node) => {
      setSelectedNode(node);
      setNodeLabel(node.data.label || '');
      setNodeValue(node.data.value || '');
      setNodeType(node.data.type || '');
      onNodeSelect?.(node.id);
    },
    [onNodeSelect]
  );

  const onPaneClick = useCallback(() => {
    setSelectedNode(null);
    onNodeSelect?.(null);
  }, [onNodeSelect]);

  // Double click on pane to create new node
  const onPaneDoubleClick = useCallback(
    (event: any) => {
      const bounds = event.target.getBoundingClientRect();
      const position = {
        x: event.clientX - bounds.left - 75,
        y: event.clientY - bounds.top - 25,
      };

      const newNode: Node = {
        id: `${Date.now()}`,
        type: 'custom',
        position,
        data: { label: 'New Node', type: 'custom', value: '' },
      };
      
      const newNodes = [...nodes, newNode];
      setNodes(newNodes);
      syncToBackend(newNodes, edges);
    },
    [nodes, edges, setNodes, syncToBackend]
  );

  const handleUpdateNode = () => {
    if (!selectedNode) return;

    const updatedNodes = nodes.map((node) => {
      if (node.id === selectedNode.id) {
        return {
          ...node,
          data: {
            ...node.data,
            label: nodeLabel,
            value: nodeValue,
            type: nodeType,
          },
        };
      }
      return node;
    });
    
    setNodes(updatedNodes);
    syncToBackend(updatedNodes, edges);
    
    // Update selected node reference
    const updated = updatedNodes.find(n => n.id === selectedNode.id);
    if (updated) setSelectedNode(updated);
  };

  const handleDeleteNode = () => {
    if (!selectedNode) return;
    
    const updatedNodes = nodes.filter((node) => node.id !== selectedNode.id);
    const updatedEdges = edges.filter(
      (edge) => edge.source !== selectedNode.id && edge.target !== selectedNode.id
    );
    
    setNodes(updatedNodes);
    setEdges(updatedEdges);
    syncToBackend(updatedNodes, updatedEdges);
    setSelectedNode(null);
  };

  const handleNodesChangeWithSync = useCallback((changes: any) => {
    onNodesChange(changes);
    // Sync position changes to backend after a short delay
    const moveChanges = changes.filter((c: any) => c.type === 'position' && c.dragging === false);
    if (moveChanges.length > 0) {
      setTimeout(() => {
        syncToBackend(nodes, edges);
      }, 300);
    }
  }, [onNodesChange, nodes, edges, syncToBackend]);

  return (
    <div className="flex flex-col h-full bg-gray-50">
      <div className="flex items-center justify-between px-4 py-2 bg-white border-b border-gray-200">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-gray-700">Node Editor</span>
          {lastSaved && (
            <span className="text-xs text-green-600 flex items-center gap-1">
              <Save className="w-3 h-3" />
              Saved {lastSaved.toLocaleTimeString()}
            </span>
          )}
        </div>
        <span className="text-xs text-gray-500">Double-click canvas to add node</span>
      </div>
      <div className="flex-1 relative overflow-hidden">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={handleNodesChangeWithSync}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeClick={onNodeClick}
          onPaneClick={onPaneClick}
          onDoubleClick={onPaneDoubleClick}
          nodeTypes={memoizedNodeTypes}
          fitView
        >
          <Background />
          <Controls />
          <MiniMap />
          <Panel position="bottom-left" className="bg-white/90 backdrop-blur-sm px-3 py-2 rounded-lg text-xs text-gray-600">
            Nodes: {nodes.length} | Connections: {edges.length}
          </Panel>
        </ReactFlow>
        
        {/* Node Properties Panel - contained within node editor */}
        {selectedNode && (
          <div className="absolute right-4 top-4 w-72 bg-white rounded-lg shadow-xl border border-gray-200 p-4 z-50 max-h-[calc(100%-2rem)] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Settings className="w-4 h-4 text-gray-600" />
                <span className="text-sm font-semibold text-gray-700">Node Properties</span>
              </div>
              <Button
                size="sm"
                variant="ghost"
                onClick={handleDeleteNode}
                className="h-7 w-7 p-0 text-red-600 hover:text-red-700 hover:bg-red-50"
              >
                <Trash2 className="w-4 h-4" />
              </Button>
            </div>
            <div className="space-y-3">
              <div>
                <Label htmlFor="node-id" className="text-xs text-gray-500">Node ID</Label>
                <Input
                  id="node-id"
                  value={selectedNode.id}
                  disabled
                  className="h-8 text-sm mt-1 bg-gray-50"
                />
              </div>
              <div>
                <Label htmlFor="node-type" className="text-xs">Type</Label>
                <Input
                  id="node-type"
                  value={nodeType}
                  onChange={(e) => setNodeType(e.target.value)}
                  placeholder="input, function, output..."
                  className="h-8 text-sm mt-1"
                />
              </div>
              <div>
                <Label htmlFor="node-label" className="text-xs">Label</Label>
                <Input
                  id="node-label"
                  value={nodeLabel}
                  onChange={(e) => setNodeLabel(e.target.value)}
                  className="h-8 text-sm mt-1"
                />
              </div>
              <div>
                <Label htmlFor="node-value" className="text-xs">Value / Code</Label>
                <Textarea
                  id="node-value"
                  value={nodeValue}
                  onChange={(e) => setNodeValue(e.target.value)}
                  className="text-sm mt-1 font-mono min-h-20"
                  placeholder="Enter node value or code..."
                />
              </div>
              <Button
                size="sm"
                onClick={handleUpdateNode}
                className="w-full h-9 bg-blue-600 hover:bg-blue-700"
              >
                <Save className="w-4 h-4 mr-2" />
                Save Changes
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}