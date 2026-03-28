"""
Python Worker — FastAPI service for script registry and hook dispatch.
Knows about: JSON contract from PythonHookBridge.
Does NOT know: Java exists. Netty exists. MC protocol internals.
"""

import asyncio
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Any, Optional

app = FastAPI(title="NettyCompiler Python Worker", version="2.0")

# --- Script Registry ---
# scriptId -> { "handle": callable, "hooks": list, "packet_types": list }
registry: dict[str, dict] = {}


class ScriptPayload(BaseModel):
    scriptId: str
    source: str
    hooks: list[str] = []
    packetTypes: list[str] = []


class HookPayload(BaseModel):
    sessionId: str
    hook: str
    packetType: Optional[str] = None
    payload: dict[str, Any] = {}
    tick: int = 0


# --- Script Management ---

@app.post("/scripts/load")
async def load_script(payload: ScriptPayload):
    """Compile and register a script. Hot swap = dict key overwrite."""
    try:
        namespace = {}
        exec(compile(payload.source, payload.scriptId, "exec"), namespace)

        if "handle" not in namespace:
            raise ValueError("Script must define a 'handle' function")

        registry[payload.scriptId] = {
            "handle": namespace["handle"],
            "hooks": payload.hooks,
            "packet_types": payload.packetTypes,
        }

        return {"status": "loaded", "scriptId": payload.scriptId}

    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.delete("/scripts/{script_id}")
async def unload_script(script_id: str):
    """Remove a script from the registry."""
    if script_id not in registry:
        raise HTTPException(status_code=404, detail=f"Script '{script_id}' not found")
    del registry[script_id]
    return {"status": "unloaded", "scriptId": script_id}


@app.get("/scripts")
async def list_scripts():
    """List all registered script IDs."""
    return {"scripts": list(registry.keys())}


# --- Hook Dispatch ---

async def _dispatch(hook: str, payload: HookPayload) -> dict:
    """Dispatch a hook call to all matching scripts."""
    results = []

    for script_id, entry in registry.items():
        # Filter: only call scripts registered for this hook
        if entry["hooks"] and hook not in entry["hooks"]:
            continue

        # Filter: for on_packet, only call scripts registered for this packet type
        if hook == "on_packet" and entry["packet_types"]:
            if payload.packetType not in entry["packet_types"]:
                continue

        fn = entry["handle"]
        try:
            if asyncio.iscoroutinefunction(fn):
                result = await fn(payload.model_dump())
            else:
                result = fn(payload.model_dump())
            results.append({"scriptId": script_id, "result": result})
        except Exception as e:
            results.append({"scriptId": script_id, "error": str(e)})

    # Merge actions from all scripts
    actions = []
    for r in results:
        if isinstance(r.get("result"), dict) and "actions" in r["result"]:
            actions.extend(r["result"]["actions"])

    return {"results": results, "actions": actions}


@app.post("/hooks/connect")
async def on_connect(payload: HookPayload):
    return await _dispatch("on_connect", payload)


@app.post("/hooks/packet")
async def on_packet(payload: HookPayload):
    return await _dispatch("on_packet", payload)


@app.post("/hooks/disconnect")
async def on_disconnect(payload: HookPayload):
    return await _dispatch("on_disconnect", payload)


@app.post("/hooks/tick")
async def on_tick(payload: HookPayload):
    return await _dispatch("on_tick", payload)


# --- Health ---

@app.get("/health")
async def health():
    return {"status": "running", "scripts_loaded": len(registry)}
