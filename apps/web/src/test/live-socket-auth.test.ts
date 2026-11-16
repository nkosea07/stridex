import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const clients: Array<{
  beforeConnect?: () => Promise<void> | void
  connectHeaders: Record<string, string>
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
}> = []

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(() => {
    const client = {
      beforeConnect: undefined as (() => Promise<void> | void) | undefined,
      connectHeaders: {} as Record<string, string>,
      onConnect: undefined as (() => void) | undefined,
      subscribe: vi.fn(),
      activate: vi.fn(),
      deactivate: vi.fn().mockResolvedValue(undefined),
    }
    clients.push(client)
    return client
  }),
}))

import { connectLiveTracking, connectUserAlerts, setAccessToken } from '@/lib/api'

function jwtExpiringIn(seconds: number) {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds }))
  return `header.${payload}.signature`
}

describe('live tracking socket authentication', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    clients.length = 0
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    setAccessToken(null)
    document.cookie = 'rt_browser_csrf=csrf-token; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'rt_browser_csrf=; Max-Age=0; path=/'
  })

  it('uses a still-valid access token on each connect attempt', async () => {
    const token = jwtExpiringIn(600)
    setAccessToken(token)

    connectLiveTracking('race-1', () => undefined)
    await clients[0].beforeConnect?.()

    expect(clients[0].connectHeaders).toEqual({ Authorization: `Bearer ${token}` })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('refreshes an expired or missing token before reconnecting', async () => {
    setAccessToken(jwtExpiringIn(-60))
    const refreshed = jwtExpiringIn(600)
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ token: refreshed, user: { id: 'u' } }), { status: 200 }))

    connectLiveTracking('race-1', () => undefined)
    await clients[0].beforeConnect?.()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(clients[0].connectHeaders).toEqual({ Authorization: `Bearer ${refreshed}` })
  })

  it('disconnects only the client it created', () => {
    setAccessToken(jwtExpiringIn(600))
    const disconnectLive = connectLiveTracking('race-1', () => undefined)
    connectUserAlerts('user-1', () => undefined)

    disconnectLive()

    expect(clients[0].deactivate).toHaveBeenCalledTimes(1)
    expect(clients[1].deactivate).not.toHaveBeenCalled()
  })
})

describe('browser refresh across tabs', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    setAccessToken(null)
    document.cookie = 'rt_browser_csrf=tab-a-csrf; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'rt_browser_csrf=; Max-Age=0; path=/'
  })

  it('retries once with the new session when another tab rotated the refresh token first', async () => {
    const refreshed = jwtExpiringIn(600)
    fetchMock
      .mockImplementationOnce(async () => {
        document.cookie = 'rt_browser_csrf=tab-b-csrf; path=/'
        return new Response('{}', { status: 401 })
      })
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: refreshed, user: { id: 'u' } }), { status: 200 }))

    connectLiveTracking('race-1', () => undefined)
    await clients[clients.length - 1].beforeConnect?.()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect((fetchMock.mock.calls[1][1] as RequestInit).headers).toEqual({ 'X-CSRF-Token': 'tab-b-csrf' })
    expect(clients[clients.length - 1].connectHeaders).toEqual({ Authorization: `Bearer ${refreshed}` })
  })

  it('does not retry when the session was genuinely revoked', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 401 }))

    connectLiveTracking('race-1', () => undefined)
    await clients[clients.length - 1].beforeConnect?.()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(clients[clients.length - 1].connectHeaders).toEqual({})
  })
})
