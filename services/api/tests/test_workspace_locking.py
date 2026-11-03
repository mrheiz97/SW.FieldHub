"""Focused regressions for workspace lock authorization refresh."""
from unittest.mock import Mock

import pytest
from fastapi import HTTPException

from app.security import lock_workspace


def test_lock_invalidates_previously_loaded_authorization_objects():
    db = Mock()
    db.scalar.return_value = object()
    lock_workspace(db, "ravens")
    db.expire_all.assert_called_once_with()


def test_missing_workspace_cannot_be_locked():
    db = Mock()
    db.scalar.return_value = None
    with pytest.raises(HTTPException) as error:
        lock_workspace(db, "unknown")
    assert error.value.status_code == 404
    db.expire_all.assert_not_called()
