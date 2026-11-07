package ro.hack4arad.topul.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Ca `runCatching` din Kotlin, dar NU prinde anularea unei corutine (când ieși dintr-un ecran
 * în timp ce se încarcă ceva). Altfel utilizatorul vedea „StandaloneCoroutine was cancelled”.
 * Importată explicit în fiecare fișier, ascunde versiunea standard.
 */
inline fun <R> runCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
