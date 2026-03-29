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
  EdgeProps,
  getBezierPath,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { Settings, Trash2, Save, Plus, Link2 } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Textarea } from './ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';

// Custom Node Component
const CustomNode = memo(({ data, selected }: any) => {
  const getNodeColor = (type: string) => {
    switch (type?.toLowerCase()) {
      case 'kiosk':
        return 'border-blue-500 bg-blue-50';
      case 'warehouse':
        return 'border-green-500 bg-green-50';
      case 'sensor':
        return 'border-purple-500 bg-purple-50';
      case 'vehicle':
        return 'border-orange-500 bg-orange-50';
      case 'server':
        return 'border-red-500 bg-red-50';
      default:
        return 'border-gray-400 bg-gray-50';
    }
  };

  return (
      <div
          className={`px-4 py-3 shadow-lg rounded-lg border-2 transition-all ${
              selected ? 'ring-2 ring-blue-400 ring-offset-2' : ''
          } ${getNodeColor(data.type)}`}
          style={{ minWidth: 150 }}
      >
        <Handle
            type="target"
            position={Position.Left}
            className="w-3 h-3 bg-blue-600 border-2 border-white"
            id="target"
        />
        <div className="flex items-center justify-between mb-1">
          <div className="text-sm font-semibold text-gray-800">{data.label}</div>
          {data.type && (
              <div className="text-xs text-gray-600 bg-white px-2 py-0.5 rounded font-medium border border-gray-300">
                {data.type}
              </div>
          )}
        </div>
        {data.value && (
            <div className="text-xs text-gray-600 mt-2 font-mono bg-white px-2 py-1 rounded border border-gray-200">
              {data.value.split('\n').slice(0, 2).map((line: string, i: number) => (
                  <div key={i} className="truncate max-w-[180px]">{line}</div>
              ))}
              {data.value.split('\n').length > 2 && <div className="text-gray-400">...</div>}
            </div>
        )}
        <Handle
            type="source"
            position={Position.Right}
            className="w-3 h-3 bg-blue-600 border-2 border-white"
            id="source"
        />
      </div>
  );
});

CustomNode.displayName = 'CustomNode';

// Custom Edge with label
const CustomEdge = memo(({ id, sourceX, sourceY, targetX, targetY, label, markerEnd, selected }: EdgeProps & { selected?: boolean }) => {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
  });

  return (
      <>
        <path
            id={id}
            className={`react-flow__edge-path ${selected ? 'stroke-blue-500' : 'stroke-gray-400'}`}
            d={edgePath}
            strokeWidth={selected ? 3 : 2}
            markerEnd={markerEnd}
        />
        {label && (
            <g transform={`translate(${labelX}, ${labelY})`}>
              <rect
                  x={-30}
                  y={-10}
                  width={60}
                  height={20}
                  fill="white"
                  stroke={selected ? '#3b82f6' : '#9ca3af'}
                  strokeWidth={1}
                  rx={4}
              />
              <text
                  x={0}
                  y={4}
                  textAnchor="middle"
                  fontSize={11}
                  fontWeight={500}
                  fill={selected ? '#1e40af' : '#4b5563'}
              >
                {label}
              </text>
            </g>
        )}
      </>
  );
});

CustomEdge.displayName = 'CustomEdge';

const nodeTypes = { custom: CustomNode };
const edgeTypes = { custom: CustomEdge };

// Node templates for different object types
const NODE_TEMPLATES = {
  Kiosk: {
    label: 'Kiosk',
    type: 'Kiosk',
    value: 'location = "Mall A"\nitems = 50\nstatus = "active"',
  },
  Warehouse: {
    label: 'Warehouse',
    type: 'Warehouse',
    value: 'location = "District 5"\ncapacity = 10000\noccupancy = 7500',
  },
  Sensor: {
    label: 'Sensor',
    type: 'Sensor',
    value: 'sensor_id = "TEMP_001"\ntemperature = 22.5\nbattery = 85',
  },
  Vehicle: {
    label: 'Vehicle',
    type: 'Vehicle',
    value: 'vehicle_id = "TRUCK_01"\nspeed = 65\nfuel = 75',
  },
  Server: {
    label: 'Server',
    type: 'Server',
    value: 'hostname = "web-01"\ncpu_usage = 45\nmemory = 8192',
  },
  Custom: {
    label: 'Custom Node',
    type: 'Node',
    value: 'property1 = "value1"\nproperty2 = 100',
  },
};

const initialNodes: Node[] = [
  {
    id: '1',
    type: 'custom',
    position: { x: 100, y: 100 },
    data: { label: 'Kiosk_A', type: 'Kiosk', value: 'location = "Mall A"\nitems = 50\nstatus = "active"' },
  },
  {
    id: '2',
    type: 'custom',
    position: { x: 400, y: 100 },
    data: { label: 'Kiosk_B', type: 'Kiosk', value: 'location = "Mall B"\nitems = 30\nstatus = "offline"' },
  },
  {
    id: '3',
    type: 'custom',
    position: { x: 250, y: 250 },
    data: { label: 'Warehouse_1', type: 'Warehouse', value: 'location = "District 5"\ncapacity = 10000\noccupancy = 7500' },
  },
];

const initialEdges: Edge[] = [
  {
    id: 'e1-2',
    source: '1',
    target: '2',
    type: 'custom',
    label: 'distance: 5km',
    data: { label: 'distance: 5km', properties: { distance: '5km' } },
    animated: true
  },
  {
    id: 'e1-3',
    source: '1',
    target: '3',
    type: 'custom',
    label: 'supply',
    data: { label: 'supply', properties: { type: 'supply' } },
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
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null);
  const [nodeLabel, setNodeLabel] = useState('');
  const [nodeValue, setNodeValue] = useState('');
  const [nodeType, setNodeType] = useState('');
  const [edgeLabel, setEdgeLabel] = useState('');
  const [edgeProperties, setEdgeProperties] = useState('');
  const [lastSaved, setLastSaved] = useState<Date | null>(null);

  const memoizedNodeTypes = useMemo(() => nodeTypes, []);
  const memoizedEdgeTypes = useMemo(() => edgeTypes, []);

  // Backend sync
  const syncToBackend = useCallback((updatedNodes: Node[], updatedEdges: Edge[]) => {
    const backendData = {
      nodes: updatedNodes.map(n => ({
        id: n.id,
        type: n.data.type,
        label: n.data.label,
        value: n.data.value,
        position: n.position,
        data: n.data,
      })),
      edges: updatedEdges.map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        label: e.label,
        data: e.data,
      })),
      timestamp: new Date().toISOString(),
    };

    onBackendUpdate?.(backendData);
    setLastSaved(new Date());
  }, [onBackendUpdate]);

  const onConnect = useCallback(
      (params: Connection) => {
        const newEdge = {
          ...params,
          type: 'custom',
          animated: true,
          label: 'connection',
          data: { label: 'connection', properties: {} },
        };
        const newEdges = addEdge(newEdge, edges);
        setEdges(newEdges);
        syncToBackend(nodes, newEdges);
      },
      [edges, nodes, setEdges, syncToBackend]
  );

  const onNodeClick = useCallback(
      (_event: any, node: Node) => {
        setSelectedNode(node);
        setSelectedEdge(null);
        setNodeLabel(node.data.label || '');
        setNodeValue(node.data.value || '');
        setNodeType(node.data.type || '');
        onNodeSelect?.(node.id);
      },
      [onNodeSelect]
  );

  const onEdgeClick = useCallback(
      (_event: any, edge: Edge) => {
        setSelectedEdge(edge);
        setSelectedNode(null);
        setEdgeLabel(edge.label as string || '');
        setEdgeProperties(JSON.stringify(edge.data?.properties || {}, null, 2));
      },
      []
  );

  const onPaneClick = useCallback(() => {
    setSelectedNode(null);
    setSelectedEdge(null);
    onNodeSelect?.(null);
  }, [onNodeSelect]);

  const createNodeFromTemplate = useCallback(
      (templateKey: keyof typeof NODE_TEMPLATES, position: { x: number; y: number }) => {
        const template = NODE_TEMPLATES[templateKey];
        const newNode: Node = {
          id: `${Date.now()}`,
          type: 'custom',
          position,
          data: { ...template, label: `${template.type}_${Date.now()}` },
        };

        const newNodes = [...nodes, newNode];
        setNodes(newNodes);
        syncToBackend(newNodes, edges);

        return newNode;
      },
      [nodes, edges, setNodes, syncToBackend]
  );

  const onPaneDoubleClick = useCallback(
      (event: any) => {
        const bounds = event.target.getBoundingClientRect();
        const position = {
          x: event.clientX - bounds.left - 75,
          y: event.clientY - bounds.top - 25,
        };
        createNodeFromTemplate('Custom', position);
      },
      [createNodeFromTemplate]
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

    const updated = updatedNodes.find(n => n.id === selectedNode.id);
    if (updated) setSelectedNode(updated);
  };

  const handleUpdateEdge = () => {
    if (!selectedEdge) return;

    let parsedProps = {};
    try {
      parsedProps = JSON.parse(edgeProperties);
    } catch (e) {
      alert('Invalid JSON in edge properties');
      return;
    }

    const updatedEdges = edges.map((edge) => {
      if (edge.id === selectedEdge.id) {
        return {
          ...edge,
          label: edgeLabel,
          data: {
            label: edgeLabel,
            properties: parsedProps,
          },
        };
      }
      return edge;
    });

    setEdges(updatedEdges);
    syncToBackend(nodes, updatedEdges);

    const updated = updatedEdges.find(e => e.id === selectedEdge.id);
    if (updated) setSelectedEdge(updated);
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

  const handleDeleteEdge = () => {
    if (!selectedEdge) return;

    const updatedEdges = edges.filter((edge) => edge.id !== selectedEdge.id);
    setEdges(updatedEdges);
    syncToBackend(nodes, updatedEdges);
    setSelectedEdge(null);
  };

  const handleNodesChangeWithSync = useCallback((changes: any) => {
    onNodesChange(changes);
    const moveChanges = changes.filter((c: any) => c.type === 'position' && c.dragging === false);
    if (moveChanges.length > 0) {
      setTimeout(() => {
        setNodes((currentNodes) => {
          syncToBackend(currentNodes, edges);
          return currentNodes;
        });
      }, 300);
    }
  }, [onNodesChange, edges, syncToBackend, setNodes]);

  const QuickAddButtons = () => {
    const templateKeys: Array<keyof typeof NODE_TEMPLATES> = ['Kiosk', 'Warehouse', 'Sensor', 'Vehicle', 'Server', 'Custom'];

    return (
        <div className="flex gap-1">
          {templateKeys.map((key) => {
            const template = NODE_TEMPLATES[key];
            return (
                <Button
                    key={key}
                    size="sm"
                    variant="ghost"
                    className="h-7 px-2 text-xs hover:bg-gray-100"
                    onClick={() => {
                      const position = {
                        x: Math.random() * 400 + 100,
                        y: Math.random() * 300 + 100,
                      };
                      createNodeFromTemplate(key, position);
                    }}
                    title={`Add ${template.label}`}
                >
                  <Plus className="w-3 h-3 mr-1" />
                  {template.type}
                </Button>
            );
          })}
        </div>
    );
  };

  return (
      <div className="flex flex-col h-full bg-gray-50">
        <div className="flex items-center justify-between px-4 py-2 bg-white border-b border-gray-200">
          <div className="flex items-center gap-3">
            <span className="text-sm font-semibold text-gray-700">Object Map Editor</span>
            {lastSaved && (
                <span className="text-xs text-green-600 flex items-center gap-1">
              <Save className="w-3 h-3" />
                  {lastSaved.toLocaleTimeString()}
            </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <QuickAddButtons />
          </div>
        </div>
        <div className="flex-1 relative overflow-hidden">
          <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={handleNodesChangeWithSync}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={onNodeClick}
              onEdgeClick={onEdgeClick}
              onPaneClick={onPaneClick}
              onDoubleClick={onPaneDoubleClick}
              nodeTypes={memoizedNodeTypes}
              edgeTypes={memoizedEdgeTypes}
              fitView
          >
            <Background />
            <Controls />
            <MiniMap
                nodeColor={(node) => {
                  switch (node.data?.type?.toLowerCase()) {
                    case 'kiosk': return '#3b82f6';
                    case 'warehouse': return '#10b981';
                    case 'sensor': return '#8b5cf6';
                    case 'vehicle': return '#f97316';
                    case 'server': return '#ef4444';
                    default: return '#9ca3af';
                  }
                }}
            />
            <Panel position="bottom-left" className="bg-white/90 backdrop-blur-sm px-3 py-2 rounded-lg text-xs text-gray-600">
              Objects: {nodes.length} | Connections: {edges.length}
            </Panel>
          </ReactFlow>

          {/* Node Properties Panel */}
          {selectedNode && (
              <div className="absolute right-4 top-4 w-72 bg-white rounded-lg shadow-xl border border-gray-200 p-4 z-50 max-h-[calc(100%-2rem)] overflow-y-auto">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Settings className="w-4 h-4 text-gray-600" />
                    <span className="text-sm font-semibold text-gray-700">Object Properties</span>
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
                    <Label className="text-xs text-gray-500">Object ID</Label>
                    <Input value={selectedNode.id} disabled className="h-8 text-sm mt-1 bg-gray-50" />
                  </div>
                  <div>
                    <Label className="text-xs">Type</Label>
                    <Select value={nodeType} onValueChange={setNodeType}>
                      <SelectTrigger className="h-8 text-sm mt-1">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="Kiosk">Kiosk</SelectItem>
                        <SelectItem value="Warehouse">Warehouse</SelectItem>
                        <SelectItem value="Sensor">Sensor</SelectItem>
                        <SelectItem value="Vehicle">Vehicle</SelectItem>
                        <SelectItem value="Server">Server</SelectItem>
                        <SelectItem value="Node">Custom</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div>
                    <Label className="text-xs">Label</Label>
                    <Input value={nodeLabel} onChange={(e) => setNodeLabel(e.target.value)} className="h-8 text-sm mt-1" />
                  </div>
                  <div>
                    <Label className="text-xs">Properties (key = value format)</Label>
                    <Textarea
                        value={nodeValue}
                        onChange={(e) => setNodeValue(e.target.value)}
                        className="text-sm mt-1 font-mono min-h-24"
                        placeholder="location = &quot;Mall A&quot;\nitems = 50\nstatus = &quot;active&quot;"
                    />
                  </div>
                  <Button size="sm" onClick={handleUpdateNode} className="w-full h-9 bg-blue-600 hover:bg-blue-700">
                    <Save className="w-4 h-4 mr-2" />
                    Save Changes
                  </Button>
                </div>
              </div>
          )}

          {/* Edge Properties Panel */}
          {selectedEdge && (
              <div className="absolute right-4 top-4 w-72 bg-white rounded-lg shadow-xl border border-gray-200 p-4 z-50">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Link2 className="w-4 h-4 text-gray-600" />
                    <span className="text-sm font-semibold text-gray-700">Connection Properties</span>
                  </div>
                  <Button
                      size="sm"
                      variant="ghost"
                      onClick={handleDeleteEdge}
                      className="h-7 w-7 p-0 text-red-600 hover:text-red-700 hover:bg-red-50"
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
                <div className="space-y-3">
                  <div>
                    <Label className="text-xs">Label</Label>
                    <Input value={edgeLabel} onChange={(e) => setEdgeLabel(e.target.value)} className="h-8 text-sm mt-1" placeholder="distance: 5km" />
                  </div>
                  <div>
                    <Label className="text-xs">Properties (JSON)</Label>
                    <Textarea
                        value={edgeProperties}
                        onChange={(e) => setEdgeProperties(e.target.value)}
                        className="text-sm mt-1 font-mono min-h-20"
                        placeholder='{\n  "distance": "5km",\n  "type": "road"\n}'
                    />
                  </div>
                  <Button size="sm" onClick={handleUpdateEdge} className="w-full h-9 bg-blue-600 hover:bg-blue-700">
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