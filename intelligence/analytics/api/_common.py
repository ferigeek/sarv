from fastapi import HTTPException


async def or_422(coro):
    """Await ``coro``, mapping ``ValueError`` to HTTP 422."""
    try:
        return await coro
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
