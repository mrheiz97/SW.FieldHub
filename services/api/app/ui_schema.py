"""Server-defined presentation schema for web and Android.

Only predefined component types and routes are returned. This is UI metadata,
not executable code, and never substitutes for endpoint authorization.
"""
from .module_registry import visible_modules

UI_SCHEMA_VERSION = 1

PAGE_DEFINITIONS = {
    "forms": {"title": "Forms", "layout": "list", "components": [
        {"id": "forms-list", "type": "resource-list", "resource": "forms", "title": "Available forms"}
    ]},
    "members": {"title": "Members", "layout": "list", "components": [
        {"id": "members-list", "type": "resource-list", "resource": "members", "title": "Workspace members"}
    ]},
    "roles": {"title": "Roles", "layout": "list", "components": [
        {"id": "roles-list", "type": "resource-list", "resource": "roles", "title": "Workspace roles"}
    ]},
}

DASHBOARD_CARDS = (
    {"id": "forms-shortcut", "type": "navigation-card", "title": "Forms",
     "description": "Browse published forms", "target": "forms"},
    {"id": "members-shortcut", "type": "navigation-card", "title": "Members",
     "description": "Manage workspace members", "target": "members"},
    {"id": "roles-shortcut", "type": "navigation-card", "title": "Roles",
     "description": "Manage workspace roles", "target": "roles"},
)


def build_ui(permissions: set[str], enabled: set[str]) -> dict:
    modules = visible_modules(permissions, enabled)
    navigation = [item for module in modules for item in module["navigation"]]
    permitted = {item["id"] for item in navigation}
    return {
        "schemaVersion": UI_SCHEMA_VERSION,
        "dashboard": {
            "layout": "grid",
            "cards": [dict(card) for card in DASHBOARD_CARDS if card["target"] in permitted],
        },
        "navigation": navigation,
        "pages": [
            {"id": item["id"], "route": item["route"], **PAGE_DEFINITIONS[item["id"]]}
            for item in navigation if item["id"] in PAGE_DEFINITIONS
        ],
    }
