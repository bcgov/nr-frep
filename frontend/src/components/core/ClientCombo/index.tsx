import { ComboBox, Loading } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import type { ClientSearchResult } from '@/types/search';

import { MIN_CLIENT_TERM_LENGTH, clientLabel, searchClientsAuto } from '@/utils/clientSearch';

import './clientCombo.scss';

type Props = {
  id: string;
  titleText: string;
  /** The label of the currently-selected client, so the field survives a remount. */
  selectedLabel: string;
  /** Number and display label of the picked client; both empty when the field is cleared. */
  onSelect: (clientNumber: string, clientName: string) => void;
  /**
   * The raw text in the field, reported on every keystroke.
   *
   * <p>Only a *selection* produces a client number to filter on, so typed text that matches no
   * client would otherwise be silently dropped — and the caller would run an unfiltered search
   * while the field still showed the term. Callers use this to notice that state and refuse.
   */
  onTermChange?: (term: string) => void;
  invalid?: boolean;
  invalidText?: string;
  disabled?: boolean;
};

/**
 * Type-ahead client picker, replacing the search-form-in-a-modal.
 *
 * <p>The lookup it replaced made the user open a dialog, choose which of five fields to search,
 * submit, then pick a row — for a filter that only needs a client number. This searches name and
 * acronym together (and client number when the term is all digits) as they type.
 *
 * <p>Debounced at 300ms with a {@link MIN_CLIENT_TERM_LENGTH}-character floor: the legacy proc
 * BULK COLLECTs into a VARRAY(500) and raises when a search overflows it, so a one- or two-letter
 * prefix is both useless and an error. The spinner sits in the label rather than over the field so
 * the input never jumps.
 */
const ClientCombo: FC<Props> = ({
  id,
  titleText,
  selectedLabel,
  onSelect,
  onTermChange,
  invalid,
  invalidText,
  disabled,
}) => {
  const [term, setTerm] = useState('');
  const [items, setItems] = useState<ClientSearchResult[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const trimmed = term.trim();
    // Selecting an item makes Carbon fire onInputChange with the item's own label, so without this
    // every pick triggered a fresh search for "Name (ACRONYM) · 00002483" — a term that matches
    // nothing and, before the width guards in searchClientsAuto, overflowed the acronym criteria.
    if (trimmed.length < MIN_CLIENT_TERM_LENGTH || trimmed === selectedLabel.trim()) {
      setItems([]);
      setLoading(false);
      return;
    }
    // `active` guards a stale response: the term may have moved on by the time this resolves, and
    // letting it land would show suggestions for something the user has already typed past.
    let active = true;
    setLoading(true);
    const handle = setTimeout(() => {
      searchClientsAuto(trimmed)
        .then((results) => {
          if (active) setItems(results);
        })
        .catch(() => {
          // Silent: a failed lookup leaves the field empty rather than raising a toast on every
          // keystroke. The user can still type a client number into the filter directly.
          if (active) setItems([]);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 300);
    return () => {
      active = false;
      clearTimeout(handle);
    };
  }, [term, selectedLabel]);

  return (
    <ComboBox
      autoComplete="off"
      id={id}
      className="client-combo"
      disabled={disabled}
      invalid={invalid}
      invalidText={invalidText}
      // Without this Carbon discards text that matches no suggestion the moment the field blurs —
      // including the blur caused by clicking Search. The term the caller validates on would be
      // gone before the click handler ran, and the field would silently empty itself under an
      // error message. Keeping the text is also what makes "that client does not exist" legible.
      allowCustomValue
      titleText={
        <span className="client-combo__label">
          {titleText}
          {loading && (
            <Loading
              small
              withOverlay={false}
              description="Searching clients"
              className="client-combo__spinner"
            />
          )}
        </span>
      }
      helperText={`Enter name, acronym, or client number (min. ${MIN_CLIENT_TERM_LENGTH} characters)`}
      placeholder="Search for a client"
      items={items}
      itemToString={(item: ClientSearchResult | null) => (item ? clientLabel(item) : '')}
      // Carbon needs a selected *item* to render its label. The picked client is rarely still in the
      // suggestion list (the term has usually changed), so a lightweight stand-in carrying only the
      // label is enough for itemToString.
      selectedItem={selectedLabel ? ({ clientName: selectedLabel } as ClientSearchResult) : null}
      onInputChange={(value: string) => {
        setTerm(value ?? '');
        onTermChange?.(value ?? '');
      }}
      onChange={({ selectedItem }: { selectedItem?: ClientSearchResult | null }) =>
        onSelect(
          selectedItem?.clientNumber?.trim() ?? '',
          selectedItem ? clientLabel(selectedItem) : '',
        )
      }
    />
  );
};

export default ClientCombo;
