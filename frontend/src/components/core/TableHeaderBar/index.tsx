import { type FC, type ReactNode } from 'react';

import './index.scss';

interface TableHeaderBarProps {
  /** Left-aligned content — a short table title or a summary line. */
  title?: ReactNode;
  /** Right-aligned actions — buttons render white against the gray band. */
  actions?: ReactNode;
  className?: string;
}

/**
 * Gray header band shown above a Carbon DataTable — title/summary on the left,
 * action button(s) on the right. Standardizes the table-header look app-wide
 * (modeled on the District Random List table), replacing Carbon's
 * {@code TableContainer} title/description block.
 */
export const TableHeaderBar: FC<TableHeaderBarProps> = ({ title, actions, className }) => (
  <div className={`table-header-bar${className ? ` ${className}` : ''}`}>
    <div className="table-header-bar__title">{title}</div>
    {actions != null && <div className="table-header-bar__actions">{actions}</div>}
  </div>
);

export default TableHeaderBar;
