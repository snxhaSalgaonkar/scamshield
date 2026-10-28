// The 22 Indian telecom circles (licence service areas), with an approximate centre for the map.
// Names must match number_series.telecom_circle in supabase/004_seed.sql.

export interface Circle {
  name: string
  lon: number
  lat: number
  /** Label side on the map, to keep neighbouring labels apart. */
  label: 'l' | 'r'
}

export const CIRCLES: Circle[] = [
  { name: 'Jammu & Kashmir', lon: 75.3, lat: 33.8, label: 'r' },
  { name: 'Himachal Pradesh', lon: 77.2, lat: 31.9, label: 'r' },
  { name: 'Punjab', lon: 75.4, lat: 30.9, label: 'l' },
  { name: 'Haryana', lon: 76.1, lat: 29.2, label: 'l' },
  { name: 'Delhi', lon: 77.1, lat: 28.6, label: 'r' },
  { name: 'Rajasthan', lon: 73.8, lat: 26.6, label: 'l' },
  { name: 'Uttar Pradesh (West)', lon: 78.6, lat: 28.0, label: 'r' },
  { name: 'Uttar Pradesh (East)', lon: 82.2, lat: 26.4, label: 'r' },
  { name: 'Bihar', lon: 85.6, lat: 25.3, label: 'r' },
  { name: 'Assam', lon: 92.6, lat: 26.2, label: 'l' },
  { name: 'North East', lon: 94.3, lat: 24.6, label: 'l' },
  { name: 'West Bengal', lon: 87.9, lat: 23.9, label: 'r' },
  { name: 'Kolkata', lon: 88.4, lat: 22.5, label: 'r' },
  { name: 'Gujarat', lon: 71.6, lat: 22.6, label: 'l' },
  { name: 'Madhya Pradesh', lon: 78.2, lat: 23.4, label: 'r' },
  { name: 'Odisha', lon: 84.5, lat: 20.5, label: 'r' },
  { name: 'Maharashtra', lon: 76.4, lat: 19.6, label: 'r' },
  { name: 'Mumbai', lon: 72.9, lat: 19.1, label: 'l' },
  { name: 'Andhra Pradesh', lon: 79.4, lat: 16.6, label: 'r' },
  { name: 'Karnataka', lon: 75.9, lat: 14.6, label: 'l' },
  { name: 'Tamil Nadu', lon: 78.6, lat: 11.1, label: 'r' },
  { name: 'Kerala', lon: 76.4, lat: 9.9, label: 'l' },
]

export const CIRCLE_NAMES = CIRCLES.map((c) => c.name)
