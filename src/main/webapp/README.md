# Harmonia Client-Side Docs

## Local Development

1. Install dependencies:

```bash
npm install
```

2. Run the development server:

```bash
npm run dev
```

## Project Structure

The important folders and structures of the projects are defined as follows:

```text
webapp/
├─ src/                         # Main application source code
│  ├─ assets/                   # Static assets (images, fonts, icons, etc.)
│  ├─ components/               # Reusable React components (self-implemented and generated with shadcn)
│  │  └─ ui/                    # UI components, generated with shadcn
│  ├─ data/                     # Data loaders (API-related utilities) and mock data
│  ├─ hooks/                    # Custom React hooks used across the project
│  ├─ lib/                      # Utilities
│  ├─ pages/                    # Application pages used by the router
│  ├─ types/                    # TypeScript types, interfaces, and shared definitions
│  ├─ config.ts                 # Global configuration (e.g., flags, environment settings)
│  ├─ main.ts                   # App entry point that initializes React
│  ├─ index.css                 # Global stylesheet for the application
│  └─ App.tsx                   # Root component that sets up app structure/layout
├─ package.json                 # Project dependencies and scripts
└─ README.md                    # Project documentation

```

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
]);
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x';
import reactDom from 'eslint-plugin-react-dom';

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
]);
```
