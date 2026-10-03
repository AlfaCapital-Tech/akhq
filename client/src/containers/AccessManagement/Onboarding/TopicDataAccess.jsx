import React, { useEffect, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faShieldAlt } from '@fortawesome/free-solid-svg-icons';
import { get } from '../../../utils/api';
import { uriAccessManagementDataAccess } from '../../../utils/endpoints';
import RequestAccessModal from '../RequestAccessModal';
import { showMascot } from './Mascot';
import { isAccessManagementEnabled } from './onboardingState';

// Renders the topic data tab only when the user can read it, otherwise a "no access" notice instead of a 403.
export default function TopicDataAccess({ clusterId, topicId, children }) {
  const enabled = isAccessManagementEnabled();
  const [read, setRead] = useState(enabled ? null : true);
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    if (!enabled) return;
    get(uriAccessManagementDataAccess(clusterId, topicId))
      .then(res => setRead(res.data?.read !== false))
      // on error fall back to the upstream behaviour
      .catch(() => setRead(true));
  }, [enabled, clusterId, topicId]);

  useEffect(() => {
    if (read !== false) return;
    showMascot('topic', {
      pose: 'think',
      title: 'Хотите почитать сообщения?',
      text: 'Доступа к сообщениям этого топика у вас пока нет. Могу помочь его запросить — это займёт минуту.',
      action: { label: 'Запросить доступ', onClick: () => setShowModal(true) }
    });
  }, [read]);

  if (read === null) return null;
  if (read) return children;

  return (
    <>
      <div className="alert alert-secondary d-flex align-items-center justify-content-between" role="status">
        <span>Нет доступа к сообщениям этого топика.</span>
        <button className="btn btn-danger btn-sm" onClick={() => setShowModal(true)}>
          <FontAwesomeIcon icon={faShieldAlt} aria-hidden={true} /> Request Access
        </button>
      </div>
      <RequestAccessModal
        show={showModal}
        onClose={() => setShowModal(false)}
        clusterId={clusterId}
        topicName={topicId}
      />
    </>
  );
}
