import type { StorybookConfig } from '@storybook/react-vite'
import { resolve } from 'path'
import { fileURLToPath } from 'url'
const __dirname = fileURLToPath(new URL('.', import.meta.url))

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(js|jsx|ts|tsx|mdx)'],
  addons: [],
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
  viteFinal: (config) => {
    config.resolve ??= {}
    config.resolve.alias = {
      ...config.resolve.alias,
      '@': resolve(__dirname, '../src'),
    }
    // VitePWA causes workbox cache-size errors in Storybook build — remove it
    const isPwaPlugin = (p: unknown): boolean => {
      if (!p || typeof p !== 'object' || !('name' in p)) return false
      const name = (p as { name: unknown }).name
      if (typeof name !== 'string') return false
      return name.includes('pwa') || name.includes('workbox') || name.includes('GenerateSW')
    }
    const flatPlugins = (config.plugins ?? []).flat(Infinity)
    config.plugins = flatPlugins.filter((p) => !isPwaPlugin(p))
    return config
  },
}

export default config
