import type { NumberSeries } from './types'

// Same rows as supabase/004_seed.sql (scripts/test-logic.ts checks they stay identical).
// Used by mock mode and the simulator. APPROXIMATE: mobile number portability breaks the mapping.
const RAW = `
9810 Airtel Delhi|9811 Vi Delhi|9818 Airtel Delhi|9871 Airtel Delhi|9899 Vi Delhi|9910 Airtel Delhi|8448 Airtel Delhi|7428 Airtel Delhi|8860 Airtel Delhi|8595 Airtel Delhi|7011 Jio Delhi
9820 Vi Mumbai|9821 Vi Mumbai|9833 Vi Mumbai|9892 Airtel Mumbai|9930 Vi Mumbai|9167 Vi Mumbai|7738 Airtel Mumbai|9020 Jio Mumbai
9822 Vi Maharashtra|9890 Vi Maharashtra|9850 Airtel Maharashtra|9423 BSNL Maharashtra
9840 Airtel Tamil Nadu|9884 Vi Tamil Nadu|9841 Airtel Tamil Nadu|9443 BSNL Tamil Nadu
9880 Airtel Karnataka|9886 Vi Karnataka|9900 Airtel Karnataka|9845 Airtel Karnataka
9831 Airtel Kolkata|9836 Vi Kolkata|9903 Airtel Kolkata|9830 Vi Kolkata|8100 Airtel Kolkata|8420 Airtel Kolkata
9849 Airtel Andhra Pradesh|9848 Vi Andhra Pradesh|9866 Airtel Andhra Pradesh|9000 Airtel Andhra Pradesh|8886 Airtel Andhra Pradesh
9824 Vi Gujarat|9825 Vi Gujarat|9879 Vi Gujarat
9828 Vi Rajasthan|9829 Airtel Rajasthan|9414 BSNL Rajasthan
9415 BSNL Uttar Pradesh (East)|9839 Vi Uttar Pradesh (East)|9450 BSNL Uttar Pradesh (East)
9412 BSNL Uttar Pradesh (West)|9837 Vi Uttar Pradesh (West)|9411 BSNL Uttar Pradesh (West)|7042 Airtel Uttar Pradesh (West)
9431 BSNL Bihar|9835 Airtel Bihar|9934 Airtel Bihar|6203 Jio Bihar|9905 Airtel Bihar
9437 BSNL Odisha|9861 Airtel Odisha
9846 Vi Kerala|9847 Airtel Kerala|9895 Airtel Kerala
9815 Airtel Punjab|9814 Vi Punjab|9876 Airtel Punjab
9416 BSNL Haryana|9812 Vi Haryana|9354 Airtel Haryana
9425 BSNL Madhya Pradesh|9826 Airtel Madhya Pradesh
9435 BSNL Assam|9854 Airtel Assam
9419 BSNL Jammu & Kashmir|9418 BSNL Himachal Pradesh|9436 BSNL North East
9434 BSNL West Bengal|9832 Airtel West Bengal|7595 Airtel West Bengal|7890 Airtel West Bengal`

export const NUMBER_SERIES: NumberSeries[] = RAW.trim()
  .split(/[|\n]/)
  .map((entry) => {
    const [prefix, operator, ...circle] = entry.trim().split(' ')
    return { prefix, operator, telecom_circle: circle.join(' ') }
  })

const byPrefix = new Map(NUMBER_SERIES.map((s) => [s.prefix, s]))

/** Circle for a full number via its 4-digit series, or null if the series is unknown. */
export function circleOf(e164: string): string | null {
  return byPrefix.get(e164.replace(/\D/g, '').slice(-10, -6))?.telecom_circle ?? null
}

/** First 5 digits of the 10-digit national number (contract §5). */
export const prefixOf = (e164: string) => e164.replace(/\D/g, '').slice(-10, -5)
