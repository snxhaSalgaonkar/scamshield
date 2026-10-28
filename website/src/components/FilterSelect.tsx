import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

interface Props<T extends string> {
  label: string
  value: T
  items: { value: T; label: string }[]
  onChange: (value: T) => void
  className?: string
}

export function FilterSelect<T extends string>({ label, value, items, onChange, className }: Props<T>) {
  return (
    <Select items={items} value={value} onValueChange={(v) => v != null && onChange(v as T)}>
      <SelectTrigger size="sm" aria-label={label} className={className}>
        <span className="text-muted-foreground">{label}:</span>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {items.map((item) => (
          <SelectItem key={item.value} value={item.value}>
            {item.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
