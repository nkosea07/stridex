import type { PublicRaceResult, PublicRaceResultPage } from '@/types';
import { isApiConflict } from './api';

export type PublicResultsPaginationState = {
  items: PublicRaceResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  revision: number;
};

export type PublicResultsPageLoader = (
  page: number,
  size: number,
  revision?: number,
) => Promise<PublicRaceResultPage>;

export function createPublicResultsPaginationState(page: PublicRaceResultPage): PublicResultsPaginationState {
  return {
    items: page.items,
    page: page.page,
    size: page.size,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    revision: page.revision,
  };
}

/**
 * Appends one page from the same result snapshot. A revision conflict replaces
 * the partial snapshot with a fresh page zero so callers never mix revisions.
 */
export async function loadNextPublicResultsPage(
  current: PublicResultsPaginationState,
  loadPage: PublicResultsPageLoader,
): Promise<PublicResultsPaginationState> {
  try {
    const next = await loadPage(current.page + 1, current.size, current.revision);
    return {
      ...createPublicResultsPaginationState(next),
      items: [...current.items, ...next.items],
    };
  } catch (error) {
    if (!isApiConflict(error)) throw error;

    return createPublicResultsPaginationState(await loadPage(0, current.size));
  }
}
