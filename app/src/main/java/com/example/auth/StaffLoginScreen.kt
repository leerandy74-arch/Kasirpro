package com.example.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.localization.AppLanguage
import com.example.localization.AppStrings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffLoginScreen(
    strings: AppStrings,
    currentLanguage: AppLanguage,
    isBootstrapping: Boolean,
    isLoggingIn: Boolean,
    loginError: String?,
    onLanguageChange: (AppLanguage) -> Unit,
    onLoginSubmit: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("admin.pusat") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EmeraldDeep,
                        Color(0xFF0A2829),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Brand & Multi-language Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldBright.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = strings.appTitle,
                                tint = EmeraldBright
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = lang == currentLanguage,
                            onClick = { onLanguageChange(lang) },
                            label = {
                                Text(
                                    text = lang.shortBadge,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("login_lang_${lang.code}")
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Hero Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(172.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pos_hero_1794924209364),
                        contentDescription = strings.appTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, EmeraldDeep.copy(alpha = 0.88f))
                                )
                            )
                    )
                    Surface(
                        color = AmberGold,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF1F1600),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "AES-256-GCM E2EE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1F1600),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = strings.appSubtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Username & Password Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.signInTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.signInSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Quick-select default role presets
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Isi Cepat Akun Default:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StaffPresetChip("admin.pusat", "pusat123") { u, p ->
                                    username = u
                                    password = p
                                }
                                StaffPresetChip("admin.cabang", "cabang123") { u, p ->
                                    username = u
                                    password = p
                                }
                                StaffPresetChip("kasir.01", "kasir123") { u, p ->
                                    username = u
                                    password = p
                                }
                            }
                            Text(
                                text = "admin.pusat (pusat123) • admin.cabang (cabang123) • kasir.01 (kasir123)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(strings.usernameLabel) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_login_username")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.passwordLabel) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isBootstrapping && !isLoggingIn) {
                                    onLoginSubmit(username, password)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_login_password")
                    )

                    if (!loginError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = loginError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onLoginSubmit(username, password) },
                        enabled = !isBootstrapping && !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("staff_login_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isBootstrapping || isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.loginButton,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Text(
                        text = strings.signInSecurityBadge,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffPresetChip(
    user: String,
    pass: String,
    onFill: (String, String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clickable { onFill(user, pass) }
            .testTag("preset_$user")
    ) {
        Text(
            text = user,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
