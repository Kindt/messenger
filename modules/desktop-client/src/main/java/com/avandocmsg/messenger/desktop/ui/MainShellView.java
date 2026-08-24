package com.avandocmsg.messenger.desktop.ui;



import com.avandocmsg.messenger.desktop.sdk.DesktopPaths;
import com.avandocmsg.messenger.desktop.sdk.DesktopRuntime;

import com.avandocmsg.messenger.desktop.sdk.capabilities.CapabilityGate;

import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;

import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;

import com.avandocmsg.messenger.desktop.sdk.mentions.MentionParser;


import com.avandocmsg.messenger.desktop.sdk.queue.OutgoingMessageQueue;

import com.avandocmsg.messenger.desktop.sdk.call.InProcessCallClient;

import com.avandocmsg.messenger.desktop.sdk.session.DesktopSession;

import com.avandocmsg.messenger.desktop.sdk.ws.CallInviteEvent;

import java.nio.file.Files;

import java.nio.file.Path;

import com.avandocmsg.messenger.desktop.sdk.attachments.AttachmentPathResolver;

import javafx.application.Platform;

import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TitledPane;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;

import javafx.geometry.Insets;

import javafx.scene.Parent;

import javafx.scene.control.Button;

import javafx.scene.control.Label;

import javafx.scene.control.ListView;

import javafx.scene.control.TabPane;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import javafx.scene.layout.BorderPane;

import javafx.scene.layout.HBox;

import javafx.scene.layout.Priority;

import javafx.scene.layout.VBox;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import javafx.stage.FileChooser;



public final class MainShellView {



    private final StackPane root = new StackPane();

    private final BorderPane mainPane = new BorderPane();

    private final Object liveCallLock = new Object();

    private InProcessCallClient liveCall;

    private DesktopCallAudio liveAudio;

    private volatile boolean callBusy;

    public MainShellView(

        DesktopRuntime runtime,

        DesktopSession session,

        String username,

        Runnable onLogout,

        Runnable onRefresh

    ) throws Exception {

        var initialCaps = session.servers().isEmpty()

            ? new com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse()

            : capabilitiesForServer(runtime, session, new ServerId(session.servers().getFirst().serverId()), username);

        var gateRef = new AtomicReference<>(new CapabilityGate(initialCaps));

        var gate = gateRef.get();



        root.getStyleClass().add("qip-root");

        mainPane.getStyleClass().add("qip-root");

        var header = new HBox(12);

        header.setId(DesktopUiIds.SHELL_HEADER);

        header.getStyleClass().add("qip-title-bar");

        header.setPadding(new Insets(8, 12, 8, 12));

        var userAvatar = new Label(initials(runtime.activeProfile().displayName()));

        userAvatar.getStyleClass().add("qip-avatar");

        var titleOrb = new QipStatusOrb();

        titleOrb.setMode(QipStatusOrb.Mode.ONLINE);

        var userCol = new VBox(2);

        userCol.getChildren().add(new Label(runtime.activeProfile().displayName()));

        var userSub = new Label((session.isDemo() ? "Демо · " : "") + username);

        userSub.getStyleClass().add("qip-subtitle");

        userCol.getChildren().add(userSub);

        var status = new ComboBox<String>();

        status.setId(DesktopUiIds.SHELL_USER_STATUS);

        status.getItems().addAll("В сети", "Отошёл", "Занят", "Невидимка");

        status.setValue("В сети");

        status.valueProperty().addListener((o, a, b) -> titleOrb.setMode(mapStatus(b)));

        Region spacer = new Region();

        HBox.setHgrow(spacer, Priority.ALWAYS);

        var logout = DesktopUiIcons.button("🚪", "Выход");

        logout.setId(DesktopUiIds.SHELL_LOGOUT);

        logout.setOnAction(e -> {
            hangupLiveCall();
            onLogout.run();
        });

        header.getChildren().addAll(userAvatar, titleOrb, userCol, status, spacer, logout);



        var inboxRows = new ArrayList<InboxRow>();

        for (var server : session.servers()) {

            var sid = new ServerId(server.serverId());

            for (var chat : session.listChats(sid, username)) {

                inboxRows.add(new InboxRow(server, chat));

            }

        }



        var messages = new ChatMessagePane();

        messages.setCurrentUserId(username);

        var chatTitle = new Label("Выберите контакт");

        chatTitle.getStyleClass().add("qip-chat-header");

        chatTitle.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(chatTitle, Priority.ALWAYS);

        var chatBack = new Button("←");

        chatBack.setId(DesktopUiIds.CHAT_BACK);

        chatBack.getStyleClass().add("qip-btn-icon");

        chatBack.setVisible(false);

        chatBack.setTooltip(new javafx.scene.control.Tooltip("К списку контактов"));

        var chatHeader = new HBox(8, chatBack, chatTitle);

        chatHeader.setId(DesktopUiIds.CHAT_HEADER);

        chatHeader.getStyleClass().add("qip-chat-header-bar");

        chatHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        messages.showEmptyState("Выберите контакт");

        var threadMessages = new TextArea();

        threadMessages.setId(DesktopUiIds.THREAD_MESSAGES);

        threadMessages.setEditable(false);

        threadMessages.setPrefRowCount(6);

        var composer = new TextField();

        composer.setId(DesktopUiIds.COMPOSER);

        composer.setPromptText("Сообщение…");

        var threadIdField = new TextField();

        threadIdField.setId(DesktopUiIds.THREAD_ID);

        threadIdField.setPromptText("thread_id (optional)");

        var attach = DesktopUiIcons.button("📎", "Вложение");
        attach.setId(DesktopUiIds.ATTACH);

        attach.getStyleClass().add("qip-btn-icon");

        attach.setTooltip(new javafx.scene.control.Tooltip("Вложение"));

        var send = DesktopUiIcons.button("➤", "Отправить", "qip-btn-send");

        send.setId(DesktopUiIds.SEND);

        send.getStyleClass().add("qip-btn-send");

        var searchField = new TextField();

        searchField.setId(DesktopUiIds.SEARCH_FIELD);

        searchField.setPromptText("Поиск…");

        var searchBtn = DesktopUiIcons.button("🔍", "Найти");

        searchBtn.setId(DesktopUiIds.SEARCH_BTN);

        var searchResults = new ListView<String>();

        searchResults.setId(DesktopUiIds.SEARCH_RESULTS);

        var callBtn = DesktopUiIcons.button("📞", "Аудиозвонок");
        callBtn.setId(DesktopUiIds.CALL_BTN);
        callBtn.setVisible(session.isDemo() || gate.isEnabled(CapabilityGate.Feature.LIVE_CALLS));

        var videoCallBtn = DesktopUiIcons.button("📹", "Видеозвонок");
        videoCallBtn.setId(DesktopUiIds.VIDEO_CALL_BTN);
        videoCallBtn.setVisible(session.isDemo() || gate.isEnabled(CapabilityGate.Feature.LIVE_CALLS));

        var pollBtn = DesktopUiIcons.button("📊", "Опрос");
        pollBtn.setId(DesktopUiIds.POLL_BTN);

        pollBtn.setVisible(gate.isEnabled(CapabilityGate.Feature.PRODUCTIVITY));



        var inboxMaster = FXCollections.observableArrayList(inboxRows);

        var inboxFiltered = new FilteredList<>(inboxMaster, row -> true);

        var inbox = new ListView<InboxRow>();

        inbox.setId(DesktopUiIds.INBOX_LIST);

        inbox.getStyleClass().add("qip-contact-list");

        inbox.setItems(inboxFiltered);

        inbox.setCellFactory(lv -> new ContactListCell());

        var contactFilter = new TextField();

        contactFilter.setId(DesktopUiIds.CONTACT_FILTER);

        contactFilter.setPromptText("Поиск контакта…");

        contactFilter.getStyleClass().add("qip-contact-filter");

        contactFilter.textProperty().addListener((o, a, b) -> {

            var q = b == null ? "" : b.trim().toLowerCase();

            if (q.isEmpty()) {

                inboxFiltered.setPredicate(row -> true);

            } else {

                inboxFiltered.setPredicate(row -> row.label().toLowerCase().contains(q));

            }

        });



        var queue = new OutgoingMessageQueue(runtime.profileStore().stateDir(runtime.activeProfile().profileId()));



        Runnable loadMessages = () -> {

            var row = inbox.getSelectionModel().getSelectedItem();

            if (row == null) {

                chatTitle.setText("Выберите контакт");

                messages.showEmptyState("Выберите контакт");

                return;

            }

            messages.showLoading();

            try {

                var sid = new ServerId(row.server().serverId());

                var list = session.listMessages(sid, username, row.chatRef(), null);

                messages.setMessages(list);

                chatTitle.setText(row.label() + (gateRef.get().isEnabled(CapabilityGate.Feature.E2EE) ? "  🔒 E2EE" : ""));

                session.markRead(sid, username, row.chatRef());

            } catch (Exception ex) {

                messages.showError("Ошибка: " + ex.getMessage());

            }

        };

        messages.setRetryAction(loadMessages);



        Runnable loadThread = () -> {

            var row = inbox.getSelectionModel().getSelectedItem();

            if (row == null || threadIdField.getText().isBlank()) {

                threadMessages.clear();

                return;

            }

            try {

                var sid = new ServerId(row.server().serverId());

                var list = session.listMessages(sid, username, row.chatRef(), threadIdField.getText().trim());

                var sb = new StringBuilder();

                for (var m : list) {

                    sb.append(m.content()).append('\n');

                }

                threadMessages.setText(sb.toString());

            } catch (Exception ex) {

                threadMessages.setText(ex.getMessage());

            }

        };





        threadIdField.textProperty().addListener((o, a, b) -> loadThread.run());



        send.setOnAction(e -> sendMessage(session, username, inbox, composer, threadIdField, queue, messages, loadMessages));

        send.setDefaultButton(true);

        composer.setOnAction(e -> send.fire());

        attach.setOnAction(e -> pickAttachment(runtime, session, username, inbox, messages, loadMessages));



        composer.textProperty().addListener((o, a, b) -> {

            if (b == null || b.isBlank() || session.isDemo()) {

                return;

            }

            var row = inbox.getSelectionModel().getSelectedItem();

            if (row == null) {

                return;

            }

            try {

                session.sendTyping(new ServerId(row.server().serverId()), username, row.chatRef());

            } catch (Exception ignored) {

                // typing is best-effort

            }

        });



        searchBtn.setOnAction(e -> {

            if (!gateRef.get().isEnabled(CapabilityGate.Feature.SEARCH)) {

                searchResults.getItems().setAll("Search addon off");

                return;

            }

            var row = inbox.getSelectionModel().getSelectedItem();

            if (row == null) {

                return;

            }

            try {

                var sid = new ServerId(row.server().serverId());

                var resp = session.search(sid, username, searchField.getText());

                var lines = resp.hits().stream()

                    .map(h -> h.title() + " — " + h.snippet())

                    .toList();

                searchResults.getItems().setAll(lines);

            } catch (Exception ex) {

                searchResults.getItems().setAll("ERR: " + ex.getMessage());

            }

        });



        callBtn.setOnAction(e -> launchCall(session, username, inbox, messages, "audio"));

        videoCallBtn.setOnAction(e -> launchCall(session, username, inbox, messages, "video"));



        var chatsTab = DesktopUiIcons.tab(DesktopUiIds.TAB_CHATS, "💬", "Чаты", null);

        var threadPane = new TitledPane("Ответы в треде", new VBox(6, threadIdField, threadMessages));

        threadPane.setId(DesktopUiIds.THREAD_TOGGLE);

        threadPane.getStyleClass().add("qip-thread-pane");

        threadPane.setExpanded(false);

        threadMessages.getStyleClass().add("qip-messages");

        var emojiBtn = DesktopUiIcons.button("☺", "Смайлы");
        emojiBtn.setId(DesktopUiIds.EMOJI_BTN);

        EmojiPickerPopup.attach(emojiBtn, composer);

        var composerTools = new HBox(4, attach, emojiBtn);

        composerTools.getStyleClass().add("qip-composer-tools");

        var composerCaps = new HBox(4, pollBtn, callBtn, videoCallBtn);

        composerCaps.getStyleClass().add("qip-composer-caps");

        var composerBar = new HBox(8, composerTools, composer, composerCaps, send);

        composerBar.getStyleClass().add("qip-composer-bar");

        HBox.setHgrow(composer, Priority.ALWAYS);

        var chatBody = new VBox(messages, threadPane, composerBar);

        VBox.setVgrow(messages, Priority.ALWAYS);

        chatBody.getStyleClass().add("qip-chat-panel");

        var chatLayout = new BorderPane();

        chatLayout.setTop(chatHeader);

        chatLayout.setCenter(chatBody);

        var contactPanel = new VBox(6, contactFilter, inbox);

        contactPanel.getStyleClass().add("qip-contact-panel");

        VBox.setVgrow(inbox, Priority.ALWAYS);

        contactPanel.setPrefWidth(280);
        contactPanel.setMinWidth(240);
        contactPanel.setMaxWidth(320);

        var split = new BorderPane();

        split.setLeft(contactPanel);

        split.setCenter(chatLayout);

        final double narrowBreakpoint = 960;

        Runnable applyNarrowLayout = () -> {

            if (root.getScene() == null) {

                return;

            }

            var w = root.getScene().getWidth();

            var narrow = w > 0 && w < narrowBreakpoint;

            var row = inbox.getSelectionModel().getSelectedItem();

            if (!narrow) {

                split.setLeft(contactPanel);

                split.setCenter(chatLayout);

                chatBack.setVisible(false);

                return;

            }

            if (row == null) {

                split.setLeft(contactPanel);

                split.setCenter(null);

                chatBack.setVisible(false);

            } else {

                split.setLeft(null);

                split.setCenter(chatLayout);

                chatBack.setVisible(true);

            }

        };

        chatBack.setOnAction(e -> {

            inbox.getSelectionModel().clearSelection();

            loadMessages.run();

        });

        chatsTab.setContent(split);



        var searchTab = DesktopUiIcons.tab(DesktopUiIds.TAB_SEARCH, "🔍", "Поиск", new VBox(8, searchField, searchBtn, searchResults));



        Runnable refreshGateRunnable = () -> {

            var row = inbox.getSelectionModel().getSelectedItem();

            if (row == null) {

                return;

            }

            var sid = new ServerId(row.server().serverId());

            var rowCaps = capabilitiesForServer(runtime, session, sid, username);

            var g = new CapabilityGate(rowCaps);

            gateRef.set(g);

            searchTab.setDisable(!g.isEnabled(CapabilityGate.Feature.SEARCH));

            pollBtn.setVisible(g.isEnabled(CapabilityGate.Feature.PRODUCTIVITY));

            callBtn.setVisible(session.isDemo() || g.isEnabled(CapabilityGate.Feature.LIVE_CALLS));

            videoCallBtn.setVisible(session.isDemo() || g.isEnabled(CapabilityGate.Feature.LIVE_CALLS));

        };

        inbox.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {

            loadMessages.run();

            refreshGateRunnable.run();

            applyNarrowLayout.run();

        });



        searchTab.setDisable(!gateRef.get().isEnabled(CapabilityGate.Feature.SEARCH));
        if (searchTab.isDisabled()) {
            searchTab.setTooltip(new javafx.scene.control.Tooltip("Поиск отключён на сервере"));
        }

        var settingsTab = DesktopUiIcons.tab(
            DesktopUiIds.TAB_SETTINGS,
            "⚙",
            "Настройки",
            new SettingsView(runtime, initialCaps, onRefresh).root()
        );

        var serversTab = DesktopUiIcons.tab(
            DesktopUiIds.TAB_SERVERS,
            "🖥",
            "Серверы",
            buildServersPanel(runtime, session, username, onRefresh)
        );



        var tabs = new TabPane(chatsTab, searchTab, settingsTab, serversTab);

        tabs.setId(DesktopUiIds.SHELL_TABS);

        tabs.getStyleClass().addAll("qip-tabs", "qip-nav-rail-tabs");

        tabs.setSide(javafx.geometry.Side.LEFT);

        mainPane.setTop(header);

        mainPane.setCenter(tabs);

        com.avandocmsg.messenger.desktop.sdk.security.SecuritySettings securityPolicy;

        try {

            securityPolicy = new com.avandocmsg.messenger.desktop.sdk.security.SecuritySettingsStore(

                runtime.profileStore(),

                runtime.activeProfile().profileId()

            ).read();

        } catch (Exception ex) {

            securityPolicy = com.avandocmsg.messenger.desktop.sdk.security.SecuritySettings.fstecMaximum();

        }

        var lockOverlay = new SessionLockOverlay(() -> { });

        root.getChildren().add(mainPane);

        if (!"false".equals(System.getProperty("korus.desktop.session.lock", "true"))) {

            root.getChildren().add(lockOverlay);

            if (securityPolicy.idleLockMinutes() > 0) {

            var idle = new javafx.animation.PauseTransition(

                javafx.util.Duration.minutes(securityPolicy.idleLockMinutes())

            );

            idle.setOnFinished(ev -> lockOverlay.lock());

            Runnable resetIdle = () -> idle.playFromStart();

            root.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, ev -> resetIdle.run());

            root.addEventFilter(javafx.scene.input.KeyEvent.ANY, ev -> resetIdle.run());

            resetIdle.run();

            }

        }



        flushQueue(session, username, queue, messages);



        if (!inboxRows.isEmpty()) {

            inbox.getSelectionModel().select(0);

        }



        final var securityPolicyFinal = securityPolicy;

        if (!session.isDemo() && runtime.wsHub() != null) {

            runtime.wsHub().setGlobalHandler(json -> Platform.runLater(() -> {

                var preview = com.avandocmsg.messenger.desktop.sdk.ws.WsEventPreview.parse(json);

                var stage = root.getScene() == null ? null : (javafx.stage.Stage) root.getScene().getWindow();

                var focusedChat = inbox.getSelectionModel().getSelectedItem();

                var sameChat = focusedChat != null
                    && preview.chatId() != null
                    && preview.chatId().equals(focusedChat.chatRef().chatId());

                var alert = !preview.isOwnMessage(username)
                    && (stage == null || !stage.isFocused() || stage.isIconified() || !sameChat);

                if (alert && securityPolicyFinal.osNotificationsEnabled() && DesktopOsNotifications.isSupported()) {

                    DesktopOsNotifications.show(preview.title(), preview.body());

                }

                if (alert && securityPolicyFinal.soundNotifications()) {

                    DesktopNotificationSound.playIncoming();

                }

                handleIncomingCall(session, username, inbox, messages, json);

                loadMessages.run();

            }));

        }



        root.sceneProperty().addListener((obs, oldScene, scene) -> {

            if (scene == null) {

                return;

            }

            var stage = (javafx.stage.Stage) scene.getWindow();

            if (stage != null && !header.getStyleClass().contains("qip-title-draggable")) {

                header.getStyleClass().add("qip-title-draggable");

                WindowChromeHelper.wireDrag(stage, header);

                var minBtn = WindowChromeHelper.minimizeButton(stage);

                minBtn.setId(DesktopUiIds.WIN_MIN);

                var maxBtn = WindowChromeHelper.maximizeButton(stage);

                maxBtn.setId(DesktopUiIds.WIN_MAX);

                var closeBtn = WindowChromeHelper.closeButton(stage);

                closeBtn.setId(DesktopUiIds.WIN_CLOSE);

                var winBox = new HBox(4, minBtn, maxBtn, closeBtn);

                winBox.getStyleClass().add("qip-win-controls");

                header.getChildren().add(header.getChildren().indexOf(logout), winBox);

            }

            scene.widthProperty().addListener((o, a, b) -> applyNarrowLayout.run());

            applyNarrowLayout.run();

            if (!session.servers().isEmpty()) {
                try {
                    var server = session.servers().getFirst();
                    var sid = new ServerId(server.serverId());
                    var token = session.isDemo() ? null : runtime.sessions().token(sid, username);
                    var api = session.isDemo() ? null : runtime.sessions().clientFor(server);
                    var snap = runtime.brandingService().fetchAndCache(session, server, username, api, token);
                    com.avandocmsg.messenger.desktop.ui.branding.DesktopBrandingApplier.apply(scene, snap, true);
                } catch (Exception ignored) {
                    // offline fallback
                }
            }
        });

    }



    private static void sendMessage(

        DesktopSession session,

        String username,

        ListView<InboxRow> inbox,

        TextInputControl composer,

        TextField threadIdField,

        OutgoingMessageQueue queue,

        ChatMessagePane messages,

        Runnable reload

    ) {

        var row = inbox.getSelectionModel().getSelectedItem();

        if (row == null || composer.getText().isBlank()) {

            return;

        }

        var content = composer.getText().trim();

        MentionParser.parseMentionedUserIds(content);

        var ref = row.chatRef();

        var threadId = threadIdField.getText().isBlank() ? null : threadIdField.getText().trim();

        try {

            session.send(ref.serverId(), username, ref, content, threadId);

            composer.clear();

            reload.run();

        } catch (Exception ex) {

            try {

                queue.enqueue(ref, content, threadId);

                messages.appendText("\n[offline queued] " + content);

                composer.clear();

            } catch (Exception qex) {

                messages.appendText("\n[send failed] " + ex.getMessage());

            }

        }

    }



    private static void pickAttachment(

        DesktopRuntime runtime,

        DesktopSession session,

        String username,

        ListView<InboxRow> inbox,

        ChatMessagePane messages,

        Runnable reload

    ) {

        var row = inbox.getSelectionModel().getSelectedItem();

        if (row == null) {

            return;

        }

        try {

            var source = testAttachFile();

            if (source == null) {

                var chooser = new FileChooser();

                var selected = chooser.showOpenDialog(null);

                if (selected == null) {

                    return;

                }

                source = selected.toPath();

            }

            if (session.isDemo()) {

                var resolver = new AttachmentPathResolver(

                    DesktopPaths.downloadsRoot(),

                    runtime.activeProfile().displayName()

                );

                var target = resolver.resolve(

                    row.server().displayName(),

                    java.util.UUID.randomUUID().toString(),

                    source.getFileName().toString()

                );

                Files.createDirectories(target.getParent());

                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                messages.appendText("\n[attach] " + target);

                return;

            }

            var sid = new ServerId(row.server().serverId());

            var sent = session.sendFile(sid, username, row.chatRef(), source, null);

            messages.appendText("\n[file sent] " + sent.id());

            reload.run();

        } catch (Exception ex) {

            messages.appendText("\n[attach failed] " + ex.getMessage());

        }

    }



    private static com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse capabilitiesForServer(

        DesktopRuntime runtime,

        DesktopSession session,

        ServerId serverId,

        String username

    ) {

        if (runtime.capabilitiesCache() != null) {

            var cached = runtime.capabilitiesCache().get(serverId);

            if (cached.capabilities() != null && !cached.capabilities().isEmpty()) {

                return cached;

            }

        }

        try {

            return session.capabilities(serverId, username);

        } catch (Exception ex) {

            return new com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse();

        }

    }



    private static Path testAttachFile() {

        var p = System.getProperty("korus.desktop.test.attach.file");

        return p == null || p.isBlank() ? null : Path.of(p);

    }



    private static Parent buildServersPanel(
        DesktopRuntime runtime,
        DesktopSession session,
        String username,
        Runnable onRefresh
    ) {
        var list = new ListView<String>();
        list.setId(DesktopUiIds.SERVERS_LIST);
        list.getStyleClass().add("qip-servers-list");
        list.setPrefHeight(220);
        list.getItems().addAll(session.servers().stream()
            .map(s -> s.displayName() + " — " + s.apiBaseUrl() + (s.paused() ? " [paused]" : ""))
            .toList());
        var refresh = DesktopUiIcons.button("🔄", "Обновить список");
        refresh.setId(DesktopUiIds.SERVERS_REFRESH);
        refresh.setOnAction(e -> onRefresh.run());
        var settings = DesktopUiIcons.button("⚙", "VPN и брендинг сервера");
        settings.setId(DesktopUiIds.SERVER_SETTINGS);
        settings.setOnAction(e -> {
            var idx = list.getSelectionModel().getSelectedIndex();
            if (idx < 0 || idx >= session.servers().size()) {
                return;
            }
            var server = session.servers().get(idx);
            var stage = (javafx.stage.Stage) list.getScene().getWindow();
            ServerSettingsDialog.show(stage, runtime, session, server, username, onRefresh);
        });
        var emptyHint = new Label("Добавьте сервер для подключения к API");
        emptyHint.setId(DesktopUiIds.SERVERS_EMPTY);
        emptyHint.setVisible(session.servers().isEmpty());
        list.getItems().addListener((javafx.collections.ListChangeListener<String>) c ->
            emptyHint.setVisible(list.getItems().isEmpty())
        );
        var panel = new VBox(
            8,
            new Label("Зарегистрированные серверы"),
            emptyHint,
            list,
            new HBox(8, refresh, settings, serversAddBtn(runtime, session, username, onRefresh))
        );
        panel.getStyleClass().add("qip-servers-panel");
        VBox.setVgrow(list, Priority.ALWAYS);
        return panel;
    }

    private static Button serversAddBtn(
        DesktopRuntime runtime,
        DesktopSession session,
        String username,
        Runnable onRefresh
    ) {
        var add = DesktopUiIcons.button("➕", "Добавить сервер");
        add.setId(DesktopUiIds.SERVERS_ADD);
        add.setDisable(session.isDemo());
        if (session.isDemo()) {
            add.setTooltip(new javafx.scene.control.Tooltip("В демо-режиме серверы не добавляются"));
        }
        add.setOnAction(e -> ServerAddDialog.show(runtime, username, onRefresh));
        return add;
    }



    private static void flushQueue(

        DesktopSession session,

        String username,

        OutgoingMessageQueue queue,

        ChatMessagePane messages

    ) throws Exception {

        for (var pending : queue.load()) {

            try {

                var serverId = new ServerId(pending.serverId());

                var ref = new ChatRef(serverId, pending.chatId());

                session.send(serverId, username, ref, pending.content(), pending.threadId());

                queue.remove(pending.id());

                messages.appendText("\n[flushed] " + pending.content());

            } catch (Exception ignored) {

                queue.bumpAttempts(pending.id());

            }

        }

    }



    private void launchCall(
        DesktopSession session,
        String username,
        ListView<InboxRow> inbox,
        ChatMessagePane messages,
        String mediaMode
    ) {
        if (session.isDemo()) {
            try {
                var row = selectedInboxRow(inbox);
                if (row == null) {
                    return;
                }
                var sid = new ServerId(row.server().serverId());
                var joinUrl = session.startCall(sid, username, row.chatRef(), mediaMode);
                messages.appendText("\n[call] " + joinUrl);
            } catch (Exception ex) {
                messages.appendText("\n[call failed] " + ex.getMessage());
            }
            return;
        }
        if (liveCall != null) {
            hangupLiveCall();
            return;
        }
        var row = selectedInboxRow(inbox);
        if (row == null) {
            return;
        }
        startLiveCallAsync(session, username, row, mediaMode, messages, null);
    }

    private void handleIncomingCall(
        DesktopSession session,
        String username,
        ListView<InboxRow> inbox,
        ChatMessagePane messages,
        String json
    ) {
        if (session.isDemo()) {
            return;
        }
        var invite = CallInviteEvent.parse(json);
        if (invite == null || !invite.invited()) {
            return;
        }
        synchronized (liveCallLock) {
            if (liveCall != null && liveCall.join() != null
                && invite.sessionId().equals(liveCall.join().sessionId())) {
                return;
            }
            if (callBusy || liveCall != null) {
                return;
            }
        }
        var row = inboxRowForChat(inbox, invite.chatId());
        if (row == null) {
            return;
        }
        messages.appendText("\n[incoming call] подключаюсь…");
        startLiveCallAsync(session, username, row, invite.mediaIntent(), messages, invite.sessionId());
    }

    private void startLiveCallAsync(
        DesktopSession session,
        String username,
        InboxRow row,
        String mediaMode,
        ChatMessagePane messages,
        String sessionId
    ) {
        if (!callBusyCompareAndSet()) {
            return;
        }
        Thread.ofVirtual().name("korus-desktop-live-call").start(() -> {
            InProcessCallClient client = null;
            try {
                var sid = new ServerId(row.server().serverId());
                client = sessionId == null || sessionId.isBlank()
                    ? session.startLiveCall(sid, username, row.chatRef(), mediaMode)
                    : session.joinLiveCall(sid, username, row.chatRef(), sessionId);
                var audio = DesktopCallAudio.start(client);
                synchronized (liveCallLock) {
                    liveCall = client;
                    liveAudio = audio;
                }
                client.onHangup(() -> Platform.runLater(() -> {
                    hangupLiveCall();
                    messages.appendText("\n[call] завершён");
                }));
                var capture = audio.captureEnabled() ? "микрофон" : "без микрофона";
                var playback = audio.playbackEnabled() ? "динамик" : "без динамика";
                Platform.runLater(() -> messages.appendText(
                    "\n[call] разговор начат · " + capture + " · " + playback
                ));
            } catch (Exception ex) {
                if (client != null) {
                    client.leave();
                }
                hangupLiveCall();
                Platform.runLater(() -> messages.appendText("\n[call failed] " + ex.getMessage()));
            }
        });
    }

    private boolean callBusyCompareAndSet() {
        synchronized (liveCallLock) {
            if (callBusy) {
                return false;
            }
            callBusy = true;
            return true;
        }
    }

    private void hangupLiveCall() {
        InProcessCallClient client;
        DesktopCallAudio audio;
        synchronized (liveCallLock) {
            client = liveCall;
            audio = liveAudio;
            liveCall = null;
            liveAudio = null;
            callBusy = false;
        }
        if (audio != null) {
            audio.close();
        }
        if (client != null) {
            client.leave();
        }
    }

    private static InboxRow selectedInboxRow(ListView<InboxRow> inbox) {
        var row = inbox.getSelectionModel().getSelectedItem();
        if (row == null && !inbox.getItems().isEmpty()) {
            return inbox.getItems().getFirst();
        }
        return row;
    }

    private static InboxRow inboxRowForChat(ListView<InboxRow> inbox, String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return selectedInboxRow(inbox);
        }
        for (var row : inbox.getItems()) {
            if (chatId.equals(row.chatRef().chatId()) || chatId.equals(row.chat().resolvedId())) {
                return row;
            }
        }
        return selectedInboxRow(inbox);
    }

    private static QipStatusOrb.Mode mapStatus(String label) {
        if (label == null) {
            return QipStatusOrb.Mode.OFFLINE;
        }
        return switch (label) {
            case "В сети" -> QipStatusOrb.Mode.ONLINE;
            case "Отошёл" -> QipStatusOrb.Mode.AWAY;
            case "Занят" -> QipStatusOrb.Mode.BUSY;
            default -> QipStatusOrb.Mode.OFFLINE;
        };
    }

    private static String initials(String name) {

        if (name == null || name.isBlank()) {

            return "?";

        }

        var parts = name.trim().split("\\s+");

        if (parts.length >= 2) {

            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();

        }

        return name.substring(0, Math.min(2, name.length())).toUpperCase();

    }



    public Parent root() {

        return root;

    }

}
