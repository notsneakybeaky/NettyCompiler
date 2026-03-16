# Example: Greet players on connect
# Upload via POST /scripts with scriptId "greeter"

def handle(payload):
    """Send a welcome message when a player connects."""
    hook = payload.get("hook", "")

    if hook == "on_connect":
        session_id = payload["sessionId"]
        print(f"[greeter] New connection: {session_id}")
        return {
            "actions": [
                {
                    "type": "SEND",
                    "packetType": "ChatMessage",
                    "payload": {
                        "message": "Welcome to the Netty Runtime server!",
                        "position": 1
                    }
                }
            ]
        }

    return {"actions": []}
