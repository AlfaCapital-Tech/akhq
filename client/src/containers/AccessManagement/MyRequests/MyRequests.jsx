import React from 'react';
import Header from '../../Header';
import Table from '../../../components/Table';
import Root from '../../../components/Root';
import { withRouter } from '../../../utils/withRouter';
import { uriAccessManagementMyRequests } from '../../../utils/endpoints';
import { Link } from 'react-router-dom';

class MyRequests extends Root {
  state = {
    clusterId: '',
    requests: [],
    loading: true
  };

  componentDidMount() {
    const { clusterId } = this.props.params;
    this.setState({ clusterId }, () => {
      this.loadRequests();
    });
  }

  async loadRequests() {
    const { clusterId } = this.state;
    try {
      const response = await this.getApi(uriAccessManagementMyRequests(clusterId));
      this.setState({ requests: response.data || [], loading: false });
    } catch (err) {
      console.error('Error:', err);
      this.setState({ loading: false });
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

  render() {
    const { requests, loading, clusterId } = this.state;

    return (
      <div>
        <Header title="My Access Requests" />
        <div className="mb-3">
          <Link
            to={`/ui/${clusterId}/access-management/manage`}
            className="btn btn-primary btn-sm"
          >
            Manage Accesses (Owner/Admin)
          </Link>
        </div>
        <Table
          loading={loading}
          columns={[
            {
              id: 'topicName',
              accessor: 'topicName',
              colName: 'Topic',
              sortable: true
            },
            {
              id: 'role',
              accessor: 'role',
              colName: 'Role',
              sortable: true
            },
            {
              id: 'status',
              accessor: 'status',
              colName: 'Status',
              cell: item => this.renderStatus(item.status)
            },
            {
              id: 'reason',
              accessor: 'reason',
              colName: 'Reason',
              cell: item => item.reason || '-'
            },
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
          data={requests}
          noContent="No access requests found"
        />
      </div>
    );
  }
}

export default withRouter(MyRequests);
