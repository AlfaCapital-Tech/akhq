import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import wave from '../../../images/mascot/gavryusha-wave.webp';
import waveBlink from '../../../images/mascot/gavryusha-wave-blink.webp';
import point from '../../../images/mascot/gavryusha-point.webp';
import think from '../../../images/mascot/gavryusha-think.webp';
import happy from '../../../images/mascot/gavryusha-happy.webp';
import {
  claimMascotSlot,
  isAccessManagementEnabled,
  isTourSeen,
  turnMascotOff
} from './onboardingState';
import './mascot.css';

const POSES = { wave, point, think, happy };

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

function useBlink(enabled) {
  const [closed, setClosed] = useState(false);
  useEffect(() => {
    if (!enabled || reducedMotion()) return undefined;
    const timer = setInterval(() => {
      setClosed(true);
      setTimeout(() => setClosed(false), 160);
    }, 4000);
    return () => clearInterval(timer);
  }, [enabled]);
  return closed;
}

export function MascotBubble({ pose = 'wave', title, children, actions, floating = false }) {
  const closed = useBlink(pose === 'wave');
  return (
    <div className={`akhq-mascot${floating ? ' akhq-mascot--floating' : ''}`} role="dialog">
      <div className="akhq-mascot__bubble">
        {title && <div className="akhq-mascot__title">{title}</div>}
        {children}
        <div className="akhq-mascot__actions">{actions}</div>
      </div>
      <img
        className="akhq-mascot__image"
        src={closed ? waveBlink : POSES[pose]}
        alt="Гаврюша"
        draggable={false}
      />
    </div>
  );
}

let say = null;

// Asks the mascot to pop up, at most once per place and session and never after "do not show again".
export const showMascot = (place, message) => {
  if (say && claimMascotSlot(place)) say(message);
};

export default function OnboardingMascot({ clusterId }) {
  const [message, setMessage] = useState(null);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  useEffect(() => {
    say = setMessage;
    return () => {
      say = null;
    };
  }, []);

  useEffect(() => {
    if (!isAccessManagementEnabled() || pathname.endsWith('/login') || isTourSeen()) return;
    showMascot('announce', {
      pose: 'wave',
      title: 'Привет! Я Гаврюша',
      text: 'Теперь доступ к топикам можно запросить прямо здесь, без заявок и писем. Показать, как это сделать?',
      action: {
        label: 'Показать',
        onClick: () => navigate(`/ui/${clusterId}/access-management?tour=1`)
      }
    });
  }, [clusterId, pathname, navigate]);

  if (!message) return null;

  const close = () => setMessage(null);
  return (
    <MascotBubble
      floating
      pose={message.pose}
      title={message.title}
      actions={
        <>
          <button
            className="btn btn-primary btn-sm"
            onClick={() => {
              close();
              message.action.onClick();
            }}
          >
            {message.action.label}
          </button>
          <button className="btn btn-outline-secondary btn-sm" onClick={close}>
            Не сейчас
          </button>
          <button
            className="btn btn-link btn-sm text-secondary"
            onClick={() => {
              turnMascotOff();
              close();
            }}
          >
            Больше не показывать
          </button>
        </>
      }
    >
      {message.text}
    </MascotBubble>
  );
}
