import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Upload, Check, X, Trash2, Image as ImageIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { mediaApi } from '@/lib/api'
import type { MediaItem } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'

const statusColors: Record<string, string> = {
  PENDING: 'bg-warning/20 text-warning-foreground',
  APPROVED: 'bg-success/20 text-success',
  REJECTED: 'bg-destructive/20 text-destructive',
}

export function MediaPage() {
  const { raceId } = useParams()
  const { user } = useAuth()
  const canModerateMedia = isAdminRole(user?.role)
  const [items, setItems] = useState<MediaItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isUploading, setIsUploading] = useState(false)
  const [caption, setCaption] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const loadMedia = useCallback(async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      setItems(await mediaApi.list(raceId))
    } catch {
      toast.error('Failed to load media')
    } finally {
      setIsLoading(false)
    }
  }, [raceId])

  useEffect(() => {
    void loadMedia()
  }, [loadMedia])

  const handleUpload = async (file: File) => {
    if (!raceId) return
    setIsUploading(true)
    try {
      await mediaApi.upload(raceId, file, caption || undefined)
      toast.success('Media uploaded for review')
      setCaption('')
      await loadMedia()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Upload failed')
    } finally {
      setIsUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleModerate = async (id: string, approve: boolean) => {
    if (!raceId) return
    try {
      if (approve) {
        await mediaApi.approve(raceId, id)
      } else {
        await mediaApi.reject(raceId, id)
      }
      toast.success(approve ? 'Media approved' : 'Media rejected')
      await loadMedia()
    } catch {
      toast.error('Failed to update media')
    }
  }

  const handleDelete = async (id: string) => {
    if (!raceId) return
    try {
      await mediaApi.remove(raceId, id)
      toast.success('Media deleted')
      await loadMedia()
    } catch {
      toast.error('Failed to delete media')
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Media Gallery</h1>
        <p className="text-muted-foreground">Upload race photos/videos and moderate what spectators see</p>
      </div>

      <Card>
        <CardHeader><CardTitle>Upload Media</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <Input
            placeholder="Caption (optional)"
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
          />
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*,video/mp4,video/webm"
            className="block w-full text-sm"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) void handleUpload(file)
            }}
          />
          <p className="text-xs text-muted-foreground flex items-center gap-1">
            <Upload className="h-3 w-3" /> {isUploading ? 'Uploading…' : 'Images or MP4/WebM, up to 15MB'}
          </p>
        </CardContent>
      </Card>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading media…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No media uploaded yet.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => (
            <Card key={item.id} className="overflow-hidden">
              <div className="flex h-44 items-center justify-center bg-muted">
                {item.mediaType === 'VIDEO' ? (
                  <video src={item.url} className="h-full w-full object-cover" controls />
                ) : (
                  <img src={item.url} alt={item.caption || 'Race media'} className="h-full w-full object-cover" />
                )}
              </div>
              <CardContent className="space-y-2 p-3">
                <div className="flex items-center justify-between gap-2">
                  <Badge className={statusColors[item.status] ?? 'bg-muted'}>{item.status}</Badge>
                  <span className="flex items-center gap-1 text-xs text-muted-foreground">
                    <ImageIcon className="h-3 w-3" /> {item.mediaType}
                  </span>
                </div>
                {item.caption && <p className="text-sm">{item.caption}</p>}
                <div className="flex gap-2">
                  {canModerateMedia && item.status !== 'APPROVED' && (
                    <Button size="sm" variant="outline" onClick={() => void handleModerate(item.id, true)}>
                      <Check className="mr-1 h-3 w-3" /> Approve
                    </Button>
                  )}
                  {canModerateMedia && item.status !== 'REJECTED' && (
                    <Button size="sm" variant="outline" onClick={() => void handleModerate(item.id, false)}>
                      <X className="mr-1 h-3 w-3" /> Reject
                    </Button>
                  )}
                  {canModerateMedia && (
                    <Button size="sm" variant="ghost" className="text-destructive" onClick={() => void handleDelete(item.id)}>
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
