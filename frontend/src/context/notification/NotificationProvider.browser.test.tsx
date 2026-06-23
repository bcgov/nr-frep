import { render, screen, act } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import NotificationProvider from './NotificationProvider';
import { useNotification } from './useNotification';

import type { NotificationContent } from './NotificationContext';

// Helper component to trigger notification
const TestComponent = () => {
  const { display } = useNotification();
  return (
    <button
      onClick={() =>
        display({
          title: 'Test Title',
          subtitle: 'Test Subtitle',
          caption: 'Test Caption',
          kind: 'success',
          timeout: 5000,
          onClose: vi.fn(),
          onCloseButtonClick: vi.fn(),
        })
      }
    >
      Show Notification
    </button>
  );
};

// Triggers a toast of a given kind/timeout/title for the severity-timeout tests.
const KindButton = ({
  kind,
  timeout,
  title,
}: {
  kind: NotificationContent['kind'];
  timeout: number;
  title: string;
}) => {
  const { display } = useNotification();
  return <button onClick={() => display({ title, kind, timeout })}>{`show-${title}`}</button>;
};

describe('NotificationProvider', () => {
  it('renders children', () => {
    render(
      <NotificationProvider>
        <div>Child</div>
      </NotificationProvider>,
    );
    expect(screen.getByText('Child')).toBeInTheDocument();
  });

  it('displays a notification when display is called', () => {
    render(
      <NotificationProvider>
        <TestComponent />
      </NotificationProvider>,
    );
    act(() => {
      screen.getByText('Show Notification').click();
    });
    expect(screen.getByText('Test Title')).toBeInTheDocument();
    expect(screen.getByText('Test Subtitle')).toBeInTheDocument();
    expect(screen.getByText('Test Caption')).toBeInTheDocument();
  });

  it('removes the notification when onClose is triggered', () => {
    render(
      <NotificationProvider>
        <TestComponent />
      </NotificationProvider>,
    );
    act(() => {
      screen.getByText('Show Notification').click();
    });
    // Simulate close by finding the close button and clicking it
    const closeBtn = screen.getByLabelText('closes notification');
    act(() => {
      closeBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    // Notification should be removed
    expect(screen.queryByText('Test Title')).not.toBeInTheDocument();
  });

  it('does not auto-dismiss error toasts (user must close them)', () => {
    vi.useFakeTimers();
    try {
      render(
        <NotificationProvider>
          <KindButton kind="error" timeout={9000} title="Boom" />
        </NotificationProvider>,
      );
      act(() => screen.getByText('show-Boom').click());
      expect(screen.getByText('Boom')).toBeInTheDocument();
      // Well past any requested timeout — an error toast stays put.
      act(() => vi.advanceTimersByTime(60000));
      expect(screen.getByText('Boom')).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps info toasts up past their requested (short) timeout', () => {
    vi.useFakeTimers();
    try {
      render(
        <NotificationProvider>
          <KindButton kind="info" timeout={3000} title="Note" />
        </NotificationProvider>,
      );
      act(() => screen.getByText('show-Note').click());
      // Requested 3000ms, but info toasts are extended to a minimum read time.
      act(() => vi.advanceTimersByTime(4000));
      expect(screen.getByText('Note')).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('should fail if no provider is present', () => {
    expect(() => render(<TestComponent />)).toThrow(
      'useNotification must be used within a NotificationProvider',
    );
  });
});
