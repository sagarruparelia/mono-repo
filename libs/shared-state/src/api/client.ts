import { useAuthStore } from '../stores/auth.store';

/**
 * API response wrapper
 */
export interface ApiResponse<T> {
  data: T;
  status: number;
}

/**
 * API error type
 */
export interface ApiError {
  message: string;
  status: number;
  code?: string;
}

/**
 * Retry configuration
 */
export interface RetryConfig {
  maxRetries: number;
  baseDelay: number;
  maxDelay: number;
  retryOn: number[];
}

/**
 * Default retry configuration with exponential backoff
 */
const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 3,
  baseDelay: 1000,
  maxDelay: 10000,
  retryOn: [408, 429, 500, 502, 503, 504],
};

/**
 * Request configuration
 */
interface RequestConfig extends RequestInit {
  params?: Record<string, string>;
  timeout?: number;
  retry?: boolean | Partial<RetryConfig>;
}

/**
 * Calculate delay for exponential backoff with jitter
 */
function calculateBackoff(attempt: number, config: RetryConfig): number {
  const exponentialDelay = config.baseDelay * Math.pow(2, attempt);
  const jitter = Math.random() * 0.3 * exponentialDelay; // 30% jitter
  return Math.min(exponentialDelay + jitter, config.maxDelay);
}

/**
 * Sleep utility for retry delays
 */
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Base API client with interceptors, retry logic, and timeout support
 */
export class ApiClient {
  private baseUrl: string;
  private defaultTimeout: number;
  private defaultRetryConfig: RetryConfig;

  constructor(baseUrl = '', options?: { timeout?: number; retry?: Partial<RetryConfig> }) {
    this.baseUrl = baseUrl;
    this.defaultTimeout = options?.timeout ?? 30000; // 30 seconds default
    this.defaultRetryConfig = { ...DEFAULT_RETRY_CONFIG, ...options?.retry };
  }

  /**
   * Set base URL (useful for MFEs with different API bases)
   */
  setBaseUrl(url: string) {
    this.baseUrl = url;
  }

  /**
   * Build URL with query params
   */
  private buildUrl(path: string, params?: Record<string, string>): string {
    const url = new URL(path, this.baseUrl || window.location.origin);
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        url.searchParams.append(key, value);
      });
    }
    return url.toString();
  }

  /**
   * Request interceptor - adds common headers
   */
  private async requestInterceptor(config: RequestConfig): Promise<RequestConfig> {
    const headers = new Headers(config.headers);

    // Add content type for JSON requests
    if (!headers.has('Content-Type') && config.body) {
      headers.set('Content-Type', 'application/json');
    }

    // Add credentials for session cookie
    config.credentials = 'include';

    return {
      ...config,
      headers,
    };
  }

  /**
   * Response interceptor - handles common error cases
   */
  private async responseInterceptor<T>(response: Response): Promise<T> {
    if (response.status === 401) {
      // Clear auth state on unauthorized
      useAuthStore.getState().clearAuth();
      throw { message: 'Unauthorized', status: 401 } as ApiError;
    }

    if (response.status === 403) {
      throw { message: 'Forbidden', status: 403 } as ApiError;
    }

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw {
        message: errorBody.message || response.statusText,
        status: response.status,
        code: errorBody.code,
      } as ApiError;
    }

    // Handle empty responses
    const text = await response.text();
    if (!text) {
      return undefined as T;
    }

    return JSON.parse(text);
  }

  /**
   * Fetch with timeout using AbortController
   */
  private async fetchWithTimeout(
    url: string,
    config: RequestConfig,
    timeout: number
  ): Promise<Response> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
      const response = await fetch(url, {
        ...config,
        signal: controller.signal,
      });
      return response;
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw { message: 'Request timeout', status: 408, code: 'TIMEOUT' } as ApiError;
      }
      throw error;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * Check if error is retryable
   */
  private isRetryable(error: ApiError, retryConfig: RetryConfig): boolean {
    return retryConfig.retryOn.includes(error.status);
  }

  /**
   * Generic request method with retry and timeout support
   */
  private async request<T>(
    method: string,
    path: string,
    config: RequestConfig = {}
  ): Promise<T> {
    const url = this.buildUrl(path, config.params);
    const interceptedConfig = await this.requestInterceptor({
      ...config,
      method,
    });

    const timeout = config.timeout ?? this.defaultTimeout;
    const retryEnabled = config.retry !== false;
    const retryConfig: RetryConfig = {
      ...this.defaultRetryConfig,
      ...(typeof config.retry === 'object' ? config.retry : {}),
    };

    let lastError: ApiError | null = null;
    const maxAttempts = retryEnabled ? retryConfig.maxRetries + 1 : 1;

    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        const response = await this.fetchWithTimeout(url, interceptedConfig, timeout);
        return await this.responseInterceptor<T>(response);
      } catch (error) {
        const apiError = error as ApiError;
        lastError = apiError;

        // Don't retry on 401/403 - these are definitive auth failures
        if (apiError.status === 401 || apiError.status === 403) {
          throw apiError;
        }

        // Check if we should retry
        const isLastAttempt = attempt === maxAttempts - 1;
        if (!isLastAttempt && retryEnabled && this.isRetryable(apiError, retryConfig)) {
          const delay = calculateBackoff(attempt, retryConfig);
          console.warn(
            `Request to ${path} failed (attempt ${attempt + 1}/${maxAttempts}). ` +
            `Retrying in ${Math.round(delay)}ms...`,
            apiError
          );
          await sleep(delay);
          continue;
        }

        throw apiError;
      }
    }

    throw lastError ?? { message: 'Unknown error', status: 500 } as ApiError;
  }

  /**
   * GET request
   */
  async get<T>(path: string, params?: Record<string, string>): Promise<T> {
    return this.request<T>('GET', path, { params });
  }

  /**
   * POST request
   */
  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('POST', path, {
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * PUT request
   */
  async put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('PUT', path, {
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * PATCH request
   */
  async patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('PATCH', path, {
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * DELETE request
   */
  async delete<T>(path: string): Promise<T> {
    return this.request<T>('DELETE', path);
  }

  /**
   * Upload file with multipart/form-data
   * Note: Does not set Content-Type header - browser will set it with boundary
   */
  async uploadFile<T>(
    path: string,
    file: File,
    metadata?: Record<string, string>
  ): Promise<T> {
    const formData = new FormData();
    formData.append('file', file);

    if (metadata) {
      Object.entries(metadata).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          formData.append(key, value);
        }
      });
    }

    const url = this.buildUrl(path);

    const response = await fetch(url, {
      method: 'POST',
      body: formData,
      credentials: 'include',
      // Don't set Content-Type - browser will set it with multipart boundary
    });

    return this.responseInterceptor<T>(response);
  }
}

/**
 * Default API client instance
 */
export const api = new ApiClient();

/**
 * Create a new API client with custom base URL
 */
export const createApiClient = (baseUrl: string) => new ApiClient(baseUrl);
