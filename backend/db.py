"""
Tiny sqlite layer for persisting standing rules + their firing history.

We deliberately keep this dumb: no ORM, no migrations, just a single
`init_db()` you call at startup. One process, one file.
"""

from __future__ import annotations

import json
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import Iterator

DB_PATH = Path(__file__).resolve().parent.parent / "vox.db"

_lock = threading.Lock()


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


@contextmanager
def get_conn() -> Iterator[sqlite3.Connection]:
    """Serialised access — sqlite is fine for our load."""
    with _lock:
        conn = _connect()
        try:
            yield conn
            conn.commit()
        finally:
            conn.close()


def init_db() -> None:
    with get_conn() as c:
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS rules (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                kind        TEXT NOT NULL,
                summary     TEXT NOT NULL,
                config      TEXT NOT NULL,        -- JSON
                active      INTEGER NOT NULL DEFAULT 1,
                created_at  TEXT NOT NULL,
                fired_count INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS firings (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id    INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
                fired_at   TEXT NOT NULL,
                detail     TEXT NOT NULL          -- JSON
            );

            CREATE INDEX IF NOT EXISTS idx_firings_rule ON firings(rule_id);
            CREATE INDEX IF NOT EXISTS idx_rules_active ON rules(active);
            """
        )


# ---------------------------------------------------------------------------
# Rule CRUD
# ---------------------------------------------------------------------------


def insert_rule(kind: str, summary: str, config: dict) -> int:
    with get_conn() as c:
        cur = c.execute(
            "INSERT INTO rules (kind, summary, config, created_at) VALUES (?, ?, ?, ?)",
            (kind, summary, json.dumps(config), datetime.utcnow().isoformat()),
        )
        return cur.lastrowid


def list_active_rules(kind: str | None = None) -> list[dict]:
    sql = "SELECT * FROM rules WHERE active = 1"
    args: tuple = ()
    if kind:
        sql += " AND kind = ?"
        args = (kind,)
    sql += " ORDER BY id DESC"
    with get_conn() as c:
        rows = c.execute(sql, args).fetchall()
    return [_row_to_rule(r) for r in rows]


def list_all_rules() -> list[dict]:
    with get_conn() as c:
        rows = c.execute("SELECT * FROM rules ORDER BY id DESC").fetchall()
    return [_row_to_rule(r) for r in rows]


def deactivate_rule(rule_id: int) -> None:
    with get_conn() as c:
        c.execute("UPDATE rules SET active = 0 WHERE id = ?", (rule_id,))


def delete_rule(rule_id: int) -> None:
    with get_conn() as c:
        c.execute("DELETE FROM rules WHERE id = ?", (rule_id,))


def record_firing(rule_id: int, detail: dict) -> None:
    with get_conn() as c:
        c.execute(
            "INSERT INTO firings (rule_id, fired_at, detail) VALUES (?, ?, ?)",
            (rule_id, datetime.utcnow().isoformat(), json.dumps(detail)),
        )
        c.execute("UPDATE rules SET fired_count = fired_count + 1 WHERE id = ?", (rule_id,))


def recent_firings(limit: int = 50) -> list[dict]:
    with get_conn() as c:
        rows = c.execute(
            "SELECT * FROM firings ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
    return [
        {
            "id": r["id"],
            "rule_id": r["rule_id"],
            "fired_at": r["fired_at"],
            "detail": json.loads(r["detail"]),
        }
        for r in rows
    ]


def _row_to_rule(r: sqlite3.Row) -> dict:
    return {
        "id": r["id"],
        "kind": r["kind"],
        "summary": r["summary"],
        "config": json.loads(r["config"]),
        "active": bool(r["active"]),
        "created_at": r["created_at"],
        "fired_count": r["fired_count"],
    }
