package com.xxx.carelorie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xxx.carelorie.ui.layout.ContentWidth
import com.xxx.carelorie.ui.layout.constrainedWidth
import com.xxx.carelorie.ui.viewmodels.ChatMessage
import com.xxx.carelorie.ui.viewmodels.DietChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietChatScreen(navController: NavController, viewModel: DietChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    
    var editingMessageIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var deletingMessageIndex by remember { mutableStateOf<Int?>(null) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    if (editingMessageIndex != null) {
        AlertDialog(
            onDismissRequest = { editingMessageIndex = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingMessageIndex?.let { index ->
                        viewModel.editMessage(index, editingText)
                    }
                    editingMessageIndex = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessageIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deletingMessageIndex != null) {
        AlertDialog(
            onDismissRequest = { deletingMessageIndex = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message? This will also delete all subsequent messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingMessageIndex?.let { index ->
                            viewModel.deleteMessage(index)
                        }
                        deletingMessageIndex = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessageIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Diet Assistant") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.onInputChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask for diet advice...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = uiState.inputText.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            uiState.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxHeight()
                    // A transcript that runs the full width of a tablet is unreadable, so the
                    // conversation stays a centred column.
                    .constrainedWidth(ContentWidth.Reading)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(uiState.messages) { index, message ->
                    ChatBubble(
                        message = message,
                        onEdit = {
                            editingText = message.text
                            editingMessageIndex = index
                        },
                        onDelete = {
                            deletingMessageIndex = index
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    // A bubble that spans the whole row is hard to scan, so leave a margin on the far side.
    val maxBubbleWidth = this@BoxWithConstraints.maxWidth * 0.78f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 0.dp,
                    bottomEnd = if (message.isUser) 0.dp else 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                if (message.isUser) {
                                    showMenu = true
                                }
                            }
                        )
                    }
            ) {
                Text(
                    text = parseMarkdown(message.text),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 15.sp
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
        Text(
            text = if (message.isUser) "You" else "AI Assistant",
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    }
}

/**
 * Simple markdown parser that supports **bold** and _italic_ or *italic*.
 */
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        val italicRegex = Regex("""(?<!\*)\*(?!\*)(.*?)(?<!\*)\*(?!\*)|_(.*?)_""")

        val matches = (boldRegex.findAll(text) + italicRegex.matches(text).let { italicRegex.findAll(text) })
            .sortedBy { it.range.first }

        matches.forEach { match ->
            // Append text before the match
            append(text.substring(cursor, match.range.first))
            
            val isBold = match.value.startsWith("**")
            val content = match.groupValues.firstNotNullOf { it.takeIf { g -> g != match.value && g.isNotEmpty() } }

            if (isBold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
            } else {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
            }
            cursor = match.range.last + 1
        }
        // Append remaining text
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
