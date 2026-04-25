"""
Whisper transcription wrapper.

Selects backend by env var:
    GROQ_API_KEY   -> Groq Whisper-large-v3 (fast, free tier)
    OPENAI_API_KEY -> OpenAI Whisper-1
"""

from __future__ import annotations

import io
import os


def transcribe(audio_bytes: bytes, mime: str = "audio/webm", language: str = "en") -> str:
    if os.getenv("GROQ_API_KEY"):
        return _transcribe_groq(audio_bytes, mime, language)
    if os.getenv("OPENAI_API_KEY"):
        return _transcribe_openai(audio_bytes, mime, language)
    raise RuntimeError(
        "No transcription provider configured. Set GROQ_API_KEY or OPENAI_API_KEY."
    )


# ---------------------------------------------------------------------------


def _filename_for(mime: str) -> str:
    return {
        "audio/webm": "speech.webm",
        "audio/ogg": "speech.ogg",
        "audio/mpeg": "speech.mp3",
        "audio/mp4": "speech.m4a",
        "audio/wav": "speech.wav",
        "audio/x-wav": "speech.wav",
    }.get(mime, "speech.webm")


def _transcribe_groq(audio_bytes: bytes, mime: str, language: str) -> str:
    from groq import Groq  # type: ignore

    client = Groq()
    model = os.getenv("GROQ_WHISPER_MODEL", "whisper-large-v3")
    bio = io.BytesIO(audio_bytes)
    bio.name = _filename_for(mime)
    resp = client.audio.transcriptions.create(
        model=model,
        file=bio,
        language=language,
        response_format="json",
    )
    return (resp.text or "").strip()


def _transcribe_openai(audio_bytes: bytes, mime: str, language: str) -> str:
    from openai import OpenAI

    client = OpenAI()
    model = os.getenv("OPENAI_WHISPER_MODEL", "whisper-1")
    bio = io.BytesIO(audio_bytes)
    bio.name = _filename_for(mime)
    resp = client.audio.transcriptions.create(
        model=model,
        file=bio,
        language=language,
        response_format="json",
    )
    return (resp.text or "").strip()
