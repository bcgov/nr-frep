import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// The dialog's footer overrides Carbon, so the geometry case below needs the real stylesheets.
import '@/styles/index.scss';
import './chrChecklist.scss';

import FeatureList from './FeatureList';
import { clearCodeListCache } from './useCodeList';

import type { Feature } from '@/types/chrChecklist';

// The dropdowns come from the code tables now, so anything mounting a form needs them stubbed.
vi.mock('@/services/APIs', async () => {
  const { chrCodeListApi } = await import('@/testing/chrCodeListApi');
  return { default: { configuration: chrCodeListApi() } };
});

beforeEach(() => clearCodeListCache());

vi.mock('@/context/confirm/useConfirm', () => ({
  useConfirm: () => vi.fn().mockResolvedValue(true),
}));

const featureA: Feature = {
  id: '1',
  featureLabel: '1',
  compositeFeatureInd: 'false',
  featureDescriptionCode: 'CMT',
  featureInfoSourceCode: 'AIA',
};
const featureB: Feature = {
  id: '2',
  featureLabel: '2',
  compositeFeatureInd: 'false',
  featureDescriptionCode: 'CT',
  featureInfoSourceCode: 'SP',
  featureDescription: 'Trail along the ridge',
};

/** The list is controlled, so the harness holds the array the way the page does. */
const renderList = (initial: Feature[] = [featureA, featureB]) => {
  const onSave = vi.fn().mockResolvedValue(true);
  const Harness = () => {
    const [features, setFeatures] = useState(initial);
    return (
      <FeatureList
        features={features}
        onChange={setFeatures}
        onSave={onSave}
        readOnly={false}
        busy={false}
      />
    );
  };
  render(<Harness />);
  return { onSave };
};

describe('FeatureList table', () => {
  it('shows the class, information source, description and associations per row', async () => {
    renderList();

    const row = screen.getByText('Trail along the ridge').closest('tr') as HTMLElement;
    // The class and source labels come from the code tables, so the row shows the bare code until
    // the fetch settles.
    expect(await within(row).findByText('Cultural Trail')).toBeTruthy();
    expect(within(row).getByText('SP - Site Plan')).toBeTruthy();
    // Nothing associated yet — every empty cell reads the same way.
    expect(within(row).getAllByText('—').length).toBeGreaterThan(0);
  });

  it('associates both features from the row action and persists on Save', async () => {
    // The association is a relationship, so ticking one side has to write the other's label too —
    // otherwise the pair disagrees and submit validation sees only half of it.
    const { onSave } = renderList();

    await userEvent.click(screen.getAllByRole('button', { name: /Associate/ })[0]);
    await userEvent.click(
      await screen.findByRole('checkbox', { name: /Associate with feature 2/ }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save associations' }));

    const saved = onSave.mock.calls[0][0] as Feature[];
    expect(saved[0].associatedFeatures).toEqual(['2']);
    expect(saved[1].associatedFeatures).toEqual(['1']);
  });

  it('identifies each candidate by class and source, not just number', async () => {
    // Numbers alone are not enough to pick the right feature when a checklist has a dozen of them.
    renderList();

    await userEvent.click(screen.getAllByRole('button', { name: /Associate/ })[0]);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Cultural Trail')).toBeTruthy();
    expect(within(dialog).getByText('SP - Site Plan')).toBeTruthy();
    // The subject of the dialog is never offered as its own association.
    expect(within(dialog).queryByRole('checkbox', { name: /Associate with feature 1/ })).toBeNull();
  });

  it('confirms the save in the tab', async () => {
    renderList();

    await userEvent.click(screen.getAllByRole('button', { name: /Associate/ })[0]);
    await userEvent.click(
      await screen.findByRole('checkbox', { name: /Associate with feature 2/ }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save associations' }));

    expect(await screen.findByText('Associations updated')).toBeTruthy();
  });

  it('restores the list when the dialog is cancelled', async () => {
    const { onSave } = renderList();

    await userEvent.click(screen.getAllByRole('button', { name: /Associate/ })[0]);
    await userEvent.click(
      await screen.findByRole('checkbox', { name: /Associate with feature 2/ }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onSave).not.toHaveBeenCalled();
    // Reopening shows the box clear again — the snapshot was restored, not just the dialog closed.
    await userEvent.click(screen.getAllByRole('button', { name: /Associate/ })[0]);
    expect(
      (await screen.findByRole('checkbox', {
        name: /Associate with feature 2/,
      })) as HTMLInputElement,
    ).not.toBeChecked();
  });

  /**
   * Carbon's modal footer is also a `cds--btn-set`, which forces `inline-size: 100%` on every child.
   * Overriding `flex` alone left both buttons as wide as the footer and `justify-content: flex-end`
   * pushed Cancel clean outside the dialog. Asserted on geometry rather than class names: the markup
   * is identical whether or not the override wins.
   */
  it('keeps both footer buttons inside the dialog', async () => {
    renderList();
    await userEvent.click(screen.getAllByRole('button', { name: /Associate/ })[0]);
    await screen.findByRole('dialog');

    const container = document.querySelector('.cds--modal-container')!.getBoundingClientRect();
    const buttons = Array.from(document.querySelectorAll('.cds--modal-footer .cds--btn'));
    expect(buttons).toHaveLength(2);

    for (const button of buttons) {
      const box = button.getBoundingClientRect();
      expect(box.left).toBeGreaterThanOrEqual(container.left);
      expect(box.right).toBeLessThanOrEqual(container.right);
      // Sized to content, not stretched across the footer.
      expect(box.width).toBeLessThan(container.width / 2);
    }
  });

  it('offers nothing to associate with a lone feature', () => {
    renderList([featureA]);
    expect(screen.getByRole('button', { name: /Associate/ })).toBeDisabled();
  });
});

/**
 * The composite flow, driven through the dialog rather than the helpers, so the wiring between the
 * two is covered too.
 *
 * `onSave` applies the array here: on the real page `saveFeatures` posts it and feeds the stored
 * list back into `checkList.features`, so the table re-renders from what was saved.
 */
const renderSaving = (initial: Feature[] = [featureA, featureB]) => {
  const onSave = vi.fn();
  const Harness = () => {
    const [features, setFeatures] = useState(initial);
    onSave.mockImplementation(async (next: Feature[]) => {
      setFeatures(next);
      return true;
    });
    return (
      <FeatureList
        features={features}
        onChange={setFeatures}
        onSave={onSave}
        readOnly={false}
        busy={false}
      />
    );
  };
  render(<Harness />);
  return { onSave };
};

/**
 * Open the Create composite dialog and fill it in enough to be valid: the composite's own class and
 * source are required, so a test that only ticks features would be blocked before it got anywhere.
 */
const createComposite = async (user: ReturnType<typeof userEvent.setup>, labels: string[]) => {
  await user.click(screen.getAllByRole('button', { name: /Create composite/ })[0]);
  const dialog = screen.getByRole('dialog');
  for (const label of labels) {
    await user.click(
      within(dialog).getByRole('checkbox', { name: `Include feature ${label} in this composite` }),
    );
  }
  // The asterisk marking these required is aria-hidden, so the accessible name is the plain label.
  await user.selectOptions(within(dialog).getByRole('combobox', { name: 'Feature class' }), 'ARCH');
  await user.selectOptions(
    within(dialog).getByRole('combobox', { name: 'Information source' }),
    'AIA',
  );
  return dialog;
};

/** The composite row's Feature cell, or null when the table holds no composite. */
const compositeLabel = (): string | null => {
  const row = document.querySelector('.chr-features__composite-row');
  return row ? (row.querySelector('td')?.textContent?.trim() ?? null) : null;
};

/** Submit the create dialog — its primary button shares a name with the toolbar button. */
const submitCreate = async (user: ReturnType<typeof userEvent.setup>, dialog: HTMLElement) =>
  user.click(within(dialog).getByRole('button', { name: 'Create composite' }));

describe('FeatureList composites', () => {
  it('groups the chosen features and shows them under the composite', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1', '2']);
    await submitCreate(user, dialog);

    const saved = onSave.mock.calls.at(-1)?.[0] as Feature[];
    const anchor = saved.find((f) => f.compositeFeatureInd === 'true') as Feature;
    expect(anchor.featureDescriptionCode).toBe('ARCH');
    expect(saved.filter((f) => f.compositeFeature === anchor.featureLabel)).toHaveLength(2);

    // The composite shows its own feature number, not an invented name.
    expect(compositeLabel()).toBe(anchor.featureLabel);
    expect(screen.getByText('2 features assessed as one unit')).toBeTruthy();
  });

  it('confirms the creation, naming the composite and its size', async () => {
    const user = userEvent.setup();
    renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1', '2']);
    await submitCreate(user, dialog);

    expect(screen.getByText('Feature 3 created as a composite of 2 features')).toBeTruthy();
  });

  it('will not create a group of fewer than two features', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1']);
    await submitCreate(user, dialog);

    // The backend rejects a one-member composite at submit, so it is refused here — and says why
    // rather than leaving a dead button.
    expect(within(dialog).getByRole('alert').textContent).toMatch(/Select at least two features/);
    expect(onSave).not.toHaveBeenCalled();
  });

  it('names the required fields left blank instead of blocking the button', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    await user.click(screen.getByRole('button', { name: /Create composite/ }));
    const dialog = await screen.findByRole('dialog');
    const create = within(dialog).getByRole('button', { name: 'Create composite' });
    expect(create.hasAttribute('disabled')).toBe(false);

    await user.click(create);
    expect(within(dialog).getByText('Feature class is required.')).toBeTruthy();
    expect(within(dialog).getByText('Information source is required.')).toBeTruthy();
    expect(within(dialog).getByRole('alert').textContent).toMatch(/Select at least two features/);
    expect(onSave).not.toHaveBeenCalled();
  });

  it('adds a new feature from inside the dialog and groups it', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1', '2']);
    await user.click(within(dialog).getByRole('button', { name: /Add a new feature/ }));
    await user.selectOptions(within(dialog).getByLabelText('Feature class for feature 3'), 'CT');
    await submitCreate(user, dialog);

    const saved = onSave.mock.calls.at(-1)?.[0] as Feature[];
    const added = saved.find((f) => f.featureLabel === '3') as Feature;
    expect(added.featureDescriptionCode).toBe('CT');
    expect(added.compositeFeature).toBe('4');
    expect(screen.getByText('3 features assessed as one unit')).toBeTruthy();
  });

  it('leaves nothing behind when the dialog is cancelled', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1', '2']);
    await user.click(within(dialog).getByRole('button', { name: /Add a new feature/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(onSave).not.toHaveBeenCalled();
    expect(compositeLabel()).toBeNull();
    // The feature added inside the dialog was never written to the checklist.
    expect(screen.queryByText('3')).toBeNull();
  });

  it('offers a member only Associate — it is assessed through its composite', async () => {
    const user = userEvent.setup();
    renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1', '2']);
    await submitCreate(user, dialog);

    const memberRow = screen.getByText('1').closest('tr') as HTMLElement;
    expect(within(memberRow).getByRole('button', { name: /Associate/ })).toBeTruthy();
    expect(within(memberRow).queryByRole('button', { name: /Edit/ })).toBeNull();
    expect(within(memberRow).queryByRole('button', { name: /Delete/ })).toBeNull();
  });

  it('ungroups a composite and frees its features', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await createComposite(user, ['1', '2']);
    await submitCreate(user, dialog);
    await user.click(screen.getByRole('button', { name: /Ungroup/ }));
    const confirmDialog = await screen.findByRole('dialog');
    await user.click(within(confirmDialog).getByRole('radio', { name: /Keep them/ }));
    await user.click(within(confirmDialog).getByRole('button', { name: /Ungroup/ }));

    const saved = onSave.mock.calls.at(-1)?.[0] as Feature[];
    expect(saved).toHaveLength(2);
    expect(saved.every((f) => !f.compositeFeature)).toBe(true);
    expect(compositeLabel()).toBeNull();
    // Freed features get their full set of actions back.
    const row = screen.getByText('1').closest('tr') as HTMLElement;
    expect(within(row).getByRole('button', { name: /Delete/ })).toBeTruthy();
  });

  it('cannot start a composite until there are two features to group', () => {
    renderSaving([featureA]);
    expect(screen.getByRole('button', { name: /Create composite/ }).hasAttribute('disabled')).toBe(
      true,
    );
  });
});

describe('Create composite dialog layout', () => {
  /**
   * The composite dialog shares the associate dialog's footer overrides through a grouped selector.
   * Asserted on geometry, not on class names: the markup is identical whether or not the override
   * wins, and Carbon's default (`cds--btn-set` forcing `inline-size: 100%`) puts Cancel outside the
   * dialog entirely.
   */
  it('keeps both footer buttons inside the dialog', async () => {
    const user = userEvent.setup();
    renderSaving();
    await user.click(screen.getByRole('button', { name: /Create composite/ }));
    await screen.findByRole('dialog');

    const container = document.querySelector('.cds--modal-container')!.getBoundingClientRect();
    const buttons = Array.from(document.querySelectorAll('.cds--modal-footer .cds--btn'));
    expect(buttons).toHaveLength(2);

    for (const button of buttons) {
      const box = button.getBoundingClientRect();
      expect(box.left).toBeGreaterThanOrEqual(container.left);
      expect(box.right).toBeLessThanOrEqual(container.right + 1);
      // Sized to content, not stretched across the footer.
      expect(box.width).toBeLessThan(container.width / 2);
    }
  });
});

describe('Members dialog', () => {
  const four = [
    featureA,
    featureB,
    { id: '3', featureLabel: '3', compositeFeatureInd: 'false', featureDescriptionCode: 'ARCH' },
    { id: '4', featureLabel: '4', compositeFeatureInd: 'false', featureDescriptionCode: 'TUS' },
  ] as Feature[];

  const openMembers = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(screen.getByRole('button', { name: /Members/ }));
    return screen.findByRole('dialog');
  };

  it('names the composite it is editing and pre-selects its members', async () => {
    const user = userEvent.setup();
    renderSaving(four);

    const create = await createComposite(user, ['1', '2']);
    await submitCreate(user, create);

    const dialog = await openMembers(user);
    expect(within(dialog).getByText('Members of Feature 5')).toBeTruthy();
    expect(
      within(dialog).getByRole('checkbox', { name: 'Include feature 1 in this composite' }),
    ).toBeChecked();
    expect(
      within(dialog).getByRole('checkbox', { name: 'Include feature 3 in this composite' }),
    ).not.toBeChecked();
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeTruthy();
  });

  it('removes a feature when its selection is cleared', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving(four);

    const create = await createComposite(user, ['1', '2', '3']);
    await submitCreate(user, create);

    const dialog = await openMembers(user);
    await user.click(
      within(dialog).getByRole('checkbox', { name: 'Include feature 3 in this composite' }),
    );
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    const saved = onSave.mock.calls.at(-1)?.[0] as Feature[];
    expect(saved.find((f) => f.featureLabel === '3')?.compositeFeature).toBeUndefined();
    expect(screen.getByText('2 features assessed as one unit')).toBeTruthy();
  });

  it('moves a feature across from another composite', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving(four);

    // Feature 5 groups 1 and 2; feature 6 groups 3 and 4.
    const first = await createComposite(user, ['1', '2']);
    await submitCreate(user, first);
    const second = await createComposite(user, ['3', '4']);
    await submitCreate(user, second);

    await user.click(screen.getAllByRole('button', { name: /Members/ })[0]);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Members of Feature 5')).toBeTruthy();
    // A feature spoken for by another composite is still offered here — moving it is the point.
    await user.click(
      within(dialog).getByRole('checkbox', { name: 'Include feature 3 in this composite' }),
    );
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    const saved = onSave.mock.calls.at(-1)?.[0] as Feature[];
    const first1 = saved.find((f) => f.featureLabel === '1') as Feature;
    const moved = saved.find((f) => f.featureLabel === '3') as Feature;
    expect(moved.compositeFeature).toBe(first1.compositeFeature);
    expect(screen.getByText('3 features assessed as one unit')).toBeTruthy();
  });

  it('offers no composite as a member of another composite', async () => {
    const user = userEvent.setup();
    renderSaving(four);

    const first = await createComposite(user, ['1', '2']);
    await submitCreate(user, first);
    const second = await createComposite(user, ['3', '4']);
    await submitCreate(user, second);

    await user.click(screen.getAllByRole('button', { name: /Members/ })[0]);
    const dialog = await screen.findByRole('dialog');
    // Composite 2's anchor took label 6; a composite must never be groupable into one.
    expect(
      within(dialog).queryByRole('checkbox', { name: 'Include feature 6 in this composite' }),
    ).toBeNull();
  });

  it('will not leave a composite with fewer than two members', async () => {
    const user = userEvent.setup();
    renderSaving(four);

    const create = await createComposite(user, ['1', '2']);
    await submitCreate(user, create);

    const dialog = await openMembers(user);
    await user.click(
      within(dialog).getByRole('checkbox', { name: 'Include feature 2 in this composite' }),
    );
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(within(dialog).getByRole('alert').textContent).toMatch(/Select at least two features/);
  });
});

describe('Ungroup dialog', () => {
  /** featureA carries only its identity; featureB has a description of its own. */
  const openUngroup = async (user: ReturnType<typeof userEvent.setup>) => {
    const create = await createComposite(user, ['1', '2']);
    await submitCreate(user, create);
    await user.click(screen.getByRole('button', { name: /Ungroup/ }));
    return screen.findByRole('dialog');
  };

  it('says what ungrouping costs and names the features it would strand', async () => {
    const user = userEvent.setup();
    renderSaving([featureA, featureB]);

    const dialog = await openUngroup(user);
    expect(
      within(dialog).getByText(/each of the 2 features will need its own before the checklist/),
    ).toBeTruthy();
    expect(within(dialog).getByText('Feature 1 has no details of its own.')).toBeTruthy();
  });

  it('names the missing answer instead of blocking the button', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await openUngroup(user);
    // Creating the composite already saved once; only growth from here counts as an ungroup.
    const saves = onSave.mock.calls.length;
    const ungroup = within(dialog).getByRole('button', { name: /Ungroup/ });
    expect(ungroup.hasAttribute('disabled')).toBe(false);

    await user.click(ungroup);
    expect(within(dialog).getByText('Choose whether to keep or delete them.')).toBeTruthy();
    expect(onSave.mock.calls).toHaveLength(saves);

    // Answering clears the way through.
    await user.click(within(dialog).getByRole('radio', { name: /Keep them/ }));
    await user.click(ungroup);
    expect(onSave.mock.calls).toHaveLength(saves + 1);
  });

  it('deletes the undescribed features when asked to, and keeps the rest', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await openUngroup(user);
    await user.click(within(dialog).getByRole('radio', { name: /Delete them/ }));
    await user.click(within(dialog).getByRole('button', { name: /Ungroup/ }));

    const saved = onSave.mock.calls.at(-1)?.[0] as Feature[];
    expect(saved.map((f) => f.featureLabel)).toEqual(['2']);
  });

  it('changes nothing when cancelled', async () => {
    const user = userEvent.setup();
    const { onSave } = renderSaving([featureA, featureB]);

    const dialog = await openUngroup(user);
    const saves = onSave.mock.calls.length;
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(onSave.mock.calls).toHaveLength(saves);
    expect(compositeLabel()).not.toBeNull();
  });

  it('does not ask the question when every member was assessed in its own right', async () => {
    const user = userEvent.setup();
    const described = { ...featureA, featureDescription: 'A cluster of CMTs' };
    const { onSave } = renderSaving([described, featureB]);

    const create = await createComposite(user, ['1', '2']);
    await submitCreate(user, create);
    await user.click(screen.getByRole('button', { name: /Ungroup/ }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).queryByRole('radio')).toBeNull();
    await user.click(within(dialog).getByRole('button', { name: /Ungroup/ }));
    expect((onSave.mock.calls.at(-1)?.[0] as Feature[]).map((f) => f.featureLabel)).toEqual([
      '1',
      '2',
    ]);
  });
});

describe('Create composite guidance', () => {
  it('explains what a composite feature is, where they are created', async () => {
    const user = userEvent.setup();
    renderSaving();
    await user.click(screen.getByRole('button', { name: /Create composite/ }));
    const dialog = await screen.findByRole('dialog');

    expect(
      within(dialog).getByText(/culturally, spatially, or functionally connected/),
    ).toBeTruthy();
    expect(within(dialog).getByText(/adjacent berry harvesting area/)).toBeTruthy();
  });

  it('does not repeat the explanation when editing members', async () => {
    const user = userEvent.setup();
    renderSaving();
    const create = await createComposite(user, ['1', '2']);
    await submitCreate(user, create);
    await user.click(screen.getByRole('button', { name: /Members/ }));
    const dialog = await screen.findByRole('dialog');

    expect(
      within(dialog).queryByText(/culturally, spatially, or functionally connected/),
    ).toBeNull();
  });
});
