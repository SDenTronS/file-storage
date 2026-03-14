import http from 'k6/http';
import { sleep, check, fail } from 'k6';

const token = `${__ENV.AUTH_TOKEN}`;
const base_url = `${__ENV.BASE_URL}`;
const ZIP_SIGNATURE = new Uint8Array([
  0x50, 0x4B, 0x03, 0x04,
]);
const ARCHIVE_SIZE_BYTES = 10 * 1024 * 1024;
const PART_SIZE_BYTES = 5 * 1024 * 1024;
const ARCHIVE_TOTAL_BYTES = ARCHIVE_SIZE_BYTES + ZIP_SIGNATURE.length;

export const options = {
  scenarios: {
    scenario_1: {
      executor: 'constant-arrival-rate',

      duration: '30s',
      rate: 400,
      preAllocatedVUs: 5000,
      timeUnit: '10s',
      gracefulStop: '10s',
    }
  },
};

export default function() {
  let body = {
    path: '/photos_2025',
    fileName: 'archive_100mb.zip',
    contentType: 'application/zip',
    overwrite: false,
    size: ARCHIVE_TOTAL_BYTES
  }

  let res = http.post(`${base_url}/v1/uploads`, JSON.stringify(body), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token,
    }
  });

  let ok = check(res, { "status is 200": (res) => res.status === 200 });

  if (!ok) {
    failWithLog('createUpload', `requestBody=${JSON.stringify(body)}`, res);
  }

  let createBody;
  try {
    createBody = JSON.parse(res.body);
  } catch (e) {
    failWithLog('createUpload', 'invalid JSON', res);
  }

  const multipartUploadId = createBody?.multipartUploadId ?? createBody?.uploadId ?? createBody?.id;
  if (!multipartUploadId) {
    failWithLog('createUpload', 'missing upload id', res);
  }

  const etags = uploadArchiveInParts(multipartUploadId);
  const fileId = completeUpload(multipartUploadId, etags);
  sleep(2);
  deleteFile(fileId);
}

function retrievePresignPut(multipartUploadId, partNumber) {
  const requestName = 'retrievePresignPut';
  const url = `${base_url}/v1/uploads/${multipartUploadId}?partNumber=${partNumber}`;

  const res = http.post(url, '', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
  });

  if (!ok) {
    failWithLog(requestName, 'invalid status', res);
  }

  let body;
  try {
    body = JSON.parse(res.body);
  } catch (e) {
    failWithLog(requestName, 'invalid JSON', res);
  }

  if (!body?.url) {
    failWithLog(requestName, 'missing url', res);
  }

  return body.url;
}

function uploadArchiveInParts(multipartUploadId) {
  const etags = [];

  const archiveParts = Math.ceil(ARCHIVE_SIZE_BYTES / PART_SIZE_BYTES);
  for (let index = 0; index < archiveParts; index += 1) {
    const partNumber = index + 1;
    const offset = index * PART_SIZE_BYTES;
    const remaining = ARCHIVE_SIZE_BYTES - offset;
    const currentSize = index === 0
      ? PART_SIZE_BYTES + ZIP_SIGNATURE.length
      : (remaining >= PART_SIZE_BYTES ? PART_SIZE_BYTES : remaining);
    const data = buildArchivePart(currentSize, index === 0);
    const etag = uploadPart(multipartUploadId, partNumber, data);
    etags.push(etag);
  }

  return etags;
}

function buildArchivePart(sizeBytes, includeSignature = false) {
  const part = new Uint8Array(sizeBytes);
  if (includeSignature) {
    part.set(ZIP_SIGNATURE, 0);
  }
  return part;
}

function uploadPart(multipartUploadId, partNumber, data) {
  const url = retrievePresignPut(multipartUploadId, partNumber);
  let res = http.put(url, data, {
    headers: {
      'Content-Type': 'application/octet-stream',
    }
  });

  let ok = check(res, { "status is 200": (res) => res.status === 200 });
  if (!ok) {
    failWithLog('uploadPart', `partNumber=${partNumber}`, res);
  }

  let etag = res.headers && (res.headers.ETag || res.headers.etag || res.headers.Etag);

  if (!etag) {
    failWithLog('uploadPart', `empty etag`, res);
  }

  return etag;
}

function completeUpload(uploadId, etags) {
  const requestName = 'completeUpload';
  const parts = etags.map((etag, index) => ({
    partNumber: index + 1,
    etag: etag,
  }));

  const res = http.post(
    `${base_url}/v1/uploads/${uploadId}/complete`,
    JSON.stringify({ parts }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token,
      },
    }
  );

  const ok = check(res, { "status is 200": (r) => r.status === 200 });
  if (!ok) {
    failWithLog(requestName, 'invalid status', res);
  }

  let body;
  try {
    body = JSON.parse(res.body);
  } catch (e) {
    failWithLog(requestName, 'invalid JSON', res);
  }

  const fileId = body?.fileId;
  if (!fileId) {
    failWithLog(requestName, 'missing fileId', res);
  }

  return fileId;
}

function deleteFile(fileId) {
  const requestName = 'deleteFile';

  const res = http.del(
    `${base_url}/v1/files/${fileId}`,
    null,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  const ok = check(res, {
    'status is 200 or 204': (r) => r.status === 200 || r.status === 204,
  });

  if (!ok) {
    failWithLog(requestName, `fileId=${fileId}`, res);
  }
}

function failWithLog(name, details, res) {
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
