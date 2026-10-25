import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  authApi,
  getAccessToken,
  normalizeUser,
  participantsApi,
  paymentReversalApi,
  privacyAdminApi,
  publicRegistrationApi,
  setAccessToken,
} from '@/lib/api'
import type { User } from '@/types'

const baseUser: User = {
  id: 'user-1',
  email: 'runner@example.com',
  firstName: 'Runner',
  lastName: 'One',
  role: 'ATHLETE',
  capabilities: ['ATHLETE'],
  preferredCapability: 'ATHLETE',
  isActive: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

function clearCsrfCookie() {
  document.cookie = 'rt_browser_csrf=; Max-Age=0; path=/'
}

describe('web API security boundaries', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    setAccessToken(null)
    clearCsrfCookie()
  })

  afterEach(() => {
    setAccessToken(null)
    clearCsrfCookie()
    vi.unstubAllGlobals()
  })

  it('preserves an explicit empty capability list and only falls back for an absent field', () => {
    const explicitEmpty = normalizeUser({ ...baseUser, capabilities: [] })
    expect(explicitEmpty.capabilities).toEqual([])
    expect(explicitEmpty.preferredCapability).toBeNull()

    const legacyUser = { ...baseUser } as Partial<User>
    delete legacyUser.capabilities
    expect(normalizeUser(legacyUser as User).capabilities).toEqual(['ATHLETE'])
  })

  it('clears stale access tokens when refresh has no CSRF or fails', async () => {
    setAccessToken('stale-token')
    expect(await authApi.restoreBrowserSession()).toBe(false)
    expect(getAccessToken()).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()

    document.cookie = 'rt_browser_csrf=csrf-token; path=/'
    setAccessToken('stale-token')
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 401 }))

    expect(await authApi.restoreBrowserSession()).toBe(false)
    expect(getAccessToken()).toBeNull()
  })

  it('resolves privacy and finance administration endpoints from the contract', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('[]', { status: 200 }))
      .mockResolvedValueOnce(new Response('[]', { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'job-1' }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))

    await privacyAdminApi.listBlockedJobs()
    await paymentReversalApi.listOpen()
    await privacyAdminApi.verifyScope('job-1', 'local|/verified/legacy', 'Deployment record')

    expect(String(fetchMock.mock.calls[0][0])).toContain('/admin/privacy/object-deletion-jobs?scope=UNVERIFIED_LEGACY')
    expect(String(fetchMock.mock.calls[1][0])).toContain('/admin/payments/reversal-cases')
    expect(String(fetchMock.mock.calls[2][0])).toContain('/admin/privacy/object-deletion-jobs/job-1/scope')
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({
      scope: 'local|/verified/legacy', reason: 'Deployment record',
    })
  })

  it('does not install a refresh token after logout invalidates the session', async () => {
    let resolveRefresh!: (response: Response) => void
    let resolveLogout!: (response: Response) => void
    fetchMock
      .mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveRefresh = resolve }))
      .mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveLogout = resolve }))

    document.cookie = 'rt_browser_csrf=csrf-token; path=/'
    setAccessToken('stale-token')
    const restore = authApi.restoreBrowserSession()
    const logout = authApi.logout()

    expect(getAccessToken()).toBeNull()
    resolveRefresh(new Response(JSON.stringify({ token: 'late-token' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    expect(await restore).toBe(false)
    expect(getAccessToken()).toBeNull()

    resolveLogout(new Response(null, { status: 204 }))
    await logout
    expect(getAccessToken()).toBeNull()
  })

  it('omits credentials from public registration-field requests', async () => {
    setAccessToken('private-token')
    fetchMock.mockResolvedValueOnce(new Response('[]', {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))

    await publicRegistrationApi.listFields('race-1')

    const [, request] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(request.credentials).toBe('omit')
    expect(new Headers(request.headers).get('Authorization')).toBeNull()
  })

  it('checks deletion recovery without an authenticated browser session', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ message: 'If eligible' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))

    await authApi.cancelDeletionWithPassword('runner@example.com', 'password')

    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toContain('/auth/deletion/cancel')
    expect(new Headers(request.headers).get('Authorization')).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('sends external refund staging and reconciliation as separate operations', async () => {
    setAccessToken('super-admin-token')
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ id: 'payment-1', status: 'COMPLETED' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))

    await participantsApi.stageExternalRefund('race-1', 'participant-1', 'Duplicate charge')
    await participantsApi.reconcileExternalRefund('race-1', 'participant-1', 'attempt-1', {
      providerReference: 'CASE-1', reason: 'Duplicate charge', outcome: 'NOT_REFUNDED',
    })

    expect(String(fetchMock.mock.calls[0][0])).toContain('/payment/refund/attempts')
    expect(String(fetchMock.mock.calls[1][0])).toContain('/payment/refund/attempts/attempt-1/reconcile')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toMatchObject({
      providerReference: 'CASE-1', outcome: 'NOT_REFUNDED',
    })
  })

  it('refreshes and retries authenticated capability updates after a 401', async () => {
    document.cookie = 'rt_browser_csrf=csrf-token; path=/'
    setAccessToken('expired-token')
    fetchMock
      .mockResolvedValueOnce(new Response('{}', { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'refreshed-token' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        ...baseUser,
        capabilities: ['ATHLETE', 'SPECTATOR'],
        preferredCapability: 'SPECTATOR',
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))

    const user = await authApi.updateCapabilities({
      capabilities: ['ATHLETE', 'SPECTATOR'],
      preferredCapability: 'SPECTATOR',
    })

    expect(user.preferredCapability).toBe('SPECTATOR')
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[0][1]?.headers).toMatchObject({ Authorization: 'Bearer expired-token' })
    expect(fetchMock.mock.calls[1][0]).toContain('/auth/browser/refresh')
    expect(fetchMock.mock.calls[2][1]?.headers).toMatchObject({ Authorization: 'Bearer refreshed-token' })
  })

  it('does not refresh or retry a delayed 401 after the browser session switches accounts', async () => {
    document.cookie = 'rt_browser_csrf=csrf-token; path=/'
    setAccessToken('expired-token')
    let resolveInitialRequest!: (response: Response) => void
    fetchMock
      .mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveInitialRequest = resolve }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        token: 'new-account-token',
        user: { ...baseUser, id: 'user-2', email: 'new@example.com' },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'stale-refresh-token' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))

    const staleRequest = authApi.updateCapabilities({
      capabilities: ['ATHLETE', 'SPECTATOR'],
      preferredCapability: 'SPECTATOR',
    })
    const switchedSession = authApi.login('new@example.com', 'password')

    await expect(switchedSession).resolves.toMatchObject({ token: 'new-account-token' })
    resolveInitialRequest(new Response('{}', { status: 401 }))

    await expect(staleRequest).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/auth/browser/refresh'))).toBe(false)
  })

  it('does not refresh or retry a stale FormData request after the browser session switches accounts', async () => {
    document.cookie = 'rt_browser_csrf=csrf-token; path=/'
    setAccessToken('expired-token')
    let resolveInitialRequest!: (response: Response) => void
    fetchMock
      .mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveInitialRequest = resolve }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        token: 'new-account-token',
        user: {
          ...baseUser,
          id: 'user-2',
          email: 'new@example.com',
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))

    const staleImport = participantsApi.importCsv('race-1', new File(['bib_number'], 'participants.csv', { type: 'text/csv' }))
    const switchedSession = authApi.login('new@example.com', 'password')

    await expect(switchedSession).resolves.toMatchObject({ token: 'new-account-token' })
    resolveInitialRequest(new Response('{}', { status: 401 }))

    await expect(staleImport).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/auth/browser/refresh'))).toBe(false)
  })

  it('rejects failed CSV imports before parsing the response body as a success', async () => {
    setAccessToken('valid-token')
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      message: 'CSV contains an invalid header',
      code: 'CSV_IMPORT_INVALID',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    }))

    await expect(
      participantsApi.importCsv('race-1', new File(['wrong_header'], 'participants.csv', { type: 'text/csv' })),
    ).rejects.toMatchObject({
      status: 400,
      code: 'CSV_IMPORT_INVALID',
      message: 'CSV contains an invalid header',
    })

    const request = fetchMock.mock.calls[0][1] as RequestInit
    expect(request.credentials).toBe('include')
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer valid-token')
    expect(new Headers(request.headers).get('Content-Type')).toBeNull()
    expect(request.body).toBeInstanceOf(FormData)
  })
})
