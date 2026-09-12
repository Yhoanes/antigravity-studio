package com.antigravity.studio.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.core.auth.AuthState
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.CyberObsidian
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

/**
 * TopBar action component for Google Sign-In & Multi-Account Management.
 * Implements SPEC-002 Section 5.1 with the Cyber-Obsidian design system.
 */
@Composable
fun GoogleAuthTopBarAction(
    modifier: Modifier = Modifier,
    authState: AuthState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSwitchAccountClick: () -> Unit = onSignInClick
) {
    var showAccountDialog by remember { mutableStateOf(false) }

    when (authState) {
        is AuthState.Authenticated -> {
            // Modern badge: 🟢 email in JetBrains Mono
            Box(
                modifier = modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceObsidian)
                    .border(1.dp, StatusSuccess.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .clickable { showAccountDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🟢",
                        fontSize = 9.sp
                    )
                    Text(
                        text = authState.email,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showAccountDialog) {
                GoogleAccountDialog(
                    email = authState.email,
                    displayName = authState.displayName,
                    onDismiss = { showAccountDialog = false },
                    onSignOut = {
                        showAccountDialog = false
                        onSignOutClick()
                    },
                    onSwitchAccount = {
                        showAccountDialog = false
                        onSwitchAccountClick()
                    }
                )
            }
        }

        is AuthState.Authenticating -> {
            // Subtle authenticating pill
            Box(
                modifier = modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceObsidian)
                    .border(1.dp, CosmicViolet.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🟣",
                        fontSize = 9.sp
                    )
                    Text(
                        text = "Autenticando...",
                        color = CosmicViolet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        else -> {
            // [G Iniciar Sesión con Google] Cyber-Obsidian / Neon button
            Box(
                modifier = modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceObsidian)
                    .border(1.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .clickable { onSignInClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Stylized 'G' icon
                    Text(
                        text = "G",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Iniciar Sesión con Google",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Account dialog displaying profile details, Google Gemini Cloud quotas,
 * and options to sign out or add another account.
 */
@Composable
private fun GoogleAccountDialog(
    email: String,
    displayName: String,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        titleContentColor = NeonCyan,
        textContentColor = TextPrimary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🛡️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cuenta de Google",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profile header card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceObsidian)
                        .border(1.dp, BorderObsidian, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Initial avatar
                    val initial = (if (displayName.isNotEmpty()) displayName else email)
                        .firstOrNull()?.uppercase() ?: "G"

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.2f))
                            .border(1.5.dp, NeonCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = NeonCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        if (displayName.isNotEmpty()) {
                            Text(
                                text = displayName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = email,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🟢 ONLINE · Gemini 1.5 Pro Ready",
                            color = StatusSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(
                    text = "Autenticado con PKCE directamente en tu Xiaomi Pad 6. Las cuotas y llamadas a la API de Google se ejecutan localmente sin servidores intermediarios.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusError.copy(alpha = 0.2f),
                    contentColor = StatusError
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Cerrar Sesión",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onSwitchAccount,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderObsidian),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Añadir otra cuenta",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cerrar",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}
