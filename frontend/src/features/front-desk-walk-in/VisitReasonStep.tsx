import { FormField } from '../../components/FormField'
import { VISIT_REASONS, VISIT_REASON_DETAIL_MAX, type VisitReason } from './visitReasons'

export interface VisitReasonStepProps {
  reason: VisitReason | ''
  detail: string
  onChange: (reason: VisitReason | '', detail: string) => void
  reasonError?: string
  detailError?: string
}

// FR-004: a required reason from the standard list; "Other" opens a short free-text reason.
export function VisitReasonStep({ reason, detail, onChange, reasonError, detailError }: VisitReasonStepProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <FormField label="Reason for visit" htmlFor="walkInReason" required error={reasonError}>
        <select
          id="walkInReason"
          className="input"
          value={reason}
          onChange={(e) => onChange(e.target.value as VisitReason | '', e.target.value === 'OTHER' ? detail : '')}
        >
          <option value="" disabled>
            Choose a reason
          </option>
          {VISIT_REASONS.map((r) => (
            <option key={r.value} value={r.value}>
              {r.label}
            </option>
          ))}
        </select>
      </FormField>
      {reason === 'OTHER' && (
        <FormField
          label="Describe the reason"
          htmlFor="walkInReasonDetail"
          required
          error={detailError}
          hint={`${detail.length}/${VISIT_REASON_DETAIL_MAX}`}
        >
          <input
            id="walkInReasonDetail"
            className="input"
            maxLength={VISIT_REASON_DETAIL_MAX}
            value={detail}
            onChange={(e) => onChange(reason, e.target.value)}
          />
        </FormField>
      )}
    </div>
  )
}
