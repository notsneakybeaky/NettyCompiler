# Example: Chat message interceptor
# Upload this via POST /scripts with scriptId "chat_logger"
#
# curl -X POST http://localhost:8080/scripts \
#   -H "Content-Type: application/json" \
#   -d '{
#     "scriptId": "chat_logger",
#     "hooks": ["on_packet"],
#     "packetTypes": ["ChatMessage"],
#     "source": "..."
#   }'

def handle(payload):
    """Log every chat message and forward it unchanged."""
    session_id = payload["sessionId"]
    packet_type = payload.get("packetType", "unknown")
    data = payload.get("payload", {})

    print(f"[chat_logger] [{session_id}] {packet_type}: {data}")

    # Forward the packet unchanged
    return {
        "actions": [
            {
                "type": "FORWARD",
                "packetType": packet_type,
                "payload": data
            }
        ]
    }
