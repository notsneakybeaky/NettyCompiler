from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from typing import Any, Optional, Callable
import asyncio

app = FastAPI(title="NettyWorker", version="2.1")

# The single active script handler for this container
active_handler: Optional[Callable] = None
# A simple persistent dictionary for the script to use
persistent_state: dict[str, Any] = {}

class HotFlashPayload(BaseModel):
    source: str
    script_id: Optional[str] = None # No longer strictly required

@app.post("/hot-flash")
async def hot_flash(payload: HotFlashPayload):
    """Compiles and sets the global handler without clearing persistent_state."""
    global active_handler
    try:
        namespace = {"__state__": persistent_state}
        exec(compile(payload.source, "<string>", "exec"), namespace)

        if "handle" not in namespace:
            raise ValueError("Script must define a 'handle(payload)' function")

        active_handler = namespace["handle"]
        return {"status": "success", "detail": "Hot-flashed new logic"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/hooks/{hook_name}")
async def dispatch_hook(hook_name: str, request: Request):
    """Generic hook dispatcher for connect, packet, disconnect, etc."""
    global active_handler
    if not active_handler:
        return {"status": "no_script", "actions": []}

    payload = await request.json()

    try:
        if asyncio.iscoroutinefunction(active_handler):
            result = await active_handler(payload)
        else:
            result = active_handler(payload)

        # Ensure result is a dict with an actions list
        actions = result.get("actions", []) if isinstance(result, dict) else []
        return {"status": "ok", "actions": actions}
    except Exception as e:
        return {"status": "error", "error": str(e), "actions": []}

@app.post("/reset")
async def reset():
    """Wipe everything for container reuse."""
    global active_handler
    active_handler = None
    persistent_state.clear()
    return {"status": "reset"}

@app.get("/health")
async def health():
    return {"status": "running", "has_script": active_handler is not None}