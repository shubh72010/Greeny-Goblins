/*
 * JusPlayer (2026)
 * © Følius — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import moe.rukamori.archivetune.R

data class BluetoothDeviceUiModel(
    val address: String,
    val name: String,
    val displayName: String,
)

private fun getPairedBluetoothDevices(context: Context): List<BluetoothDeviceUiModel> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    return try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return emptyList()
        adapter.bondedDevices?.mapNotNull { device ->
            try {
                val address = device.address ?: return@mapNotNull null
                val name = try { device.name } catch (_: SecurityException) { null } ?: address
                BluetoothDeviceUiModel(
                    address = address,
                    name = name,
                    displayName = name,
                )
            } catch (_: SecurityException) {
                null
            }
        }?.sortedBy { it.displayName.lowercase() } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun BluetoothDevicePickerDialog(
    currentSelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    onRequestPermission: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var selected by remember(currentSelected) { mutableStateOf(currentSelected.toMutableSet()) }
    var devices by remember { mutableStateOf<List<BluetoothDeviceUiModel>>(emptyList()) }
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            devices = getPairedBluetoothDevices(context)
        } else {
            devices = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_start_on_bluetooth_select_devices)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!hasPermission) {
                    Text(
                        text = stringResource(R.string.auto_start_on_bluetooth_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (onRequestPermission != null) {
                        TextButton(onClick = {
                            onRequestPermission()
                            // re-check after request will be done via launcher callback outside
                        }) {
                            Text(stringResource(R.string.auto_start_on_bluetooth_grant_permission))
                        }
                    }
                } else if (devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.auto_start_on_bluetooth_no_paired_devices),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.auto_start_on_bluetooth_no_paired_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                selected = devices.map { it.address }.toMutableSet()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.auto_start_on_bluetooth_select_all)) }
                        TextButton(
                            onClick = { selected = mutableSetOf() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.auto_start_on_bluetooth_clear_all)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(devices, key = { it.address }) { device ->
                            val isChecked = device.address in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = selected.toMutableSet().apply {
                                            if (isChecked) remove(device.address) else add(device.address)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selected = selected.toMutableSet().apply {
                                            if (checked) add(device.address) else remove(device.address)
                                        }
                                    },
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toSet()) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
