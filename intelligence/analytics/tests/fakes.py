import asyncio


class FakeCur:
    """Fake async cursor serving canned result sets, one per fetch.

    ``results`` is either a single result set (returned by every fetch)
    or a list of result sets consumed in order — one per ``fetchall`` /
    ``fetchone`` call, for query functions that execute several statements.
    A list whose elements are themselves lists counts as a queue;
    anything else (list of row tuples, single tuple, empty list) counts
    as one result set. psycopg rows are tuples, never lists, so the
    distinction is unambiguous.
    """

    def __init__(self, results):
        if isinstance(results, list) and any(isinstance(r, list) for r in results):
            self._queue = list(results)
        else:
            self._queue = None
            self._single = results
        self.queries = []
        self.params = None

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def execute(self, query, params):
        self.queries.append(query)
        self.params = params

    async def fetchall(self):
        if self._queue is not None:
            return self._queue.pop(0)
        return self._single

    async def fetchone(self):
        if self._queue is not None:
            return self._queue.pop(0)
        return self._single


class FakeConn:
    def __init__(self, results):
        self._cur = FakeCur(results)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    def cursor(self):
        return self._cur

    @property
    def queries(self):
        return self._cur.queries

    @property
    def params(self):
        return self._cur.params


class FakePool:
    def __init__(self, results):
        self._results = results
        self.conns: list = []

    def connection(self):
        conn = FakeConn(self._results)
        self.conns.append(conn)
        return conn


def run(coro):
    return asyncio.run(coro)


def authed_client(app):
    """TestClient carrying a valid admin session cookie.

    The app requires authentication on every route except /health, /login
    and /static, so endpoint tests must send a signed token — same as a
    browser after signing in.
    """
    from fastapi.testclient import TestClient

    from analytics.auth import COOKIE_NAME, create_token
    from analytics.config import settings

    token = create_token("tester", settings.analytics_secret_key, 3600)
    client = TestClient(app, cookies={COOKIE_NAME: token})
    return client
