"""本地端口转发：本机 -> 应用机(SSH) -> 数据库机内网 3306。

为什么要绕这一圈：MySQL 的授权是**按来源主机**的 —— 直接从本机连数据库机，
`root@localhost` 密码不对（.env 里那套是给应用用的），走内网 IP 又被
`Host ... is not allowed`。只有**从应用机发起**才匹配得上授权。
应用机上没有 mysql 客户端、也没有 pymysql，所以在本机建隧道。

用法：
    with DBTunnel() as (host, port):
        pymysql.connect(host=host, port=port, ...)
"""
import select
import socket
import threading
from pathlib import Path

import paramiko

APP_HOST = '8.146.233.197'
DB_PRIVATE = ('172.20.68.219', 3306)
ENV_PATH = Path(__file__).resolve().parent.parent / '.env'


def _env():
    out = {}
    for line in ENV_PATH.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            out[k.strip()] = v.strip().strip('"').strip("'")
    return out


class DBTunnel:
    """把本机一个随机端口转发到数据库机内网 3306（经应用机）。"""

    def __init__(self, app_host=APP_HOST, dest=DB_PRIVATE):
        self.app_host = app_host
        self.dest = dest
        self._client = None
        self._sock = None
        self._thread = None
        self._stop = False
        self.host = '127.0.0.1'
        self.port = None

    def __enter__(self):
        env = _env()
        self._client = paramiko.SSHClient()
        self._client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self._client.connect(self.app_host, username=env['SERVER_USER'],
                             password=env['SERVER_PASSWRD'], timeout=30)
        self._sock = socket.socket()
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind(('127.0.0.1', 0))
        self._sock.listen(8)
        self.port = self._sock.getsockname()[1]
        self._thread = threading.Thread(target=self._serve, daemon=True)
        self._thread.start()
        return self.host, self.port

    def _serve(self):
        transport = self._client.get_transport()
        while not self._stop:
            try:
                conn, _ = self._sock.accept()
            except OSError:
                return
            try:
                chan = transport.open_channel('direct-tcpip', self.dest, conn.getpeername())
            except Exception:
                conn.close()
                continue
            threading.Thread(target=self._pipe, args=(conn, chan), daemon=True).start()

    @staticmethod
    def _pipe(conn, chan):
        try:
            while True:
                r, _, _ = select.select([conn, chan], [], [], 1)
                if conn in r:
                    data = conn.recv(8192)
                    if not data:
                        break
                    chan.sendall(data)
                if chan in r:
                    data = chan.recv(8192)
                    if not data:
                        break
                    conn.sendall(data)
        except Exception:
            pass
        finally:
            chan.close()
            conn.close()

    def __exit__(self, *exc):
        self._stop = True
        try:
            self._sock.close()
        except Exception:
            pass
        if self._client:
            self._client.close()
        return False


if __name__ == '__main__':
    import pymysql
    env = _env()
    with DBTunnel() as (h, p):
        print('tunnel ready on', h, p)
        conn = pymysql.connect(host=h, port=p, user='root',
                               password=env['MYSQL_DB_SERVER_PASSWRD'],
                               database='ruoyi-vue-pro', charset='utf8mb4')
        with conn.cursor() as cur:
            cur.execute('SELECT COUNT(*) FROM product_spu WHERE tenant_id=162 AND deleted=0')
            print('租户162 商品数：', cur.fetchone()[0])
        conn.close()
