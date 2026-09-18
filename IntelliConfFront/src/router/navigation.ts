import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'

export type ConferenceRouteName =
  | 'Dashboard'
  | 'ConferenceInfo'
  | 'ConferenceSetup'
  | 'ConferenceCommitteeInit'
  | 'MailWorkflow'
  | 'MailCompose'
  | 'ConferenceUsers'
  | 'ConferenceMailAccount'
  | 'AuthorDiscovery'
  | 'ElectronicSeal'

function firstQueryValue(value: unknown) {
  return Array.isArray(value) ? value[0] : value
}

export function getConferenceId(query: Record<string, unknown>) {
  const value = firstQueryValue(query.confId)
  return typeof value === 'string' ? value.trim() : ''
}

export function conferenceLocation(
  name: ConferenceRouteName,
  confId: string,
  query: LocationQueryRaw = {},
): RouteLocationRaw {
  const normalizedId = confId.trim()
  if (!normalizedId) return { name: 'Portal' }

  return {
    name,
    query: {
      ...query,
      confId: normalizedId,
    },
  }
}

/**
 * Only allow an in-app absolute path to be restored after login. This keeps
 * deep links working without turning the redirect query into an open redirect.
 */
export function safeRedirectTarget(value: unknown) {
  const candidate = firstQueryValue(value)
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return ''
  }

  try {
    const parsed = new URL(candidate, 'http://intelliconf.local')
    if (parsed.origin !== 'http://intelliconf.local' || parsed.pathname === '/auth') return ''
    return `${parsed.pathname}${parsed.search}${parsed.hash}`
  } catch {
    return ''
  }
}
