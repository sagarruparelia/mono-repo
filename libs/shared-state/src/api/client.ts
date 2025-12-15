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
 * Request configuration
 */
interface RequestConfig extends RequestInit {
  params?: Record<string, string>;
}

/**
 * Base API client with interceptors
 */
class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = '') {
    this.baseUrl = baseUrl;
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
   * Generic request method
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

    const response = await fetch(url, interceptedConfig);
    return this.responseInterceptor<T>(response);
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
}

/**
 * Default API client instance
 */
export const api = new ApiClient();

/**
 * Create a new API client with custom base URL
 */
export const createApiClient = (baseUrl: string) => new ApiClient(baseUrl);
