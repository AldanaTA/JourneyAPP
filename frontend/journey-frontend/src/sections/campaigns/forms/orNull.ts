/** Empty text means "not set". */
export const orNull = (value: string) => (value.trim() ? value.trim() : null);
