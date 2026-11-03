"""Trusted, versioned server-side module descriptions.

Navigation is a presentation hint; API permissions remain enforced independently.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class NavigationEntry:
    id: str
    label: str
    route: str
    permission: str
    icon: str


@dataclass(frozen=True)
class ModuleManifest:
    id: str
    version: str
    label: str
    navigation: tuple[NavigationEntry, ...]


MODULES = (
    ModuleManifest(
        id="forms",
        version="1.0.0",
        label="Forms",
        navigation=(
            NavigationEntry("forms", "Forms", "/forms", "forms.read", "clipboard-list"),
        ),
    ),
    ModuleManifest(
        id="recruiting",
        version="0.1.0",
        label="Recruiting",
        navigation=(NavigationEntry("recruiting", "Recruiting", "/recruiting", "forms.read", "contact-round"),),
    ),
    ModuleManifest(
        id="administration",
        version="1.0.0",
        label="Administration",
        navigation=(
            NavigationEntry("members", "Members", "/members", "workspace.manage", "users"),
            NavigationEntry("roles", "Roles", "/roles", "workspace.manage", "shield"),
        ),
    ),
)


def visible_modules(permissions: set[str], enabled_module_ids: set[str] | None = None) -> list[dict]:
    """Return only trusted entries authorized for this workspace membership."""
    result = []
    for module in MODULES:
        if enabled_module_ids is not None and module.id not in enabled_module_ids:
            continue
        entries = [
            {"id": item.id, "label": item.label, "route": item.route, "icon": item.icon}
            for item in module.navigation
            if item.permission in permissions
        ]
        if entries:
            result.append({
                "id": module.id,
                "version": module.version,
                "label": module.label,
                "navigation": entries,
            })
    return result
