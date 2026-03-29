// frontend/src/app/utils/compiler.ts
// Object Mapper Compiler - Generates Python class instances from visual graph

export interface GraphNode {
    id: string;
    type?: string;
    position: { x: number; y: number };
    data: {
        label?: string;
        type?: string;
        value?: string;
        properties?: Record<string, any>;
    };
}

export interface GraphEdge {
    id: string;
    source: string;
    target: string;
    data?: {
        label?: string;
        properties?: Record<string, any>;
    };
}

/**
 * Generates Python code from visual object map
 */
export function generatePythonCode(nodes: GraphNode[], edges: GraphEdge[]): string {
    if (!nodes || nodes.length === 0) {
        return '# No nodes in graph\n';
    }

    let code = '';

    // 1. Header
    code += '# Auto-generated from Visual Object Mapper\n';
    code += `# Generated at: ${new Date().toLocaleString()}\n`;
    code += '# Edit this code as needed\n\n';
    code += 'import json\n';
    code += 'from typing import Dict, List, Any\n\n';

    // 2. Generate class definitions (one per node type)
    const nodeTypes = getUniqueNodeTypes(nodes);
    nodeTypes.forEach(nodeType => {
        code += generateClassDefinition(nodeType, nodes);
    });

    // 3. Generate instances
    code += '\n# === Instance Creation ===\n\n';
    nodes.forEach(node => {
        code += generateInstance(node);
    });

    // 4. Generate connections/relationships
    if (edges.length > 0) {
        code += '\n# === Connections ===\n\n';
        code += generateConnections(edges, nodes);
    }

    // 5. Add helper functions
    code += '\n# === Helper Functions ===\n\n';
    code += generateHelperFunctions();

    return code;
}

/**
 * Get unique node types from graph
 */
function getUniqueNodeTypes(nodes: GraphNode[]): string[] {
    const types = new Set<string>();
    nodes.forEach(node => {
        const type = node.data?.type || 'Node';
        types.add(type);
    });
    return Array.from(types);
}

/**
 * Generate class definition for a node type
 */
function generateClassDefinition(nodeType: string, nodes: GraphNode[]): string {
    // Find all nodes of this type to determine common properties
    const typeNodes = nodes.filter(n => (n.data?.type || 'Node') === nodeType);
    const properties = extractCommonProperties(typeNodes);

    let code = `class ${nodeType}:\n`;
    code += `    """${nodeType} instance"""\n`;
    code += `    def __init__(self, id: str, label: str`;

    // Add properties as parameters
    properties.forEach(prop => {
        code += `, ${prop}: Any = None`;
    });

    code += '):\n';
    code += '        self.id = id\n';
    code += '        self.label = label\n';

    properties.forEach(prop => {
        code += `        self.${prop} = ${prop}\n`;
    });

    code += '\n    def to_dict(self) -> Dict[str, Any]:\n';
    code += '        return {\n';
    code += '            "id": self.id,\n';
    code += '            "label": self.label,\n';
    properties.forEach(prop => {
        code += `            "${prop}": self.${prop},\n`;
    });
    code += '        }\n';

    code += '\n    def __repr__(self):\n';
    code += `        return f"${nodeType}({{self.label}})"\n`;

    code += '\n';
    return code;
}

/**
 * Extract property names from nodes
 */
function extractCommonProperties(nodes: GraphNode[]): string[] {
    const propSet = new Set<string>();

    nodes.forEach(node => {
        const value = node.data?.value || '';
        const lines = value.split('\n');

        lines.forEach(line => {
            const trimmed = line.trim();
            // Match: property_name = value or property_name: value
            const match = trimmed.match(/^(\w+)\s*[:=]/);
            if (match) {
                propSet.add(match[1]);
            }
        });

        // Also check data.properties if exists
        if (node.data?.properties) {
            Object.keys(node.data.properties).forEach(key => propSet.add(key));
        }
    });

    return Array.from(propSet);
}

/**
 * Parse node value into properties object
 */
function parseNodeProperties(value: string): Record<string, any> {
    const props: Record<string, any> = {};
    const lines = value.split('\n');

    lines.forEach(line => {
        const trimmed = line.trim();
        if (!trimmed) return;

        // Match: key = value or key: value
        const match = trimmed.match(/^(\w+)\s*[:=]\s*(.+)$/);
        if (match) {
            const [, key, val] = match;
            // Try to parse as JSON, otherwise keep as string
            try {
                props[key] = JSON.parse(val);
            } catch {
                // Remove quotes if present
                props[key] = val.replace(/^["']|["']$/g, '');
            }
        }
    });

    return props;
}

/**
 * Generate instance creation code
 */
function generateInstance(node: GraphNode): string {
    const nodeType = node.data?.type || 'Node';
    const label = node.data?.label || `node_${node.id}`;
    const varName = sanitizeVarName(label, node.id);

    const props = parseNodeProperties(node.data?.value || '');

    let code = `${varName} = ${nodeType}(\n`;
    code += `    id="${node.id}",\n`;
    code += `    label="${label}",\n`;

    Object.entries(props).forEach(([key, value]) => {
        const valueStr = typeof value === 'string' ? `"${value}"` : JSON.stringify(value);
        code += `    ${key}=${valueStr},\n`;
    });

    code += ')\n';
    return code;
}

/**
 * Generate connections/edges code
 */
function generateConnections(edges: GraphEdge[], nodes: GraphNode[]): string {
    let code = 'connections = [\n';

    edges.forEach(edge => {
        const sourceNode = nodes.find(n => n.id === edge.source);
        const targetNode = nodes.find(n => n.id === edge.target);

        if (!sourceNode || !targetNode) return;

        const sourceVar = sanitizeVarName(sourceNode.data?.label || '', edge.source);
        const targetVar = sanitizeVarName(targetNode.data?.label || '', edge.target);

        const edgeProps = edge.data?.properties || {};
        const edgeLabel = edge.data?.label || '';

        code += '    {\n';
        code += `        "from": ${sourceVar},\n`;
        code += `        "to": ${targetVar},\n`;

        if (edgeLabel) {
            code += `        "label": "${edgeLabel}",\n`;
        }

        Object.entries(edgeProps).forEach(([key, value]) => {
            const valueStr = typeof value === 'string' ? `"${value}"` : JSON.stringify(value);
            code += `        "${key}": ${valueStr},\n`;
        });

        code += '    },\n';
    });

    code += ']\n';
    return code;
}

/**
 * Generate helper utility functions
 */
function generateHelperFunctions(): string {
    return `def print_all_instances():
    """Print all instances"""
    instances = [obj for obj in globals().values() if hasattr(obj, 'to_dict')]
    for instance in instances:
        print(f"{instance}: {instance.to_dict()}")

def export_graph_json():
    """Export entire graph as JSON"""
    instances = [obj for obj in globals().values() if hasattr(obj, 'to_dict')]
    graph = {
        "nodes": [inst.to_dict() for inst in instances],
        "connections": connections if 'connections' in globals() else []
    }
    return json.dumps(graph, indent=2)

# Uncomment to run:
# print_all_instances()
# print(export_graph_json())
`;
}

/**
 * Sanitize variable name
 */
function sanitizeVarName(label: string, fallbackId: string): string {
    if (!label) return `node_${fallbackId.replace(/[^a-zA-Z0-9]/g, '_')}`;

    // Convert to snake_case and remove invalid chars
    let varName = label
        .toLowerCase()
        .replace(/\s+/g, '_')
        .replace(/[^a-zA-Z0-9_]/g, '');

    // Ensure starts with letter
    if (!/^[a-zA-Z]/.test(varName)) {
        varName = 'node_' + varName;
    }

    return varName;
}

/**
 * Validate graph structure
 */
export function validateGraph(nodes: GraphNode[], edges: GraphEdge[]): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!nodes || nodes.length === 0) {
        errors.push("Graph has no nodes");
    }

    // Check for duplicate IDs
    const ids = nodes.map(n => n.id);
    const duplicates = ids.filter((id, index) => ids.indexOf(id) !== index);
    if (duplicates.length > 0) {
        errors.push(`Duplicate node IDs: ${duplicates.join(', ')}`);
    }

    // Check edges reference valid nodes
    edges.forEach(edge => {
        if (!nodes.find(n => n.id === edge.source)) {
            errors.push(`Edge ${edge.id} references invalid source: ${edge.source}`);
        }
        if (!nodes.find(n => n.id === edge.target)) {
            errors.push(`Edge ${edge.id} references invalid target: ${edge.target}`);
        }
    });

    return {
        valid: errors.length === 0,
        errors
    };
}