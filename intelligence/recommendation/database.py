import os
import psycopg
from contextlib import contextmanager


class Database:
    def __init__(self):
        self.name = os.environ["DB_NAME"]
        self.username = os.environ["DB_USER"]
        self.password = os.environ["DB_PASSWORD"]
        self.host = os.getenv("DB_HOST", "localhost")
        self.port = os.getenv("DB_PORT", "5432")

    def get_database_url(self) -> str:
        return f"postgresql://{self.username}:{self.password}@{self.host}:{self.port}/{self.name}"


@contextmanager
def get_connection():
    db = Database()
    conn = psycopg.connect(db.get_database_url())
    try:
        yield conn
    finally:
        conn.close()