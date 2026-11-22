import js from '@eslint/js'
import globals from 'globals'
import i18next from 'eslint-plugin-i18next'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist', 'node_modules', '.react-router'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': 'off',
      'react-hooks/exhaustive-deps': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
  },
  {
    // No hardcoded user-facing text anywhere in the web app (see AGENTS.md "Internationalization").
    files: ['src/**/*.tsx', 'app/**/*.tsx'],
    ignores: ['src/test/**', '**/*.test.tsx'],
    plugins: {
      i18next,
    },
    rules: {
      'i18next/no-literal-string': ['error', {
        mode: 'jsx-only',
        'should-validate-template': false,
        'jsx-attributes': {
          include: ['placeholder', 'title', 'alt', 'aria-label', 'aria-description', 'label', 'description'],
        },
      }],
    },
  },
)
