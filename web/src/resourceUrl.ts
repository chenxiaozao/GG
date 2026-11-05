/** Frontend assets live under Pages base; uploads live on the API origin. */
export function resourceUrl(value?: string): string {
  if (!value) return `${import.meta.env.BASE_URL}products/placeholder.svg`;
  if (value.startsWith('/products/')) return `${import.meta.env.BASE_URL}${value.slice(1)}`;
  if (value.startsWith('/uploads/')) {
    const api = import.meta.env.VITE_API_BASE_URL || '/api';
    return /^https?:\/\//.test(api) ? new URL(value, api).href : value;
  }
  return value;
}
