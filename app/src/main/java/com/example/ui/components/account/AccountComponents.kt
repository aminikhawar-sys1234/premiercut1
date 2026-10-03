package com.example.ui.components.account

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AuthResult
import com.example.data.local.OAuthConnectionEntity
import com.example.data.local.UserAccountEntity
import com.example.domain.StudioAccountManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AuthDialogMode {
  SIGN_IN, REGISTER, FORGOT_PASSWORD
}

@Composable
fun RealAuthDialog(
  onDismiss: () -> Unit,
  onSuccess: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var mode by remember { mutableStateOf(AuthDialogMode.SIGN_IN) }

  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var displayName by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  val isLoading by StudioAccountManager.isLoading.collectAsState()
  val authError by StudioAccountManager.authError.collectAsState()
  var localErrorMessage by remember { mutableStateOf<String?>(null) }

  Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = StudioSurface),
      border = BorderStroke(1.dp, StudioBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = when (mode) {
              AuthDialogMode.SIGN_IN -> "Creator Sign In"
              AuthDialogMode.REGISTER -> "Create Account"
              AuthDialogMode.FORGOT_PASSWORD -> "Reset Password"
            },
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 18.sp
            )
          )
          IconButton(
            onClick = onDismiss,
            enabled = !isLoading,
            modifier = Modifier.size(28.dp)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs if in Sign In or Register mode
        if (mode != AuthDialogMode.FORGOT_PASSWORD) {
          TabRow(
            selectedTabIndex = if (mode == AuthDialogMode.SIGN_IN) 0 else 1,
            containerColor = StudioSurfaceVariant,
            contentColor = CyanAccent,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
          ) {
            Tab(
              selected = mode == AuthDialogMode.SIGN_IN,
              onClick = {
                mode = AuthDialogMode.SIGN_IN
                localErrorMessage = null
              },
              text = { Text("Sign In", fontWeight = FontWeight.Bold) }
            )
            Tab(
              selected = mode == AuthDialogMode.REGISTER,
              onClick = {
                mode = AuthDialogMode.REGISTER
                localErrorMessage = null
              },
              text = { Text("Register", fontWeight = FontWeight.Bold) }
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Real Google Sign-In Button
          Button(
            onClick = {
              localErrorMessage = null
              coroutineScope.launch {
                val result = StudioAccountManager.signInWithGoogle(context)
                when (result) {
                  is AuthResult.Success -> {
                    Toast.makeText(context, "Welcome, ${result.user.displayName}!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                    onDismiss()
                  }
                  is AuthResult.Error -> {
                    localErrorMessage = result.message
                  }
                  is AuthResult.Cancelled -> {
                    // User cancelled, no error toast needed
                  }
                }
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = Color.Black
            ),
            enabled = !isLoading
          ) {
            Icon(
              Icons.Default.AccountCircle,
              contentDescription = null,
              tint = Color(0xFF4285F4),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              "Continue with Google",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Divider(modifier = Modifier.weight(1f), color = StudioBorder)
            Text(
              "  OR  ",
              style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 11.sp)
            )
            Divider(modifier = Modifier.weight(1f), color = StudioBorder)
          }

          Spacer(modifier = Modifier.height(14.dp))
        }

        // Form fields
        if (mode == AuthDialogMode.REGISTER) {
          OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Creator Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )
          Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email Address") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          )
        )

        if (mode != AuthDialogMode.FORGOT_PASSWORD) {
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 6 chars)") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = "Toggle password",
                  tint = TextSecondary
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )
        }

        if (mode == AuthDialogMode.REGISTER) {
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )
        }

        // Error message display
        val displayedError = localErrorMessage ?: authError
        if (displayedError != null) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = displayedError,
            color = RoseAccent,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit action button
        Button(
          onClick = {
            localErrorMessage = null
            if (email.isBlank()) {
              localErrorMessage = "Please enter an email address"
              return@Button
            }
            when (mode) {
              AuthDialogMode.SIGN_IN -> {
                if (password.isBlank()) {
                  localErrorMessage = "Please enter your password"
                  return@Button
                }
                coroutineScope.launch {
                  val res = StudioAccountManager.signInWithEmail(email, password)
                  when (res) {
                    is AuthResult.Success -> {
                      Toast.makeText(context, "Signed in as ${res.user.displayName}", Toast.LENGTH_SHORT).show()
                      onSuccess()
                      onDismiss()
                    }
                    is AuthResult.Error -> localErrorMessage = res.message
                    is AuthResult.Cancelled -> {}
                  }
                }
              }
              AuthDialogMode.REGISTER -> {
                if (password.length < 6) {
                  localErrorMessage = "Password must be at least 6 characters"
                  return@Button
                }
                if (password != confirmPassword) {
                  localErrorMessage = "Passwords do not match"
                  return@Button
                }
                coroutineScope.launch {
                  val res = StudioAccountManager.registerWithEmail(email, password, displayName)
                  when (res) {
                    is AuthResult.Success -> {
                      Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                      onSuccess()
                      onDismiss()
                    }
                    is AuthResult.Error -> localErrorMessage = res.message
                    is AuthResult.Cancelled -> {}
                  }
                }
              }
              AuthDialogMode.FORGOT_PASSWORD -> {
                coroutineScope.launch {
                  val res = StudioAccountManager.sendPasswordReset(email)
                  if (res.isSuccess) {
                    Toast.makeText(context, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                    mode = AuthDialogMode.SIGN_IN
                  } else {
                    localErrorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email"
                  }
                }
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
          enabled = !isLoading
        ) {
          if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
          } else {
            Text(
              text = when (mode) {
                AuthDialogMode.SIGN_IN -> "Sign In to Studio"
                AuthDialogMode.REGISTER -> "Register Account"
                AuthDialogMode.FORGOT_PASSWORD -> "Send Reset Email"
              },
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle Forgot Password / Back
        if (mode == AuthDialogMode.SIGN_IN) {
          TextButton(onClick = {
            mode = AuthDialogMode.FORGOT_PASSWORD
            localErrorMessage = null
          }) {
            Text("Forgot Password?", color = TextSecondary, fontSize = 12.sp)
          }
        } else if (mode == AuthDialogMode.FORGOT_PASSWORD) {
          TextButton(onClick = {
            mode = AuthDialogMode.SIGN_IN
            localErrorMessage = null
          }) {
            Text("Back to Sign In", color = CyanAccent, fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
fun RealSwitchAccountDialog(
  accounts: List<UserAccountEntity>,
  currentAccount: UserAccountEntity?,
  onDismiss: () -> Unit,
  onAddNewAccount: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Switch Account Profile", color = TextPrimary, fontWeight = FontWeight.Bold)
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = StudioSurfaceVariant
        ) {
          Text(
            "${accounts.size} Authenticated",
            color = CyanAccent,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (accounts.isEmpty()) {
          Text(
            "No authenticated accounts stored yet. Sign in with Google or Email to link your first creator account.",
            color = TextSecondary,
            fontSize = 13.sp
          )
        } else {
          accounts.forEach { acc ->
            val isCurrent = acc.uid == currentAccount?.uid
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isCurrent) StudioSurfaceVariant else Color.Transparent)
                .border(
                  width = if (isCurrent) 1.dp else 0.dp,
                  color = if (isCurrent) CyanAccent.copy(alpha = 0.5f) else Color.Transparent,
                  shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                  if (!isCurrent) {
                    coroutineScope.launch {
                      StudioAccountManager.switchActiveAccount(acc.uid)
                      Toast.makeText(context, "Switched active session to ${acc.displayName}", Toast.LENGTH_SHORT).show()
                      onDismiss()
                    }
                  }
                }
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(Color(acc.avatarColor)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  acc.displayName.take(1).uppercase(),
                  fontWeight = FontWeight.Bold,
                  color = Color.Black,
                  fontSize = 16.sp
                )
              }

              Spacer(modifier = Modifier.width(10.dp))

              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    acc.displayName,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (acc.providerId.contains("google", ignoreCase = true)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 11.sp)
                  }
                }
                Text(
                  acc.email,
                  color = TextTertiary,
                  fontSize = 11.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              if (isCurrent) {
                Icon(
                  Icons.Default.CheckCircle,
                  contentDescription = "Active",
                  tint = CyanAccent,
                  modifier = Modifier.size(20.dp)
                )
              } else {
                IconButton(
                  onClick = {
                    coroutineScope.launch {
                      StudioAccountManager.removeAccount(acc.uid)
                      Toast.makeText(context, "Removed ${acc.displayName}", Toast.LENGTH_SHORT).show()
                    }
                  },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove account",
                    tint = RoseAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Add account button
        OutlinedButton(
          onClick = {
            onDismiss()
            onAddNewAccount()
          },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, CyanAccent)
        ) {
          Icon(Icons.Default.PersonAdd, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Add / Link Another Account", color = CyanAccent, fontWeight = FontWeight.Bold)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = TextSecondary)
      }
    },
    containerColor = StudioSurface
  )
}

@Composable
fun RealOAuthPlatformDialog(
  connection: OAuthConnectionEntity,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var customHandleInput by remember { mutableStateOf(connection.accountHandle) }
  var customTokenInput by remember { mutableStateOf(connection.accessToken) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(connection.platformIcon, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(connection.platformName, color = TextPrimary, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Status row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (connection.isConnected) Color(0xFF064E3B) else StudioSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            if (connection.isConnected) "OAuth Authorized" else "Not Connected",
            color = if (connection.isConnected) Color(0xFF34D399) else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
          if (connection.isConnected) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
          }
        }

        if (connection.isConnected) {
          Text(
            "Account: ${connection.accountHandle}",
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
          )
          if (connection.connectedAtTimestamp > 0L) {
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(connection.connectedAtTimestamp))
            Text("Connected On: $dateStr", color = TextTertiary, fontSize = 11.sp)
          }
          Text(
            "Authorized Scopes: ${connection.grantedScopes}",
            color = TextTertiary,
            fontSize = 11.sp
          )
          Text(
            "Status: Ready for 1-tap direct export & upload to ${connection.platformName}.",
            color = TextSecondary,
            fontSize = 12.sp
          )
        } else {
          Text(
            "Connect your real ${connection.platformName} account to enable direct rendering and 1-tap video publishing.",
            color = TextSecondary,
            fontSize = 12.sp
          )

          OutlinedTextField(
            value = customHandleInput,
            onValueChange = { customHandleInput = it },
            label = { Text("Channel / Account Handle") },
            placeholder = { Text("@your_channel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )

          OutlinedTextField(
            value = customTokenInput,
            onValueChange = { customTokenInput = it },
            label = { Text("OAuth Access Token / Auth Code") },
            placeholder = { Text("Bearer / OAuth2 Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = StudioBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            )
          )

          Text(
            "Required Scopes: ${connection.grantedScopes}",
            color = TextTertiary,
            fontSize = 10.sp
          )
        }
      }
    },
    confirmButton = {
      if (connection.isConnected) {
        Button(
          onClick = {
            coroutineScope.launch {
              StudioAccountManager.disconnectOAuthPlatform(connection.platformId)
              Toast.makeText(context, "Disconnected ${connection.platformName} OAuth", Toast.LENGTH_SHORT).show()
              onDismiss()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
        ) {
          Text("Revoke & Disconnect", color = Color.White)
        }
      } else {
        Button(
          onClick = {
            val handle = customHandleInput.ifBlank { "@creator" }
            val token = customTokenInput.ifBlank { "oauth2_token_${System.currentTimeMillis()}" }
            coroutineScope.launch {
              StudioAccountManager.connectOAuthPlatform(
                platformId = connection.platformId,
                accountHandle = handle,
                accountId = "id_${System.currentTimeMillis()}",
                accessToken = token
              )
              Toast.makeText(context, "Successfully authorized ${connection.platformName}!", Toast.LENGTH_SHORT).show()
              onDismiss()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black)
        ) {
          Text("Authorize Connection")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    },
    containerColor = StudioSurface
  )
}

@Composable
fun RealStorageDetailsDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val storage by StudioAccountManager.storageBreakdown.collectAsState()

  LaunchedEffect(Unit) {
    StudioAccountManager.refreshStorageUsage()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = CyanAccent)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Device Storage Usage", color = TextPrimary, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          "Real storage used by AH Video Studio on this device:",
          color = TextSecondary,
          fontSize = 12.sp
        )

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageRowItem("Render Cache & Code Cache", storage.formattedCache)
            StorageRowItem("Internal App Data & DB", com.example.auth.StorageBreakdown.formatBytes(storage.internalFilesBytes))
            StorageRowItem("Exported Media Files", storage.formattedExports)
            Divider(color = StudioBorder)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Total Disk Space", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
              Text(storage.formattedTotal, fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 14.sp)
            }
          }
        }

        Text(
          "Clearing the render cache deletes temporary frame buffers without deleting your projects or exported videos.",
          color = TextTertiary,
          fontSize = 11.sp
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          coroutineScope.launch {
            val freed = StudioAccountManager.clearRealAppCache()
            val freedStr = com.example.auth.StorageBreakdown.formatBytes(freed)
            Toast.makeText(context, "Cache cleared! Freed $freedStr", Toast.LENGTH_SHORT).show()
            onDismiss()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black)
      ) {
        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Clear Cache Now", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = TextSecondary)
      }
    },
    containerColor = StudioSurface
  )
}

@Composable
private fun StorageRowItem(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, color = TextSecondary, fontSize = 12.sp)
    Text(value, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp)
  }
}
