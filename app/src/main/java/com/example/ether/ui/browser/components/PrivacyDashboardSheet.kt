package com.example.ether.ui.browser.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ether.ui.browser.BrowserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardSheet(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val totalTrackers by viewModel.totalBlockedTrackers.collectAsStateWithLifecycle(0L)
    val totalAds by viewModel.totalBlockedAds.collectAsStateWithLifecycle(0L)
    val blockedOnPage by viewModel.blockedCountOnPage.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Privacy Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Page", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$blockedOnPage items blocked",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Trackers", style = MaterialTheme.typography.bodySmall)
                        Text(totalTrackers.toString(), style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Ads", style = MaterialTheme.typography.bodySmall)
                        Text(totalAds.toString(), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            var strictMode by remember { mutableStateOf(true) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Strict Blocking Mode", style = MaterialTheme.typography.bodyLarge)
                    Text("Block all known trackers and ads", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = strictMode, onCheckedChange = { strictMode = it })
            }

            Spacer(modifier = Modifier.height(16.dp))

            val appSettings by viewModel.settingsFlow.collectAsStateWithLifecycle(null)
            val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
            val isAutoIncognito = appSettings?.autoIncognitoDomains?.contains(viewModel.getDomain(currentUrl ?: "")) == true

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Always Private", style = MaterialTheme.typography.bodyLarge)
                    Text("Always open this site in Private Mode", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = isAutoIncognito,
                    onCheckedChange = { 
                        scope.launch {
                            viewModel.toggleAutoIncognitoForCurrentSite()
                        }
                    }
                )
            }
        }
    }
}
