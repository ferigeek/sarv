import psycopg
from contextlib import contextmanager
from pydantic_settings import BaseSettings

class DatabaseSettings(BaseSettings):
    name: str
    username: str
    password: str
    host: str = "localhost"
    port: int = 5432

    class Config:
        env_prefix = "DB_"
        env_file = ".env"



@contextmanager
def get_connection():
    db = DatabaseSettings()
    conn = psycopg.connect(
        dbname=db.name,
        user=db.username,
        password=db.password,
        host=db.host,
        port=db.port
    )
    try:
        yield conn
    finally:
        conn.close()