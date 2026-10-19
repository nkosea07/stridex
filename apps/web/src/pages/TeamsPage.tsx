import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Plus, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { registrationApi } from '@/lib/api'
import type { Team } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'

export function TeamsPage() {
  const { raceId } = useParams()
  const { user } = useAuth()
  const canManageTeams = isAdminRole(user?.role)
  const [teams, setTeams] = useState<Team[]>([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({ name: '', clubName: '' })

  const load = async () => {
    if (!raceId) return
    setLoading(true)
    try {
      setTeams(await registrationApi.listTeams(raceId))
    } catch {
      toast.error('Failed to load teams')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [raceId])

  const handleCreate = async () => {
    if (!raceId) return
    if (!form.name.trim()) {
      toast.error('Team name is required')
      return
    }
    setSaving(true)
    try {
      await registrationApi.createTeam(raceId, {
        name: form.name.trim(),
        clubName: form.clubName.trim() || undefined,
      })
      toast.success('Team created')
      setDialogOpen(false)
      setForm({ name: '', clubName: '' })
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create team')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Teams</h1>
          <p className="text-muted-foreground">Teams athletes can join during registration</p>
        </div>
        {canManageTeams && (
          <Button onClick={() => setDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            New team
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Registered teams</CardTitle>
          <CardDescription>Team membership is managed at registration.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Club</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Members</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {teams.map((team) => (
                <TableRow key={team.id}>
                  <TableCell className="font-medium">{team.name}</TableCell>
                  <TableCell>{team.clubName ?? '-'}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{team.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <span className="inline-flex items-center gap-1">
                      <Users className="h-3.5 w-3.5 text-muted-foreground" />
                      {team.memberCount}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
              {!loading && teams.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className="text-sm text-muted-foreground">
                    No teams registered.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {canManageTeams && <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>New team</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>Name</Label><Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} /></div>
            <div><Label>Club</Label><Input value={form.clubName} onChange={(e) => setForm((f) => ({ ...f, clubName: e.target.value }))} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>Cancel</Button>
            <Button onClick={() => void handleCreate()} disabled={saving}>{saving ? 'Creating…' : 'Create'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </div>
  )
}
