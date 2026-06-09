/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo',
  testEnvironment: 'node',
  testMatch: [
    '**/__tests__/**/*.test.ts',
    '**/__tests__/**/*.test.tsx',
  ],
  moduleNameMapper: {
    // AsyncStorage mock
    '@react-native-async-storage/async-storage':
      require.resolve('@react-native-async-storage/async-storage/jest/async-storage-mock'),
    // NetInfo mock
    '@react-native-community/netinfo':
      '<rootDir>/src/__mocks__/@react-native-community/netinfo.ts',
    // SecureStorage mock
    '../storage/secureStorage':
      '<rootDir>/src/__mocks__/secureStorage.ts',
    '../../storage/secureStorage':
      '<rootDir>/src/__mocks__/secureStorage.ts',
    // react-native-safe-area-context mock
    'react-native-safe-area-context': require.resolve(
      'react-native-safe-area-context/jest/mock'
    ),
  },
  setupFilesAfterFramework: [],
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?)|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg)',
  ],
}
