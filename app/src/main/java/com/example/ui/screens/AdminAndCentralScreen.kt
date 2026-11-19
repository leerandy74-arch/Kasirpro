package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.integrations.BluetoothPrinterDevice
import com.example.integrations.WhatsAppNotifier
import com.example.localization.AppLanguage
import com.example.localization.AppStrings
import com.example.localization.stringsFor
import com.example.model.AuditLog
import com.example.model.Branch
import com.example.model.CloudBackupRecord
import com.example.model.PosTransaction
import com.example.model.StaffAccount
import com.example.model.StaffRole
import com.example.model.TransactionStatus
import com.example.model.WorkspaceConfig
import com.example.security.CryptoManager
import com.example.ui.components.RoleBadge
import com.example.ui.components.formatIdr
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MintSoft
import com.example.viewmodel.BranchMetric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAndCentralScreen(
    strings: AppStrings,
    currentLanguage: AppLanguage,
    workspace: WorkspaceConfig?,
    activeStaff: StaffAccount?,
    branches: List<Branch>,
    branchMetrics: List<BranchMetric>,
    staffList: List<StaffAccount>,
    auditLogs: List<AuditLog>,
    cloudBackups: List<CloudBackupRecord>,
    latestTx: PosTransaction?,
    e2eeKey: SecretKey?,
    printerMac: String,
    printerName: String,
    bondedPrinters: List<BluetoothPrinterDevice>,
    googleCloudEmail: String?,
    isBusy: Boolean,
    onAuthenticateAdmin: (String, String) -> Boolean,
    onCreateBranch: (String, String, String, String) -> Unit,
    onCreateStaff: (String, String, StaffRole, String, String) -> Unit,
    onChangeStaffPassword: (StaffAccount, String) -> Unit,
    onToggleStaffActive: (StaffAccount, Boolean) -> Unit,
    onUnlockVault: (String) -> Boolean,
    onLockVault: () -> Unit,
    onTriggerBackup: () -> Unit,
    onRestoreBackup: (CloudBackupRecord) -> Unit,
    onConnectGoogleCloud: () -> Unit,
    onDisconnectGoogleCloud: () -> Unit,
    onSyncCloudNow: () -> Unit,
    onUpdateSettings: (String, String, String, Long) -> Unit,
    onRefreshPrinters: () -> Unit,
    onSelectPrinter: (BluetoothPrinterDevice) -> Unit,
    onTestPrint: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onShowMessage: (String) -> Unit
) {
    // Role-based access control: Cashier must enter Admin Password to unlock Admin Dashboard
    if (activeStaff == null || activeStaff.staffRole == StaffRole.CASHIER) {
        AdminPasswordLockGate(
            strings = strings,
            onAuthenticateAdmin = onAuthenticateAdmin
        )
        return
    }

    val isCentralAdmin = activeStaff.staffRole == StaffRole.CENTRAL_ADMIN
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.centralMonitorTitle) },
                modifier = Modifier.testTag("admin_tab_central")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.staffPasswordTitle) },
                modifier = Modifier.testTag("admin_tab_staff")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(strings.cloudBackupTitle) },
                modifier = Modifier.testTag("admin_tab_backup")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(strings.printerAndSupportTitle) },
                modifier = Modifier.testTag("admin_tab_settings")
            )
        }

        when (selectedTab) {
            0 -> CentralMonitorTab(
                strings = strings,
                isCentralAdmin = isCentralAdmin,
                activeBranchId = activeStaff.branchId,
                branchMetrics = branchMetrics,
                auditLogs = auditLogs,
                onCreateBranch = onCreateBranch
            )
            1 -> StaffAndPasswordTab(
                strings = strings,
                isCentralAdmin = isCentralAdmin,
                activeStaff = activeStaff,
                branches = branches,
                staffList = staffList,
                onCreateStaff = onCreateStaff,
                onChangeStaffPassword = onChangeStaffPassword,
                onToggleStaffActive = onToggleStaffActive
            )
            2 -> CloudBackupAndE2eeTab(
                strings = strings,
                workspace = workspace,
                e2eeKey = e2eeKey,
                cloudBackups = cloudBackups,
                latestTx = latestTx,
                googleCloudEmail = googleCloudEmail,
                isBusy = isBusy,
                onUnlockVault = onUnlockVault,
                onLockVault = onLockVault,
                onTriggerBackup = onTriggerBackup,
                onRestoreBackup = onRestoreBackup,
                onConnectGoogleCloud = onConnectGoogleCloud,
                onDisconnectGoogleCloud = onDisconnectGoogleCloud,
                onSyncCloudNow = onSyncCloudNow,
                onUpdateBackupInterval = { hrs ->
                    workspace?.let {
                        onUpdateSettings(it.storeName, it.qrisMerchantId, it.qrisMerchantName, hrs)
                    }
                },
                onShowMessage = onShowMessage
            )
            else -> PrinterLanguageSupportTab(
                strings = strings,
                currentLanguage = currentLanguage,
                workspace = workspace,
                printerMac = printerMac,
                printerName = printerName,
                bondedPrinters = bondedPrinters,
                onUpdateSettings = onUpdateSettings,
                onRefreshPrinters = onRefreshPrinters,
                onSelectPrinter = onSelectPrinter,
                onTestPrint = onTestPrint,
                onLanguageChange = onLanguageChange,
                onShowMessage = onShowMessage
            )
        }
    }
}

@Composable
private fun AdminPasswordLockGate(
    strings: AppStrings,
    onAuthenticateAdmin: (String, String) -> Boolean
) {
    var username by remember { mutableStateOf("admin.pusat") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = AmberSoft,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = EmeraldDeep,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(strings.adminLockTitle, style = MaterialTheme.typography.titleLarge)
                Text(
                    strings.adminLockSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = username == "admin.pusat",
                        onClick = { username = "admin.pusat"; password = ""; errorMsg = null },
                        label = { Text("admin.pusat") }
                    )
                    FilterChip(
                        selected = username == "admin.cabang",
                        onClick = { username = "admin.cabang"; password = ""; errorMsg = null },
                        label = { Text("admin.cabang") }
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMsg = null },
                    label = { Text(strings.usernameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_gate_username")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text(strings.passwordLabel) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("admin_gate_password")
                )

                Text(
                    text = "Default: admin.pusat (pusat123) • admin.cabang (cabang123)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        if (!onAuthenticateAdmin(username, password)) {
                            errorMsg = strings.invalidCredentials
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_gate_unlock_btn")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.unlockButton)
                }
            }
        }
    }
}

@Composable
private fun CentralMonitorTab(
    strings: AppStrings,
    isCentralAdmin: Boolean,
    activeBranchId: String,
    branchMetrics: List<BranchMetric>,
    auditLogs: List<AuditLog>,
    onCreateBranch: (String, String, String, String) -> Unit
) {
    var showAddBranch by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }

    val visibleBranches = remember(branchMetrics, isCentralAdmin, activeBranchId) {
        if (isCentralAdmin) branchMetrics else branchMetrics.filter { it.branch.id == activeBranchId }
    }
    val visibleLogs = remember(auditLogs, isCentralAdmin, activeBranchId) {
        val base = if (isCentralAdmin) auditLogs else auditLogs.filter { it.branchId == activeBranchId }
        base.take(40)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCentralAdmin) strings.branchPerformanceTitle else strings.roleBranchAdmin,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isCentralAdmin) {
                            "Mode Admin Pusat: memantau seluruh cabang secara real-time"
                        } else {
                            "Mode Admin Cabang: khusus cabang aktif"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCentralAdmin) {
                    Button(onClick = { showAddBranch = true }, modifier = Modifier.testTag("add_branch_btn")) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addBranchButton)
                    }
                }
            }
        }

        items(visibleBranches, key = { it.branch.id }) { bm ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(bm.branch.name, style = MaterialTheme.typography.titleSmall)
                            Surface(color = MintSoft, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    bm.branch.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldDeep,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "${bm.txCount} transaksi • ${bm.lowStockCount} stok menipis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatIdr(bm.revenue),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Text(
                "Log Aktivitas Kasir & Admin (Real-Time)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items(visibleLogs, key = { it.id }) { log ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${log.actorName} (${log.actorRole})",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            dateFmt.format(log.createdAt?.toDate() ?: Date()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showAddBranch) {
        AddBranchDialog(
            strings = strings,
            onDismiss = { showAddBranch = false },
            onSave = { n, c, a, p ->
                showAddBranch = false
                onCreateBranch(n, c, a, p)
            }
        )
    }
}

@Composable
private fun StaffAndPasswordTab(
    strings: AppStrings,
    isCentralAdmin: Boolean,
    activeStaff: StaffAccount,
    branches: List<Branch>,
    staffList: List<StaffAccount>,
    onCreateStaff: (String, String, StaffRole, String, String) -> Unit,
    onChangeStaffPassword: (StaffAccount, String) -> Unit,
    onToggleStaffActive: (StaffAccount, Boolean) -> Unit
) {
    var showAddStaff by remember { mutableStateOf(false) }
    var passwordTarget by remember { mutableStateOf<StaffAccount?>(null) }
    val branchMap = remember(branches) { branches.associateBy { it.id } }

    // Central Admin sees & manages ALL users across all branches; Branch Admin sees branch users
    val visibleStaff = remember(staffList, isCentralAdmin, activeStaff) {
        if (isCentralAdmin) staffList else staffList.filter { it.branchId == activeStaff.branchId }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.staffPasswordTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isCentralAdmin) {
                            "Admin Pusat dapat mengubah password seluruh user (Admin Cabang & Kasir)."
                        } else {
                            "Daftar staf cabang aktif."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCentralAdmin) {
                    Button(onClick = { showAddStaff = true }, modifier = Modifier.testTag("add_staff_btn")) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addStaffButton)
                    }
                }
            }
        }

        items(visibleStaff, key = { it.id }) { user ->
            val canChangePass = isCentralAdmin || user.id == activeStaff.id
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("staff_card_${user.username}")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.fullName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "@${user.username} • ${branchMap[user.branchId]?.name ?: user.branchId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RoleBadge(user.staffRole, strings)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canChangePass) {
                            OutlinedButton(
                                onClick = { passwordTarget = user },
                                modifier = Modifier.testTag("change_pass_btn_${user.username}")
                            ) {
                                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.changePasswordButton)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        if (isCentralAdmin && user.id != activeStaff.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (user.active) "Aktif" else "Nonaktif", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = user.active,
                                    onCheckedChange = { onToggleStaffActive(user, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStaff) {
        AddStaffDialog(
            strings = strings,
            branches = branches,
            onDismiss = { showAddStaff = false },
            onSave = { u, fn, r, b, p ->
                showAddStaff = false
                onCreateStaff(u, fn, r, b, p)
            }
        )
    }

    passwordTarget?.let { target ->
        ChangePasswordDialog(
            strings = strings,
            target = target,
            onDismiss = { passwordTarget = null },
            onSave = { newPass ->
                passwordTarget = null
                onChangeStaffPassword(target, newPass)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudBackupAndE2eeTab(
    strings: AppStrings,
    workspace: WorkspaceConfig?,
    e2eeKey: SecretKey?,
    cloudBackups: List<CloudBackupRecord>,
    latestTx: PosTransaction?,
    googleCloudEmail: String?,
    isBusy: Boolean,
    onUnlockVault: (String) -> Boolean,
    onLockVault: () -> Unit,
    onTriggerBackup: () -> Unit,
    onRestoreBackup: (CloudBackupRecord) -> Unit,
    onConnectGoogleCloud: () -> Unit,
    onDisconnectGoogleCloud: () -> Unit,
    onSyncCloudNow: () -> Unit,
    onUpdateBackupInterval: (Long) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var vaultPassInput by remember { mutableStateOf("") }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // E2EE Vault Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (e2eeKey != null) EmeraldBright else AmberGold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.e2eeVaultTitle, style = MaterialTheme.typography.titleMedium)
                    }

                    if (e2eeKey != null) {
                        Surface(color = MintSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Status: TERBUKA (AES-256-GCM + Android Keystore)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldDeep
                                )
                                Text(
                                    "Key Fingerprint: ${CryptoManager.keyFingerprint(e2eeKey)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = EmeraldDeep
                                )
                                if (latestTx != null) {
                                    Text(
                                        "Sample Ciphertext (${latestTx.receiptNumber}): ${latestTx.encryptedPayload.take(36)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = EmeraldDeep
                                    )
                                }
                            }
                        }
                        OutlinedButton(onClick = onLockVault) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kunci Brankas E2EE")
                        }
                    } else {
                        OutlinedTextField(
                            value = vaultPassInput,
                            onValueChange = { vaultPassInput = it },
                            label = { Text(strings.vaultPassphraseLabel) },
                            placeholder = { Text("kasirpro-e2ee-default") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (onUnlockVault(vaultPassInput)) {
                                    vaultPassInput = ""
                                    onShowMessage("Kunci E2EE berhasil diverifikasi & dibuka.")
                                } else {
                                    onShowMessage("Passphrase E2EE salah.")
                                }
                            }
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.unlockVaultButton)
                        }
                    }
                }
            }
        }

        // Cloud Backup Schedule & Trigger
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.cloudBackupTitle, style = MaterialTheme.typography.titleMedium)
                    }

                    val lastMs = workspace?.lastBackupEpochMs ?: 0L
                    Text(
                        text = if (lastMs > 0L) {
                            "Backup Terakhir: ${dateFmt.format(Date(lastMs))}"
                        } else {
                            "Backup Terakhir: Menunggu jadwal pertama"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(strings.autoBackupIntervalLabel, style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1L, 6L, 12L, 24L).forEach { hrs ->
                            FilterChip(
                                selected = (workspace?.autoBackupIntervalHours ?: 12L) == hrs,
                                onClick = { onUpdateBackupInterval(hrs) },
                                label = { Text("${hrs}h") }
                            )
                        }
                    }

                    Button(
                        onClick = onTriggerBackup,
                        enabled = !isBusy && e2eeKey != null,
                        modifier = Modifier.fillMaxWidth().testTag("manual_cloud_backup_btn")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.backupNowButton)
                    }
                }
            }
        }

        // Optional Google Cloud Sync card (never required for login)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.cloudSyncOptionalTitle, style = MaterialTheme.typography.titleMedium)
                    if (googleCloudEmail.isNullOrBlank()) {
                        Text(
                            text = "Aplikasi berjalan dengan login username & database lokal terenkripsi. Hubungkan akun Google hanya jika ingin mencadangkan & sinkronisasi ke Firestore Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onConnectGoogleCloud,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("connect_google_cloud_btn")
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.connectGoogleCloudButton)
                        }
                    } else {
                        Surface(color = MintSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Terhubung: $googleCloudEmail",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldDeep,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onSyncCloudNow,
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).testTag("sync_cloud_now_btn")
                            ) {
                                Text(strings.syncCloudNowButton)
                            }
                            OutlinedButton(
                                onClick = onDisconnectGoogleCloud,
                                enabled = !isBusy,
                                modifier = Modifier.testTag("disconnect_google_cloud_btn")
                            ) {
                                Text(strings.disconnectGoogleCloudButton)
                            }
                        }
                    }
                }
            }
        }

        items(cloudBackups, key = { it.id }) { bkp ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${bkp.backupType} • ${bkp.recordCount} record",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "${dateFmt.format(bkp.createdAt?.toDate() ?: Date())} • ${bkp.createdBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "SHA-256: ${bkp.checksumSha256.take(16)}...",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    OutlinedButton(
                        onClick = { onRestoreBackup(bkp) },
                        enabled = !isBusy && e2eeKey != null
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.restoreBackupButton)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrinterLanguageSupportTab(
    strings: AppStrings,
    currentLanguage: AppLanguage,
    workspace: WorkspaceConfig?,
    printerMac: String,
    printerName: String,
    bondedPrinters: List<BluetoothPrinterDevice>,
    onUpdateSettings: (String, String, String, Long) -> Unit,
    onRefreshPrinters: () -> Unit,
    onSelectPrinter: (BluetoothPrinterDevice) -> Unit,
    onTestPrint: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var showPrinterPicker by remember { mutableStateOf(false) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants.values.all { it }
        if (ok) {
            onRefreshPrinters()
            showPrinterPicker = true
        } else {
            onShowMessage("Izin Bluetooth diperlukan untuk mencari & mencetak ke printer thermal.")
        }
    }

    var storeName by remember(workspace) { mutableStateOf(workspace?.storeName ?: "KasirPro") }
    var qrisId by remember(workspace) { mutableStateOf(workspace?.qrisMerchantId ?: "ID1020304050607") }
    var qrisName by remember(workspace) { mutableStateOf(workspace?.qrisMerchantName ?: "KASIRPRO") }

    // Multilingual Customer Support Hub state
    var supportLang by remember { mutableStateOf(currentLanguage) }
    val supportStrings = remember(supportLang) { stringsFor(supportLang) }
    var customerWaPhone by remember { mutableStateOf("") }
    var supportMessage by remember(supportLang) { mutableStateOf(supportStrings.whatsappSupportOrderReady) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Bluetooth Thermal Printer Integration
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Printer Struk Bluetooth (ESC/POS)", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = if (printerMac.isNotBlank()) {
                        "${strings.printerConnectedLabel}: $printerName ($printerMac)"
                    } else {
                        strings.printerDisconnectedLabel
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                btPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    )
                                )
                            } else {
                                btPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH))
                            }
                        },
                        modifier = Modifier.testTag("select_bt_printer_btn")
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.bluetoothConnectButton)
                    }
                    OutlinedButton(onClick = onTestPrint) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.testPrintButton)
                    }
                }
            }
        }

        // 2. Store & QRIS Merchant Settings
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Profil Toko & QRIS Merchant", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(value = storeName, onValueChange = { storeName = it }, label = { Text("Nama Toko") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qrisId, onValueChange = { qrisId = it }, label = { Text("QRIS NMID / Merchant ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qrisName, onValueChange = { qrisName = it }, label = { Text("Nama Merchant QRIS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        onUpdateSettings(
                            storeName,
                            qrisId,
                            qrisName,
                            workspace?.autoBackupIntervalHours ?: 12L
                        )
                    }
                ) {
                    Text(strings.saveButton)
                }
            }
        }

        // 3. Multi-Language App Switcher
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.languageTitle, style = MaterialTheme.typography.titleMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = lang == currentLanguage,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("settings_lang_${lang.code}")
                        )
                    }
                }
            }
        }

        // 4. Multilingual Customer Support WhatsApp Hub
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Chat, contentDescription = null, tint = EmeraldBright)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.customerSupportTitle, style = MaterialTheme.typography.titleMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = supportLang == lang,
                            onClick = {
                                supportLang = lang
                                supportMessage = stringsFor(lang).whatsappSupportOrderReady
                            },
                            label = { Text(lang.shortBadge) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = supportMessage == supportStrings.whatsappSupportOrderReady,
                        onClick = { supportMessage = supportStrings.whatsappSupportOrderReady },
                        label = { Text("Order Ready") }
                    )
                    FilterChip(
                        selected = supportMessage == supportStrings.whatsappSupportPromo,
                        onClick = { supportMessage = supportStrings.whatsappSupportPromo },
                        label = { Text("Promo") }
                    )
                    FilterChip(
                        selected = supportMessage == supportStrings.whatsappSupportFollowUp,
                        onClick = { supportMessage = supportStrings.whatsappSupportFollowUp },
                        label = { Text("Follow-Up") }
                    )
                }
                OutlinedTextField(
                    value = customerWaPhone,
                    onValueChange = { customerWaPhone = it },
                    label = { Text(strings.customerWhatsappOptional) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supportMessage,
                    onValueChange = { supportMessage = it },
                    label = { Text("Pesan Pelanggan") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        WhatsAppNotifier.openWhatsAppChat(context, customerWaPhone, supportMessage)
                            .onFailure { onShowMessage("Masukkan nomor WhatsApp pelanggan yang valid.") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.sendSupportMessageButton)
                }
            }
        }
    }

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text(strings.bluetoothConnectButton) },
            text = {
                if (bondedPrinters.isEmpty()) {
                    Text(
                        "Tidak ada perangkat Bluetooth terpasang (paired) yang ditemukan. Pasangkan printer thermal ESC/POS Anda di pengaturan Bluetooth Android terlebih dahulu.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        bondedPrinters.forEach { device ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPrinter(device)
                                        showPrinterPicker = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(device.name, style = MaterialTheme.typography.titleSmall)
                                    Text(device.address, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterPicker = false }) { Text(strings.cancelButton) }
            }
        )
    }
}

@Composable
private fun AddBranchDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addBranchButton) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Cabang") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Kode (contoh: JKT-04)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Alamat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telepon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && code.isNotBlank()) onSave(name, code, address, phone) }) {
                Text(strings.saveButton)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelButton) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStaffDialog(
    strings: AppStrings,
    branches: List<Branch>,
    onDismiss: () -> Unit,
    onSave: (String, String, StaffRole, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(StaffRole.CASHIER) }
    var branchId by remember(branches) { mutableStateOf(branches.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addStaffButton) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(strings.usernameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(strings.newPasswordLabel) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("Peran:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StaffRole.entries.forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.name) })
                    }
                }
                Text("Cabang:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    branches.forEach { b ->
                        FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.code) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.trim().length >= 3 && fullName.isNotBlank() && password.length >= 6 && branchId.isNotBlank()) {
                        onSave(username, fullName, role, branchId, password)
                    }
                }
            ) {
                Text(strings.saveButton)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelButton) } }
    )
}

@Composable
private fun ChangePasswordDialog(
    strings: AppStrings,
    target: StaffAccount,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.changePasswordButton}: @${target.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${target.fullName} (${target.role})", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(strings.newPasswordLabel) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("new_password_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newPassword.length >= 6) onSave(newPassword) },
                enabled = newPassword.length >= 6,
                modifier = Modifier.testTag("confirm_password_change_btn")
            ) {
                Text(strings.saveButton)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelButton) } }
    )
}
