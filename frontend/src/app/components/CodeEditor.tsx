import { useState } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { python } from '@codemirror/lang-python';
import { Play, Copy, Trash2 } from 'lucide-react';
import { Button } from './ui/button';

interface CodeEditorProps {
  onRun: (code: string) => void;
}

export function CodeEditor({ onRun }: CodeEditorProps) {
  const [code, setCode] = useState(`# Python Code Editor
# Write your Python code here

def hello_world():
    print("Hello, World!")
    return "Success"

hello_world()
`);

  const handleRun = () => {
    onRun(code);
  };

  const handleClear = () => {
    setCode('');
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
  };

  return (
    <div className="flex flex-col h-full bg-[#1e1e1e]">
      <div className="flex items-center justify-between px-4 py-2 bg-[#2d2d30] border-b border-[#3e3e42]">
        <span className="text-sm text-gray-300 font-mono">editor.py</span>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="ghost"
            className="h-7 text-gray-400 hover:text-white hover:bg-[#3e3e42]"
            onClick={handleCopy}
          >
            <Copy className="w-4 h-4" />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="h-7 text-gray-400 hover:text-white hover:bg-[#3e3e42]"
            onClick={handleClear}
          >
            <Trash2 className="w-4 h-4" />
          </Button>
          <Button
            size="sm"
            className="h-7 bg-green-600 hover:bg-green-700 text-white"
            onClick={handleRun}
          >
            <Play className="w-4 h-4 mr-1" />
            Run
          </Button>
        </div>
      </div>
      <div className="flex-1 overflow-auto">
        <CodeMirror
          value={code}
          height="100%"
          theme="dark"
          extensions={[python()]}
          onChange={(value) => setCode(value)}
          basicSetup={{
            lineNumbers: true,
            highlightActiveLineGutter: true,
            highlightActiveLine: true,
            foldGutter: true,
          }}
          style={{ height: '100%' }}
        />
      </div>
    </div>
  );
}
