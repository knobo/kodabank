/**
 * Format a number as Norwegian currency (kr 45 230,50)
 */
export function formatCurrency(amount: number, currency = 'NOK'): string {
  return new Intl.NumberFormat('nb-NO', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/**
 * Format a date string as Norwegian date (30. mars 2026)
 */
export function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return new Intl.DateTimeFormat('nb-NO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

/**
 * Format a date string as short Norwegian date (30.03.2026)
 */
export function formatDateShort(dateStr: string): string {
  const date = new Date(dateStr);
  return new Intl.DateTimeFormat('nb-NO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

/**
 * Format IBAN with spaces every 4 characters
 */
export function formatIban(iban: string): string {
  return iban.replace(/(.{4})/g, '$1 ').trim();
}

/**
 * Format national ID (fødselsnummer) as XXX XXX XXXXX
 */
export function formatNationalId(id: string): string {
  if (id.length !== 11) return id;
  return `${id.slice(0, 3)} ${id.slice(3, 6)} ${id.slice(6)}`;
}
