import { ApiError, NetworkError } from '@/api/client';

/** Splits an error from the API into a form-level message and per-field messages. */
export function describeError(e: unknown): { message: string | null; fields: Record<string, string> } {
  if (e instanceof ApiError) {
    const fields = e.fieldErrors;
    // With field errors, the fields say it all; otherwise show the server's message.
    return { message: Object.keys(fields).length ? null : e.message, fields };
  }
  if (e instanceof NetworkError) return { message: e.message, fields: {} };
  return { message: 'Something went wrong. Please try again.', fields: {} };
}
