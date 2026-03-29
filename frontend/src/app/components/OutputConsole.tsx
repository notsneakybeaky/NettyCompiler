import { Terminal as TerminalIcon, X } from 'lucide-react';
import { Button } from './ui/button';

interface OutputConsoleProps {
  output: string[];
  onClear: () => void;
}

export function OutputConsole({ output, onClear }: OutputConsoleProps) {
  return (
    <div className="flex flex-col h-full bg-[#1e1e1e]">
      <div className="flex items-center justify-between px-4 py-2 bg-[#2d2d30] border-b border-[#3e3e42]">
        <div className="flex items-center gap-2">
          <TerminalIcon className="w-4 h-4 text-gray-400" />
          <span className="text-sm text-gray-300 font-mono">Console Output</span>
        </div>
        <Button
          size="sm"
          variant="ghost"
          className="h-7 text-gray-400 hover:text-white hover:bg-[#3e3e42]"
          onClick={onClear}
        >
          <X className="w-4 h-4" />
        </Button>
      </div>
      <div className="flex-1 overflow-auto p-4 font-mono text-sm">
        {output.length === 0 ? (
          <div className="text-gray-500 italic">
            Run your code to see output here...
          </div>
        ) : (
          output.map((line, index) => (
            <div
              key={index}
              className="text-gray-300 py-1"
              dangerouslySetInnerHTML={{ __html: line }}
            />
          ))
        )}
      </div>
    </div>
  );
}
