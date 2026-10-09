import { useEffect, useState } from 'react'
import { mediaApi } from '@/lib/api'
import type { MediaItem } from '@/types'

interface RaceMediaGalleryProps {
  raceId: string
}

export function RaceMediaGallery({ raceId }: RaceMediaGalleryProps) {
  const [items, setItems] = useState<MediaItem[]>([])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const data = await mediaApi.publicList(raceId)
        if (!cancelled) setItems(data)
      } catch {
        if (!cancelled) setItems([])
      }
    })()
    return () => {
      cancelled = true
    }
  }, [raceId])

  if (items.length === 0) {
    return null
  }

  return (
    <section className="rounded-[28px] border border-white/10 bg-white/6 p-4 text-white shadow-[0_20px_60px_rgba(2,6,23,0.35)] backdrop-blur-sm">
      <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Media</p>
      <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em]">Race Gallery</h2>
      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {items.map((item) => (
          <figure key={item.id} className="overflow-hidden rounded-2xl border border-white/10">
            {item.mediaType === 'VIDEO' ? (
              <video src={item.url} className="aspect-square h-full w-full object-cover" controls />
            ) : (
              <img src={item.url} alt={item.caption || 'Race media'} className="aspect-square h-full w-full object-cover" />
            )}
            {item.caption && <figcaption className="px-2 py-1 text-xs text-slate-300">{item.caption}</figcaption>}
          </figure>
        ))}
      </div>
    </section>
  )
}
