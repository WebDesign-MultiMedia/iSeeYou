import { useColorScheme } from 'react-native';

/** Design tokens. Light and dark palettes share keys so components never branch on the scheme. */
const light = {
  bg: '#ffffff',
  surface: '#f6f8fb',
  card: '#ffffff',
  text: '#1b1f24',
  textMuted: '#5b6470',
  border: '#dde2e8',
  primary: '#1a73e8',
  primaryText: '#ffffff',
  danger: '#c5221f',
  success: '#188038',
  warning: '#b06000',
  overlay: 'rgba(0,0,0,0.35)',
  shadow: '#000000',
};

const dark: typeof light = {
  bg: '#0f1216',
  surface: '#171b21',
  card: '#1d232b',
  text: '#eef1f5',
  textMuted: '#a3adba',
  border: '#2c343e',
  primary: '#8ab4f8',
  primaryText: '#0f1216',
  danger: '#f28b82',
  success: '#81c995',
  warning: '#fdd663',
  overlay: 'rgba(0,0,0,0.55)',
  shadow: '#000000',
};

export type Palette = typeof light;

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;
export const radius = { sm: 8, md: 12, lg: 20, pill: 999 } as const;
export const font = {
  title: { fontSize: 28, fontWeight: '700' as const },
  heading: { fontSize: 20, fontWeight: '700' as const },
  body: { fontSize: 16 },
  label: { fontSize: 14, fontWeight: '600' as const },
  caption: { fontSize: 13 },
};

export function useTheme(): Palette {
  return useColorScheme() === 'dark' ? dark : light;
}
