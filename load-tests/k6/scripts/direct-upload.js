import http from 'k6/http';
import {
  assertResponse,
  authHeaders,
  baseUrl,
  buildBinaryPayload,
  buildDirectUploadFormData,
  buildUniqueFileName,
  buildUniquePath,
  createConstantArrivalRateOptions,
  deleteFile,
  failWithLog,
  parseJsonBody,
} from './common.js';

const DIRECT_UPLOAD_SIZE_BYTES = 5 * 1024;
const DIRECT_UPLOAD_SIGNATURE = [0x44, 0x49, 0x52, 0x45, 0x43, 0x54];
const DIRECT_UPLOAD_CONTENT_TYPE = 'application/octet-stream';
const DIRECT_UPLOAD_PAYLOAD = buildBinaryPayload(DIRECT_UPLOAD_SIZE_BYTES, DIRECT_UPLOAD_SIGNATURE);
const DIRECT_UPLOAD_REQUEST_NAME = 'directUpload';

export const options = createConstantArrivalRateOptions('direct_upload');

export default function() {
  const path = buildUniquePath('direct-upload');
  const fileName = buildUniqueFileName('direct-upload', 'bin');
  const body = buildDirectUploadFormData(path, fileName, DIRECT_UPLOAD_CONTENT_TYPE, DIRECT_UPLOAD_PAYLOAD);

  const res = http.post(`${baseUrl}/v1/uploads/direct`, body, {
    headers: authHeaders(),
  });

  assertResponse(
    res,
    { 'status is 200': (response) => response.status === 200 },
    DIRECT_UPLOAD_REQUEST_NAME,
    `path=${path}, fileName=${fileName}, size=${DIRECT_UPLOAD_SIZE_BYTES}`,
  );

  const responseBody = parseJsonBody(res, DIRECT_UPLOAD_REQUEST_NAME);
  const fileId = responseBody?.fileId;
  if (!fileId) {
    failWithLog(DIRECT_UPLOAD_REQUEST_NAME, 'missing fileId', res);
  }

  deleteFile(fileId);
}
