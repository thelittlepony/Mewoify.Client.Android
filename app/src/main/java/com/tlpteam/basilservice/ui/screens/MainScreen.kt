package com.tlpteam.basilservice.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.tlpteam.basilservice.data.model.*
import com.tlpteam.basilservice.service.AudioPlayerManager
import com.tlpteam.basilservice.ui.viewmodel.MessengerViewModel
import com.tlpteam.basilservice.util.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MessengerViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val guilds by viewModel.guilds.collectAsState()
    val dmList by viewModel.dmList.collectAsState()
    val selectedGuildId by viewModel.selectedGuildId.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedChannelId by viewModel.selectedChannelId.collectAsState()
    val selectedDmId by viewModel.selectedDmId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val userCache by viewModel.userCache.collectAsState()
    val selectedUserProfile by viewModel.selectedUserProfile.collectAsState()
    val inviteCodeResult by viewModel.inviteCodeResult.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val guildMembers by viewModel.guildMembers.collectAsState()
    val presenceMap by viewModel.presenceMap.collectAsState()
    val guildPictureVersions by viewModel.guildPictureVersions.collectAsState()

    var showCreateGuildDialog by remember { mutableStateOf(false) }
    var showJoinInviteDialog by remember { mutableStateOf(false) }
    var showInvitesListGuildId by remember { mutableStateOf<String?>(null) }
    var showCreateInviteGuildId by remember { mutableStateOf<String?>(null) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var createChannelDefaultCategoryId by remember { mutableStateOf<String?>(null) }
    var editingChannel by remember { mutableStateOf<ChannelResponse?>(null) }
    var selectedGuildSettingsId by remember { mutableStateOf<String?>(null) }
    var editingGuild by remember { mutableStateOf<GuildSummaryResponse?>(null) }
    var showAccountMenuDialog by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }

    var messageInput by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editTextContent by remember { mutableStateOf("") }
    var expandedMenuMessageId by remember { mutableStateOf<String?>(null) }
    var selectedFileUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var pendingGuildPictureId by remember { mutableStateOf<String?>(null) }
    val guildPicturePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val guildId = pendingGuildPictureId
        pendingGuildPictureId = null
        if (uri != null && guildId != null) {
            viewModel.uploadGuildPicture(guildId, context, uri)
        }
    }
    val clipboardManager = LocalClipboardManager.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val combined = (selectedFileUris + uris).take(8)
            selectedFileUris = combined
        }
    }

    var lastMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val newLastId = messages.lastOrNull()?.id
        if (newLastId != null && newLastId != lastMessageId) {
            lastMessageId = newLastId
            if (messages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.scrollToItem(messages.size - 1)
            }
        }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null && firstVisibleItem.index == 0
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreMessages()
        }
    }

    LaunchedEffect(dmList, currentUser) {
        val currentUserId = currentUser?.id
        if (currentUserId != null) {
            dmList.forEach { dm ->
                val peerId = if (dm.userId1 == currentUserId) dm.userId2 else dm.userId1
                viewModel.ensureUserCached(peerId)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .padding(16.dp)
                ) {
                    // Account header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentUser?.displayName ?: currentUser?.username ?: "Account",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { showAccountMenuDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = "${serverUrl.trimEnd('/')}/profile_pictures/default.avif",
                                contentDescription = "Default Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (currentUser != null) {
                                AsyncImage(
                                    model = "${serverUrl.trimEnd('/')}/profile_pictures/${currentUser!!.id}.avif",
                                    contentDescription = "User Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val isDmFocused = selectedDmId != null

                    // DMs Section
                    Text("Direct Messages", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = if (isDmFocused) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.heightIn(max = 100.dp)
                        }
                    ) {
                        items(dmList) { dm ->
                            val currentUserId = currentUser?.id
                            val peerUserId = if (dm.userId1 == currentUserId) dm.userId2 else dm.userId1
                            val peerUser = userCache[peerUserId]
                            val peerName = peerUser?.displayName ?: peerUser?.username ?: "DM (${peerUserId.take(6)})"

                            NavigationDrawerItem(
                                label = { Text(peerName) },
                                icon = {
                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = "${serverUrl.trimEnd('/')}/profile_pictures/default.avif",
                                                contentDescription = "Default Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            AsyncImage(
                                                model = "${serverUrl.trimEnd('/')}/profile_pictures/$peerUserId.avif",
                                                contentDescription = "User Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        val isOnline = presenceMap[peerUserId] ?: (peerUser?.isOnline == true)
                                        OnlineIndicator(
                                            isOnline = isOnline,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 2.dp, y = 2.dp)
                                                .size(8.dp)
                                        )
                                    }
                                },
                                selected = dm.id == selectedDmId,
                                onClick = {
                                    viewModel.selectDm(dm.id)
                                    coroutineScope.launch { drawerState.close() }
                                }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Servers & Channels Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Servers", style = MaterialTheme.typography.labelMedium)
                        Row {
                            IconButton(onClick = { showCreateGuildDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Add Guild", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showJoinInviteDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "Join Invite", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = if (!isDmFocused) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.heightIn(max = 100.dp)
                        }
                    ) {
                        items(guilds) { guild ->
                            val isGuildSelected = guild.id == selectedGuildId
                            Column {
                                NavigationDrawerItem(
                                    label = { Text(guild.name, style = MaterialTheme.typography.bodyLarge) },
                                    icon = {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = guild.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            val gVer = guildPictureVersions[guild.id] ?: 0L
                                            val gUrl = "${serverUrl.trimEnd('/')}/guild_pictures/${guild.id}.avif" + if (gVer > 0L) "?v=$gVer" else ""
                                            AsyncImage(
                                                model = gUrl,
                                                contentDescription = "Guild Avatar",
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    },
                                    selected = isGuildSelected,
                                    onClick = {
                                        viewModel.selectGuild(guild.id)
                                    },
                                    badge = {
                                        IconButton(
                                            onClick = { selectedGuildSettingsId = guild.id },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = "Server Settings", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                                if (isGuildSelected) {
                                    val categories = channels.filter { it.channelType == "category" }
                                    val uncategorized = channels.filter { it.channelType == "text" && (it.categoryId == null || !categories.any { c -> c.id == it.categoryId }) }

                                    uncategorized.forEach { channel ->
                                        val isChannelSelected = channel.id == selectedChannelId
                                        NavigationDrawerItem(
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("# ${channel.name}")
                                                    if (channel.nsfw) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("⚠️", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            },
                                            selected = isChannelSelected,
                                            onClick = {
                                                viewModel.selectChannel(channel.id)
                                                coroutineScope.launch { drawerState.close() }
                                            },
                                            modifier = Modifier.padding(start = 16.dp),
                                            badge = {
                                                IconButton(onClick = { editingChannel = channel }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Settings, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        )
                                    }

                                    categories.forEach { category ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = category.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        createChannelDefaultCategoryId = category.id
                                                        showCreateChannelDialog = true
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add Channel", modifier = Modifier.size(14.dp))
                                                }
                                                IconButton(
                                                    onClick = { editingChannel = category },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Settings, contentDescription = "Edit Category", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }

                                        val childChannels = channels.filter { it.channelType == "text" && it.categoryId == category.id }
                                        childChannels.forEach { channel ->
                                            val isChannelSelected = channel.id == selectedChannelId
                                            NavigationDrawerItem(
                                                label = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("# ${channel.name}")
                                                        if (channel.nsfw) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("⚠️", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                },
                                                selected = isChannelSelected,
                                                onClick = {
                                                    viewModel.selectChannel(channel.id)
                                                    coroutineScope.launch { drawerState.close() }
                                                },
                                                modifier = Modifier.padding(start = 28.dp),
                                                badge = {
                                                    IconButton(onClick = { editingChannel = channel }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Settings, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            createChannelDefaultCategoryId = null
                                            showCreateChannelDialog = true
                                        },
                                        modifier = Modifier.padding(start = 16.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create Channel/Category")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout")
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val currentUserId = currentUser?.id
                        val titleText = if (selectedGuildId != null) {
                            val guildName = guilds.find { it.id == selectedGuildId }?.name ?: "Server"
                            val channelName = channels.find { it.id == selectedChannelId }?.name ?: "channel"
                            "$guildName > #$channelName"
                        } else if (selectedDmId != null) {
                            val dm = dmList.find { it.id == selectedDmId }
                            if (dm != null && currentUserId != null) {
                                val peerUserId = if (dm.userId1 == currentUserId) dm.userId2 else dm.userId1
                                val peerUser = userCache[peerUserId]
                                peerUser?.displayName ?: peerUser?.username ?: "Direct Message"
                            } else {
                                "Direct Message"
                            }
                        } else {
                            "Mewoify"
                        }
                        Text(text = titleText, maxLines = 1)
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                        }
                    },
                    actions = {
                        if (selectedGuildId != null) {
                            IconButton(onClick = { showMembersSheet = true }) {
                                Icon(Icons.Default.People, contentDescription = "Members")
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (isConnected) Color.Green else Color.Red, shape = MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Messages Feed
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(messages) { index, msg ->
                        val isOwnMessage = msg.userId == currentUser?.id
                        val senderUser = userCache[msg.userId]
                        val senderName = senderUser?.displayName ?: senderUser?.username ?: msg.userId.take(8)
                        val prevMsg = if (index > 0) messages[index - 1] else null
                        val isGrouped = isMessageGroupedWithPrevious(msg, prevMsg)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
                        ) {
                            if (!isGrouped) {
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.loadUserProfile(msg.userId) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = "${serverUrl.trimEnd('/')}/profile_pictures/default.avif",
                                            contentDescription = "Default Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        AsyncImage(
                                            model = "${serverUrl.trimEnd('/')}/profile_pictures/${msg.userId}.avif",
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        val isOnline = presenceMap[msg.userId] ?: (senderUser?.isOnline == true)
                                        OnlineIndicator(
                                            isOnline = isOnline,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            viewModel.loadUserProfile(msg.userId)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = NotificationHelper.formatServerTimestamp(msg.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            Box {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isOwnMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            expandedMenuMessageId = msg.id
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        if (editingMessageId == msg.id) {
                                            OutlinedTextField(
                                                value = editTextContent,
                                                onValueChange = { editTextContent = it },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(onClick = { editingMessageId = null }) {
                                                    Text("Cancel")
                                                }
                                                Button(onClick = {
                                                    viewModel.editMessage(msg.id, editTextContent)
                                                    editingMessageId = null
                                                }) {
                                                    Text("Save")
                                                }
                                            }
                                        } else {
                                            val fileUrls = NotificationHelper.parseFileContentUrls(serverUrl, msg.fileContent)
                                            if (fileUrls.isNotEmpty()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    fileUrls.forEach { url ->
                                                        val type = getAttachmentType(url)
                                                        when (type) {
                                                            AttachmentType.IMAGE -> {
                                                                AsyncImage(
                                                                    model = url,
                                                                    contentDescription = "Attachment",
                                                                    modifier = Modifier
                                                                        .widthIn(max = 240.dp)
                                                                        .heightIn(max = 300.dp)
                                                                        .clickable { fullscreenImageUrl = url },
                                                                    contentScale = ContentScale.Fit
                                                                )
                                                            }
                                                            AttachmentType.FILE -> {
                                                                GenericFileItem(url = url)
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            if (msg.textContent.isNotBlank()) {
                                                Text(text = msg.textContent, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedMenuMessageId == msg.id,
                                    onDismissRequest = { expandedMenuMessageId = null }
                                ) {
                                    if (isOwnMessage) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                expandedMenuMessageId = null
                                                editingMessageId = msg.id
                                                editTextContent = msg.textContent
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                expandedMenuMessageId = null
                                                viewModel.deleteMessage(msg.id)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Copy Text") },
                                        onClick = {
                                            expandedMenuMessageId = null
                                            clipboardManager.setText(AnnotatedString(msg.textContent))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Message Input Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        if (selectedFileUris.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(selectedFileUris.size) { index ->
                                    val uri = selectedFileUris[index]
                                    val mimeType = context.contentResolver.getType(uri) ?: ""
                                    val isImg = mimeType.startsWith("image/")
                                    val isAud = mimeType.startsWith("audio/")
                                    var displayName = "File"
                                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                        if (cursor.moveToFirst() && nameIndex != -1) {
                                            displayName = cursor.getString(nameIndex) ?: "File"
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isImg) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "Selected Image",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isAud) Icons.Default.MusicNote else Icons.Default.InsertDriveFile,
                                                    contentDescription = "File",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedFileUris = selectedFileUris.filterIndexed { i, _ -> i != index }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedFileUris.size < 8) {
                                        filePickerLauncher.launch("*/*")
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach Files")
                            }
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { messageInput = it },
                                placeholder = { Text("Type a message...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                maxLines = 3
                            )
                            FilledIconButton(onClick = {
                                if (messageInput.isNotBlank() || selectedFileUris.isNotEmpty()) {
                                    if (selectedFileUris.isNotEmpty()) {
                                        viewModel.sendMessageWithFiles(context, selectedFileUris, messageInput)
                                    } else {
                                        viewModel.sendMessage(messageInput)
                                    }
                                    messageInput = ""
                                    selectedFileUris = emptyList()
                                }
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
    }

    // Members Bottom Sheet
    if (showMembersSheet) {
        ModalBottomSheet(onDismissRequest = { showMembersSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .padding(16.dp)
            ) {
                Text("Server Members (${guildMembers.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(guildMembers) { userId ->
                        val memberUser = userCache[userId]
                        val memberName = memberUser?.displayName ?: memberUser?.username ?: userId.take(8)
                        val isOnline = presenceMap[userId] ?: (memberUser?.isOnline == true)
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMembersSheet = false
                                    viewModel.loadUserProfile(userId)
                                },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = "${serverUrl.trimEnd('/')}/profile_pictures/default.avif",
                                        contentDescription = "Default Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    AsyncImage(
                                        model = "${serverUrl.trimEnd('/')}/profile_pictures/$userId.avif",
                                        contentDescription = "User Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    OnlineIndicator(
                                        isOnline = isOnline,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-2).dp, y = (-2).dp)
                                    )
                                }
                            },
                            headlineContent = { Text(memberName) },
                            supportingContent = { if (memberUser?.username != null) Text("@${memberUser.username}") }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Image Viewer Dialog
    if (fullscreenImageUrl != null) {
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullscreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullscreenImageUrl,
                    contentDescription = "Fullscreen Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                // Close Button
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // User Profile Dialog
    if (selectedUserProfile != null) {
        val userId = selectedUserProfile!!.id
        val isOnline = presenceMap[userId] ?: selectedUserProfile!!.isOnline
        UserProfileDialog(
            user = selectedUserProfile!!,
            isOnline = isOnline,
            onDismiss = { viewModel.clearSelectedUserProfile() },
            onOpenDm = { peerId -> viewModel.openDm(peerId) }
        )
    }

    // Invite Code Dialog
    if (inviteCodeResult != null) {
        InviteCodeDialog(
            invite = inviteCodeResult!!,
            onDismiss = { viewModel.clearInviteCode() }
        )
    }

    // Invites List Dialog
    if (showInvitesListGuildId != null) {
        InvitesListDialog(
            guildId = showInvitesListGuildId!!,
            viewModel = viewModel,
            onDismiss = { showInvitesListGuildId = null }
        )
    }

    // Create Invite Dialog
    if (showCreateInviteGuildId != null) {
        CreateInviteDialog(
            guildId = showCreateInviteGuildId!!,
            onDismiss = { showCreateInviteGuildId = null },
            onCreateInvite = { usage, date ->
                viewModel.createInvite(showCreateInviteGuildId!!, usage, date)
            }
        )
    }

    // Server Settings Dialog
    if (selectedGuildSettingsId != null) {
        val guild = guilds.find { it.id == selectedGuildSettingsId }
        if (guild != null) {
            ServerSettingsDialog(
                guild = guild,
                currentUserId = currentUser?.id,
                serverUrl = serverUrl,
                guildPictureVersions = guildPictureVersions,
                userCache = userCache,
                onDismiss = { selectedGuildSettingsId = null },
                onEdit = { editingGuild = guild },
                onInvite = { showCreateInviteGuildId = guild.id },
                onManageInvites = { showInvitesListGuildId = guild.id },
                onUploadPicture = {
                    pendingGuildPictureId = guild.id
                    guildPicturePickerLauncher.launch("image/*")
                },
                onDelete = { viewModel.deleteGuild(guild.id) },
                onLeave = { viewModel.leaveGuild(guild.id) }
            )
        }
    }

    // Edit Guild Dialog
    if (editingGuild != null) {
        EditGuildDialog(
            guild = editingGuild!!,
            onDismiss = { editingGuild = null },
            onUpdate = { name, desc ->
                viewModel.updateGuild(editingGuild!!.id, name, desc)
                editingGuild = null
            }
        )
    }

    // Dialogs
    if (showCreateGuildDialog) {
        CreateGuildDialog(
            onDismiss = { showCreateGuildDialog = false },
            onCreate = { name: String, desc: String? ->
                viewModel.createGuild(name, desc)
                showCreateGuildDialog = false
            }
        )
    }

    if (showJoinInviteDialog) {
        JoinInviteDialog(
            onDismiss = { showJoinInviteDialog = false },
            onJoin = { code: String ->
                viewModel.joinGuildByInvite(code)
                showJoinInviteDialog = false
            }
        )
    }

    if (showCreateChannelDialog && selectedGuildId != null) {
        val categories = channels.filter { it.channelType == "category" }
        CreateChannelDialog(
            categories = categories,
            defaultCategoryId = createChannelDefaultCategoryId,
            onDismiss = { showCreateChannelDialog = false },
            onCreate = { name, type, categoryId, nsfw ->
                viewModel.createChannel(selectedGuildId!!, name, type, categoryId, nsfw)
                showCreateChannelDialog = false
            }
        )
    }

    if (editingChannel != null && selectedGuildId != null) {
        val categories = channels.filter { it.channelType == "category" }
        EditChannelDialog(
            channel = editingChannel!!,
            categories = categories,
            onDismiss = { editingChannel = null },
            onUpdate = { name, type, categoryId, nsfw ->
                viewModel.updateChannel(selectedGuildId!!, editingChannel!!.id, name, type, categoryId, nsfw)
                editingChannel = null
            },
            onDelete = {
                viewModel.deleteChannel(selectedGuildId!!, editingChannel!!.id)
                editingChannel = null
            }
        )
    }

    if (showAccountMenuDialog) {
        AccountInstanceDialog(
            viewModel = viewModel,
            onDismiss = { showAccountMenuDialog = false }
        )
    }
}

@Composable
fun UserProfileDialog(
    user: PublicUserResponse,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onOpenDm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(user.displayName ?: user.username)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OnlineIndicator(isOnline = isOnline)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFB0BEC5)
                    )
                }
            }
        },
        text = {
            Column {
                Text("Username: @${user.username}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                if (!user.bio.isNullOrBlank()) {
                    Text("Bio: ${user.bio}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text("Joined: ${user.createdAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            Button(onClick = {
                onOpenDm(user.id)
                onDismiss()
            }) {
                Text("Send Message (DM)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun OnlineIndicator(isOnline: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(
                color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFB0BEC5),
                shape = CircleShape
            )
    )
}

@Composable
fun InviteCodeDialog(invite: InviteResponse, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Invite Created") },
        text = {
            Column {
                Text("Share this invite code with others:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = invite.inviteCode,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (invite.usage != null) {
                    Text("Max Uses: ${invite.usage} (Used: ${invite.uses})", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Uses: ${invite.uses} (Unlimited)", style = MaterialTheme.typography.bodySmall)
                }
                if (!invite.date.isNullOrBlank()) {
                    Text("Expires at: ${invite.date}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Never expires", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun InvitesListDialog(
    guildId: String,
    viewModel: MessengerViewModel,
    onDismiss: () -> Unit
) {
    var invites by remember { mutableStateOf<List<InviteResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(guildId) {
        viewModel.listInvites(guildId) { fetched ->
            invites = fetched
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Invites") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (invites.isEmpty()) {
                    Text("No active invites found.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(invites) { invite ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = invite.inviteCode, style = MaterialTheme.typography.titleMedium)
                                        val usageText = if (invite.usage != null) "Uses: ${invite.uses}/${invite.usage}" else "Uses: ${invite.uses} (Unlimited)"
                                        Text(text = usageText, style = MaterialTheme.typography.bodySmall)
                                        val expiryText = if (!invite.date.isNullOrBlank()) "Expires: ${invite.date.take(19)}" else "Never expires"
                                        Text(text = expiryText, style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = {
                                        viewModel.deleteInvite(guildId, invite.inviteCode) {
                                            viewModel.listInvites(guildId) { updated ->
                                                invites = updated
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Invite", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CreateInviteDialog(
    guildId: String,
    onDismiss: () -> Unit,
    onCreateInvite: (Int?, String?) -> Unit
) {
    var usageText by remember { mutableStateOf("") }
    var selectedExpiryIndex by remember { mutableStateOf(0) }
    var expiryExpanded by remember { mutableStateOf(false) }

    val expiryOptions = listOf("Never", "30 minutes", "1 hour", "6 hours", "12 hours", "1 day", "7 days")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Server Invite") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = usageText,
                    onValueChange = { usageText = it.filter { c -> c.isDigit() } },
                    label = { Text("Max Uses (optional, blank = unlimited)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Expiration Time", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expiryExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Expires: ${expiryOptions[selectedExpiryIndex]}")
                    }
                    DropdownMenu(
                        expanded = expiryExpanded,
                        onDismissRequest = { expiryExpanded = false }
                    ) {
                        expiryOptions.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedExpiryIndex = index
                                    expiryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val usage = usageText.toIntOrNull()
                    val expiryIso = when (selectedExpiryIndex) {
                        1 -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(30 * 60))
                        2 -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(3600))
                        3 -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(6 * 3600))
                        4 -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(12 * 3600))
                        5 -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().plus(1, ChronoUnit.DAYS))
                        6 -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().plus(7, ChronoUnit.DAYS))
                        else -> null
                    }
                    onCreateInvite(usage, expiryIso)
                    onDismiss()
                }
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditGuildDialog(guild: GuildSummaryResponse, onDismiss: () -> Unit, onUpdate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(guild.name) }
    var desc by remember { mutableStateOf(guild.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onUpdate(name, desc.ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateGuildDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Server (Guild)") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name, desc.ifBlank { null }) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JoinInviteDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Server via Invite") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Invite Code") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (code.isNotBlank()) onJoin(code) }) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateChannelDialog(
    categories: List<ChannelResponse>,
    defaultCategoryId: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String, String?, Boolean) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var channelType by remember { mutableStateOf("text") }
    var selectedCategoryId by remember { mutableStateOf(defaultCategoryId) }
    var nsfw by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel or Category") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Type:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = channelType == "text",
                        onClick = { channelType = "text" }
                    )
                    Text("Text Channel")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = channelType == "category",
                        onClick = {
                            channelType = "category"
                            selectedCategoryId = null
                        }
                    )
                    Text("Category")
                }

                if (channelType == "text") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = nsfw, onCheckedChange = { nsfw = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NSFW Channel")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Category:", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val catName = categories.find { it.id == selectedCategoryId }?.name ?: "None (Uncategorized)"
                            Text("Category: $catName")
                        }
                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Uncategorized)") },
                                onClick = {
                                    selectedCategoryId = null
                                    categoryDropdownExpanded = false
                                }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nameInput.isNotBlank()) {
                    onCreate(nameInput.trim(), channelType, if (channelType == "text") selectedCategoryId else null, nsfw)
                }
            }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ServerSettingsDialog(
    guild: GuildSummaryResponse,
    currentUserId: String?,
    serverUrl: String,
    guildPictureVersions: Map<String, Long>,
    userCache: Map<String, PublicUserResponse>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onInvite: () -> Unit,
    onManageInvites: () -> Unit,
    onUploadPicture: () -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit
) {
    val ownerUser = userCache[guild.ownerId]
    val ownerName = ownerUser?.displayName ?: ownerUser?.username ?: guild.ownerId.take(8)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server: ${guild.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = guild.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val gVer = guildPictureVersions[guild.id] ?: 0L
                    val gUrl = "${serverUrl.trimEnd('/')}/guild_pictures/${guild.id}.avif" + if (gVer > 0L) "?v=$gVer" else ""
                    AsyncImage(
                        model = gUrl,
                        contentDescription = "Guild Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (!guild.description.isNullOrBlank()) {
                    Text("Description: ${guild.description}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Owner: $ownerName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("Created: ${guild.createdAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onInvite()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite People")
                }

                if (guild.ownerId == currentUserId) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onUploadPicture()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Server Picture")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onManageInvites()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Invites")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onEdit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Server")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Server")
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onLeave()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Leave Server")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EditChannelDialog(
    channel: ChannelResponse,
    categories: List<ChannelResponse>,
    onDismiss: () -> Unit,
    onUpdate: (String, String, String?, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var nameInput by remember { mutableStateOf(channel.name) }
    var channelType by remember { mutableStateOf(channel.channelType) }
    var selectedCategoryId by remember { mutableStateOf(channel.categoryId) }
    var nsfw by remember { mutableStateOf(channel.nsfw) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (channel.channelType == "category") "Edit Category" else "Edit Channel") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (channel.channelType == "text") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = nsfw, onCheckedChange = { nsfw = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NSFW Channel")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Category:", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val catName = categories.find { it.id == selectedCategoryId }?.name ?: "None (Uncategorized)"
                            Text("Category: $catName")
                        }
                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Uncategorized)") },
                                onClick = {
                                    selectedCategoryId = null
                                    categoryDropdownExpanded = false
                                }
                            )
                            categories.filter { it.id != channel.id }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                Button(onClick = {
                    if (nameInput.isNotBlank()) {
                        onUpdate(nameInput.trim(), channelType, selectedCategoryId, nsfw)
                    }
                }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AccountInstanceDialog(viewModel: MessengerViewModel, onDismiss: () -> Unit) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var urlImageInput by remember { mutableStateOf(serverUrl) }
    var displayNameInput by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var bioInput by remember { mutableStateOf(currentUser?.bio ?: "") }
    val context = LocalContext.current
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadProfilePicture(context, uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile & Instance Manager") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { avatarPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Profile Picture")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bioInput,
                    onValueChange = { bioInput = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.updateProfile(displayNameInput.ifBlank { null }, bioInput.ifBlank { null })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Profile")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlImageInput,
                    onValueChange = {
                        urlImageInput = it
                        viewModel.setServerUrl(urlImageInput)
                    },
                    label = { Text("Current Server Instance URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Saved Accounts:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.height(100.dp)) {
                    items(savedAccounts) { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setServerUrl(account.serverUrl)
                                    viewModel.switchAccount(account)
                                    onDismiss()
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(account.username, style = MaterialTheme.typography.bodyLarge)
                                Text(account.serverUrl, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { viewModel.removeAccount(account) }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun isMessageGroupedWithPrevious(currentMsg: com.tlpteam.basilservice.data.model.MessageResponse, prevMsg: com.tlpteam.basilservice.data.model.MessageResponse?): Boolean {
    if (prevMsg == null) return false
    if (currentMsg.userId != prevMsg.userId) return false
    return try {
        val parseCurr = if (!currentMsg.createdAt.endsWith("Z") && !currentMsg.createdAt.contains("+")) "${currentMsg.createdAt}Z" else currentMsg.createdAt
        val parsePrev = if (!prevMsg.createdAt.endsWith("Z") && !prevMsg.createdAt.contains("+")) "${prevMsg.createdAt}Z" else prevMsg.createdAt
        val timeCurr = java.time.Instant.parse(parseCurr).toEpochMilli()
        val timePrev = java.time.Instant.parse(parsePrev).toEpochMilli()
        kotlin.math.abs(timeCurr - timePrev) <= 5 * 60 * 1000L
    } catch (e: Exception) {
        false
    }
}

enum class AttachmentType {
    IMAGE, FILE
}

fun isAudioFile(url: String): Boolean {
    val lower = url.lowercase()
    val cleanUrl = lower.substringBefore('?').substringBefore('#')
    return cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".wav") || cleanUrl.endsWith(".ogg") ||
           cleanUrl.endsWith(".m4a") || cleanUrl.endsWith(".aac") || cleanUrl.endsWith(".flac")
}

fun getAttachmentType(url: String): AttachmentType {
    val lower = url.lowercase()
    val cleanUrl = lower.substringBefore('?').substringBefore('#')
    return when {
        cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || cleanUrl.endsWith(".png") ||
        cleanUrl.endsWith(".webp") || cleanUrl.endsWith(".avif") || cleanUrl.endsWith(".gif") ||
        cleanUrl.endsWith(".bmp") -> AttachmentType.IMAGE

        else -> AttachmentType.FILE
    }
}

@Composable
fun GenericFileItem(url: String) {
    val context = LocalContext.current
    val fileName = url.substringAfterLast('/').ifBlank { "File attachment" }
    val isAudio = isAudioFile(url)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                if (isAudio) {
                    AudioPlayerManager.play(context, url)
                    Toast.makeText(context, "Playing in notification shade...", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.InsertDriveFile,
                contentDescription = "File",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (isAudio) Icons.Default.PlayArrow else Icons.Default.Download,
                contentDescription = if (isAudio) "Play Audio" else "Open/Download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
