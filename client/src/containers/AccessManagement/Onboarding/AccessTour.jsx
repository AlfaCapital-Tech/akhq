import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Joyride, STATUS } from 'react-joyride';
import { get } from '../../../utils/api';
import { uriAccessManagementMe } from '../../../utils/endpoints';
import { MascotBubble } from './Mascot';
import { markTourSeen } from './onboardingState';

const LOCALE = { back: 'Назад', close: 'Закрыть', last: 'Готово', next: 'Далее', skip: 'Пропустить' };

const target = name => `[data-tour="${name}"]`;

// Steps whose target is not on the page (e.g. management section hidden while empty) are shown centered.
const step = (name, pose, title, content) => {
  const present = name && document.querySelector(target(name));
  return present
    ? { target: target(name), data: { pose }, title, content }
    : { target: 'body', placement: 'center', data: { pose }, title, content };
};

const buildSteps = approver => [
  step(
    'access-menu',
    'point',
    'Раздел Access',
    'Всё про доступы к топикам — здесь. Запросы, их статусы, а для владельцев префиксов — согласование.'
  ),
  step(
    'my-requests',
    'point',
    'Мои запросы',
    'Здесь ваши запросы и их статусы: Pending — ждёт согласования, Approved — доступ выдан, Rejected — отклонён с причиной.'
  ),
  step(
    'prefix-request',
    'point',
    'Доступ на префикс',
    'Нужны все топики команды? Запросите доступ сразу на префикс — он покроет и топики, которые появятся позже.'
  ),
  step(
    null,
    'think',
    'Доступ к одному топику',
    'На странице любого топика есть кнопка Request Access: выберите роль, напишите, зачем нужен доступ, — и запрос уйдёт владельцу префикса. Если владельца нет, запрос согласуют суперадмины.'
  ),
  ...(approver
    ? [
        step(
          'pending',
          'point',
          'Вы согласующий',
          'Запросы к вашим префиксам попадают во вкладку Pending, а у пункта Access в меню появляется счётчик.'
        ),
        step(
          'pending',
          'think',
          'Approve и Reject',
          'Approve выдаёт доступ сразу. Reject — с причиной, её увидит автор запроса. Выданный доступ можно отозвать во вкладке Current Accesses.'
        )
      ]
    : []),
  step(
    null,
    'happy',
    'Готово!',
    'Доступ начинает работать сразу после одобрения — перелогиниваться не нужно.'
  )
];

function MascotTooltip({ step: current, index, size, isLastStep, backProps, primaryProps, skipProps, tooltipProps }) {
  return (
    <div {...tooltipProps}>
      <MascotBubble
        pose={current.data?.pose}
        title={current.title}
        actions={
          <>
            <span className="akhq-mascot__progress">
              {index + 1} из {size}
            </span>
            {!isLastStep && (
              <button className="btn btn-link btn-sm text-secondary" {...skipProps}>
                Пропустить
              </button>
            )}
            {index > 0 && (
              <button className="btn btn-outline-secondary btn-sm" {...backProps}>
                Назад
              </button>
            )}
            <button className="btn btn-primary btn-sm" {...primaryProps}>
              {isLastStep ? 'Готово' : 'Далее'}
            </button>
          </>
        }
      >
        {current.content}
      </MascotBubble>
    </div>
  );
}

export default function AccessTour({ clusterId, ready }) {
  const [steps, setSteps] = useState(null);
  const { search, pathname } = useLocation();
  const navigate = useNavigate();

  const start = async () => {
    let approver = false;
    try {
      approver = (await get(uriAccessManagementMe(clusterId))).data?.approver === true;
    } catch {
      // without the flag the tour just skips the approval steps
    }
    setSteps(buildSteps(approver));
  };

  useEffect(() => {
    if (ready && new URLSearchParams(search).has('tour')) {
      navigate(pathname, { replace: true });
      start();
    }
  }, [ready, search]);

  const onEvent = ({ status }) => {
    if (status === STATUS.FINISHED || status === STATUS.SKIPPED) {
      markTourSeen();
      setSteps(null);
    }
  };

  return (
    <>
      <button className="btn btn-secondary btn-sm" onClick={start} disabled={!ready}>
        Как получить доступ?
      </button>
      {steps && (
        <Joyride
          run
          continuous
          steps={steps}
          onEvent={onEvent}
          tooltipComponent={MascotTooltip}
          locale={LOCALE}
          options={{ skipBeacon: true, overlayClickAction: false, zIndex: 1050 }}
        />
      )}
    </>
  );
}
