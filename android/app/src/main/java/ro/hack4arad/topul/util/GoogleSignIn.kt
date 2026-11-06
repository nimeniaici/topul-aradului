package ro.hack4arad.topul.util

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import ro.hack4arad.topul.BuildConfig

/** „Continuă cu Google” prin Credential Manager. Întoarce ID token-ul pe care îl verifică serverul. */
object GoogleSignIn {
    /** Butonul apare doar dacă GOOGLE_WEB_CLIENT_ID e setat în local.properties. */
    val enabled: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    class Cancelled : Exception()

    /** @param context trebuie să fie Activity-ul (pentru fereastra Google). */
    suspend fun idToken(context: Context): String {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (e: NoCredentialException) {
            throw IllegalStateException("Nu există niciun cont Google pe telefon. Adaugă unul din Setări.")
        } catch (e: GetCredentialException) {
            throw IllegalStateException("Login-ul Google a eșuat: ${e.message ?: e.type}")
        }
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Răspuns neașteptat de la Google.")
    }
}
