import { useState, useEffect, useRef } from 'react';
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from './components/ui/resizable';
import { CodeEditor } from './components/CodeEditor';
import { NodeEditor } from './components/NodeEditor';
import { OutputConsole } from './components/OutputConsole';
import { Code2, Network, Terminal } from 'lucide-react';
import { generatePythonCode, GraphNode, GraphEdge } from './utils/compiler';
import { Button } from './components/ui/button';

export default function App() {
  const [output, setOutput] = useState<string[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [generatedCode, setGeneratedCode] = useState<string>('');

  const ws = useRef<WebSocket | null>(null);
  const graphState = useRef<{ nodes: GraphNode[]; edges: GraphEdge[] }>({
    nodes: [],
    edges: []
  });

  // Connect to WebSocket
  useEffect(() => {
    connectToWebSocket();
    return () => {
      if (ws.current) {
        ws.current.close();
      }
    };
  }, []);

  const connectToWebSocket = () => {
    try {
      ws.current = new WebSocket("ws://localhost:8080/ws");

      ws.current.onopen = () => {
        setIsConnected(true);
        setOutput(prev => [...prev,
          `<span class="text-green-400">✓ Connected to Python Execution Engine</span>`
        ]);
      };

      ws.current.onmessage = (event) => {
        const data = event.data;
        if (data.toLowerCase().includes('error') || data.toLowerCase().includes('traceback')) {
          setOutput(prev => [...prev, `<span class="text-red-400">${escapeHtml(data)}</span>`]);
        } else if (data.toLowerCase().includes('warning')) {
          setOutput(prev => [...prev, `<span class="text-yellow-400">${escapeHtml(data)}</span>`]);
        } else {
          setOutput(prev => [...prev, `<span class="text-gray-300">${escapeHtml(data)}</span>`]);
        }
      };

      ws.current.onerror = () => {
        setIsConnected(false);
        setOutput(prev => [...prev,
          `<span class="text-red-400">✗ Connection Error! Is backend running on port 8080?</span>`
        ]);
      };

      ws.current.onclose = () => {
        setIsConnected(false);
        setOutput(prev => [...prev,
          `<span class="text-yellow-400">⚠ Disconnected. Reconnecting in 5s...</span>`
        ]);
        setTimeout(() => connectToWebSocket(), 5000);
      };
    } catch (error) {
      console.error('WebSocket error:', error);
    }
  };

  // Run code from CodeEditor
  const handleRunCode = (code: string) => {
    const timestamp = new Date().toLocaleTimeString();

    setOutput(prev => [
      ...prev,
      `<span class="text-blue-400">[${timestamp}]</span> <span class="text-yellow-400">Executing Python code...</span>`,
      `<span class="text-gray-400">---</span>`
    ]);

    if (ws.current && ws.current.readyState === WebSocket.OPEN) {
      ws.current.send(code);
    } else {
      setOutput(prev => [...prev,
        `<span class="text-red-400">Error: Not connected to backend.</span>`
      ]);
    }
  };

  const handleClearOutput = () => {
    setOutput([]);
  };

  const handleNodeSelect = (nodeId: string | null) => {
    if (nodeId) {
      const timestamp = new Date().toLocaleTimeString();
      setOutput(prev => [
        ...prev,
        `<span class="text-blue-400">[${timestamp}]</span> <span class="text-purple-400">Selected node: ${nodeId}</span>`,
      ]);
    }
  };

  // Store graph updates
  const handleBackendUpdate = (data: any) => {
    graphState.current = {
      nodes: data.nodes || [],
      edges: data.edges || []
    };

    const timestamp = new Date().toLocaleTimeString();
    setOutput(prev => [
      ...prev,
      `<span class="text-blue-400">[${timestamp}]</span> <span class="text-cyan-400">Graph updated:</span> <span class="text-gray-400">${data.nodes.length} nodes, ${data.edges.length} edges</span>`,
    ]);
  };

  const escapeHtml = (unsafe: string): string => {
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
  };

  return (
      <div className="h-screen w-screen bg-gray-900 flex flex-col">
        {/* Header */}
        <div className="h-12 bg-[#2d2d30] border-b border-[#3e3e42] flex items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <Code2 className="w-5 h-5 text-blue-400 mr-2" />
            <h1 className="text-white font-semibold text-lg">Visual Object Mapper</h1>
          </div>

          <div className="flex items-center gap-3">
            <Button
                onClick={() => {
                  const code = generatePythonCode(graphState.current.nodes, graphState.current.edges);
                  setGeneratedCode(code);
                  setOutput(prev => [...prev, `<span class="text-green-400">✓ Code generated and loaded into editor</span>`]);
                }}
                className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded"
            >
              Generate Code
            </Button>

            <div className="flex items-center gap-2">
              <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
              <span className="text-xs text-gray-400">
              {isConnected ? 'Connected' : 'Disconnected'}
            </span>
            </div>
          </div>
        </div>

        {/* Main Content */}
        <div className="flex-1 overflow-hidden">
          <ResizablePanelGroup direction="horizontal">
            {/* Left - Node Editor */}
            <ResizablePanel defaultSize={50} minSize={30}>
              <div className="h-full flex flex-col bg-white">
                <div className="flex items-center gap-2 px-3 py-2 bg-white border-b border-gray-200">
                  <Network className="w-4 h-4 text-purple-500" />
                  <span className="text-sm text-gray-700 font-medium">Visual Node Map</span>
                </div>
                <div className="flex-1">
                  <NodeEditor
                      onNodeSelect={handleNodeSelect}
                      onBackendUpdate={handleBackendUpdate}
                  />
                </div>
              </div>
            </ResizablePanel>

            <ResizableHandle className="w-1 bg-[#3e3e42] hover:bg-blue-500 transition-colors" />

            {/* Right - Code Editor and Console */}
            <ResizablePanel defaultSize={50} minSize={25}>
              <ResizablePanelGroup direction="vertical">
                {/* Code Editor */}
                <ResizablePanel defaultSize={65} minSize={30}>
                  <div className="h-full flex flex-col bg-[#1e1e1e]">
                    <div className="flex items-center gap-2 px-3 py-2 bg-[#252526] border-b border-[#3e3e42]">
                      <Code2 className="w-4 h-4 text-blue-400" />
                      <span className="text-sm text-gray-300 font-medium">Code Editor</span>
                      {generatedCode && (
                          <span className="text-xs text-green-400 ml-auto">Code ready in console</span>
                      )}
                    </div>
                    <div className="flex-1">
                      <CodeEditor
                          onRun={handleRunCode}
                          generatedCode={generatedCode}
                      />
                    </div>
                  </div>
                </ResizablePanel>

                <ResizableHandle className="h-1 bg-[#3e3e42] hover:bg-blue-500 transition-colors" />

                {/* Output Console */}
                <ResizablePanel defaultSize={35} minSize={20}>
                  <div className="h-full flex flex-col bg-[#1e1e1e]">
                    <div className="flex items-center gap-2 px-3 py-2 bg-[#252526] border-b border-[#3e3e42]">
                      <Terminal className="w-4 h-4 text-green-400" />
                      <span className="text-sm text-gray-300 font-medium">Output Console</span>
                    </div>
                    <div className="flex-1 overflow-y-auto">
                      <OutputConsole output={output} onClear={handleClearOutput} />
                    </div>
                  </div>
                </ResizablePanel>
              </ResizablePanelGroup>
            </ResizablePanel>
          </ResizablePanelGroup>
        </div>

        {/* Status Bar */}
        <div className="h-6 bg-[#007acc] flex items-center px-4 text-xs text-white">
        <span className="mr-4 flex items-center gap-1">
          <div className={`w-1.5 h-1.5 rounded-full ${isConnected ? 'bg-green-300' : 'bg-red-300'}`} />
          {isConnected ? 'Ready' : 'Disconnected'}
        </span>
          <span className="mr-4">Python 3.12</span>
          <span className="mr-4">ws://localhost:8080/ws</span>
          <span className="ml-auto">
          Nodes: {graphState.current.nodes.length} | Edges: {graphState.current.edges.length}
        </span>
        </div>
      </div>
  );
}