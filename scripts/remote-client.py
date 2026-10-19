#!/usr/bin/env python3
"""A minimal manual client for Inspektor's remote protocol (docs/REMOTE_PROTOCOL.md).

It connects, prints the server's hello, subscribes, and prints every event as it arrives, until
the app closes the connection or you press Ctrl-C. Standard library only.

    python3 scripts/remote-client.py PORT                  # follow the app's traffic live
    python3 scripts/remote-client.py PORT --after 40       # backfill only what came after #40
    python3 scripts/remote-client.py PORT --body 12        # print transaction 12's response body
    python3 scripts/remote-client.py PORT --body 12 --part request
    python3 scripts/remote-client.py PORT --overrides      # list the app's overrides
    python3 scripts/remote-client.py PORT --raw            # print every frame as full JSON

PORT is a port on this machine: `adb forward tcp:0 localabstract:inspektor_<pid>_kotlin` prints
one for an Android app; an iOS simulator or desktop app listens on 38470-38477 directly; a
physical iPhone needs `iproxy 38470 38470` first.
"""
import argparse
import base64
import json
import socket
import struct
import sys


def send(sock, message):
    payload = json.dumps(message).encode()
    sock.sendall(struct.pack(">I", len(payload)) + payload)


def read_exact(sock, count):
    data = b""
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            if data:
                raise ConnectionError("the server closed the connection inside a frame")
            return None
        data += chunk
    return data


def receive(sock):
    header = read_exact(sock, 4)
    if header is None:
        return None
    (length,) = struct.unpack(">I", header)
    return json.loads(read_exact(sock, length))


def request(sock, request_id, method, params=None):
    """Sends one request and returns its reply, printing any events that arrive first."""
    message = {"id": request_id, "method": method}
    if params is not None:
        message["params"] = params
    send(sock, message)
    while True:
        frame = receive(sock)
        if frame is None:
            sys.exit("The server closed the connection.")
        if frame.get("id") == request_id:
            if "error" in frame:
                sys.exit(f"{method} failed: {frame['error']}")
            return frame.get("result")


def describe(event):
    name = event.get("event")
    data = event.get("data")
    if name in ("transaction.started", "transaction.updated"):
        response = data.get("response")
        if response:
            outcome = str(response.get("code"))
        elif data.get("error"):
            outcome = "ERR " + data["error"].splitlines()[0]
        else:
            outcome = "..."
        took = f" {data['tookMs']} ms" if "tookMs" in data else ""
        return f"{name:20} #{data['id']:>5}  {outcome:>4}  {data['method']:6} {data['url']}{took}"
    if name == "overrides.changed":
        return f"{name:20} {len(data)} override(s)"
    return name


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("port", type=int)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--after", help="subscribe after this transaction id")
    parser.add_argument("--body", metavar="ID", help="print one transaction's body and exit")
    parser.add_argument("--part", default="response",
                        choices=["request", "response", "originalRequest", "originalResponse"])
    parser.add_argument("--overrides", action="store_true", help="list overrides and exit")
    parser.add_argument("--raw", action="store_true", help="print frames as full JSON")
    args = parser.parse_args()

    sock = socket.create_connection((args.host, args.port), timeout=10)
    sock.settimeout(None)
    hello = receive(sock)
    if hello is None:
        sys.exit("The server closed the connection without a hello.")
    app, store = hello.get("app", {}), hello.get("store", {})
    print(f"hello: protocol {hello.get('protocol')}, {app.get('name') or app.get('id')} "
          f"({app.get('id')}, pid {app.get('pid')}), store {store.get('id')}, "
          f"oldest #{store.get('oldestId')}")
    if args.raw:
        print(json.dumps(hello, indent=2))

    if args.body:
        result = request(sock, 1, "body.get", {"id": args.body, "part": args.part})
        data = result["data"]
        if result["encoding"] == "base64":
            data = base64.b64decode(data).decode(errors="replace")
        print(data)
        return
    if args.overrides:
        print(json.dumps(request(sock, 1, "overrides.list"), indent=2))
        return

    request(sock, 1, "subscribe", {"afterId": args.after} if args.after else {})
    print("subscribed; waiting for events (Ctrl-C to stop)")
    while True:
        frame = receive(sock)
        if frame is None:
            print("The server closed the connection.")
            return
        print(json.dumps(frame, indent=2) if args.raw else describe(frame))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
