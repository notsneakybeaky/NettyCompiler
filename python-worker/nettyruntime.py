"""
nettyruntime — the developer-facing Python surface.
Script authors import from this module. They think they're writing a Python server.
They're not. Netty is running it. This module just provides the decorator API
that registers functions into the hook registry.

Usage:
    from nettyruntime import on_connect, on_packet, on_tick, on_disconnect
"""

from typing import Callable, Optional

# Global hook registry — populated by decorators, consumed by main.py dispatch
_hooks: dict[str, list[dict]] = {
    "on_connect": [],
    "on_packet": [],
    "on_tick": [],
    "on_disconnect": [],
}


def on_connect(fn: Callable) -> Callable:
    """Register a function to be called when a client connects."""
    _hooks["on_connect"].append({"fn": fn, "packet_types": [], "interval": None})
    return fn


def on_packet(packet_type: Optional[str] = None):
    """Register a function to be called when a specific packet arrives.

    Usage:
        @on_packet('ChatMessage')
        def handle_chat(session, packet):
            ...
    """
    def decorator(fn: Callable) -> Callable:
        _hooks["on_packet"].append({
            "fn": fn,
            "packet_types": [packet_type] if packet_type else [],
            "interval": None,
        })
        return fn

    # Support both @on_packet and @on_packet('ChatMessage')
    if callable(packet_type):
        fn = packet_type
        packet_type = None
        _hooks["on_packet"].append({"fn": fn, "packet_types": [], "interval": None})
        return fn

    return decorator


def on_tick(interval: int = 20):
    """Register a function to be called on each server tick.

    Usage:
        @on_tick(interval=20)
        def heartbeat(session):
            ...
    """
    def decorator(fn: Callable) -> Callable:
        _hooks["on_tick"].append({"fn": fn, "packet_types": [], "interval": interval})
        return fn
    return decorator


def on_disconnect(fn: Callable) -> Callable:
    """Register a function to be called when a client disconnects."""
    _hooks["on_disconnect"].append({"fn": fn, "packet_types": [], "interval": None})
    return fn


def get_hooks() -> dict:
    """Return the full hook registry. Used internally by the worker."""
    return _hooks


def clear_hooks():
    """Clear all registered hooks. Used during hot swap."""
    for key in _hooks:
        _hooks[key].clear()
