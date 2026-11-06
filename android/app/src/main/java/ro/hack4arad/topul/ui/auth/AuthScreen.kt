package ro.hack4arad.topul.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.ForgotIn
import ro.hack4arad.topul.data.GoogleIn
import ro.hack4arad.topul.data.LoginIn
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.RegisterIn
import ro.hack4arad.topul.data.ResetIn
import ro.hack4arad.topul.data.TokenOut
import ro.hack4arad.topul.ui.components.Pill
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.MintDark
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.util.GoogleSignIn
import ro.hack4arad.topul.util.userMessage

class AuthViewModel : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    // resetare parolă: null = închis, 1 = cer emailul, 2 = introduc codul
    var resetStep by mutableStateOf<Int?>(null)
    var resetInfo by mutableStateOf<String?>(null)
        private set
    var resetError by mutableStateOf<String?>(null)

    private fun authenticate(block: suspend () -> TokenOut) {
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { block() }
                .onSuccess { Network.session.save(it) }
                .onFailure { if (it !is GoogleSignIn.Cancelled) error = it.userMessage() }
            loading = false
        }
    }

    fun submit(register: Boolean, name: String, email: String, password: String) = authenticate {
        if (register) Network.api.register(RegisterIn(name.trim(), email.trim(), password))
        else Network.api.login(LoginIn(email.trim(), password))
    }

    fun google(idToken: suspend () -> String) = authenticate { Network.api.googleLogin(GoogleIn(idToken())) }

    fun sendCode(email: String) {
        viewModelScope.launch {
            loading = true
            resetError = null
            runCatching { Network.api.forgotPassword(ForgotIn(email.trim())) }
                .onSuccess { resetInfo = it.detail; resetStep = 2 }
                .onFailure { resetError = it.userMessage() }
            loading = false
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String) {
        viewModelScope.launch {
            loading = true
            resetError = null
            runCatching { Network.api.resetPassword(ResetIn(email.trim(), code.trim(), newPassword)) }
                .onSuccess { resetStep = null; Network.session.save(it) }
                .onFailure { resetError = it.userMessage() }
            loading = false
        }
    }
}

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var register by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = !vm.loading && email.isNotBlank() && password.length >= 6 && (!register || name.trim().length >= 2)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text("👑", fontSize = 84.sp)
        Text(
            "TOPUL ARADULUI",
            style = MaterialTheme.typography.headlineMedium,
            color = Gold, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        )
        Text("Cucerește orașul. Devino Primar.", color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("📍 Check-in", Pink, Color.White)
            Pill("⚔️ Dueluri", Electric, Color.White)
            Pill("🏆 Topuri", Mint, MintDark)
        }
        Spacer(Modifier.height(28.dp))

        if (GoogleSignIn.enabled) {
            Button(
                onClick = { vm.google { GoogleSignIn.idToken(context) } },
                enabled = !vm.loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("G", color = Electric, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.size(10.dp))
                Text("Continuă cu Google", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = Track)
                Text("  sau cu email  ", color = TextMuted, fontSize = 13.sp)
                HorizontalDivider(Modifier.weight(1f), color = Track)
            }
        }

        val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
        if (register) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nume") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = next,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = next,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Parolă (min. 6 caractere)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                if (canSubmit) vm.submit(register, name, email, password)
            }),
            modifier = Modifier.fillMaxWidth(),
        )

        vm.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { vm.submit(register, name, email, password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(if (register) "Începe jocul" else "Intră în joc", fontSize = 17.sp)
        }
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = { register = !register; vm.error = null }, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(if (register) "Ai deja cont? Intră" else "Nu ai cont? Creează unul", color = Lilac)
            }
            if (!register) {
                TextButton(onClick = { vm.resetStep = 1; vm.resetError = null }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("Ai uitat parola?", color = Gold)
                }
            }
        }
    }

    if (vm.resetStep != null) ResetPasswordDialog(vm, initialEmail = email)
}

@Composable
private fun ResetPasswordDialog(vm: AuthViewModel, initialEmail: String) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    val step = vm.resetStep ?: return
    AlertDialog(
        onDismissRequest = { vm.resetStep = null },
        title = { Text(if (step == 1) "🔑 Resetează parola" else "📬 Verifică emailul") },
        text = {
            Column {
                if (step == 1) {
                    Text("Îți trimitem un cod de 6 cifre pe email.", color = Lilac)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(vm.resetInfo ?: "", color = Lilac)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
                        label = { Text("Codul de 6 cifre") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("Parola nouă (min. 6)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                    )
                }
                vm.resetError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(onClick = { vm.sendCode(email) }, enabled = !vm.loading && email.contains("@")) { Text("Trimite codul") }
            } else {
                Button(
                    onClick = { vm.resetPassword(email, code, newPassword) },
                    enabled = !vm.loading && code.length == 6 && newPassword.length >= 6,
                ) { Text("Schimbă parola") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.resetStep = null }) { Text("Renunță", color = TextMuted) } },
    )
}
