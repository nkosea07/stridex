/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('race photo gallery API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads the public gallery and keeps only displayable, published items', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([
      { id: 'm1', mediaType: 'IMAGE', caption: ' Finish line ', thumbnailUrl: '/api/v1/public/races/race-1/media/m1/thumbnail', publiclyVisible: true },
      { id: 'm2', mediaType: 'VIDEO', caption: null, thumbnailUrl: null, publiclyVisible: true },
      { id: 'm3', mediaType: 'IMAGE', publiclyVisible: false },
      { mediaType: 'IMAGE', caption: 'No id' },
    ]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const media = await api.getPublicRaceMedia('race-1')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/public\/races\/race-1\/media$/)
    expect(media).toEqual([
      { id: 'm1', mediaType: 'IMAGE', caption: 'Finish line', hasThumbnail: true },
      { id: 'm2', mediaType: 'VIDEO', caption: null, hasThumbnail: false },
    ])
  })

  it('rejects a malformed gallery response instead of rendering partial data', async () => {
    const { parsePublicRaceMedia } = await import('../lib/api.ts')

    expect(() => parsePublicRaceMedia({ items: [] })).toThrow('Race media response was malformed.')
    expect(() => parsePublicRaceMedia(['m1'])).toThrow('Race media response was malformed.')
  })

  it('builds absolute contract URLs for thumbnails and full content', async () => {
    const { publicRaceMediaUrl } = await import('../lib/api.ts')

    const thumbnail = publicRaceMediaUrl('race-1', 'm1', 'thumbnail')
    const content = publicRaceMediaUrl('race-1', 'm1', 'content')

    expect(thumbnail).toMatch(/^https?:\/\/.+\/public\/races\/race-1\/media\/m1\/thumbnail$/)
    expect(content).toMatch(/^https?:\/\/.+\/public\/races\/race-1\/media\/m1\/content$/)
  })
})
