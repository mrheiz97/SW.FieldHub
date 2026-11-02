"""Local-only static JavaScript for the FieldHub administration preview."""
from pathlib import Path
from fastapi import APIRouter, Request
from fastapi.responses import FileResponse
from .dev_login import require_local

router = APIRouter(prefix="/dev", include_in_schema=False)

@router.get("/fieldhub-admin.js")
def admin_script(request: Request):
    require_local(request)
    return FileResponse(Path(__file__).with_name("fieldhub_admin.js"),
                        media_type="text/javascript",
                        headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"})
