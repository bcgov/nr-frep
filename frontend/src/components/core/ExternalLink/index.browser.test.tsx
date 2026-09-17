import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ExternalLink from './index';

describe('ExternalLink Component', () => {
  it('opens in a new tab without handing the opened page a window handle', () => {
    render(<ExternalLink href="https://example.test/opening/1">1234567</ExternalLink>);

    const link = screen.getByRole('link', { name: /1234567/ });
    expect(link.getAttribute('href')).toBe('https://example.test/opening/1');
    expect(link.getAttribute('target')).toBe('_blank');
    // Without noopener the opened page can navigate this window through window.opener.
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('tells screen readers the link leaves the app, without announcing the glyph itself', () => {
    render(<ExternalLink href="https://example.test/">1234567</ExternalLink>);

    const link = screen.getByRole('link', { name: /1234567/ });
    expect(link.textContent).toContain('(opens in a new tab)');
    expect(link.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('renders the icon inline with the label rather than below it', () => {
    render(<ExternalLink href="https://example.test/">1234567</ExternalLink>);

    const link = screen.getByRole('link', { name: /1234567/ });
    const icon = link.querySelector('svg') as SVGElement;
    const label = link.firstChild as Text;

    const range = document.createRange();
    range.selectNodeContents(label);
    const text = range.getBoundingClientRect();
    const glyph = icon.getBoundingClientRect();

    // Same row: the glyph starts after the text ends and their vertical centres line up.
    expect(glyph.left).toBeGreaterThanOrEqual(text.right);
    expect(Math.abs((glyph.top + glyph.bottom) / 2 - (text.top + text.bottom) / 2)).toBeLessThan(3);
    expect(glyph.width).toBeGreaterThan(0);
    // Proves the component's own stylesheet reached the DOM: an <svg> would sit on the same line
    // anyway, so the layout above is only evidence once the class is confirmed to be in effect.
    expect(getComputedStyle(link).display).toBe('inline-flex');
  });
});
