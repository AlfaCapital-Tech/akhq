import { uriUIOptions } from './endpoints';
import { getUIOptions, setUIOptions } from './localstorage';
import { get } from './api';

export const getSelectedTab = (props, tabs) => {
  const url = props.location.pathname.split('/');
  const selectedTab = props.location.pathname.split('/')[url.length - 1];
  return tabs.includes(selectedTab) ? selectedTab : tabs[0];
};

export async function getClusterUIOptions(clusterId) {
  const uiOptions = getUIOptions(clusterId);
  if (!uiOptions && clusterId) {
    try {
      const resOptions = await get(uriUIOptions(clusterId));
      setUIOptions(clusterId, resOptions.data);
      return resOptions.data;
    } catch (err) {
      console.error('Error:', err);
      return {};
    }
  } else {
    return uiOptions;
  }
}

export const capitalizeTxt = text => {
  return text.charAt(0).toUpperCase() + text.slice(1);
};

export function encodeBase64Utf8(str) {
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  bytes.forEach(b => (binary += String.fromCharCode(b)));
  return btoa(binary);
}

export function decodeBase64Utf8(str) {
  const bytes = Uint8Array.from(atob(str), c => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

export default { getSelectedTab, getClusterUIOptions, capitalizeTxt, encodeBase64Utf8, decodeBase64Utf8 };
