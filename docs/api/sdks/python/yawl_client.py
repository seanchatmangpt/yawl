"""
YAWL Engine API Client for Python.

This module provides a modern async client for interacting with the YAWL Engine
through its REST interfaces. Built on httpx with async/await support.

Features:
    - Async/await native support
    - Type hints throughout
    - Automatic session management
    - Comprehensive error handling
    - Context manager support

Usage Example:
    ```python
    import asyncio
    from yawl_client import YawlClient

    async def main():
        async with YawlClient("http://localhost:8080/yawl") as client:
            # Connect and get session handle
            session = await client.session.connect("admin", "password")

            # Launch a case
            case_id = await client.cases.launch(
                spec_id="approval-workflow",
                spec_uri="http://example.com/specs/approval",
                session_handle=session
            )

            # Get work items
            items = await client.work_items.get_all_live(session)

            # Disconnect
            await client.session.disconnect(session)

    asyncio.run(main())
    ```
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from typing import Any, Optional, Union
from urllib.parse import urlencode

import httpx


@dataclass
class YawlError(Exception):
    """Exception raised when YAWL API operations fail."""

    message: str
    status_code: Optional[int] = None
    response_body: Optional[str] = None

    def __str__(self) -> str:
        if self.status_code:
            return f"YawlError [{self.status_code}]: {self.message}"
        return f"YawlError: {self.message}"


@dataclass
class SpecificationID:
    """YAWL specification identifier."""

    identifier: Optional[str] = None
    version: str = "0.1"
    uri: str = ""

    def to_params(self) -> dict[str, str]:
        """Convert to HTTP request parameters."""
        params = {
            "specversion": self.version,
            "specuri": self.uri,
        }
        if self.identifier:
            params["specidentifier"] = self.identifier
        return params


@dataclass
class WorkItemRecord:
    """Work item record from the engine."""

    id: str
    case_id: str
    task_id: str
    status: str
    spec_uri: str = ""
    spec_version: str = "0.1"
    task_name: str = ""
    resource_status: str = "Unresourced"
    started_by: Optional[str] = None
    completed_by: Optional[str] = None
    enablement_time_ms: Optional[str] = None
    start_time_ms: Optional[str] = None
    completion_time_ms: Optional[str] = None
    data: Optional[str] = None

    @classmethod
    def from_xml(cls, xml: str) -> "WorkItemRecord":
        """Parse from XML string (simplified)."""
        # In production, use proper XML parsing
        return cls(
            id="",
            case_id="",
            task_id="",
            status="Unknown",
        )


class SessionOperations:
    """Session management operations."""

    def __init__(self, client: "YawlClient"):
        self._client = client

    async def connect(self, user_id: str, password: str) -> str:
        """
        Connect to the YAWL engine and return a session handle.

        Args:
            user_id: The user ID
            password: The password

        Returns:
            Session handle string

        Raises:
            YawlError: If connection fails
        """
        response = await self._client._post(
            "/ib",
            data={
                "action": "connect",
                "userid": user_id,
                "password": password,  # Will be encrypted by engine
            },
        )

        handle = response.text.strip()
        if "failure" in handle.lower():
            raise YawlError(f"Connection failed: {handle}")

        return handle

    async def check_connection(self, session_handle: str) -> bool:
        """
        Check if a session handle is still valid.

        Args:
            session_handle: The session handle to check

        Returns:
            True if valid, False otherwise
        """
        response = await self._client._get(
            "/ib",
            params={
                "action": "checkConnection",
                "sessionHandle": session_handle,
            },
        )
        return "failure" not in response.text.lower()

    async def disconnect(self, session_handle: str) -> bool:
        """
        Disconnect from the YAWL engine.

        Args:
            session_handle: The session handle to disconnect

        Returns:
            True if disconnected successfully
        """
        response = await self._client._post(
            "/ia",
            data={
                "action": "disconnect",
                "sessionHandle": session_handle,
            },
        )
        return response.status_code == 200

    async def is_administrator(self, session_handle: str) -> bool:
        """Check if the session has administrative privileges."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "checkIsAdmin",
                "sessionHandle": session_handle,
            },
        )
        return "Granted" in response.text


class SpecificationOperations:
    """Specification management operations (Interface A)."""

    def __init__(self, client: "YawlClient"):
        self._client = client

    async def upload(self, spec_xml: str, session_handle: str) -> str:
        """
        Upload a YAWL specification to the engine.

        Args:
            spec_xml: The specification XML content
            session_handle: The session handle

        Returns:
            Response XML string
        """
        response = await self._client._post_xml(
            "/ia/specifications",
            xml_body=spec_xml,
            params={"sessionHandle": session_handle},
        )
        return response.text

    async def list(self, session_handle: str) -> str:
        """
        List all loaded specifications.

        Args:
            session_handle: The session handle

        Returns:
            Specification list XML
        """
        response = await self._client._get(
            "/ia/specifications",
            params={"sessionHandle": session_handle},
        )
        return response.text

    async def unload(
        self,
        spec_id: str,
        session_handle: str,
        version: Optional[str] = None,
        uri: Optional[str] = None,
    ) -> str:
        """
        Unload a specification from the engine.

        Args:
            spec_id: The specification identifier
            session_handle: The session handle
            version: Optional specification version
            uri: Optional specification URI

        Returns:
            Response XML string
        """
        params = {"sessionHandle": session_handle}
        if version:
            params["version"] = version
        if uri:
            params["uri"] = uri

        response = await self._client._delete(
            f"/ia/specifications/{spec_id}",
            params=params,
        )
        return response.text

    async def get_data(
        self,
        spec_id: SpecificationID,
        session_handle: str,
    ) -> str:
        """Get specification data."""
        params = {
            "action": "getSpecificationData",
            "sessionHandle": session_handle,
            **spec_id.to_params(),
        }
        response = await self._client._get("/ib", params=params)
        return response.text


class CaseOperations:
    """Case management operations (Interface B)."""

    def __init__(self, client: "YawlClient"):
        self._client = client

    async def launch(
        self,
        spec_id: str,
        spec_uri: str,
        session_handle: str,
        *,
        spec_identifier: Optional[str] = None,
        spec_version: str = "0.1",
        case_params: Optional[str] = None,
        case_id: Optional[str] = None,
        completion_observer_uri: Optional[str] = None,
        log_data: Optional[str] = None,
        delay_ms: int = 0,
    ) -> str:
        """
        Launch a new case instance.

        Args:
            spec_id: The specification ID
            spec_uri: The specification URI
            session_handle: The session handle
            spec_identifier: Optional specification identifier
            spec_version: Specification version (default "0.1")
            case_params: Optional case parameters XML
            case_id: Optional pre-selected case ID
            completion_observer_uri: Optional completion observer URI
            log_data: Optional log data XML
            delay_ms: Optional delay before launch in milliseconds

        Returns:
            Case ID string

        Raises:
            YawlError: If launch fails
        """
        data = {
            "action": "launchCase",
            "sessionHandle": session_handle,
            "specuri": spec_uri,
            "specversion": spec_version,
        }

        if spec_identifier:
            data["specidentifier"] = spec_identifier
        if case_params:
            data["caseParams"] = case_params
        if case_id:
            data["caseid"] = case_id
        if completion_observer_uri:
            data["completionObserverURI"] = completion_observer_uri
        if log_data:
            data["logData"] = log_data
        if delay_ms > 0:
            data["mSec"] = str(delay_ms)

        response = await self._client._post("/ib", data=data)
        result = response.text.strip()

        if "failure" in result.lower():
            raise YawlError(f"Launch case failed: {result}")

        return result

    async def get_all_running(self, session_handle: str) -> str:
        """Get all running case IDs."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "getAllRunningCases",
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def get_state(self, case_id: str, session_handle: str) -> str:
        """Get case state snapshot."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "getCaseState",
                "caseID": case_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def get_data(self, case_id: str, session_handle: str) -> str:
        """Get case data."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "getCaseData",
                "caseID": case_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def cancel(self, case_id: str, session_handle: str) -> str:
        """Cancel a running case."""
        response = await self._client._post(
            "/ib",
            data={
                "action": "cancelCase",
                "caseID": case_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def export_state(self, case_id: str, session_handle: str) -> str:
        """Export case state for migration."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "exportCaseState",
                "caseID": case_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text


class WorkItemOperations:
    """Work item operations (Interface B and X)."""

    def __init__(self, client: "YawlClient"):
        self._client = client

    async def get_all_live(self, session_handle: str) -> str:
        """Get all live work items."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "getLiveItems",
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def get(self, work_item_id: str, session_handle: str) -> str:
        """Get a specific work item."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "getWorkItem",
                "workItemID": work_item_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def get_for_case(self, case_id: str, session_handle: str) -> str:
        """Get work items for a case."""
        response = await self._client._get(
            "/ib",
            params={
                "action": "getWorkItemsWithIdentifier",
                "id": case_id,
                "idType": "case",
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def checkout(
        self,
        work_item_id: str,
        session_handle: str,
        log_predicate: Optional[str] = None,
    ) -> str:
        """Check out a work item."""
        data = {
            "action": "checkout",
            "workItemID": work_item_id,
            "sessionHandle": session_handle,
        }
        if log_predicate:
            data["logPredicate"] = log_predicate

        response = await self._client._post("/ib", data=data)
        result = response.text

        if "failure" in result.lower():
            raise YawlError(f"Checkout failed: {result}")

        return result

    async def checkin(
        self,
        work_item_id: str,
        data_xml: str,
        session_handle: str,
        log_predicate: Optional[str] = None,
    ) -> str:
        """Check in a work item with updated data."""
        data = {
            "action": "checkin",
            "workItemID": work_item_id,
            "data": data_xml,
            "logPredicate": log_predicate or "",
            "sessionHandle": session_handle,
        }

        response = await self._client._post("/ib", data=data)
        result = response.text

        if "failure" in result.lower():
            raise YawlError(f"Checkin failed: {result}")

        return result

    async def suspend(self, work_item_id: str, session_handle: str) -> str:
        """Suspend a work item."""
        response = await self._client._post(
            "/ib",
            data={
                "action": "suspend",
                "workItemID": work_item_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def unsuspend(self, work_item_id: str, session_handle: str) -> str:
        """Unsuspend a work item."""
        response = await self._client._post(
            "/ib",
            data={
                "action": "unsuspend",
                "workItemID": work_item_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def rollback(self, work_item_id: str, session_handle: str) -> str:
        """Rollback a work item from executing to fired."""
        response = await self._client._post(
            "/ib",
            data={
                "action": "rollback",
                "workItemID": work_item_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    async def skip(self, work_item_id: str, session_handle: str) -> str:
        """Skip a work item."""
        response = await self._client._post(
            "/ib",
            data={
                "action": "skip",
                "workItemID": work_item_id,
                "sessionHandle": session_handle,
            },
        )
        return response.text

    # Interface X REST operations

    async def cancel_with_exception(
        self,
        work_item_id: str,
        session_handle: str,
        exception_data: Optional[str] = None,
        mark_as_fail: bool = False,
    ) -> str:
        """Cancel work item with exception data (Interface X)."""
        params = {
            "sessionHandle": session_handle,
            "fail": str(mark_as_fail).lower(),
        }
        response = await self._client._post_xml(
            f"/ix/workitems/{work_item_id}/cancel",
            xml_body=exception_data or "",
            params=params,
        )
        return response.text

    async def update_data(
        self,
        work_item_id: str,
        data_xml: str,
        session_handle: str,
    ) -> str:
        """Update work item data without checking in (Interface X)."""
        response = await self._client._put_xml(
            f"/ix/workitems/{work_item_id}/data",
            xml_body=data_xml,
            params={"sessionHandle": session_handle},
        )
        return response.text

    async def register_listener(
        self,
        listener_uri: str,
        session_handle: str,
    ) -> str:
        """Register an InterfaceX listener."""
        response = await self._client._post_xml(
            "/ix/listeners",
            xml_body=listener_uri,
            params={"sessionHandle": session_handle},
        )
        return response.text

    async def unregister_listener(
        self,
        listener_uri: str,
        session_handle: str,
    ) -> str:
        """Unregister an InterfaceX listener."""
        response = await self._client._delete(
            "/ix/listeners",
            params={
                "uri": listener_uri,
                "sessionHandle": session_handle,
            },
        )
        return response.text


class AuditOperations:
    """Audit and compliance operations."""

    def __init__(self, client: "YawlClient"):
        self._client = client

    async def get_receipt_chain(self, case_id: str) -> str:
        """Get full receipt chain for a case."""
        response = await self._client._get(f"/audit/case/{case_id}/receipts")
        return response.text

    async def get_computed_state(self, case_id: str) -> str:
        """Get computed case state from receipts."""
        response = await self._client._get(f"/audit/case/{case_id}/state")
        return response.text

    async def get_stats(self, case_id: str) -> str:
        """Get case statistics."""
        response = await self._client._get(f"/audit/case/{case_id}/stats")
        return response.text

    async def get_receipt(self, receipt_id: int) -> str:
        """Get a single receipt by ID."""
        response = await self._client._get(f"/audit/receipt/{receipt_id}")
        return response.text

    async def verify_chain(self, case_id: str) -> str:
        """Verify chain integrity."""
        response = await self._client._get(f"/audit/verify/{case_id}")
        return response.text

    async def get_by_time_range(
        self,
        case_id: str,
        start_ms: int,
        end_ms: int,
    ) -> str:
        """Get receipts within a time range."""
        response = await self._client._get(
            f"/audit/case/{case_id}/time-range",
            params={"start": start_ms, "end": end_ms},
        )
        return response.text

    async def get_admitted(self, case_id: str) -> str:
        """Get admitted (committed) transitions."""
        response = await self._client._get(f"/audit/case/{case_id}/admitted")
        return response.text

    async def get_rejected(self, case_id: str) -> str:
        """Get rejected (policy violation) transitions."""
        response = await self._client._get(f"/audit/case/{case_id}/rejected")
        return response.text


class YawlClient:
    """
    Modern async YAWL API Client.

    This client provides a clean async interface for interacting with the YAWL Engine
    through its REST interfaces.

    Usage:
        ```python
        async with YawlClient("http://localhost:8080/yawl") as client:
            session = await client.session.connect("admin", "password")
            cases = await client.cases.get_all_running(session)
            await client.session.disconnect(session)
        ```
    """

    def __init__(
        self,
        base_url: str = "http://localhost:8080/yawl",
        timeout: float = 30.0,
    ):
        """
        Initialize the YAWL client.

        Args:
            base_url: Base URL of the YAWL engine
            timeout: Request timeout in seconds
        """
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout
        self._client: Optional[httpx.AsyncClient] = None

        # Initialize operation classes
        self.session = SessionOperations(self)
        self.specifications = SpecificationOperations(self)
        self.cases = CaseOperations(self)
        self.work_items = WorkItemOperations(self)
        self.audit = AuditOperations(self)

    async def __aenter__(self) -> "YawlClient":
        """Enter async context manager."""
        self._client = httpx.AsyncClient(
            base_url=self._base_url,
            timeout=self._timeout,
            follow_redirects=True,
        )
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        """Exit async context manager."""
        if self._client:
            await self._client.aclose()
            self._client = None

    def _ensure_client(self) -> httpx.AsyncClient:
        """Ensure HTTP client is initialized."""
        if self._client is None:
            raise RuntimeError(
                "Client not initialized. Use 'async with YawlClient(...) as client:'"
            )
        return self._client

    async def _get(
        self,
        path: str,
        params: Optional[dict[str, Any]] = None,
    ) -> httpx.Response:
        """Execute GET request."""
        client = self._ensure_client()
        response = await client.get(path, params=params)
        return self._check_response(response)

    async def _post(
        self,
        path: str,
        data: Optional[dict[str, Any]] = None,
    ) -> httpx.Response:
        """Execute POST request with form data."""
        client = self._ensure_client()
        response = await client.post(
            path,
            data=data,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        return self._check_response(response)

    async def _post_xml(
        self,
        path: str,
        xml_body: str,
        params: Optional[dict[str, Any]] = None,
    ) -> httpx.Response:
        """Execute POST request with XML body."""
        client = self._ensure_client()
        response = await client.post(
            path,
            content=xml_body,
            params=params,
            headers={"Content-Type": "application/xml"},
        )
        return self._check_response(response)

    async def _put_xml(
        self,
        path: str,
        xml_body: str,
        params: Optional[dict[str, Any]] = None,
    ) -> httpx.Response:
        """Execute PUT request with XML body."""
        client = self._ensure_client()
        response = await client.put(
            path,
            content=xml_body,
            params=params,
            headers={"Content-Type": "application/xml"},
        )
        return self._check_response(response)

    async def _delete(
        self,
        path: str,
        params: Optional[dict[str, Any]] = None,
    ) -> httpx.Response:
        """Execute DELETE request."""
        client = self._ensure_client()
        response = await client.delete(path, params=params)
        return self._check_response(response)

    def _check_response(self, response: httpx.Response) -> httpx.Response:
        """Check response for errors."""
        if response.status_code >= 400:
            raise YawlError(
                message=f"HTTP {response.status_code}: {response.text[:200]}",
                status_code=response.status_code,
                response_body=response.text,
            )
        return response


# Convenience exports
__all__ = [
    "YawlClient",
    "YawlError",
    "SpecificationID",
    "WorkItemRecord",
    "SessionOperations",
    "SpecificationOperations",
    "CaseOperations",
    "WorkItemOperations",
    "AuditOperations",
]
