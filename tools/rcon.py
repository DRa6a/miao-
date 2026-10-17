import socket
import struct
import sys
import time


def pack(req_id, req_type, body):
    payload = struct.pack('<ii', req_id, req_type) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload


def unpack(sock):
    raw_len = sock.recv(4)
    if len(raw_len) < 4:
        return None, None, ''
    (length,) = struct.unpack('<i', raw_len)
    data = b''
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    req_id, req_type = struct.unpack('<ii', data[:8])
    body = data[8:-2].decode('utf-8', 'replace')
    return req_id, req_type, body


def connect(host='127.0.0.1', port=25575, password='miao', retries=120):
    for _ in range(retries):
        try:
            s = socket.create_connection((host, port), timeout=3)
            s.sendall(pack(1, 3, password))
            rid, rtype, _ = unpack(s)
            if rid == -1:
                raise RuntimeError('rcon auth failed')
            return s
        except OSError:
            time.sleep(1)
    raise RuntimeError('rcon connect timeout')


def cmd(sock, text, req_id=2):
    sock.sendall(pack(req_id, 2, text))
    _, _, body = unpack(sock)
    print('[%s] -> %s' % (text, body.replace('\n', ' | ')))


if __name__ == '__main__':
    s = connect()
    for c in sys.argv[1:]:
        cmd(s, c)
        time.sleep(0.4)
    s.close()
