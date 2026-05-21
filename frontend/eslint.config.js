// ESLint 9 flat config — replaces .eslintrc.cjs.
// Migrated as part of the ESLint 9 / typescript-eslint 8 / eslint-plugin-react-refresh 0.5
// dependency bump (Closes #108, #111). The plain object structure of legacy
// `extends` / `parser` / `plugins` keys is replaced with array composition.
//
// Reference: https://eslint.org/docs/latest/use/configure/migration-guide

import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'

export default tseslint.config(
  // Ignore patterns — flat config requires this as its own entry (no longer a top-level key).
  { ignores: ['dist', 'coverage', 'node_modules', 'eslint.config.js'] },

  // Main TS/TSX rules: js recommended + typescript-eslint recommended + project overrides.
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
      // react-hooks-recommended is exposed as a flat preset on the plugin object in v7+;
      // spread its rules manually since flat config doesn't auto-pick up legacy `recommended`.
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      // Disable React Compiler rules — codebase does not use React Compiler.
      'react-hooks/set-state-in-effect': 'off',
      'react-hooks/refs': 'off',
      'react-hooks/purity': 'off',
      'react-hooks/preserve-manual-memoization': 'off',
      'react-hooks/immutability': 'off',
    },
  },
)
