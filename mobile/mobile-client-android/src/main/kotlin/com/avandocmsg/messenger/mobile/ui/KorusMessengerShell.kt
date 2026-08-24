package com.avandocmsg.messenger.mobile.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avandocmsg.messenger.mobile.sdk.model.ContactDto
import com.avandocmsg.messenger.mobile.vm.AppState
import com.avandocmsg.messenger.mobile.vm.HomeTab
import com.avandocmsg.messenger.mobile.vm.LiveCallPhase
import com.avandocmsg.messenger.mobile.vm.KorusViewModel
import com.avandocmsg.messenger.mobile.vm.KorusViewModelFactory
import com.avandocmsg.messenger.mobile.vm.Screen

private val KorusLightColors = lightColorScheme(
    primary = Color(0xFF405D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF0A1A3A),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF705575),
    tertiaryContainer = Color(0xFFFAD8FD),
    background = Color(0xFFF9F9FC),
    surface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFFE1E2E8),
    outlineVariant = Color(0xFFC5C6CD)
)

private val KorusDarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF102F60),
    primaryContainer = Color(0xFF294677),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4659),
    tertiary = Color(0xFFDDBCE1),
    tertiaryContainer = Color(0xFF573E5C),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44464F),
    outlineVariant = Color(0xFF44464F)
)

@Composable
fun KorusMobileApp(context: Context) {
    val vm: KorusViewModel = viewModel(factory = KorusViewModelFactory(context))
    val state by vm.state.collectAsState()

    KorusTheme(state.settings.theme) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
            color = MaterialTheme.colorScheme.background
        ) {
            when (state.screen) {
                Screen.ProfilePicker -> ProfilePickerScreen(state, vm)
                Screen.ServerList -> ServerListScreen(state, vm)
                Screen.Login -> LoginScreen(state, vm)
                Screen.Home -> HomeScreen(state, vm)
            }
        }
    }
}

@Composable
private fun KorusTheme(themePreference: String, content: @Composable () -> Unit) {
    val dark = themePreference == "dark" ||
        (themePreference == "system" && isSystemInDarkTheme())

    MaterialTheme(
        colorScheme = if (dark) KorusDarkColors else KorusLightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

@Composable
private fun ProfilePickerScreen(state: AppState, vm: KorusViewModel) {
    Scaffold(
        topBar = { MessengerTopBar(title = "Профили") },
        modifier = Modifier.testTag("profile_picker_screen")
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            BrandIntro(
                title = "Korus Messenger",
                subtitle = "Рабочие чаты на доверенных серверах"
            )

            if (state.profiles.isNotEmpty()) {
                SectionTitle("Ваши профили")
                Column(Modifier.testTag("profile_list")) {
                    state.profiles.forEach { profile ->
                        FlatActionRow(
                            icon = Icons.Outlined.Person,
                            title = profile.displayName,
                            subtitle = "Локальный профиль",
                            onClick = { vm.selectProfile(profile.profileId) },
                            modifier = Modifier.testTag("profile_${profile.profileId}")
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            SectionTitle("Новый профиль")
            Text(
                text = "Профили раздельно хранят серверы и настройки.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.newProfileName,
                onValueChange = vm::onNewProfileName,
                label = { Text("Название профиля") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_profile_name")
            )
            Button(
                onClick = vm::createProfile,
                enabled = state.newProfileName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(52.dp)
                    .testTag("create_profile_button")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать профиль")
            }
        }
    }
}

@Composable
private fun ServerListScreen(state: AppState, vm: KorusViewModel) {
    Scaffold(
        topBar = {
            MessengerTopBar(
                title = "Серверы",
                subtitle = state.activeProfile?.displayName,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationDescription = "К профилям",
                onNavigationClick = vm::switchProfile
            )
        },
        modifier = Modifier.testTag("server_list_screen")
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (state.servers.isNotEmpty()) {
                SectionTitle("Подключённые серверы")
                Column(Modifier.testTag("server_list")) {
                    state.servers.forEach { server ->
                        ServerRow(
                            title = server.displayName,
                            url = server.apiBaseUrl,
                            onLogin = { vm.openLogin(server.serverId) },
                            onOpen = { vm.openChats(server.serverId) },
                            modifier = Modifier.testTag("server_${server.serverId}"),
                            loginTag = "server_login_${server.serverId}",
                            chatsTag = "server_chats_${server.serverId}"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            } else {
                BrandIntro(
                    title = "Подключите Korus",
                    subtitle = "Добавьте адрес доверенного сервера, чтобы войти в рабочие чаты."
                )
            }

            SectionTitle(if (state.servers.isEmpty()) "Первый сервер" else "Добавить сервер")
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = vm::onServerUrl,
                        label = { Text("Адрес сервера") },
                        supportingText = {
                            Text("HTTP разрешён только для локальной лаборатории")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("server_url")
                    )
                    OutlinedTextField(
                        value = state.serverName,
                        onValueChange = vm::onServerName,
                        label = { Text("Название") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("server_name")
                    )
                    Button(
                        onClick = vm::addServer,
                        enabled = state.serverUrl.isNotBlank() && state.serverName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(52.dp)
                            .testTag("add_server_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить")
                    }
                }
            }

            state.error?.let {
                InlineMessage(
                    text = userFacingError(it),
                    isError = true,
                    modifier = Modifier.padding(top = 12.dp).testTag("server_error")
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AppState, vm: KorusViewModel) {
    val serverName = state.servers
        .firstOrNull { it.serverId == state.loginServerId }
        ?.displayName
        ?: "Korus"

    Scaffold(
        topBar = {
            MessengerTopBar(
                title = "Вход",
                subtitle = serverName,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationDescription = "К серверам",
                onNavigationClick = vm::backToServers
            )
        },
        modifier = Modifier.testTag("login_screen")
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(label = serverName, size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Вход на сервер $serverName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Учётная запись мессенджера, не администратор админки.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = vm::onUsername,
                label = { Text("Логин") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("username")
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPassword,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { vm.login() }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .testTag("password")
            )

            state.error?.let {
                InlineMessage(
                    text = userFacingError(it),
                    isError = true,
                    modifier = Modifier.padding(top = 12.dp).testTag("login_error")
                )
            }

            Button(
                onClick = vm::login,
                enabled = state.username.isNotBlank() && state.password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(52.dp)
                    .testTag("login_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Войти")
            }

            state.loggedInUser?.let { user ->
                InlineMessage(
                    text = "Вход выполнен: $user",
                    isError = false,
                    modifier = Modifier.padding(top = 20.dp).testTag("logged_in_label")
                )
                Button(
                    onClick = vm::openChatsFromLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(52.dp)
                        .testTag("open_chats_button")
                ) {
                    Icon(Icons.Filled.ChatBubble, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("К чатам")
                }
                TextButton(onClick = vm::logout) {
                    Text("Выйти")
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: AppState, vm: KorusViewModel) {
    if (state.homeTab == HomeTab.Chats && state.selectedChatId != null) {
        ThreadScreen(state, vm)
        return
    }

    var searchOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HomeTopBar(
                tab = state.homeTab,
                serverName = state.activeServerName,
                onSearch = if (state.homeTab == HomeTab.Chats) {
                    { searchOpen = true }
                } else {
                    null
                },
                onRefresh = if (state.homeTab == HomeTab.Contacts) vm::refreshContacts else null
            )
        },
        bottomBar = {
            HomeNavigation(
                selected = state.homeTab,
                onSelected = vm::selectHomeTab
            )
        },
        modifier = Modifier.testTag("home_screen")
    ) { inner ->
        when (state.homeTab) {
            HomeTab.Chats -> ChatsTab(state, vm, inner)
            HomeTab.Contacts -> ContactsTab(state, inner)
            HomeTab.Addons -> ServicesTab(state, vm, inner)
            HomeTab.Settings -> SettingsTab(state, vm, inner)
        }
    }

    if (searchOpen) {
        MessageSearchDialog(
            state = state,
            vm = vm,
            onDismiss = { searchOpen = false }
        )
    }
}

@Composable
private fun ChatsTab(state: AppState, vm: KorusViewModel, inner: PaddingValues) {
    Box(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .testTag("chats_screen")
    ) {
        if (state.chats.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Чатов пока нет",
                body = "На этом сервере пока нет доступных чатов.",
                actionLabel = "К серверам",
                onAction = vm::backToServers,
                modifier = Modifier.fillMaxSize().testTag("chats_empty")
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("chat_list"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = state.chats,
                    key = { it.resolvedId() }
                ) { chat ->
                    val chatId = chat.resolvedId()
                    val title = chatDisplayTitle(chatId, chat.title)
                    ConversationRow(
                        title = title,
                        onClick = { vm.selectChat(chatId) },
                        modifier = Modifier.testTag("chat_${state.activeServerId}_$chatId")
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 80.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadScreen(state: AppState, vm: KorusViewModel) {
    val chatId = state.selectedChatId ?: return
    val chat = state.chats.firstOrNull { it.resolvedId() == chatId }
    val title = chatDisplayTitle(chatId, chat?.title)
    val context = androidx.compose.ui.platform.LocalContext.current
    val callsEnabled = vm.isAddonEnabled("addon-calls")
    val inCall = state.liveCallPhase != LiveCallPhase.Idle
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.toggleLiveCall()
        }
    }
    fun onCallClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            vm.toggleLiveCall()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = vm::clearChatSelection,
                        modifier = Modifier.testTag("thread_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(label = title, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("thread_title")
                            )
                            Text(
                                text = state.activeServerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.testTag("thread_server_label")
                            )
                        }
                    }
                },
                actions = {
                    if (callsEnabled) {
                        IconButton(
                            onClick = ::onCallClick,
                            modifier = Modifier.testTag("thread_call_button")
                        ) {
                            Icon(
                                imageVector = if (inCall) Icons.Filled.CallEnd else Icons.Filled.Call,
                                contentDescription = if (inCall) "Завершить звонок" else "Позвонить",
                                tint = if (inCall) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            )
        },
        modifier = Modifier.testTag("thread_screen")
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            state.liveCallDetail?.let { detail ->
                InlineMessage(
                    text = detail,
                    isError = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("thread_call_status")
                )
            }
            state.error?.let {
                InlineMessage(
                    text = userFacingError(it),
                    isError = !it.startsWith("Queued offline:", ignoreCase = true),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag(
                            if (it.startsWith("Queued offline:", ignoreCase = true)) {
                                "message_queued_notice"
                            } else {
                                "thread_error"
                            }
                        )
                )
            }

            if (state.messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "Сообщений пока нет",
                    body = "Напишите первое сообщение.",
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("thread_empty")
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("message_list"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(state.messages) { index, message ->
                        MessageBubble(
                            content = message.content,
                            modifier = if (message.id.isNullOrBlank()) {
                                Modifier
                            } else {
                                Modifier.testTag(
                                    "message_${state.activeServerId}_${chatId}_${message.id}"
                                )
                            }
                        )
                    }
                }
            }

            Composer(
                value = state.composeText,
                onValueChange = vm::onComposeText,
                onSend = vm::sendMessage
            )
        }
    }
}

@Composable
private fun ContactsTab(state: AppState, inner: PaddingValues) {
    Box(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .testTag("contacts_screen")
    ) {
        if (state.contacts.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.People,
                title = "Контакты не найдены",
                body = "Каталог этого сервера пока пуст.",
                modifier = Modifier.fillMaxSize().testTag("contacts_empty")
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("contact_list"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = state.contacts,
                    key = { it.resolvedId() }
                ) { contact ->
                    ContactRow(
                        contact = contact,
                        serverId = state.activeServerId.orEmpty()
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 80.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ServicesTab(
    state: AppState,
    vm: KorusViewModel,
    inner: PaddingValues
) {
    val capabilities = state.capabilities
    Box(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .testTag("services_screen")
    ) {
        when {
            capabilities == null -> EmptyState(
                icon = Icons.Outlined.Apps,
                title = "Сервисы не загружены",
                body = "Не удалось определить возможности этого сервера.",
                modifier = Modifier.fillMaxSize().testTag("services_error")
            )
            capabilities.addons.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Apps,
                title = "Нет дополнительных сервисов",
                body = "На этом сервере доступны только основные функции.",
                modifier = Modifier.fillMaxSize().testTag("services_empty")
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item {
                    SectionTitle("Возможности сервера")
                }
                items(capabilities.addons.toList(), key = { it.first }) { (id, addon) ->
                    ServiceRow(
                        title = serviceLabel(id),
                        technicalId = id,
                        enabled = addon.enabled,
                        modifier = Modifier.testTag("addon_$id")
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("Коммуникации")
                    ServiceRow(
                        title = "Push-уведомления",
                        technicalId = "addon-engage",
                        enabled = vm.isAddonEnabled("addon-engage"),
                        modifier = Modifier.testTag("addon_push")
                    )
                    ServiceRow(
                        title = "Звонки",
                        technicalId = "addon-calls",
                        enabled = vm.isAddonEnabled("addon-calls"),
                        modifier = Modifier.testTag("addon_calls")
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(state: AppState, vm: KorusViewModel, inner: PaddingValues) {
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(inner)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen")
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    label = state.activeProfile?.displayName ?: state.loggedInUser,
                    size = 52.dp
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.activeProfile?.displayName ?: "Профиль",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("settings_profile")
                    )
                    Text(
                        text = state.loggedInUser ?: state.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    )
                }
            }
        }

        SectionTitle("Аккаунт", Modifier.padding(top = 24.dp))
        FlatActionRow(
            icon = Icons.Outlined.Storage,
            title = "Сервер",
            subtitle = state.activeServerName,
            onClick = vm::backToServers,
            modifier = Modifier.testTag("settings_server")
        )
        FlatActionRow(
            icon = Icons.Outlined.Person,
            title = "Сменить профиль",
            subtitle = "Серверы и настройки хранятся раздельно",
            onClick = vm::switchProfile,
            modifier = Modifier.testTag("settings_switch_profile_button")
        )

        SectionTitle("Язык", Modifier.padding(top = 24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_locale")
                .testTag("settings_locale_picker"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.settings.locale == "ru",
                onClick = { vm.onSettingsLocale("ru") },
                label = { Text("Русский") },
                leadingIcon = if (state.settings.locale == "ru") {
                    { Icon(Icons.Outlined.Language, contentDescription = null, Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
            FilterChip(
                selected = state.settings.locale == "en",
                onClick = { vm.onSettingsLocale("en") },
                label = { Text("English") }
            )
        }

        SectionTitle("Оформление", Modifier.padding(top = 24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_theme")
                .testTag("settings_theme_picker"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.settings.theme == "system",
                onClick = { vm.onSettingsTheme("system") },
                label = { Text("Как в системе") },
                leadingIcon = if (state.settings.theme == "system") {
                    { Icon(Icons.Outlined.Settings, contentDescription = null, Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
            FilterChip(
                selected = state.settings.theme == "dark",
                onClick = { vm.onSettingsTheme("dark") },
                label = { Text("Тёмная") },
                leadingIcon = if (state.settings.theme == "dark") {
                    { Icon(Icons.Outlined.DarkMode, contentDescription = null, Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }

        SectionTitle("Сессия", Modifier.padding(top = 24.dp))
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("logout_button")
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Выйти с сервера")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти с текущего сервера?") },
            text = { Text("Для повторного входа потребуется корпоративная учётная запись.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.logout()
                    },
                    modifier = Modifier.testTag("logout_confirm_button")
                ) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    modifier = Modifier.testTag("logout_cancel_button")
                ) {
                    Text("Отмена")
                }
            },
            modifier = Modifier.testTag("logout_dialog")
        )
    }
}

@Composable
private fun MessageSearchDialog(
    state: AppState,
    vm: KorusViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск сообщений") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::onSearchQuery,
                    label = { Text("Запрос") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.runSearch() }),
                    modifier = Modifier.fillMaxWidth().testTag("search_input")
                )
                if (state.searchDone) {
                    InlineMessage(
                        text = "Поиск выполнен",
                        isError = false,
                        modifier = Modifier.padding(top = 12.dp).testTag("search_done")
                    )
                } else {
                    Text(
                        text = "Результаты появятся после подключения типизированной выдачи.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = vm::runSearch,
                enabled = state.searchQuery.isNotBlank(),
                modifier = Modifier.testTag("search_button")
            ) {
                Text("Найти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        modifier = Modifier.testTag("message_search_overlay")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessengerTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null
) {
    TopAppBar(
        navigationIcon = {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(navigationIcon, contentDescription = navigationDescription)
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    tab: HomeTab,
    serverName: String,
    onSearch: (() -> Unit)?,
    onRefresh: (() -> Unit)?
) {
    val title = when (tab) {
        HomeTab.Chats -> "Чаты"
        HomeTab.Contacts -> "Контакты"
        HomeTab.Addons -> "Сервисы"
        HomeTab.Settings -> "Настройки"
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("active_server_label")
                )
            }
        },
        actions = {
            if (onSearch != null) {
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier.testTag("open_search_button")
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Поиск сообщений")
                }
            }
            if (onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_contacts_button")
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить контакты")
                }
            }
        }
    )
}

private data class HomeDestination(
    val tab: HomeTab,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val tag: String
)

@Composable
private fun HomeNavigation(selected: HomeTab, onSelected: (HomeTab) -> Unit) {
    val destinations = remember {
        listOf(
            HomeDestination(
                HomeTab.Chats,
                "Чаты",
                Icons.Filled.ChatBubble,
                Icons.Outlined.ChatBubbleOutline,
                "tab_chats"
            ),
            HomeDestination(
                HomeTab.Contacts,
                "Контакты",
                Icons.Filled.People,
                Icons.Outlined.People,
                "tab_contacts"
            ),
            HomeDestination(
                HomeTab.Addons,
                "Сервисы",
                Icons.Filled.Apps,
                Icons.Outlined.Apps,
                "tab_addons"
            ),
            HomeDestination(
                HomeTab.Settings,
                "Настройки",
                Icons.Filled.Settings,
                Icons.Outlined.Settings,
                "tab_settings"
            )
        )
    }

    NavigationBar {
        destinations.forEach { destination ->
            val isSelected = selected == destination.tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelected(destination.tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) {
                            destination.selectedIcon
                        } else {
                            destination.icon
                        },
                        contentDescription = null
                    )
                },
                label = { Text(destination.label) },
                modifier = Modifier.testTag(destination.tag)
            )
        }
    }
}

@Composable
private fun BrandIntro(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "K",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun ServerRow(
    title: String,
    url: String,
    onLogin: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    loginTag: String,
    chatsTag: String
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(label = title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 62.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onLogin,
                modifier = Modifier.height(44.dp).testTag(loginTag)
            ) {
                Text("Войти")
            }
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.height(44.dp).testTag(chatsTag)
            ) {
                Text("Чаты")
            }
        }
    }
}

@Composable
private fun ConversationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(label = title)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactRow(contact: ContactDto, serverId: String) {
    val contactId = contact.resolvedId()
    val title = contact.label()
    val subtitle = contact.login
        ?.takeIf { it.isNotBlank() && it != title }
        ?.let { "@$it" }

    Box(Modifier.testTag("contact_ref_${serverId}_$contactId")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("contact_$contactId"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(label = title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(content: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.82f)
        ) {
            Text(
                text = content?.takeIf(String::isNotBlank) ?: "Сообщение без текста",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("composer")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Сообщение") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).testTag("composer_input")
            )
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(52.dp).testTag("send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun ServiceRow(
    title: String,
    technicalId: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (enabled) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.Block
                    },
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (enabled) {
                    "Доступно на этом сервере"
                } else {
                    "Недоступно на этом сервере"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = technicalId,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun FlatActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Avatar(label: String?, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val paletteIndex = label.orEmpty().hashCode().ushr(1) % 3
    val background = when (paletteIndex) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val foreground = when (paletteIndex) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = CircleShape,
        color = background,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = avatarInitials(label),
                color = foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 18.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun InlineMessage(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) {
                    Icons.Outlined.ErrorOutline
                } else {
                    Icons.Outlined.CheckCircle
                },
                contentDescription = null,
                tint = content
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = content
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

private fun serviceLabel(id: String): String = when (id) {
    "addon-engage" -> "Push-уведомления"
    "addon-calls" -> "Звонки"
    "addon-bot" -> "Чат-боты"
    "addon-live" -> "Трансляции"
    else -> id
}
