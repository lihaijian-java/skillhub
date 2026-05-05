import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { useAuthMethods } from './use-auth-methods'

interface LoginButtonProps {
  returnTo?: string
}

/**
 * Returns the appropriate icon for a given OAuth provider.
 */
function OAuthIcon({ provider }: { provider: string }) {
  const normalizedProvider = provider.toLowerCase()
  return (
    <span className="mr-3 flex h-5 w-5 shrink-0 items-center justify-center overflow-hidden rounded-full" aria-hidden="true">
      <img
        src={`/${normalizedProvider}-logo.svg`}
        alt=""
        className="h-5 w-5 object-contain"
      />
    </span>
  )
}

/**
 * Renders OAuth login buttons from the auth-method catalog returned by the backend.
 */
export function LoginButton({ returnTo }: LoginButtonProps) {
  const { t, i18n } = useTranslation()
  const { data, isLoading } = useAuthMethods(returnTo)

  const providers = (data ?? []).filter((method) => method.methodType === 'OAUTH_REDIRECT')
  const isChinese = i18n.resolvedLanguage?.split('-')[0] === 'zh'
  const formatLoginLabel = (name: string) => {
    const label = t('loginButton.loginWith', { name })
    return isChinese ? label.replace(/\s+/g, '') : label
  }

  if (isLoading) {
    return (
      <div className="space-y-3">
        <Button className="w-full h-12" disabled>
          <div className="w-5 h-5 rounded-full animate-shimmer mr-3" />
          {t('loginButton.loading')}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {providers.map((provider) => (
        <Button
          key={provider.id}
          className="w-full h-12 text-base"
          variant="outline"
          onClick={() => {
            window.location.href = provider.actionUrl
          }}
        >
          <OAuthIcon provider={provider.provider} />
          {formatLoginLabel(provider.displayName)}
        </Button>
      ))}
    </div>
  )
}
