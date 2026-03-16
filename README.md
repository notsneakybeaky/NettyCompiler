# The Netty Compiler

**It's Flask but for multiplayer systems and networked applications.**

A general purpose Netty server where protocol and application logic are fully swappable plugins. Write Python scripts. Java executes them. Nobody needs to know Java is involved.

## Architecture

```
[ Netty Core ]         → networking, event loops, thread safety
[ Protocol Plugin ]    → packet shapes and state machine
[ Application Logic ]  → Python scripts via hot swap
```

Each layer only knows about the layer directly below it.

## Quick Start

### Docker (recommended)

```bash
docker-compose up
```

This starts:
- **Netty server** on port `25565` (game protocol) and `8080` (HTTP API)
- **Python worker** on port `8000` (FastAPI)

### Upload a Script

```bash
curl -X POST http://localhost:8080/scripts \
  -H "Content-Type: application/json" \
  -d '{
    "scriptId": "chat_logger",
    "hooks": ["on_packet"],
    "packetTypes": ["ChatMessage"],
    "source": "def handle(payload):\n    print(payload)\n    return {\"actions\": [{\"type\": \"FORWARD\", \"packetType\": payload[\"packetType\"], \"payload\": payload[\"payload\"]}]}"
  }'
```

Behavior changes instantly. Zero restart.

### List Scripts

```bash
curl http://localhost:8000/scripts
```

### Remove a Script

```bash
curl -X DELETE http://localhost:8000/scripts/chat_logger
```

## Project Structure

```
netty-runtime/
├── CLAUDE.md                  # Full spec — feed to Opus
├── PROMPT_TEMPLATE.md         # Prompt template for code generation
├── docker-compose.yml
├── build.gradle
│
├── netty-core/                # Layer 1: Netty Core
│   └── src/main/java/com/nettyruntime/
│       ├── core/              # Interfaces: Protocol, PacketHandler, Session
│       ├── codec/             # PacketCodec (generalized)
│       ├── registry/          # PacketRegistry (state + direction + id)
│       ├── session/           # DefaultSession (two-leg proxy)
│       ├── handler/           # ProtocolHandler, PythonHookBridge
│       └── server/            # NettyServer, ServerInitializer, HttpApiHandler
│
├── protocol-mc/               # Layer 2: MC Protocol Plugin
│   └── src/main/java/com/nettyruntime/mc/
│       ├── protocol/          # MinecraftProtocol, McPacket
│       └── packet/            # Handshaking, Status, Login, Play packets
│
├── python-worker/             # Layer 3: Application Logic
│   ├── main.py                # FastAPI worker
│   ├── nettyruntime.py        # Developer-facing decorator API
│   ├── requirements.txt
│   └── Dockerfile
│
├── flutter-dashboard/         # Layer 4: Visualization (Phase 3)
├── scripts/                   # Example Python scripts
└── data/                      # SQLite + schema
```

## JSON Contracts

### Java → Python (hook call)
```json
{
  "sessionId": "string",
  "hook": "on_packet | on_connect | on_disconnect | on_tick",
  "packetType": "string | null",
  "payload": {},
  "tick": 0
}
```

### Python → Java (hook response)
```json
{
  "actions": [
    { "type": "SEND | FORWARD | BLOCK | DISCONNECT", "packetType": "string", "payload": {} }
  ]
}
```

## Stop Conditions

| Phase | Done When |
|-------|-----------|
| 0 | Every class mapped. Layer violations identified. No code written. |
| 1 | Real MC client connects through proxy. Python script intercepts packets. |
| 2 | Two Docker containers. POST a script. Behavior changes. Kill Python → Netty survives. |
| 3 | Rewrite any layer completely. Other layers don't break. |

---
