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
    // i18n-migrated files: no hardcoded JSX text. Grow this list as areas are localized
    // (see AGENTS.md "Internationalization").
    files: [
      'src/pages/LoginPage.tsx',
      'src/pages/RegisterPage.tsx',
      'src/pages/SettingsPage.tsx',
      'src/pages/RegistrationFieldsPage.tsx',
      'src/pages/CategoriesPage.tsx',
      'src/components/layout/DashboardLayout.tsx',
      'src/components/layout/UserLayout.tsx',
      'src/components/auth/RequireAuthenticatedRoute.tsx',
      'src/components/auth/RequireAdminRoute.tsx',
      'src/components/auth/RequireOperationsRoute.tsx',
      'src/components/auth/RequireRoleRoute.tsx',
      'src/components/auth/RequireCapabilityRoute.tsx',
      'app/routes/home.tsx',
    ],
    plugins: {
      i18next,
    },
    rules: {
      'i18next/no-literal-string': ['error', { mode: 'jsx-text-only', 'should-validate-template': false }],
    },
  },
)
