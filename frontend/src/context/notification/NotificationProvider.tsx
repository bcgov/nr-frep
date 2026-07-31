import { ToastNotification } from '@carbon/react';
import { useState, useEffect, type ReactNode, useCallback } from 'react';

import { NotificationContext, type NotificationContent } from './NotificationContext';

// Minimum on-screen time for info/warning toasts so users have time to read them. A `timeout` of 0
// means "never auto-dismiss" (Carbon + the slide-out effect below both treat it that way).
const INFO_WARNING_MIN_TIMEOUT = 12000;
const INFO_WARNING_KINDS: ReadonlySet<NotificationContent['kind']> = new Set([
  'info',
  'info-square',
  'warning',
  'warning-alt',
]);

/**
 * Normalizes the auto-dismiss timeout by severity, centrally (so individual call sites don't have to):
 * error toasts never self-dismiss (the user must close them), and info/warning toasts stay up for at
 * least {@link INFO_WARNING_MIN_TIMEOUT}. Success (and anything else) keeps its requested timeout.
 */
const resolveTimeout = (content: NotificationContent): number => {
  if (content.kind === 'error') {
    return 0;
  }
  if (INFO_WARNING_KINDS.has(content.kind)) {
    return Math.max(content.timeout, INFO_WARNING_MIN_TIMEOUT);
  }
  return content.timeout;
};

export const NotificationProvider = ({ children }: { children: ReactNode }) => {
  const [notificationContent, setNotificationContent] = useState<NotificationContent | null>(null);
  const [notificationClass, setNotificationClass] = useState<string>('slide-in');

  const display = useCallback((content: NotificationContent) => {
    setNotificationClass('slide-in');
    setNotificationContent({ ...content, timeout: resolveTimeout(content) });
  }, []);

  const onClose = useCallback(() => {
    setNotificationClass('slide-out');
    notificationContent?.onClose?.();
    setNotificationContent(null);
  }, [notificationContent]);

  useEffect(() => {
    if (notificationContent && notificationContent.timeout > 0) {
      if (notificationClass === 'slide-in') {
        const timer = setTimeout(() => {
          setNotificationClass('slide-out');
        }, notificationContent.timeout - 300);
        return () => clearTimeout(timer);
      }
    }
  }, [notificationClass, notificationContent]);

  return (
    <NotificationContext.Provider value={{ display }}>
      {children}
      {notificationContent && (
        <ToastNotification
          className={notificationClass}
          lowContrast
          aria-label="closes notification"
          caption={notificationContent.caption}
          kind={notificationContent.kind}
          onClose={onClose}
          onCloseButtonClick={notificationContent.onCloseButtonClick}
          role="status"
          statusIconDescription="notification"
          subtitle={notificationContent.subtitle}
          timeout={notificationContent.timeout}
          title={notificationContent.title}
        >
          {notificationContent.children}
        </ToastNotification>
      )}
    </NotificationContext.Provider>
  );
};

export default NotificationProvider;
