import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { searchPatients, type PatientSearchResult } from './api'

// _diagnostics [P0] - [staff-console-audit-2026-09-10] - [RAW_ID_ENTRY]: backs the raw free-text
// "Patient ID" inputs in WalkInForm/BookSlotForm that previously required staff to already know
// a patient's UUID out-of-band - a front-desk clerk has no way to produce one for a patient
// standing at the counter. Debounced server-side search (not a preloaded <datalist>) so this
// scales to a clinic with hundreds of patients, matching PatientSearch's own search endpoint.
export interface PatientPickerProps {
  clinicId: string
  token: string
  value: string
  onChange: (patientId: string, patient: PatientSearchResult | null) => void
  id?: string
  required?: boolean
}

const SEARCH_DEBOUNCE_MS = 300
const MIN_SEARCH_LENGTH = 2
const MAX_RESULTS_SHOWN = 8

export function PatientPicker({ clinicId, token, value, onChange, id, required }: PatientPickerProps) {
  const [searchText, setSearchText] = useState('')
  const [selected, setSelected] = useState<PatientSearchResult | null>(null)
  const [results, setResults] = useState<PatientSearchResult[]>([])
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const containerRef = useRef<HTMLDivElement>(null)

  // The parent clears `value` (e.g. switching from "existing patient" to "new walk-in patient"
  // and back) without going through selectPatient/clearSelection - mirror that here too.
  useEffect(() => {
    if (value === '') {
      setSelected(null)
    }
  }, [value])

  useEffect(() => {
    if (selected) {
      setResults([])
      setOpen(false)
      return
    }
    const trimmed = searchText.trim()
    if (trimmed.length === 0) {
      setResults([])
      setOpen(false)
      setSearching(false)
      return
    }
    if (trimmed.length < MIN_SEARCH_LENGTH) {
      setResults([])
      setOpen(true)
      setSearching(false)
      return
    }

    setOpen(true)
    setSearching(true)
    let cancelled = false
    const timer = setTimeout(() => {
      searchPatients(clinicId, trimmed, token, { size: MAX_RESULTS_SHOWN })
        .then((found) => {
          if (cancelled) return
          setResults(found.patients)
          setActiveIndex(-1)
        })
        .catch(() => {
          if (!cancelled) setResults([])
        })
        .finally(() => {
          if (!cancelled) setSearching(false)
        })
    }, SEARCH_DEBOUNCE_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [searchText, clinicId, token, selected])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function selectPatient(patient: PatientSearchResult) {
    setSelected(patient)
    setSearchText('')
    setOpen(false)
    onChange(patient.patientId, patient)
  }

  function clearSelection() {
    setSelected(null)
    setSearchText('')
    onChange('', null)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!open || results.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((i) => Math.min(i + 1, results.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((i) => Math.max(i - 1, 0))
    } else if (event.key === 'Enter') {
      if (activeIndex >= 0) {
        event.preventDefault()
        selectPatient(results[activeIndex])
      }
    } else if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  const listboxId = `${id ?? 'patient-picker'}-listbox`

  if (selected) {
    return (
      <div className="mt-1 flex items-center justify-between gap-3 rounded-lg border border-gray-300 bg-gray-50 px-3.5 py-2.5">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-gray-900">{selected.name}</p>
          {selected.phone && <p className="truncate text-sm text-gray-500">{selected.phone}</p>}
        </div>
        <button
          type="button"
          onClick={clearSelection}
          className="shrink-0 text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
        >
          Change
        </button>
      </div>
    )
  }

  return (
    <div ref={containerRef} className="relative mt-1">
      <input
        id={id}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined}
        aria-required={required}
        autoComplete="off"
        placeholder="Search by name or phone"
        value={searchText}
        onChange={(e) => setSearchText(e.target.value)}
        onKeyDown={handleKeyDown}
        onFocus={() => {
          if (searchText.trim().length > 0) setOpen(true)
        }}
        className="input"
      />
      {open && (
        <ul id={listboxId} role="listbox" className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-lg border border-gray-200 bg-white py-1 shadow-md">
          {searchText.trim().length < MIN_SEARCH_LENGTH && (
            <li className="px-3 py-2 text-sm text-gray-500">Type at least 2 characters to search.</li>
          )}
          {searchText.trim().length >= MIN_SEARCH_LENGTH && searching && (
            <li className="px-3 py-2 text-sm text-gray-500">Searching…</li>
          )}
          {searchText.trim().length >= MIN_SEARCH_LENGTH && !searching && results.length === 0 && (
            <li className="px-3 py-2 text-sm text-gray-500">No matching patients.</li>
          )}
          {!searching &&
            results.map((patient, index) => (
              <li
                key={patient.patientId}
                id={`${listboxId}-option-${index}`}
                role="option"
                aria-selected={index === activeIndex}
                onMouseDown={(e) => {
                  e.preventDefault()
                  selectPatient(patient)
                }}
                onMouseEnter={() => setActiveIndex(index)}
                className={`cursor-pointer px-3 py-2 text-sm ${
                  index === activeIndex ? 'bg-indigo-50 text-indigo-900' : 'text-gray-700 hover:bg-gray-50'
                }`}
              >
                <p className="font-medium">{patient.name}</p>
                {patient.phone && <p className="text-xs text-gray-500">{patient.phone}</p>}
              </li>
            ))}
        </ul>
      )}
    </div>
  )
}
