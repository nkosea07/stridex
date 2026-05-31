import { Monitor, Moon, Sun } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useTheme, type ThemePreference } from '@/contexts/ThemeContext'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

type ThemeSelectProps = {
  className?: string
}

const themeOptions: Array<{ value: ThemePreference; label: string; icon: typeof Sun }> = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
]

function isThemePreference(value: string): value is ThemePreference {
  return value === 'light' || value === 'dark' || value === 'system'
}

export function ThemeSelect({ className }: ThemeSelectProps) {
  const { theme, setTheme } = useTheme()

  return (
    <Select
      value={theme}
      onValueChange={(value) => {
        if (isThemePreference(value)) {
          setTheme(value)
        }
      }}
    >
      <SelectTrigger className={cn('w-[150px]', className)} aria-label="Theme">
        <SelectValue placeholder="Theme" />
      </SelectTrigger>
      <SelectContent>
        {themeOptions.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <span className="inline-flex items-center gap-2">
              <option.icon className="h-4 w-4" />
              {option.label}
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

