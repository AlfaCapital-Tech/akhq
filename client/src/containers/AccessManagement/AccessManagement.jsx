import React from 'react';
import Header from '../Header';
import Table from '../../components/Table';
import ConfirmModal from '../../components/Modal/ConfirmModal';
import Root from '../../components/Root';
import { withRouter } from '../../utils/withRouter';
import { toast } from 'react-toastify';
import {
  uriAccessManagementMyRequests,
  uriAccessManagementPendingRequests,
  uriAccessManagementAllRequests,
  uriAccessManagementAccesses,
  uriAccessManagementApprove,
  uriAccessManagementReject,
  uriAccessManagementRevokeAccess
} from '../../utils/endpoints';

class AccessManagement extends Root {
  state = {
    clusterId: '',
    // My Requests
    myRequests: [],
    myRequestsLoading: true,
    // Management (owner/admin)
    isOwner: false,
    managementLoading: true,
    selectedTab: 'pending',
    pendingRequests: [],
    allRequests: [],
    accesses: [],
    // Modals
    showRejectModal: false,
    showRevokeModal: false,
    rejectRequestId: null,
    revokeAccessId: null,
    rejectReason: ''
  };

  componentDidMount() {
    const { clusterId } = this.props.params;
    this.setState({ clusterId }, () => {
      this.loadMyRequests();
      this.loadManagementData();
    });
  }

  async loadMyRequests() {
    const { clusterId } = this.state;
    try {
      const response = await this.getApi(uriAccessManagementMyRequests(clusterId));
      this.setState({ myRequests: response.data || [], myRequestsLoading: false });
    } catch (err) {
      console.error('Error loading my requests:', err);
      this.setState({ myRequestsLoading: false });
    }
  }

  async loadManagementData() {
    const { clusterId } = this.state;
    this.setState({ managementLoading: true });
    try {
      const [pending, all, accesses] = await Promise.all([
        this.getApi(uriAccessManagementPendingRequests(clusterId)),
        this.getApi(uriAccessManagementAllRequests(clusterId)),
        this.getApi(uriAccessManagementAccesses(clusterId))
      ]);
      const pendingData = pending.data || [];
      const allData = all.data || [];
      const accessesData = accesses.data || [];
      const isOwner = pendingData.length > 0 || allData.length > 0 || accessesData.length > 0;
      this.setState({
        pendingRequests: pendingData,
        allRequests: allData,
        accesses: accessesData,
        isOwner,
        managementLoading: false
      });
    } catch (err) {
      console.error('Error loading management data:', err);
      this.setState({ managementLoading: false });
    }
  }

  async handleApprove(requestId) {
    const { clusterId } = this.state;
    try {
      await this.putApi(uriAccessManagementApprove(clusterId, requestId));
      toast.success('Request approved');
      this.loadMyRequests();
      this.loadManagementData();
    } catch (err) {
      toast.error('Failed to approve request');
    }
  }

  openRejectModal(requestId) {
    this.setState({ showRejectModal: true, rejectRequestId: requestId, rejectReason: '' });
  }

  closeRejectModal = () => {
    this.setState({ showRejectModal: false, rejectRequestId: null, rejectReason: '' });
  };

  async handleReject() {
    const { clusterId, rejectRequestId, rejectReason } = this.state;
    try {
      await this.putApi(uriAccessManagementReject(clusterId, rejectRequestId), {
        rejectReason: rejectReason
      });
      toast.success('Request rejected');
      this.closeRejectModal();
      this.loadMyRequests();
      this.loadManagementData();
    } catch (err) {
      toast.error('Failed to reject request');
    }
  }

  openRevokeModal(accessId) {
    this.setState({ showRevokeModal: true, revokeAccessId: accessId });
  }

  closeRevokeModal = () => {
    this.setState({ showRevokeModal: false, revokeAccessId: null });
  };

  async handleRevoke() {
    const { clusterId, revokeAccessId } = this.state;
    try {
      await this.removeApi(uriAccessManagementRevokeAccess(clusterId, revokeAccessId));
      toast.success('Access revoked');
      this.closeRevokeModal();
      this.loadMyRequests();
      this.loadManagementData();
    } catch (err) {
      toast.error('Failed to revoke access');
    }
  }

  renderStatus(status) {
    switch (status) {
      case 'PENDING':
        return <span className="badge bg-warning text-dark">Pending</span>;
      case 'APPROVED':
        return <span className="badge bg-success">Approved</span>;
      case 'REJECTED':
        return <span className="badge bg-danger">Rejected</span>;
      default:
        return <span className="badge bg-secondary">{status}</span>;
    }
  }

  formatDate(dateStr) {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString();
  }

  tabClassName = tab => {
    return this.state.selectedTab === tab ? 'nav-link active' : 'nav-link';
  };

  // --- My Requests section ---

  renderMyRequests() {
    const { myRequests, myRequestsLoading } = this.state;
    return (
      <Table
        loading={myRequestsLoading}
        columns={[
          { id: 'topicName', accessor: 'topicName', colName: 'Topic', sortable: true },
          { id: 'role', accessor: 'role', colName: 'Role', sortable: true },
          {
            id: 'status',
            accessor: 'status',
            colName: 'Status',
            cell: item => this.renderStatus(item.status)
          },
          { id: 'reason', accessor: 'reason', colName: 'Reason', cell: item => item.reason || '-' },
          {
            id: 'rejectReason',
            accessor: 'rejectReason',
            colName: 'Reject Reason',
            cell: item => item.rejectReason || '-'
          },
          {
            id: 'resolvedBy',
            accessor: 'resolvedBy',
            colName: 'Resolved By',
            cell: item => item.resolvedBy || '-'
          },
          {
            id: 'createdAt',
            accessor: 'createdAt',
            colName: 'Created',
            cell: item => this.formatDate(item.createdAt)
          }
        ]}
        data={myRequests}
        noContent="No access requests found"
      />
    );
  }

  // --- Management tabs ---

  renderPendingTab() {
    const { pendingRequests, managementLoading } = this.state;
    return (
      <Table
        loading={managementLoading}
        columns={[
          { id: 'username', accessor: 'username', colName: 'User', sortable: true },
          { id: 'topicName', accessor: 'topicName', colName: 'Topic', sortable: true },
          { id: 'role', accessor: 'role', colName: 'Role', sortable: true },
          { id: 'reason', accessor: 'reason', colName: 'Reason', cell: item => item.reason || '-' },
          {
            id: 'createdAt',
            accessor: 'createdAt',
            colName: 'Created',
            cell: item => this.formatDate(item.createdAt)
          },
          {
            id: 'actions',
            accessor: 'id',
            colName: 'Actions',
            cell: item => (
              <div>
                <button
                  className="btn btn-success btn-sm me-1"
                  onClick={() => this.handleApprove(item.id)}
                >
                  Approve
                </button>
                <button
                  className="btn btn-danger btn-sm"
                  onClick={() => this.openRejectModal(item.id)}
                >
                  Reject
                </button>
              </div>
            )
          }
        ]}
        data={pendingRequests}
        noContent="No pending requests"
      />
    );
  }

  renderHistoryTab() {
    const { allRequests, managementLoading } = this.state;
    return (
      <Table
        loading={managementLoading}
        columns={[
          { id: 'username', accessor: 'username', colName: 'User', sortable: true },
          { id: 'topicName', accessor: 'topicName', colName: 'Topic', sortable: true },
          { id: 'role', accessor: 'role', colName: 'Role', sortable: true },
          {
            id: 'status',
            accessor: 'status',
            colName: 'Status',
            cell: item => this.renderStatus(item.status)
          },
          { id: 'reason', accessor: 'reason', colName: 'Reason', cell: item => item.reason || '-' },
          {
            id: 'resolvedBy',
            accessor: 'resolvedBy',
            colName: 'Resolved By',
            cell: item => item.resolvedBy || '-'
          },
          {
            id: 'createdAt',
            accessor: 'createdAt',
            colName: 'Created',
            cell: item => this.formatDate(item.createdAt)
          }
        ]}
        data={allRequests}
        noContent="No requests found"
      />
    );
  }

  renderAccessesTab() {
    const { accesses, managementLoading } = this.state;
    return (
      <Table
        loading={managementLoading}
        columns={[
          { id: 'username', accessor: 'username', colName: 'User', sortable: true },
          { id: 'topicName', accessor: 'topicName', colName: 'Topic', sortable: true },
          { id: 'role', accessor: 'role', colName: 'Role', sortable: true },
          { id: 'grantedBy', accessor: 'grantedBy', colName: 'Granted By' },
          {
            id: 'grantedAt',
            accessor: 'grantedAt',
            colName: 'Granted At',
            cell: item => this.formatDate(item.grantedAt)
          },
          {
            id: 'actions',
            accessor: 'id',
            colName: 'Actions',
            cell: item => (
              <button
                className="btn btn-danger btn-sm"
                onClick={() => this.openRevokeModal(item.id)}
              >
                Revoke
              </button>
            )
          }
        ]}
        data={accesses}
        noContent="No active accesses"
      />
    );
  }

  renderSelectedTab() {
    switch (this.state.selectedTab) {
      case 'pending':
        return this.renderPendingTab();
      case 'history':
        return this.renderHistoryTab();
      case 'accesses':
        return this.renderAccessesTab();
      default:
        return this.renderPendingTab();
    }
  }

  renderManagementSection() {
    const { isOwner, managementLoading, pendingRequests } = this.state;

    if (managementLoading || !isOwner) {
      return null;
    }

    return (
      <div className="mt-4">
        <h4>Manage Accesses</h4>
        <div className="tabs-container" style={{ marginBottom: '4%' }}>
          <ul className="nav nav-tabs" role="tablist">
            <li className="nav-item">
              <button
                className={this.tabClassName('pending')}
                onClick={() => this.setState({ selectedTab: 'pending' })}
              >
                Pending
                {pendingRequests.length > 0 && (
                  <span className="badge bg-warning text-dark ms-2">
                    {pendingRequests.length}
                  </span>
                )}
              </button>
            </li>
            <li className="nav-item">
              <button
                className={this.tabClassName('history')}
                onClick={() => this.setState({ selectedTab: 'history' })}
              >
                History
              </button>
            </li>
            <li className="nav-item">
              <button
                className={this.tabClassName('accesses')}
                onClick={() => this.setState({ selectedTab: 'accesses' })}
              >
                Current Accesses
              </button>
            </li>
          </ul>

          <div className="tab-content">
            <div className="tab-pane active" role="tabpanel">
              {this.renderSelectedTab()}
            </div>
          </div>
        </div>
      </div>
    );
  }

  renderModals() {
    const { showRejectModal, showRevokeModal, rejectReason } = this.state;

    return (
      <>
        {/* Reject Modal */}
        <div className={showRejectModal ? 'modal display-block' : 'modal display-none'}>
          <div
            className="swal2-container swal2-center swal2-fade swal2-shown"
            style={{ overflowY: 'auto' }}
          >
            <div
              className="swal2-popup swal2-modal swal2-show"
              tabIndex="-1"
              role="dialog"
              aria-modal="true"
              style={{ display: 'flex' }}
            >
              <div className="swal2-header">
                <div
                  className="swal2-icon swal2-question swal2-animate-question-icon"
                  style={{ display: 'flex' }}
                >
                  <span className="swal2-icon-text">?</span>
                </div>
              </div>
              <div className="swal2-content">
                <div id="swal2-content" style={{ display: 'block' }}>
                  <p>Reject this access request?</p>
                  <textarea
                    className="form-control mt-2"
                    rows="3"
                    placeholder="Reason for rejection (optional)"
                    value={rejectReason}
                    onChange={e => this.setState({ rejectReason: e.target.value })}
                  />
                </div>
              </div>
              <div className="swal2-actions" style={{ display: 'flex' }}>
                <button
                  type="button"
                  className="swal2-confirm swal2-styled"
                  style={{ backgroundColor: '#dc3545', borderColor: '#dc3545' }}
                  onClick={() => this.handleReject()}
                >
                  Reject
                </button>
                <button
                  type="button"
                  className="swal2-cancel swal2-styled"
                  style={{ display: 'inline-block' }}
                  onClick={this.closeRejectModal}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Revoke Confirm Modal */}
        <ConfirmModal
          show={showRevokeModal}
          handleCancel={this.closeRevokeModal}
          handleConfirm={() => this.handleRevoke()}
          message="Are you sure you want to revoke this access?"
        />
      </>
    );
  }

  render() {
    return (
      <div>
        <Header title="Access Management" />
        <h4>My Requests</h4>
        {this.renderMyRequests()}
        {this.renderManagementSection()}
        {this.renderModals()}
      </div>
    );
  }
}

export default withRouter(AccessManagement);
