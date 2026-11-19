package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.model.Branch
import com.example.model.PosTransaction
import com.example.model.StaffAccount
import com.example.model.StaffRole
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MintSoft
import java.text.NumberFormat
import java.util.Locale

fun formatIdr(amount: Double): String {
    val fmt = NumberFormat.getNumberInstance(Locale("in", "ID")).apply {
        maximumFractionDigits = 0
    }
    return "Rp ${fmt.format(amount)}"
}

@Composable
fun TopWorkspaceHeader(
    strings: AppStrings,
    storeName: String,
    activeBranch: Branch?,
    branches: List<Branch>,
    activeStaff: StaffAccount?,
    e2eeUnlocked: Boolean,
    onSelectBranch: (String) -> Unit,
    onOpenRoleSwitcher: () -> Unit,
    onSignOut: () -> Unit
) {
    var branchMenuOpen by remember { mutableStateOf(false) }
    val canSwitchBranch = activeStaff?.staffRole == StaffRole.CENTRAL_ADMIN && branches.size > 1

    Surface(
        color = EmeraldDeep,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = storeName.ifBlank { strings.appTitle },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        // Live Sync Dot
                        Surface(
                            color = EmeraldBright.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldBright)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Branch selector pill
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = canSwitchBranch) { branchMenuOpen = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = MintSoft,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = activeBranch?.name ?: "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MintSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (canSwitchBranch) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MintSoft,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = branchMenuOpen,
                            onDismissRequest = { branchMenuOpen = false }
                        ) {
                            branches.forEach { branch ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${branch.name} (${branch.code})")
                                    },
                                    onClick = {
                                        branchMenuOpen = false
                                        onSelectBranch(branch.id)
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // E2EE status pill
                    val shieldColor by animateColorAsState(
                        if (e2eeUnlocked) EmeraldBright.copy(alpha = 0.22f) else AmberGold.copy(alpha = 0.22f),
                        label = "e2ee"
                    )
                    Surface(
                        color = shieldColor,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (e2eeUnlocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (e2eeUnlocked) strings.e2eeActiveBadge else strings.e2eeLockedBadge,
                            tint = if (e2eeUnlocked) EmeraldBright else AmberGold,
                            modifier = Modifier.padding(6.dp).size(16.dp)
                        )
                    }

                    // Active Role Chip
                    Surface(
                        color = when (activeStaff?.staffRole) {
                            StaffRole.CENTRAL_ADMIN -> AmberGold
                            StaffRole.BRANCH_ADMIN -> EmeraldBright
                            else -> Color.White.copy(alpha = 0.16f)
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clickable(onClick = onOpenRoleSwitcher)
                            .testTag("role_switcher_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeStaff?.staffRole == StaffRole.CASHIER) {
                                    Icons.Default.Badge
                                } else {
                                    Icons.Default.AdminPanelSettings
                                },
                                contentDescription = strings.switchRoleTitle,
                                tint = if (activeStaff?.staffRole == StaffRole.CASHIER) Color.White else Color(0xFF0A241E),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = when (activeStaff?.staffRole) {
                                    StaffRole.CENTRAL_ADMIN -> strings.roleCentralAdmin
                                    StaffRole.BRANCH_ADMIN -> strings.roleBranchAdmin
                                    StaffRole.CASHIER -> strings.roleCashier
                                    null -> "..."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (activeStaff?.staffRole == StaffRole.CASHIER) Color.White else Color(0xFF0A241E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(40.dp).testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = strings.signOut,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoleBadge(role: StaffRole, strings: AppStrings) {
    val (bg, fg, label) = when (role) {
        StaffRole.CENTRAL_ADMIN -> Triple(AmberSoft, Color(0xFF78350F), strings.roleCentralAdmin)
        StaffRole.BRANCH_ADMIN -> Triple(MintSoft, EmeraldDeep, strings.roleBranchAdmin)
        StaffRole.CASHIER -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, strings.roleCashier)
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun RoleSwitchDialog(
    strings: AppStrings,
    adminOnly: Boolean,
    onDismiss: () -> Unit,
    onAuthenticate: (String, String) -> Boolean
) {
    var username by remember { mutableStateOf(if (adminOnly) "admin.pusat" else "kasir.01") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (adminOnly) strings.adminLockTitle else strings.switchRoleTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.adminLockSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick fill credential presets for testing & demo convenience
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Akun Default (PBKDF2):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QuickRoleChip("admin.pusat", "pusat123") { u, p -> username = u; password = p; errorMsg = null }
                            QuickRoleChip("admin.cabang", "cabang123") { u, p -> username = u; password = p; errorMsg = null }
                            if (!adminOnly) {
                                QuickRoleChip("kasir.01", "kasir123") { u, p -> username = u; password = p; errorMsg = null }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMsg = null },
                    label = { Text(strings.usernameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("role_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text(strings.passwordLabel) },
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
                    modifier = Modifier.fillMaxWidth().testTag("role_password_input")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onAuthenticate(username, password)) {
                        onDismiss()
                    } else {
                        errorMsg = strings.invalidCredentials
                    }
                },
                modifier = Modifier.testTag("role_unlock_button")
            ) {
                Text(strings.unlockButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        }
    )
}

@Composable
private fun QuickRoleChip(
    user: String,
    pass: String,
    onFill: (String, String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onFill(user, pass) }
    ) {
        Text(
            text = user,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun ReceiptSuccessDialog(
    strings: AppStrings,
    tx: PosTransaction,
    receiptPreview: String,
    customerPhone: String,
    onPrintBluetooth: () -> Unit,
    onSendWhatsapp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldBright,
                modifier = Modifier.size(44.dp)
            )
        },
        title = {
            Text(
                text = strings.checkoutSuccessTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = receiptPreview,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedButton(
                    onClick = onPrintBluetooth,
                    modifier = Modifier.fillMaxWidth().testTag("receipt_print_bluetooth_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.printReceiptButton)
                }

                if (customerPhone.isNotBlank()) {
                    Button(
                        onClick = onSendWhatsapp,
                        modifier = Modifier.fillMaxWidth().testTag("receipt_send_whatsapp_btn")
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.sendWhatsappButton)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("receipt_done_btn")) {
                Text(strings.doneButton)
            }
        }
    )
}
