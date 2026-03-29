import { useState } from 'react';
import { Play, Trash2 } from 'lucide-react';
import { Button } from './ui/button';

export function PythonTerminal() {
  const [code, setCode] = useState(`# Python Code Editor
def hello_world():
    print("Hello, World!")
    
hello_world()`);
  const [output, setOutput] = useState('');

  const handleRun = () => {
    // Simulated output
    setOutput(`>>> Running code...\nHello, World!\n>>> Execution complete`);
  };

  const handleClear = () => {
    setCode('');
    setOutput('');
  };

  return (
    <div className="flex flex-col h-full bg-gray-900 text-gray-100">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
        <h2 className="text-sm font-semibold">Python Terminal</h2>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="ghost"
            onClick={handleRun}
            className="h-7 text-green-400 hover:text-green-300 hover:bg-gray-700"
          >
            <Play className="h-4 w-4 mr-1" />
            Run
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={handleClear}
            className="h-7 text-red-400 hover:text-red-300 hover:bg-gray-700"
          >
            <Trash2 className="h-4 w-4 mr-1" />
            Clear
          </Button>
        </div>
      </div>
      
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex-1 overflow-auto">
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            className="w-full h-full px-4 py-3 bg-gray-900 text-gray-100 font-mono text-sm resize-none focus:outline-none"
            placeholder="Enter your Python code here..."
            spellCheck={false}
          />
        </div>
        
        {output && (
          <div className="border-t border-gray-700 bg-gray-950">
            <div className="px-4 py-2 bg-gray-800 text-xs font-semibold text-gray-400">
              OUTPUT
            </div>
            <div className="px-4 py-3 font-mono text-sm text-green-400 max-h-32 overflow-auto">
              {output}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
