import { useState } from 'react';
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from './components/ui/resizable';
import { CodeEditor } from './components/CodeEditor';
import { NodeEditor } from './components/NodeEditor';
import { OutputConsole } from './components/OutputConsole';
import { Code2, Network, Terminal } from 'lucide-react';

export default function App() {
  const [output, setOutput] = useState<string[]>([]);

  const handleRunCode = (code: string) => {
    // Simulate code execution with mock output
    const timestamp = new Date().toLocaleTimeString();
    const mockOutput = [
      `<span class="text-blue-400">[${timestamp}]</span> <span class="text-green-400">Running code...</span>`,
      `<span class="text-gray-400">---</span>`,
      code.split('\n').filter(line => line.trim() && !line.trim().startsWith('#')).map(line => 
        `<span class="text-yellow-300">&gt;&gt;&gt;</span> ${line}`
      ).join('<br>'),
      `<span class="text-gray-400">---</span>`,
      `<span class="text-green-400">✓ Execution completed successfully</span>`,
      `<span class="text-gray-500">Output: Hello, World!</span>`,
      '',
    ];
    
    setOutput(prev => [...prev, ...mockOutput]);
  };

  const handleClearOutput = () => {
    setOutput([]);
  };

  const handleNodeSelect = (nodeId: string | null) => {
    if (nodeId) {
      const timestamp = new Date().toLocaleTimeString();
      setOutput(prev => [
        ...prev,
        `<span class="text-blue-400">[${timestamp}]</span> <span class="text-purple-400">Node selected: ${nodeId}</span>`,
      ]);
    }
  };

  const handleBackendUpdate = (data: any) => {
    const timestamp = new Date().toLocaleTimeString();
    setOutput(prev => [
      ...prev,
      `<span class="text-blue-400">[${timestamp}]</span> <span class="text-cyan-400">Backend sync:</span> <span class="text-gray-400">${data.nodes.length} nodes, ${data.edges.length} edges updated</span>`,
    ]);
  };

  return (
    <div className="h-screen w-screen bg-gray-900 flex flex-col">
      {/* Header */}
      <div className="h-12 bg-[#2d2d30] border-b border-[#3e3e42] flex items-center px-4">
        <Code2 className="w-5 h-5 text-blue-400 mr-2" />
        <h1 className="text-white font-semibold text-lg">Python IDE with Node Editor</h1>
      </div>

      {/* Main Content - 3 Panel Layout */}
      <div className="flex-1 overflow-hidden">
        <ResizablePanelGroup direction="horizontal">
          {/* Left Panel - Node Editor */}
          <ResizablePanel defaultSize={60} minSize={30}>
            <div className="h-full flex flex-col bg-white">
              <div className="flex items-center gap-2 px-3 py-2 bg-white border-b border-gray-200">
                <Network className="w-4 h-4 text-purple-500" />
                <span className="text-sm text-gray-700 font-medium">Visual Node Editor</span>
              </div>
              <div className="flex-1">
                <NodeEditor onNodeSelect={handleNodeSelect} onBackendUpdate={handleBackendUpdate} />
              </div>
            </div>
          </ResizablePanel>

          <ResizableHandle className="w-1 bg-[#3e3e42] hover:bg-blue-500 transition-colors" />

          {/* Right Side - Code Editor and Output Console stacked vertically */}
          <ResizablePanel defaultSize={40} minSize={25}>
            <ResizablePanelGroup direction="vertical">
              {/* Code Editor on top */}
              <ResizablePanel defaultSize={65} minSize={30}>
                <div className="h-full flex flex-col bg-[#1e1e1e]">
                  <div className="flex items-center gap-2 px-3 py-2 bg-[#252526] border-b border-[#3e3e42]">
                    <Code2 className="w-4 h-4 text-blue-400" />
                    <span className="text-sm text-gray-300 font-medium">Code Editor</span>
                  </div>
                  <div className="flex-1">
                    <CodeEditor onRun={handleRunCode} />
                  </div>
                </div>
              </ResizablePanel>

              <ResizableHandle className="h-1 bg-[#3e3e42] hover:bg-blue-500 transition-colors" />

              {/* Output Console on bottom */}
              <ResizablePanel defaultSize={35} minSize={20}>
                <div className="h-full flex flex-col bg-[#1e1e1e]">
                  <div className="flex items-center gap-2 px-3 py-2 bg-[#252526] border-b border-[#3e3e42]">
                    <Terminal className="w-4 h-4 text-green-400" />
                    <span className="text-sm text-gray-300 font-medium">Output</span>
                  </div>
                  <div className="flex-1">
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
        <span className="mr-4">● Ready</span>
        <span className="mr-4">Python 3.11</span>
        <span>UTF-8</span>
      </div>
    </div>
  );
}