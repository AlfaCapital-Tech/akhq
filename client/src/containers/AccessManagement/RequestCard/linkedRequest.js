// Pure helpers for opening an access request by the `?request=<id>` link from the notification email.

export const LINKED_REQUEST_PARAM = 'request';

export function requestIdFromSearch(search) {
  if (!search) return null;
  const value = new URLSearchParams(search).get(LINKED_REQUEST_PARAM);
  return value && value.trim() ? value.trim() : null;
}

/**
 * Decides what the request card shows.
 * `allRequests` is the approver's area of responsibility (already filtered by the server),
 * `myRequests` are the current user's own requests.
 */
export function resolveLinkedRequest(requestId, { allRequests = [], myRequests = [] } = {}) {
  if (!requestId) return null;

  const asApprover = allRequests.find(r => r.id === requestId);
  if (asApprover) {
    return {
      state: asApprover.status === 'PENDING' ? 'pending-approver' : 'resolved',
      request: asApprover
    };
  }

  const asRequester = myRequests.find(r => r.id === requestId);
  if (asRequester) {
    return {
      state: asRequester.status === 'PENDING' ? 'pending-requester' : 'resolved',
      request: asRequester
    };
  }

  return { state: 'not-found', request: null };
}
