import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const INACTIVITY_TIMEOUT_MS = 60 * 60 * 1000

let timer: ReturnType<typeof setTimeout> | null = null
let activityListeners: (() => void)[] = []

export function useInactivityTracker() {
  const router = useRouter()
  const isInactive = ref(false)

  function resetTimer() {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      isInactive.value = true
      localStorage.removeItem('token')
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('currentUser')
      ElMessage.warning('因长时间未操作，系统已自动登出')
      router.push('/login')
    }, INACTIVITY_TIMEOUT_MS)
  }

  function setupListeners() {
    const events = ['mousedown', 'keydown', 'mousemove', 'scroll', 'touchstart']
    for (const evt of events) {
      const handler = () => resetTimer()
      window.addEventListener(evt, handler)
      activityListeners.push(() => window.removeEventListener(evt, handler))
    }
    resetTimer()
  }

  onMounted(() => {
    if (!localStorage.getItem('token')) return
    setupListeners()
  })

  onUnmounted(() => {
    if (timer) clearTimeout(timer)
    for (const cleanup of activityListeners) cleanup()
    activityListeners = []
  })

  return { isInactive }
}
