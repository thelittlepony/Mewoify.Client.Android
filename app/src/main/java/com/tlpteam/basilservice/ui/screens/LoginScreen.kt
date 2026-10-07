package com.tlpteam.basilservice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tlpteam.basilservice.ui.viewmodel.MessengerViewModel

@Composable
fun LoginScreen(viewModel: MessengerViewModel) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var serverUrlInput by remember { mutableStateOf(serverUrl) }
    var loginInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }

    LaunchedEffect(serverUrl) {
        serverUrlInput = serverUrl
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Mewoify",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Server URL
            OutlinedTextField(
                value = serverUrlInput,
                onValueChange = {
                    serverUrlInput = it
                    viewModel.setServerUrl(it)
                },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isRegisterMode) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = loginInput,
                onValueChange = { loginInput = it },
                label = { Text(if (isRegisterMode) "Username" else "Username / Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isRegisterMode) {
                            viewModel.register(loginInput, emailInput, passwordInput)
                        } else {
                            viewModel.login(loginInput, passwordInput)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRegisterMode) "Register" else "Login")
                }

                OutlinedButton(
                    onClick = { isRegisterMode = !isRegisterMode },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRegisterMode) "Switch to Login" else "Switch to Register")
                }
            }

            if (!errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (savedAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Saved Accounts", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    items(savedAccounts) { account ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.setServerUrl(account.serverUrl)
                                    viewModel.switchAccount(account)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = account.username, style = MaterialTheme.typography.bodyLarge)
                                    Text(text = account.serverUrl, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { viewModel.removeAccount(account) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
