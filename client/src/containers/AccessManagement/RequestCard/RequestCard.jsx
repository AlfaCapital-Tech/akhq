import React from 'react';
import PropTypes from 'prop-types';
import { approversOf } from './linkedRequest';

/**
 * Card shown above the Access Management page when it is opened by the link from the notification email.
 */
function RequestCard({ result, renderStatus, renderTarget, renderApprovers, formatDate, onApprove, onReject }) {
  if (!result) return null;

  if (result.state === 'not-found') {
    return (
      <div className="alert alert-warning" role="alert" data-testid="request-card">
        Request not found or you are not its approver.
      </div>
    );
  }

  const { request, state } = result;
  const resolved = state === 'resolved';

  return (
    <div className="card mb-4 request-card" data-testid="request-card">
      <div className="card-header d-flex justify-content-between align-items-center">
        <span>
          Access request from <b>{request.username}</b>
        </span>
        {renderStatus(request.status)}
      </div>
      <div className="card-body">
        <dl className="row mb-0">
          <dt className="col-sm-3">Topic / Prefix</dt>
          <dd className="col-sm-9">{renderTarget(request)}</dd>
          <dt className="col-sm-3">Role</dt>
          <dd className="col-sm-9">{request.role}</dd>
          <dt className="col-sm-3">Reason</dt>
          <dd className="col-sm-9">{request.reason || '-'}</dd>
          <dt className="col-sm-3">Created</dt>
          <dd className="col-sm-9">{formatDate(request.createdAt)}</dd>
          {resolved && (
            <>
              <dt className="col-sm-3">Resolved by</dt>
              <dd className="col-sm-9">
                {request.resolvedBy || '-'} on {formatDate(request.resolvedAt)}
              </dd>
              {request.status === 'REJECTED' && (
                <>
                  <dt className="col-sm-3">Reject reason</dt>
                  <dd className="col-sm-9">{request.rejectReason || '-'}</dd>
                </>
              )}
            </>
          )}
        </dl>
        {state === 'pending-approver' && (
          <div className="mt-3">
            <button className="btn btn-success me-2" onClick={() => onApprove(request.id)}>
              Approve
            </button>
            <button className="btn btn-danger" onClick={() => onReject(request.id)}>
              Reject
            </button>
          </div>
        )}
        {state === 'pending-requester' && (
          <p className="text-muted mt-3 mb-0" data-testid="request-card-approvers">
            Waiting for the decision of: {renderApprovers(approversOf(request))}
          </p>
        )}
      </div>
    </div>
  );
}

RequestCard.propTypes = {
  result: PropTypes.shape({
    state: PropTypes.string.isRequired,
    request: PropTypes.object
  }),
  renderStatus: PropTypes.func.isRequired,
  renderTarget: PropTypes.func.isRequired,
  renderApprovers: PropTypes.func.isRequired,
  formatDate: PropTypes.func.isRequired,
  onApprove: PropTypes.func.isRequired,
  onReject: PropTypes.func.isRequired
};

export default RequestCard;
