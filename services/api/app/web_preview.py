"""Local-only FieldHub web preview. Production web auth requires a separate BFF/OIDC design."""
from pathlib import Path
from fastapi import APIRouter, Request
from fastapi.responses import FileResponse
from .dev_login import require_local

router = APIRouter(prefix="/dev", include_in_schema=False)

@router.get("/fieldhub")
def fieldhub_preview(request: Request):
    require_local(request)
    return FileResponse(Path(__file__).with_name("fieldhub_preview.html"), media_type="text/html",
                        headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
                                 "Content-Security-Policy": "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; referrer no-referrer"})
