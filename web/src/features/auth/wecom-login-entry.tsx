import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/client'
import type { User } from '@/api/types'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'
import { Button } from '@/shared/ui/button'
import { toast } from '@/shared/lib/toast'

const WECOM_STATE_KEY = 'skillhub_wecom_state'
const WECOM_REDIRECT_KEY = 'skillhub_wecom_redirect'
const WECOM_QUICK_AUTH_TIMEOUT = 1800

type WeComPanel = {
  unmount: () => void
}

interface WeComLoginEntryProps {
  returnTo: string
}

function generateWeComState() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  if (globalThis.crypto?.getRandomValues) {
    const bytes = new Uint8Array(16)
    globalThis.crypto.getRandomValues(bytes)
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  }
  return `wecom-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function isHttpsUrl(url: string) {
  try {
    return new URL(url, window.location.origin).protocol === 'https:'
  } catch {
    return false
  }
}

function buildLoginSearch(returnTo: string) {
  return returnTo && returnTo !== '/dashboard' ? { returnTo } : { returnTo: '' }
}

export function WeComLoginEntry({ returnTo }: WeComLoginEntryProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/login' })
  const queryClient = useQueryClient()
  const panelContainerRef = useRef<HTMLDivElement | null>(null)
  const panelRef = useRef<WeComPanel | null>(null)
  const quickAuthTimerRef = useRef<number | null>(null)
  const [panelVisible, setPanelVisible] = useState(false)
  const [fallbacking, setFallbacking] = useState(false)
  const [processingCallback, setProcessingCallback] = useState(false)

  const { data: config } = useQuery({
    queryKey: ['auth', 'wecom', 'config'],
    queryFn: authApi.getWeComConfig,
    staleTime: 60_000,
  })

  const canUseQuickAuth = useMemo(() => Boolean(
    config?.enabled
      && config.corpId
      && config.agentId
      && config.redirectUri
      && window.location.protocol === 'https:'
      && isHttpsUrl(config.redirectUri),
  ), [config])

  const loginMutation = useMutation({
    mutationFn: (code: string) => authApi.weComLogin(code),
    onSuccess: (user: User) => {
      clearSessionScopedQueries(queryClient)
      queryClient.setQueryData<User | null>(['auth', 'me'], user)
    },
  })

  function clearQuickAuthTimer() {
    if (quickAuthTimerRef.current !== null) {
      window.clearTimeout(quickAuthTimerRef.current)
      quickAuthTimerRef.current = null
    }
  }

  function clearPanel() {
    clearQuickAuthTimer()
    panelRef.current?.unmount()
    panelRef.current = null
    setPanelVisible(false)
  }

  function saveRedirectContext(state: string) {
    sessionStorage.setItem(WECOM_STATE_KEY, state)
    sessionStorage.setItem(WECOM_REDIRECT_KEY, returnTo)
  }

  async function redirectToQrLogin(message?: string) {
    clearPanel()
    if (message) {
      toast.info(message)
    }
    setFallbacking(true)
    try {
      const state = generateWeComState()
      saveRedirectContext(state)
      const response = await authApi.getWeComAuthorizeUrl(state)
      window.location.href = response.url
    } finally {
      setFallbacking(false)
    }
  }

  async function finishWeComLogin(code: string, redirect: string) {
    clearPanel()
    setProcessingCallback(true)
    try {
      await loginMutation.mutateAsync(code)
      sessionStorage.removeItem(WECOM_STATE_KEY)
      sessionStorage.removeItem(WECOM_REDIRECT_KEY)
      await navigate({ to: redirect })
    } catch (error) {
      sessionStorage.removeItem(WECOM_STATE_KEY)
      sessionStorage.removeItem(WECOM_REDIRECT_KEY)
      toast.error(error instanceof Error ? error.message : t('login.wecomFailed'))
      await navigate({ to: '/login', search: buildLoginSearch(redirect) })
    } finally {
      setProcessingCallback(false)
    }
  }

  async function mountQuickAuthPanel() {
    if (!config) {
      return
    }

    clearPanel()
    setPanelVisible(true)
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))

    if (!panelContainerRef.current) {
      await redirectToQrLogin(t('login.wecomQuickUnavailable'))
      return
    }

    try {
      const ww = await import('@wecom/jssdk')
      quickAuthTimerRef.current = window.setTimeout(() => {
        void redirectToQrLogin(t('login.wecomQuickTimeout'))
      }, WECOM_QUICK_AUTH_TIMEOUT)

      panelRef.current = ww.createWWLoginPanel({
        el: panelContainerRef.current,
        params: {
          login_type: ww.WWLoginType.corpApp,
          appid: config.corpId,
          agentid: config.agentId,
          redirect_uri: config.redirectUri,
          state: generateWeComState(),
          redirect_type: ww.WWLoginRedirectType.callback,
          panel_size: ww.WWLoginPanelSizeType.middle,
        },
        onCheckWeComLogin({ isWeComLogin }) {
          clearQuickAuthTimer()
          if (!isWeComLogin) {
            void redirectToQrLogin(t('login.wecomDesktopNotDetected'))
          }
        },
        onLoginSuccess({ code }) {
          clearQuickAuthTimer()
          void finishWeComLogin(code, returnTo)
        },
        onLoginFail() {
          clearQuickAuthTimer()
          void redirectToQrLogin(t('login.wecomQuickUnavailable'))
        },
      })
    } catch {
      clearPanel()
      await redirectToQrLogin(t('login.wecomQuickUnavailable'))
    }
  }

  async function handleWeComLogin() {
    if (!config?.enabled) {
      toast.warning(t('login.wecomNotConfigured'))
      return
    }
    if (canUseQuickAuth) {
      await mountQuickAuthPanel()
      return
    }
    await redirectToQrLogin(t('login.wecomQrFallback'))
  }

  useEffect(() => {
    const code = (search.code || search.auth_code || '').trim()
    if (!code) {
      return
    }

    const expectedState = sessionStorage.getItem(WECOM_STATE_KEY)
    const savedRedirect = sessionStorage.getItem(WECOM_REDIRECT_KEY) || returnTo
    if (!expectedState || !search.state || search.state !== expectedState) {
      sessionStorage.removeItem(WECOM_STATE_KEY)
      sessionStorage.removeItem(WECOM_REDIRECT_KEY)
      toast.error(t('login.wecomStateMismatch'))
      void navigate({ to: '/login', search: buildLoginSearch(savedRedirect) })
      return
    }

    void finishWeComLogin(code, savedRedirect)
    // Callback should be consumed once for the current URL.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => () => {
    clearPanel()
  }, [])

  if (!config && !search.code && !search.auth_code) {
    return null
  }

  if (processingCallback) {
    return (
      <div className="rounded-xl border border-border bg-muted/30 px-4 py-3 text-center text-sm text-muted-foreground">
        {t('login.wecomProcessing')}
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <Button
        type="button"
        className="w-full h-12 text-base bg-[#07c160] text-white hover:bg-[#06ad56]"
        disabled={!config || fallbacking || loginMutation.isPending}
        onClick={handleWeComLogin}
      >
        {panelVisible ? t('login.wecomRetry') : t('login.wecomLogin')}
      </Button>
      {panelVisible ? (
        <div className="rounded-xl border border-border bg-muted/30 p-3">
          <p className="mb-3 text-center text-xs text-muted-foreground">{t('login.wecomQuickHint')}</p>
          <div ref={panelContainerRef} className="flex min-h-[360px] justify-center" />
        </div>
      ) : null}
      {fallbacking ? (
        <p className="text-center text-xs text-muted-foreground">{t('login.wecomFallbacking')}</p>
      ) : null}
      <div className="relative py-1 text-center text-xs text-muted-foreground">
        <span className="relative z-10 bg-card px-3">{t('login.or')}</span>
        <div className="absolute left-0 right-0 top-1/2 h-px bg-border" />
      </div>
    </div>
  )
}
