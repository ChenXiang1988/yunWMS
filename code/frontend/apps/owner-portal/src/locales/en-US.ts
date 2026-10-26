export default {
  app: {
    name: 'YunWMS',
    subtitle: 'Shipper Portal'
  },
  menu: {
    dashboard: 'Dashboard',
    product: 'Products'
  },
  common: {
    search: 'Search',
    reset: 'Reset',
    create: 'Create',
    actions: 'Actions',
    logout: 'Sign out'
  },
  login: {
    title: 'Sign in to Shipper Portal',
    account: 'Owner code',
    accountPlaceholder: 'e.g. OWNER0001',
    password: 'Password',
    submit: 'Sign in',
    tip: 'The initial password must be changed after the first sign-in',
    portalDenied: 'This account cannot sign in to the shipper portal. Please use the network console.',
    failed: 'Sign-in failed. Please try again later.'
  },
  changePassword: {
    title: 'Change initial password',
    subtitle: 'The initial password must be changed before you can use the system',
    oldPassword: 'Current password',
    newPassword: 'New password',
    confirmPassword: 'Confirm new password',
    strengthHint: '8–20 characters, including uppercase letters, lowercase letters and digits',
    submit: 'Change password',
    success: 'Password changed',
    mismatch: 'The two new passwords do not match',
    failed: 'Failed to change the password. Please try again later.'
  }
}
