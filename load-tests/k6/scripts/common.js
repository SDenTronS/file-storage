import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';

export const token = readRequiredEnv('AUTH_TOKEN');
export const baseUrl = readRequiredEnv('BASE_URL').replace(/\/$/, '');

export function createConstantArrivalRateOptions(scenarioName) {
  return {
    scenarios: {
      [scenarioName]: {
        executor: 'constant-arrival-rate',
        duration: readStringEnv('DURATION', '30s'),
        rate: readNumberEnv('RATE', 100),
        preAllocatedVUs: readNumberEnv('PRE_ALLOCATED_VUS', 5000),
        timeUnit: readStringEnv('TIME_UNIT', '10s'),
        gracefulStop: readStringEnv('GRACEFUL_STOP', '10s'),
      },
    },
  };
}

export function authHeaders(extraHeaders = {}) {
  return {
    Authorization: `Bearer ${token}`,
    ...extraHeaders,
  };
}

export function assertResponse(res, checks, requestName, details = 'invalid status') {
  const ok = check(res, checks);
  if (!ok) {
    failWithLog(requestName, details, res);
  }
}

export function parseJsonBody(res, requestName) {
  try {
    return JSON.parse(res.body);
  } catch (error) {
    failWithLog(requestName, 'invalid JSON', res);
  }
}

export function deleteFile(fileId) {
  const requestName = 'deleteFile';
  const res = http.del(`${baseUrl}/v1/files/${fileId}`, null, {
    headers: authHeaders(),
  });

  assertResponse(
    res,
    { 'status is 200 or 204': (response) => response.status === 200 || response.status === 204 },
    requestName,
    `fileId=${fileId}`,
  );
}

export function buildBinaryPayload(sizeBytes, leadingBytes = []) {
  const payload = new Uint8Array(sizeBytes);
  if (leadingBytes.length > 0) {
    payload.set(leadingBytes, 0);
  }
  return payload.buffer;
}

export function buildUniquePath(rootSegment) {
  const suffix = uniqueSuffix();
  return `/${rootSegment}/vu-${suffix.vuId}/iter-${suffix.iteration}`;
}

export function buildUniqueFileName(prefix, extension) {
  const suffix = uniqueSuffix();
  return `${prefix}-${suffix.vuId}-${suffix.iteration}.${extension}`;
}

export function buildDirectUploadFormData(path, fileName, contentType, payload) {
  return {
    request: http.file(JSON.stringify({ path }), 'request.json', 'application/json'),
    file: http.file(payload, fileName, contentType),
  };
}

export function failWithLog(name, details, res) {
  const responseInfo = formatResponseInfo(res);
  const suffix = responseInfo ? `, ${responseInfo}` : '';
  console.error(`${name} failed: ${details}${suffix}`);
  fail(`${name} response assertion failed`);
}

function formatResponseInfo(res) {
  if (!res) return '';
  const status = res.status ?? 'unknown';
  const url = res.url ?? res.request?.url ?? '';
  const headers = res.headers ? JSON.stringify(res.headers) : 'null';
  const body = res.body ?? '';
  return `response={status=${status}, url=${url}, headers=${headers}, body=${body}}`;
}

function readRequiredEnv(name) {
  const value = readStringEnv(name);
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function readStringEnv(name, defaultValue = '') {
  const value = __ENV[name];
  if (value === undefined || value === '') {
    return defaultValue;
  }

  return `${value}`;
}

function readNumberEnv(name, defaultValue) {
  const rawValue = __ENV[name];
  if (rawValue === undefined || rawValue === '') {
    return defaultValue;
  }

  const parsedValue = Number(rawValue);
  if (!Number.isFinite(parsedValue) || parsedValue <= 0) {
    throw new Error(`Environment variable ${name} must be a positive number`);
  }

  return parsedValue;
}

function uniqueSuffix() {
  return {
    vuId: exec.vu.idInTest,
    iteration: exec.scenario.iterationInTest,
  };
}
