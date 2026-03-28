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
    script_id: str
    source: str
    hooks: list[str] = []
    packet_types: list[str] = []


class HookPayload(BaseModel):
    """Matches the JSON contract sent by PythonHookBridge (snake_case)."""
    session_id: str
    hook: str
    message_type: Optional[str] = None
    payload: dict[str, Any] = {}


# --- Script Management ---

@app.post("/scripts/load")
async def load_script(payload: ScriptPayload):
    """Compile and register a script. Hot swap = dict key overwrite."""
    try:
        namespace = {}
        exec(compile(payload.source, payload.script_id, "exec"), namespace)

        if "handle" not in namespace:
            raise ValueError("Script must define a 'handle' function")

        registry[payload.script_id] = {
            "handle": namespace["handle"],
            "hooks": payload.hooks,
            "packet_types": payload.packet_types,
        }

        return {"status": "loaded", "script_id": payload.script_id}

    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.delete("/scripts/{script_id}")
async def unload_script(script_id: str):
    """Remove a script from the registry."""
    if script_id not in registry:
        raise HTTPException(status_code=404, detail=f"Script '{script_id}' not found")
    del registry[script_id]
    return {"status": "unloaded", "script_id": script_id}


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

        # Filter: for on_packet, only call scripts registered for this message type
        if hook == "on_packet" and entry["packet_types"]:
            if payload.message_type not in entry["packet_types"]:
                continue

        fn = entry["handle"]
        try:
            if asyncio.iscoroutinefunction(fn):
                result = await fn(payload.model_dump())
            else:
                result = fn(payload.model_dump())
            results.append({"script_id": script_id, "result": result})
        except Exception as e:
            results.append({"script_id": script_id, "error": str(e)})

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
