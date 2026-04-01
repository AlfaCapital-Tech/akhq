import React from 'react';
import PropTypes from 'prop-types';
import Root from '../../../components/Root';
import { toast } from 'react-toastify';
import {
  uriAccessManagementCreateRequest,
  uriAccessManagementMyAccess,
  uriAccessManagementTopicOwners
} from '../../../utils/endpoints';

class RequestAccessModal extends Root {
  state = {
    role: '',
    reason: '',
    owners: [],
    myAccess: [],
    loading: true,
    submitting: false
  };

  componentDidUpdate(prevProps) {
    if (this.props.show && !prevProps.show) {
      this.loadInfo();
    }
  }

  async loadInfo() {
    const { clusterId, topicName } = this.props;
    this.setState({ loading: true, role: '', reason: '' });
    try {
      const [ownersRes, accessRes] = await Promise.all([
        this.getApi(uriAccessManagementTopicOwners(clusterId, topicName)),
        this.getApi(uriAccessManagementMyAccess(clusterId, topicName))
      ]);
      this.setState({
        owners: ownersRes.data?.owners || [],
        myAccess: accessRes.data || [],
        loading: false
      });
    } catch (err) {
      this.setState({ loading: false });
    }
  }

  async handleSubmit() {
    const { clusterId, topicName, onClose } = this.props;
    const { role, reason } = this.state;

    if (!role) {
      toast.warn('Please select a role');
      return;
    }

    this.setState({ submitting: true });
    try {
      await this.postApi(uriAccessManagementCreateRequest(clusterId), {
        topicName,
        role,
        reason
      });
      toast.success('Access request submitted');
      this.setState({ submitting: false });
      onClose();
    } catch (err) {
      const message = err?.response?.data?.message || 'Failed to create request';
      toast.error(message);
      this.setState({ submitting: false });
    }
  }

  render() {
    const { show, onClose, topicName } = this.props;
    const { role, reason, owners, myAccess, loading, submitting } = this.state;

    if (!show) return null;

    return (
      <div className="modal display-block">
        <div
          className="swal2-container swal2-center swal2-fade swal2-shown"
          style={{ overflowY: 'auto' }}
        >
          <div
            className="swal2-popup swal2-modal swal2-show"
            tabIndex="-1"
            role="dialog"
            aria-modal="true"
            style={{ display: 'flex', width: '40em' }}
          >
            <div className="swal2-header">
              <h5 className="mb-0">Request Access to {topicName}</h5>
            </div>
            <div className="swal2-content" style={{ width: '100%' }}>
              <div style={{ display: 'block', textAlign: 'left', padding: '10px 20px' }}>
                {loading ? (
                  <p>Loading...</p>
                ) : (
                  <>
                    {myAccess.length > 0 && (
                      <div className="alert alert-info mb-3">
                        <strong>Current access:</strong>{' '}
                        {myAccess.map(a => a.role).join(', ')}
                      </div>
                    )}

                    {owners.length > 0 ? (
                      <div className="mb-3">
                        <label className="form-label fw-bold">Topic owners:</label>
                        <div>
                          {owners.map((o, i) => (
                            <span key={i} className="badge bg-info text-dark me-1">
                              {o.username}{o.email ? ` (${o.email})` : ''}
                            </span>
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div className="alert alert-warning mb-3">
                        <strong>Owner not assigned.</strong> The request will be reviewed by an administrator.
                      </div>
                    )}

                    <div className="mb-3">
                      <label className="form-label fw-bold">Role *</label>
                      <select
                        className="form-select"
                        value={role}
                        onChange={e => this.setState({ role: e.target.value })}
                      >
                        <option value="">Select role...</option>
                        {(JSON.parse(sessionStorage.getItem('auths') || '{}').requestableRoles || []).map(r => (
                          <option key={r.name} value={r.name}>
                            {r.name} — {r.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="mb-3">
                      <label className="form-label fw-bold">Reason</label>
                      <textarea
                        className="form-control"
                        rows="3"
                        placeholder="Why do you need access?"
                        value={reason}
                        onChange={e => this.setState({ reason: e.target.value })}
                      />
                    </div>
                  </>
                )}
              </div>
            </div>
            <div className="swal2-actions" style={{ display: 'flex' }}>
              <button
                type="button"
                className="swal2-confirm swal2-styled"
                disabled={submitting || loading}
                onClick={() => this.handleSubmit()}
              >
                {submitting ? 'Submitting...' : 'Request'}
              </button>
              <button
                type="button"
                className="swal2-cancel swal2-styled"
                style={{ display: 'inline-block' }}
                onClick={onClose}
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

RequestAccessModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  clusterId: PropTypes.string.isRequired,
  topicName: PropTypes.string.isRequired
};

export default RequestAccessModal;
