/**
 * YAWL Engine API Client for JavaScript/TypeScript.
 *
 * This module provides a modern async client for interacting with the YAWL Engine
 * through its REST interfaces. Built on fetch API with full TypeScript support.
 *
 * @packageDocumentation
 *
 * @example
 * ```typescript
 * import { YawlClient } from 'yawl-client';
 *
 * const client = new YawlClient({ baseUrl: 'http://localhost:8080/yawl' });
 *
 * // Connect and get session handle
 * const session = await client.session.connect('admin', 'password');
 *
 * // Launch a case
 * const caseId = await client.cases.launch({
 *   specUri: 'http://example.com/specs/approval',
 *   sessionHandle: session
 * });
 *
 * // Get work items
 * const items = await client.workItems.getAllLive(session);
 *
 * // Disconnect
 * await client.session.disconnect(session);
 * ```
 */

/**
 * Configuration options for the YAWL client.
 */
export interface YawlClientConfig {
  /** Base URL of the YAWL engine (e.g., 'http://localhost:8080/yawl') */
  baseUrl: string;
  /** Request timeout in milliseconds (default: 30000) */
  timeout?: number;
  /** Custom fetch function (for testing or custom environments) */
  fetch?: typeof fetch;
}

/**
 * Specification identifier.
 */
export interface SpecificationID {
  identifier?: string;
  version?: string;
  uri: string;
}

/**
 * Work item record.
 */
export interface WorkItemRecord {
  id: string;
  caseId: string;
  taskId: string;
  status: WorkItemStatus;
  specUri: string;
  specVersion: string;
  taskName: string;
  resourceStatus: ResourceStatus;
  startedBy?: string;
  completedBy?: string;
  enablementTimeMs?: string;
  startTimeMs?: string;
  completionTimeMs?: string;
  data?: string;
}

/**
 * Work item execution statuses.
 */
export type WorkItemStatus =
  | 'Enabled'
  | 'Fired'
  | 'Executing'
  | 'Complete'
  | 'Is parent'
  | 'Deadlocked'
  | 'ForcedComplete'
  | 'Failed'
  | 'Suspended'
  | 'Discarded';

/**
 * Work item resource statuses.
 */
export type ResourceStatus =
  | 'Offered'
  | 'Allocated'
  | 'Started'
  | 'Suspended'
  | 'Unoffered'
  | 'Unresourced';

/**
 * Receipt for audit trail.
 */
export interface Receipt {
  receiptId: number;
  caseId: string;
  tokenHolder: string;
  timestamp: number;
  beforeState: string;
  delta: string;
  afterState: string;
  admission: 'ADMITTED' | 'REJECTED';
  admissionReason: string;
  validatorId: string;
  hash: string;
  parentHash?: string;
  ingressSource: string;
}

/**
 * Case statistics.
 */
export interface CaseStats {
  caseId: string;
  committedTransitions: number;
  rejectedTransitions: number;
  firstTransition: string;
  lastTransition: string;
  durationMs: number;
}

/**
 * Error thrown when YAWL API operations fail.
 */
export class YawlError extends Error {
  constructor(
    message: string,
    public readonly statusCode?: number,
    public readonly responseBody?: string
  ) {
    super(message);
    this.name = 'YawlError';
  }
}

/**
 * Session management operations.
 */
export class SessionOperations {
  private client: YawlClient;

  constructor(client: YawlClient) {
    this.client = client;
  }

  /**
   * Connect to the YAWL engine and return a session handle.
   *
   * @param userId - The user ID
   * @param password - The password
   * @returns Session handle string
   * @throws YawlError if connection fails
   */
  async connect(userId: string, password: string): Promise<string> {
    const response = await this.client.post('/ib', {
      action: 'connect',
      userid: userId,
      password: password,
    });

    const handle = response.trim();
    if (handle.toLowerCase().includes('failure')) {
      throw new YawlError(`Connection failed: ${handle}`);
    }

    return handle;
  }

  /**
   * Check if a session handle is still valid.
   *
   * @param sessionHandle - The session handle to check
   * @returns True if valid, false otherwise
   */
  async checkConnection(sessionHandle: string): Promise<boolean> {
    const response = await this.client.get('/ib', {
      action: 'checkConnection',
      sessionHandle,
    });
    return !response.toLowerCase().includes('failure');
  }

  /**
   * Disconnect from the YAWL engine.
   *
   * @param sessionHandle - The session handle to disconnect
   * @returns True if disconnected successfully
   */
  async disconnect(sessionHandle: string): Promise<boolean> {
    await this.client.post('/ia', {
      action: 'disconnect',
      sessionHandle,
    });
    return true;
  }

  /**
   * Check if the session has administrative privileges.
   *
   * @param sessionHandle - The session handle
   * @returns True if admin
   */
  async isAdministrator(sessionHandle: string): Promise<boolean> {
    const response = await this.client.get('/ib', {
      action: 'checkIsAdmin',
      sessionHandle,
    });
    return response.includes('Granted');
  }
}

/**
 * Options for launching a case.
 */
export interface LaunchCaseOptions {
  specIdentifier?: string;
  specVersion?: string;
  specUri: string;
  caseParams?: string;
  caseId?: string;
  completionObserverUri?: string;
  logData?: string;
  delayMs?: number;
  sessionHandle: string;
}

/**
 * Case management operations.
 */
export class CaseOperations {
  private client: YawlClient;

  constructor(client: YawlClient) {
    this.client = client;
  }

  /**
   * Launch a new case instance.
   *
   * @param options - Launch options
   * @returns Case ID string
   * @throws YawlError if launch fails
   */
  async launch(options: LaunchCaseOptions): Promise<string> {
    const data: Record<string, string> = {
      action: 'launchCase',
      sessionHandle: options.sessionHandle,
      specuri: options.specUri,
      specversion: options.specVersion || '0.1',
    };

    if (options.specIdentifier) data.specidentifier = options.specIdentifier;
    if (options.caseParams) data.caseParams = options.caseParams;
    if (options.caseId) data.caseid = options.caseId;
    if (options.completionObserverUri)
      data.completionObserverURI = options.completionObserverUri;
    if (options.logData) data.logData = options.logData;
    if (options.delayMs && options.delayMs > 0)
      data.mSec = String(options.delayMs);

    const response = await this.client.post('/ib', data);
    const result = response.trim();

    if (result.toLowerCase().includes('failure')) {
      throw new YawlError(`Launch case failed: ${result}`);
    }

    return result;
  }

  /**
   * Get all running case IDs.
   *
   * @param sessionHandle - The session handle
   * @returns Case list XML
   */
  async getAllRunning(sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'getAllRunningCases',
      sessionHandle,
    });
  }

  /**
   * Get case state snapshot.
   *
   * @param caseId - The case identifier
   * @param sessionHandle - The session handle
   * @returns Case state XML
   */
  async getState(caseId: string, sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'getCaseState',
      caseID: caseId,
      sessionHandle,
    });
  }

  /**
   * Get case data.
   *
   * @param caseId - The case identifier
   * @param sessionHandle - The session handle
   * @returns Case data XML
   */
  async getData(caseId: string, sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'getCaseData',
      caseID: caseId,
      sessionHandle,
    });
  }

  /**
   * Cancel a running case.
   *
   * @param caseId - The case identifier
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async cancel(caseId: string, sessionHandle: string): Promise<string> {
    return this.client.post('/ib', {
      action: 'cancelCase',
      caseID: caseId,
      sessionHandle,
    });
  }

  /**
   * Export case state for migration.
   *
   * @param caseId - The case identifier
   * @param sessionHandle - The session handle
   * @returns Exported state XML
   */
  async exportState(caseId: string, sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'exportCaseState',
      caseID: caseId,
      sessionHandle,
    });
  }
}

/**
 * Work item operations.
 */
export class WorkItemOperations {
  private client: YawlClient;

  constructor(client: YawlClient) {
    this.client = client;
  }

  /**
   * Get all live work items.
   *
   * @param sessionHandle - The session handle
   * @returns Work item list XML
   */
  async getAllLive(sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'getLiveItems',
      sessionHandle,
    });
  }

  /**
   * Get a specific work item.
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @returns Work item XML
   */
  async get(workItemId: string, sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'getWorkItem',
      workItemID: workItemId,
      sessionHandle,
    });
  }

  /**
   * Get work items for a case.
   *
   * @param caseId - The case identifier
   * @param sessionHandle - The session handle
   * @returns Work item list XML
   */
  async getForCase(caseId: string, sessionHandle: string): Promise<string> {
    return this.client.get('/ib', {
      action: 'getWorkItemsWithIdentifier',
      id: caseId,
      idType: 'case',
      sessionHandle,
    });
  }

  /**
   * Check out a work item.
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @param logPredicate - Optional log predicate
   * @returns Checked out work item XML
   */
  async checkout(
    workItemId: string,
    sessionHandle: string,
    logPredicate?: string
  ): Promise<string> {
    const data: Record<string, string> = {
      action: 'checkout',
      workItemID: workItemId,
      sessionHandle,
    };
    if (logPredicate) data.logPredicate = logPredicate;

    const response = await this.client.post('/ib', data);
    if (response.toLowerCase().includes('failure')) {
      throw new YawlError(`Checkout failed: ${response}`);
    }
    return response;
  }

  /**
   * Check in a work item with updated data.
   *
   * @param workItemId - The work item identifier
   * @param dataXml - Work item data XML
   * @param sessionHandle - The session handle
   * @param logPredicate - Optional log predicate
   * @returns Response XML
   */
  async checkin(
    workItemId: string,
    dataXml: string,
    sessionHandle: string,
    logPredicate?: string
  ): Promise<string> {
    const data: Record<string, string> = {
      action: 'checkin',
      workItemID: workItemId,
      data: dataXml,
      logPredicate: logPredicate || '',
      sessionHandle,
    };

    const response = await this.client.post('/ib', data);
    if (response.toLowerCase().includes('failure')) {
      throw new YawlError(`Checkin failed: ${response}`);
    }
    return response;
  }

  /**
   * Suspend a work item.
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async suspend(workItemId: string, sessionHandle: string): Promise<string> {
    return this.client.post('/ib', {
      action: 'suspend',
      workItemID: workItemId,
      sessionHandle,
    });
  }

  /**
   * Unsuspend a work item.
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async unsuspend(workItemId: string, sessionHandle: string): Promise<string> {
    return this.client.post('/ib', {
      action: 'unsuspend',
      workItemID: workItemId,
      sessionHandle,
    });
  }

  /**
   * Rollback a work item from executing to fired.
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async rollback(workItemId: string, sessionHandle: string): Promise<string> {
    return this.client.post('/ib', {
      action: 'rollback',
      workItemID: workItemId,
      sessionHandle,
    });
  }

  /**
   * Skip a work item.
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async skip(workItemId: string, sessionHandle: string): Promise<string> {
    return this.client.post('/ib', {
      action: 'skip',
      workItemID: workItemId,
      sessionHandle,
    });
  }

  // Interface X REST operations

  /**
   * Cancel work item with exception data (Interface X).
   *
   * @param workItemId - The work item identifier
   * @param sessionHandle - The session handle
   * @param exceptionData - Optional exception data XML
   * @param markAsFail - Whether to mark as failure
   * @returns Response XML
   */
  async cancelWithException(
    workItemId: string,
    sessionHandle: string,
    exceptionData?: string,
    markAsFail: boolean = false
  ): Promise<string> {
    return this.client.postXml(
      `/ix/workitems/${workItemId}/cancel`,
      exceptionData || '',
      {
        sessionHandle,
        fail: String(markAsFail),
      }
    );
  }

  /**
   * Update work item data without checking in (Interface X).
   *
   * @param workItemId - The work item identifier
   * @param dataXml - Updated data XML
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async updateData(
    workItemId: string,
    dataXml: string,
    sessionHandle: string
  ): Promise<string> {
    return this.client.putXml(
      `/ix/workitems/${workItemId}/data`,
      dataXml,
      { sessionHandle }
    );
  }

  /**
   * Register an InterfaceX listener.
   *
   * @param listenerUri - The listener URI
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async registerListener(
    listenerUri: string,
    sessionHandle: string
  ): Promise<string> {
    return this.client.postXml('/ix/listeners', listenerUri, { sessionHandle });
  }

  /**
   * Unregister an InterfaceX listener.
   *
   * @param listenerUri - The listener URI
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async unregisterListener(
    listenerUri: string,
    sessionHandle: string
  ): Promise<string> {
    return this.client.delete('/ix/listeners', {
      uri: listenerUri,
      sessionHandle,
    });
  }
}

/**
 * Audit and compliance operations.
 */
export class AuditOperations {
  private client: YawlClient;

  constructor(client: YawlClient) {
    this.client = client;
  }

  /**
   * Get full receipt chain for a case.
   *
   * @param caseId - The case identifier
   * @returns Receipt chain JSON
   */
  async getReceiptChain(caseId: string): Promise<string> {
    return this.client.getJson(`/audit/case/${caseId}/receipts`);
  }

  /**
   * Get computed case state from receipts.
   *
   * @param caseId - The case identifier
   * @returns Computed state JSON
   */
  async getComputedState(caseId: string): Promise<string> {
    return this.client.getJson(`/audit/case/${caseId}/state`);
  }

  /**
   * Get case statistics.
   *
   * @param caseId - The case identifier
   * @returns Statistics JSON
   */
  async getStats(caseId: string): Promise<string> {
    return this.client.getJson(`/audit/case/${caseId}/stats`);
  }

  /**
   * Get a single receipt by ID.
   *
   * @param receiptId - The receipt ID
   * @returns Receipt JSON
   */
  async getReceipt(receiptId: number): Promise<string> {
    return this.client.getJson(`/audit/receipt/${receiptId}`);
  }

  /**
   * Verify chain integrity.
   *
   * @param caseId - The case identifier
   * @returns Verification result JSON
   */
  async verifyChain(caseId: string): Promise<string> {
    return this.client.getJson(`/audit/verify/${caseId}`);
  }

  /**
   * Get receipts within a time range.
   *
   * @param caseId - The case identifier
   * @param startMs - Start timestamp in milliseconds
   * @param endMs - End timestamp in milliseconds
   * @returns Receipts JSON
   */
  async getByTimeRange(
    caseId: string,
    startMs: number,
    endMs: number
  ): Promise<string> {
    return this.client.getJson(
      `/audit/case/${caseId}/time-range?start=${startMs}&end=${endMs}`
    );
  }

  /**
   * Get admitted (committed) transitions.
   *
   * @param caseId - The case identifier
   * @returns Admitted transitions JSON
   */
  async getAdmitted(caseId: string): Promise<string> {
    return this.client.getJson(`/audit/case/${caseId}/admitted`);
  }

  /**
   * Get rejected (policy violation) transitions.
   *
   * @param caseId - The case identifier
   * @returns Rejected transitions JSON
   */
  async getRejected(caseId: string): Promise<string> {
    return this.client.getJson(`/audit/case/${caseId}/rejected`);
  }
}

/**
 * Specification management operations.
 */
export class SpecificationOperations {
  private client: YawlClient;

  constructor(client: YawlClient) {
    this.client = client;
  }

  /**
   * Upload a YAWL specification.
   *
   * @param specXml - Specification XML content
   * @param sessionHandle - The session handle
   * @returns Response XML
   */
  async upload(specXml: string, sessionHandle: string): Promise<string> {
    return this.client.postXml('/ia/specifications', specXml, { sessionHandle });
  }

  /**
   * List all loaded specifications.
   *
   * @param sessionHandle - The session handle
   * @returns Specification list XML
   */
  async list(sessionHandle: string): Promise<string> {
    return this.client.get('/ia/specifications', { sessionHandle });
  }

  /**
   * Unload a specification.
   *
   * @param specId - The specification identifier
   * @param sessionHandle - The session handle
   * @param version - Optional specification version
   * @param uri - Optional specification URI
   * @returns Response XML
   */
  async unload(
    specId: string,
    sessionHandle: string,
    version?: string,
    uri?: string
  ): Promise<string> {
    const params: Record<string, string> = { sessionHandle };
    if (version) params.version = version;
    if (uri) params.uri = uri;
    return this.client.delete(`/ia/specifications/${specId}`, params);
  }
}

/**
 * Modern YAWL API Client.
 *
 * This client provides a clean async interface for interacting with the YAWL Engine
 * through its REST interfaces.
 *
 * @example
 * ```typescript
 * const client = new YawlClient({ baseUrl: 'http://localhost:8080/yawl' });
 *
 * const session = await client.session.connect('admin', 'password');
 * const cases = await client.cases.getAllRunning(session);
 * await client.session.disconnect(session);
 * ```
 */
export class YawlClient {
  private readonly baseUrl: string;
  private readonly timeout: number;
  private readonly fetchFn: typeof fetch;

  /** Session management operations */
  readonly session: SessionOperations;

  /** Specification operations (Interface A) */
  readonly specifications: SpecificationOperations;

  /** Case operations (Interface B) */
  readonly cases: CaseOperations;

  /** Work item operations */
  readonly workItems: WorkItemOperations;

  /** Audit operations */
  readonly audit: AuditOperations;

  constructor(config: YawlClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.timeout = config.timeout ?? 30000;
    this.fetchFn = config.fetch ?? fetch;

    // Initialize operation classes
    this.session = new SessionOperations(this);
    this.specifications = new SpecificationOperations(this);
    this.cases = new CaseOperations(this);
    this.workItems = new WorkItemOperations(this);
    this.audit = new AuditOperations(this);
  }

  /**
   * Execute a GET request.
   */
  async get(
    path: string,
    params?: Record<string, string>
  ): Promise<string> {
    const url = this.buildUrl(path, params);
    const response = await this.fetchWithTimeout(url, {
      method: 'GET',
      headers: { Accept: 'application/xml' },
    });
    return this.checkResponse(response);
  }

  /**
   * Execute a GET request expecting JSON.
   */
  async getJson(path: string): Promise<string> {
    const url = `${this.baseUrl}${path}`;
    const response = await this.fetchWithTimeout(url, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    return this.checkResponse(response);
  }

  /**
   * Execute a POST request with form data.
   */
  async post(
    path: string,
    data?: Record<string, string>
  ): Promise<string> {
    const url = `${this.baseUrl}${path}`;
    const body = data ? this.encodeForm(data) : '';
    const response = await this.fetchWithTimeout(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    return this.checkResponse(response);
  }

  /**
   * Execute a POST request with XML body.
   */
  async postXml(
    path: string,
    xmlBody: string,
    params?: Record<string, string>
  ): Promise<string> {
    const url = this.buildUrl(path, params);
    const response = await this.fetchWithTimeout(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/xml' },
      body: xmlBody,
    });
    return this.checkResponse(response);
  }

  /**
   * Execute a PUT request with XML body.
   */
  async putXml(
    path: string,
    xmlBody: string,
    params?: Record<string, string>
  ): Promise<string> {
    const url = this.buildUrl(path, params);
    const response = await this.fetchWithTimeout(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/xml' },
      body: xmlBody,
    });
    return this.checkResponse(response);
  }

  /**
   * Execute a DELETE request.
   */
  async delete(
    path: string,
    params?: Record<string, string>
  ): Promise<string> {
    const url = this.buildUrl(path, params);
    const response = await this.fetchWithTimeout(url, {
      method: 'DELETE',
    });
    return this.checkResponse(response);
  }

  private buildUrl(path: string, params?: Record<string, string>): string {
    let url = `${this.baseUrl}${path}`;
    if (params && Object.keys(params).length > 0) {
      url += `?${this.encodeForm(params)}`;
    }
    return url;
  }

  private encodeForm(data: Record<string, string>): string {
    return Object.entries(data)
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&');
  }

  private async fetchWithTimeout(
    url: string,
    options: RequestInit
  ): Promise<Response> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      const response = await this.fetchFn(url, {
        ...options,
        signal: controller.signal,
      });
      return response;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  private async checkResponse(response: Response): Promise<string> {
    const text = await response.text();
    if (response.status >= 400) {
      throw new YawlError(
        `HTTP ${response.status}: ${text.substring(0, 200)}`,
        response.status,
        text
      );
    }
    return text;
  }
}

export default YawlClient;
