# @vcase/react

The React adapter of [vcase](https://github.com/botiverse/vcase): scan example files, Storybook stories or `*.cases.tsx` into a vcase manifest, capture each case with Playwright, and verify the captures against committed goldens pixel for pixel. The manifest format is shared with vcase's Kotlin Compose adapter, so the same component can be compared across platforms.

```sh
npm install --save-dev @vcase/react
npx vcase-react check    # manifest matches the scan (no browser)
npx vcase-react verify   # capture and compare with goldens
npx vcase-react record   # write new and changed goldens
npx vcase-react parity --with mobile=path/to/other/goldens
```

Full documentation: [React section of the vcase README](https://github.com/botiverse/vcase#react).
