const DAYS_AHEAD = 30
const WEEKDAY_LABELS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

interface DatePill {
  iso: string
  topLabel: string
  dayNumber: number
}

function buildPills(): DatePill[] {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Array.from({ length: DAYS_AHEAD }, (_, i) => {
    const date = new Date(today)
    date.setDate(date.getDate() + i)
    const topLabel = i === 0 ? 'TODAY' : i === 1 ? 'TMRW' : WEEKDAY_LABELS[date.getDay()]
    return { iso: toIsoDate(date), topLabel, dayNumber: date.getDate() }
  })
}

export interface DateStripProps {
  selectedDate: string
  onSelect: (date: string) => void
}

// patient-slot-booking-date-logic: a BookMyShow-style date-strip - the present date and the
// next 29 days only, so there is no way to even scroll into a past date, let alone select one.
// patient-booking-visual-polish: fixed-width snap-scrolling pills (was flex-wrap, ragged edges)
// with a fading edge mask hinting there's more to scroll - a "today" pill keeps a quiet accent
// even when not selected, so a patient can find their place again after scrolling away from it.
export function DateStrip({ selectedDate, onSelect }: DateStripProps) {
  const pills = buildPills()
  const todayIso = pills[0]?.iso

  return (
    <div
      role="group"
      aria-label="Select a date"
      className="flex snap-x snap-proximity gap-2 overflow-x-auto scroll-smooth py-1 pr-6 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      style={{ maskImage: 'linear-gradient(to right, black calc(100% - 2rem), transparent)' }}
    >
      {pills.map((pill) => {
        const isSelected = pill.iso === selectedDate
        const isToday = pill.iso === todayIso
        return (
          <button
            key={pill.iso}
            type="button"
            onClick={() => onSelect(pill.iso)}
            aria-pressed={isSelected}
            className={`flex w-14 shrink-0 snap-start flex-col items-center gap-0.5 rounded-xl border px-2 py-2.5 text-center transition-all duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 ${
              isSelected
                ? 'border-indigo-600 bg-indigo-600 text-white shadow-md'
                : isToday
                  ? 'border-indigo-200 bg-indigo-50 text-gray-700 shadow-xs hover:-translate-y-0.5 hover:border-indigo-300 hover:shadow-sm'
                  : 'border-gray-200 bg-white text-gray-700 shadow-xs hover:-translate-y-0.5 hover:border-indigo-300 hover:shadow-sm'
            }`}
          >
            <span
              className={`text-[11px] font-semibold tracking-wide ${
                isSelected ? 'text-indigo-100' : isToday ? 'text-indigo-600' : 'text-gray-500'
              }`}
            >
              {pill.topLabel}
            </span>
            <span className="text-base font-semibold tabular-nums">{pill.dayNumber}</span>
          </button>
        )
      })}
    </div>
  )
}

export function todayIsoDate(): string {
  return toIsoDate(new Date())
}
