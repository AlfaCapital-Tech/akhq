import { describe, expect, it } from 'vitest';
import { requestIdFromSearch, resolveLinkedRequest } from './linkedRequest';

const pending = { id: 'a1', status: 'PENDING', username: 'alice' };
const approved = { id: 'b2', status: 'APPROVED', username: 'bob', resolvedBy: 'owner-a' };

describe('requestIdFromSearch', () => {
  it('reads the request id from the query string', () => {
    expect(requestIdFromSearch('?request=a1')).toBe('a1');
    expect(requestIdFromSearch('?tour=1&request=a1')).toBe('a1');
  });

  it('returns null without the parameter', () => {
    expect(requestIdFromSearch('')).toBeNull();
    expect(requestIdFromSearch('?tour=1')).toBeNull();
    expect(requestIdFromSearch('?request=')).toBeNull();
  });
});

describe('resolveLinkedRequest', () => {
  it('shows actions to an approver of a pending request', () => {
    const result = resolveLinkedRequest('a1', { allRequests: [pending], myRequests: [] });
    expect(result.state).toBe('pending-approver');
    expect(result.request).toBe(pending);
  });

  it('shows the status of a resolved request', () => {
    const result = resolveLinkedRequest('b2', { allRequests: [approved] });
    expect(result.state).toBe('resolved');
    expect(result.request.resolvedBy).toBe('owner-a');
  });

  it('shows a pending request to its requester without actions', () => {
    const result = resolveLinkedRequest('a1', { allRequests: [], myRequests: [pending] });
    expect(result.state).toBe('pending-requester');
  });

  it('reports a request outside the area of responsibility', () => {
    const result = resolveLinkedRequest('zz', { allRequests: [pending], myRequests: [approved] });
    expect(result.state).toBe('not-found');
    expect(result.request).toBeNull();
  });

  it('returns null without an id', () => {
    expect(resolveLinkedRequest(null, { allRequests: [pending] })).toBeNull();
  });
});
