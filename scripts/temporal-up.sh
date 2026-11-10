#!/usr/bin/env bash
# 用 macOS container 启动 Temporal，并把 127.0.0.1:7233 / 8233 转到容器地址。
# macOS 27 上 container 自己的 -p 转发会被本地网络限制重置，所以不用 -p。
set -euo pipefail
name=joybot-learn-temporal
pidfile=/tmp/joybot-learn-temporal-proxy.pid

if container ls -q | grep -qx "$name"; then
  :
elif container ls -a -q | grep -qx "$name"; then
  container start "$name"
else
  container run -d --name "$name" \
    temporalio/temporal:latest \
    server start-dev --ip 0.0.0.0 --namespace default \
    --db-filename /tmp/temporal.db --log-level warn
fi

ip=$(container inspect "$name" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["status"]["networks"][0]["ipv4Address"].split("/")[0])')

if [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
  kill "$(cat "$pidfile")" 2>/dev/null || true
fi

python3 - "$ip" <<'PY' >"$pidfile"
import os, socket, sys, threading
ip = sys.argv[1]
pid = os.fork()
if pid > 0:
    print(pid)
    raise SystemExit(0)
os.setsid()
os.chdir("/")
devnull = os.open(os.devnull, os.O_RDWR)
os.dup2(devnull, 0)
log = os.open("/tmp/joybot-learn-temporal-proxy.log", os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o644)
os.dup2(log, 1)
os.dup2(log, 2)

def pipe(a, b):
    try:
        while True:
            data = a.recv(65536)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for sock in (a, b):
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass

def serve(port):
    server = socket.socket()
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", port))
    server.listen(128)
    while True:
        client, _ = server.accept()
        try:
            upstream = socket.create_connection((ip, port), 5)
        except OSError:
            client.close()
            continue
        threading.Thread(target=pipe, args=(client, upstream), daemon=True).start()
        threading.Thread(target=pipe, args=(upstream, client), daemon=True).start()

for port in (7233, 8233):
    threading.Thread(target=serve, args=(port,), daemon=True).start()
threading.Event().wait()
PY

for _ in $(seq 1 30); do
  if curl -sf -o /dev/null --max-time 2 http://127.0.0.1:8233/; then
    exit 0
  fi
  sleep 1
done
echo "Temporal UI 未在 127.0.0.1:8233 就绪，容器地址是 ${ip}" >&2
exit 1
