import React from 'react';
import PropTypes from 'prop-types';
import Root from '../../../components/Root';
import { toast } from 'react-toastify';
import {
  uriAccessManagementCreateRequest,
  uriAccessManagementPrefixes
} from '../../../utils/endpoints';

class RequestPrefixModal extends Root {
  state = {
    prefix: '',
    role: '',
    reason: '',
    prefixes: [],
    loading: true,
    submitting: false
  };

  componentDidUpdate(prevProps) {
    if (this.props.show && !prevProps.show) {
      this.loadPrefixes();
    }
  }

  async loadPrefixes() {
    const { clusterId } = this.props;
    this.setState({ loading: true, prefix: '', role: '', reason: '' });
    try {
      const res = await this.getApi(uriAccessManagementPrefixes(clusterId));
      this.setState({ prefixes: res.data || [], loading: false });
    } catch (err) {
      this.setState({ loading: false });
    }
  }

  async handleSubmit() {
    const { clusterId, onClose, onSuccess } = this.props;
    const { prefix, role, reason } = this.state;

    if (!prefix) {
      toast.warn('Please select a prefix');
      return;
    }
    if (!role) {
      toast.warn('Please select a role');
      return;
    }

    this.setState({ submitting: true });
    try {
      await this.postApi(uriAccessManagementCreateRequest(clusterId), {
        prefix,
        role,
        reason
      });
      toast.success('Prefix access request submitted');
      this.setState({ submitting: false });
      onClose();
      if (onSuccess) onSuccess();
    } catch (err) {
      const message = err?.response?.data?.message || 'Failed to create request';
      toast.error(message);
      this.setState({ submitting: false });
    }
  }

  getSelectedPrefixOwners() {
    const { prefix, prefixes } = this.state;
    if (!prefix) return [];
    const found = prefixes.find(p => p.prefix === prefix);
    return found ? found.owners : [];
  }

  render() {
    const { show, onClose } = this.props;
    const { prefix, role, reason, prefixes, loading, submitting } = this.state;

    if (!show) return null;

    const owners = this.getSelectedPrefixOwners();

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
              <h5 className="mb-0">Request Prefix Access</h5>
            </div>
            <div className="swal2-content" style={{ width: '100%' }}>
              <div style={{ display: 'block', textAlign: 'left', padding: '10px 20px' }}>
                {loading ? (
                  <p>Loading...</p>
                ) : (
                  <>
                    <div className="mb-3">
                      <label className="form-label fw-bold">Prefix *</label>
                      <select
                        className="form-select"
                        value={prefix}
                        onChange={e => this.setState({ prefix: e.target.value })}
                      >
                        <option value="">Select prefix...</option>
                        {prefixes.map(p => (
                          <option key={p.prefix} value={p.prefix}>
                            {p.prefix}
                          </option>
                        ))}
                      </select>
                    </div>

                    {owners.length > 0 && (
                      <div className="mb-3">
                        <label className="form-label fw-bold">Prefix owners:</label>
                        <div>
                          {owners.map((o, i) => (
                            <span key={i} className="badge bg-info text-dark me-1">
                              {o.username}{o.email ? ` (${o.email})` : ''}
                            </span>
                          ))}
                        </div>
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
                        placeholder="Why do you need access to this prefix?"
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

RequestPrefixModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  clusterId: PropTypes.string.isRequired,
  onSuccess: PropTypes.func
};

export default RequestPrefixModal;
