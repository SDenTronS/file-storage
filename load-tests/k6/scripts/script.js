import http from 'k6/http';
import { sleep } from 'k6';
import {
  assertResponse,
  authHeaders,
  baseUrl,
  buildBinaryPayload,
  buildUniqueFileName,
  buildUniquePath,
  createConstantArrivalRateOptions,
  deleteFile,
  failWithLog,
  parseJsonBody,
} from './common.js';

const ZIP_SIGNATURE = [0x50, 0x4B, 0x03, 0x04];
const ARCHIVE_SIZE_BYTES = 10 * 1024 * 1024;
const PART_SIZE_BYTES = 5 * 1024 * 1024;
const ARCHIVE_TOTAL_BYTES = ARCHIVE_SIZE_BYTES + ZIP_SIGNATURE.length;

export const options = createConstantArrivalRateOptions('multipart_upload');

export default function() {
  const body = {
    path: buildUniquePath('multipart-upload'),
    fileName: buildUniqueFileName('archive', 'zip'),
    contentType: 'application/zip',
    overwrite: false,
    size: ARCHIVE_TOTAL_BYTES,
  };

  const res = http.post(`${baseUrl}/v1/uploads`, JSON.stringify(body), {
    headers: {
      ...authHeaders(),
      'Content-Type': 'application/json',
    },
  });

  assertResponse(
    res,
    { 'status is 200': (response) => response.status === 200 },
    'createUpload',
    `requestBody=${JSON.stringify(body)}`,
  );
  const createBody = parseJsonBody(res, 'createUpload');

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
  const url = `${baseUrl}/v1/uploads/${multipartUploadId}?partNumber=${partNumber}`;

  const res = http.post(url, '', {
    headers: authHeaders(),
  });

  assertResponse(res, { 'status is 200': (response) => response.status === 200 }, requestName);
  const body = parseJsonBody(res, requestName);

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
  return buildBinaryPayload(sizeBytes, includeSignature ? ZIP_SIGNATURE : []);
}

function uploadPart(multipartUploadId, partNumber, data) {
  const url = retrievePresignPut(multipartUploadId, partNumber);
  const res = http.put(url, data, {
    headers: {
      'Content-Type': 'application/octet-stream',
    },
  });

  assertResponse(res, { 'status is 200': (response) => response.status === 200 }, 'uploadPart', `partNumber=${partNumber}`);

  const etag = res.headers && (res.headers.ETag || res.headers.etag || res.headers.Etag);

  if (!etag) {
    failWithLog('uploadPart', 'empty etag', res);
  }

  return etag;
}

function completeUpload(uploadId, etags) {
  const requestName = 'completeUpload';
  const parts = etags.map((etag, index) => ({
    partNumber: index + 1,
    etag,
  }));

  const res = http.post(
    `${baseUrl}/v1/uploads/${uploadId}/complete`,
    JSON.stringify({ parts }),
    {
      headers: {
        ...authHeaders(),
        'Content-Type': 'application/json',
      },
    },
  );

  assertResponse(res, { 'status is 200': (response) => response.status === 200 }, requestName);
  const body = parseJsonBody(res, requestName);

  const fileId = body?.fileId;
  if (!fileId) {
    failWithLog(requestName, 'missing fileId', res);
  }

  return fileId;
}
