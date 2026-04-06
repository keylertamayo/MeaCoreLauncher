package com.experimento.launcher;

import com.experimento.launcher.model.JvmPresetKind;
import com.experimento.launcher.model.LauncherProfile;
import com.experimento.launcher.model.ServerEntry;
import com.experimento.launcher.mojang.ManifestVersionEntry;
import com.experimento.launcher.paths.LauncherDirectories;
import com.experimento.launcher.service.AutoOptimizerService;
import com.experimento.launcher.service.HardwareProbe;
import com.experimento.launcher.service.JvmPresetService;
import com.experimento.launcher.service.LauncherFacade;
import com.experimento.launcher.util.OfflineUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import netscape.javascript.JSObject;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherApp extends Application {

    private static final String BG_DEEP = "#0d0f11";
    private static final String BG_PANEL = "#13181e";
    private static final String BORDER = "#1e2d3d";
    private static final String ACCENT = "#00e5a0";
    private static final String TEXT_PRI = "#e8f0f7";
    private static final String TEXT_SEC = "#6b8099";
    private static final String TEXT_MUT = "#3d5068";

    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ManifestVersionEntry> allManifestEntries = new ArrayList<>();
    private boolean syncingVersionUi;
    private boolean syncingIdentityUi;

    private LauncherFacade facade;
    private List<LauncherProfile> profiles;
    private LauncherProfile selected;

    private ListView<LauncherProfile> profileList;
    private ListView<ManifestVersionEntry> installListView;
    private TextField displayNameField;
    private TextField usernameField;
    private TextField uuidField;
    private ComboBox<ManifestVersionEntry> versionCombo;
    private ComboBox<String> versionFilter;
    private ComboBox<JvmPresetKind> presetCombo;
    private TextArea jvmArea;
    private CheckBox globalMcCheck;
    private TableView<ServerEntry> serverTable;
    private TextArea logArea;
    private Label modHintLabel;
    private Label aternosHint;
    private Label statusDot;
    private HttpServer frontendServer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        LauncherDirectories dirs = LauncherDirectories.fromDefault();
        dirs.ensureBaseDirs();
        facade = new LauncherFacade(dirs);
        profiles = new ArrayList<>(facade.profiles().loadOrCreateDefault());
        for (LauncherProfile p : profiles) {
            LauncherFacade.maybeImportTlauncherJvm(p);
        }
        facade.profiles().save(profiles);

        if (startFrontendUi(stage)) {
            return;
        }

        profileList = new ListView<>(FXCollections.observableList(profiles));
        profileList.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(LauncherProfile item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setGraphic(null);
                                } else {
                                    VBox box = new VBox(2);
                                    Label name = new Label(item.displayName);
                                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
                                    Label user = new Label(item.username);
                                    user.setStyle("-fx-font-size: 10px; -fx-text-fill: " + TEXT_SEC + ";");
                                    box.getChildren().addAll(name, user);
                                    setGraphic(box);
                                    setText(null);
                                }
                            }
                        });
        profileList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> bindProfile(n));
        profileList.getSelectionModel().selectFirst();

        installListView = new ListView<>();
        installListView.setPlaceholder(new Label("Cargando manifiesto de versiones…"));

        displayNameField = new TextField();
        usernameField = new TextField();
        uuidField = new TextField();
        uuidField.setPromptText("UUID offline");

        versionFilter =
                new ComboBox<>(
                        FXCollections.observableArrayList(
                                "Todas",
                                "Solo releases",
                                "Solo snapshots",
                                "Clásicas (beta/alpha)"));
        versionFilter.setPrefWidth(140);
        versionFilter.setValue("Todas");
        versionFilter.setOnAction(e -> applyVersionFilter());

        versionCombo = new ComboBox<>();
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(versionCombo, Priority.ALWAYS);
        versionCombo.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ManifestVersionEntry item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item.label());
                            }
                        });
        versionCombo.setButtonCell(
                new ListCell<>() {
                    @Override
                    protected void updateItem(ManifestVersionEntry item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.label());
                    }
                });
        versionCombo
                .valueProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (!syncingVersionUi && selected != null && n != null) {
                                selected.lastVersionId = n.id();
                            }
                        });

        Button refreshManifestBtn = new Button("Actualizar lista");
        refreshManifestBtn.setOnAction(e -> loadVersionManifestAsync());

        presetCombo = new ComboBox<>(FXCollections.observableArrayList(JvmPresetKind.values()));
        jvmArea = new TextArea();
        jvmArea.setPrefRowCount(3);
        globalMcCheck = new CheckBox("Usar ~/.minecraft global (avanzado)");
        Button syncUuidBtn = new Button("UUID desde nombre");
        syncUuidBtn.setOnAction(e -> syncUuidFromUsername());

        displayNameField
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null && !syncingIdentityUi) {
                                selected.displayName = n;
                                syncIdentityFromDisplayName(n);
                            }
                            profileList.refresh();
                        });
        usernameField
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null && !syncingIdentityUi) {
                                selected.username = n;
                                syncUuidFromUsername();
                            }
                        });
        uuidField
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null) {
                                selected.offlineUuid = n;
                            }
                        });
        presetCombo.setOnAction(
                e -> {
                    if (selected != null && presetCombo.getValue() != null) {
                        selected.jvmPreset = presetCombo.getValue();
                        refreshHints();
                    }
                });
        jvmArea
                .textProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (selected != null) {
                                selected.customJvmArgs = n;
                            }
                        });
        globalMcCheck.setOnAction(
                e -> {
                    if (selected != null) {
                        selected.useGlobalMinecraftFolder = globalMcCheck.isSelected();
                    }
                });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        int r = 0;
        form.add(new Label("Nombre visible"), 0, r);
        form.add(displayNameField, 1, r);
        r++;
        // Usuario MC y UUID se manejan internamente a partir de "Nombre visible".
        // Se ocultan en la UI para simplificar la experiencia.
        form.add(new Label("Versión"), 0, r);
        HBox versionRow = new HBox(8, versionFilter, versionCombo, refreshManifestBtn);
        HBox.setHgrow(versionCombo, Priority.ALWAYS);
        form.add(versionRow, 1, r);
        r++;
        form.add(new Label("Preset JVM"), 0, r);
        form.add(presetCombo, 1, r);
        r++;
        form.add(new Label("JVM extra"), 0, r);
        form.add(jvmArea, 1, r);
        r++;
        form.add(globalMcCheck, 1, r);
        r++;

        serverTable = new TableView<>();
        TableColumn<ServerEntry, String> colName = new TableColumn<>("Nombre");
        colName.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue() == null ? "" : cd.getValue().name));
        TableColumn<ServerEntry, String> colAddr = new TableColumn<>("IP:puerto");
        colAddr.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue() == null ? "" : cd.getValue().address));
        TableColumn<ServerEntry, Boolean> colCracked = new TableColumn<>("Cracked");
        colCracked.setCellValueFactory(
                cd -> {
                    ServerEntry v = cd.getValue();
                    boolean b = v != null && v.crackedServer;
                    return new javafx.beans.property.SimpleBooleanProperty(b).asObject();
                });
        colCracked.setCellFactory(
                column ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Boolean item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                } else {
                                    ServerEntry se = getTableRow().getItem();
                                    CheckBox cb = new CheckBox("Servidor cracked / Aternos");
                                    cb.setSelected(se.crackedServer);
                                    cb.setOnAction(ev -> {
                                        se.crackedServer = cb.isSelected();
                                        refreshAternosRowHint(se);
                                    });
                                    setGraphic(cb);
                                }
                            }
                        });
        colName.setPrefWidth(160);
        colAddr.setPrefWidth(260);
        serverTable.getColumns().addAll(colName, colAddr, colCracked);
        serverTable.setPrefHeight(180);
        serverTable.setEditable(true);
        colName.setCellFactory(TextFieldTableCell.forTableColumn());
        colName.setOnEditCommit(
                ev -> {
                    ServerEntry se = ev.getRowValue();
                    if (se != null) {
                        se.name = ev.getNewValue();
                    }
                });
        colAddr.setCellFactory(TextFieldTableCell.forTableColumn());
        colAddr.setOnEditCommit(
                ev -> {
                    ServerEntry se = ev.getRowValue();
                    if (se != null) {
                        se.address = ev.getNewValue();
                    }
                });

        Button addSrv = new Button("Añadir servidor");
        addSrv.setOnAction(
                e -> {
                    if (selected == null) {
                        return;
                    }
                    if (selected.servers == null) {
                        selected.servers = new ArrayList<>();
                    }
                    ServerEntry se = new ServerEntry("Mi servidor", "nombre.aternos.me:25565");
                    se.crackedServer = true;
                    selected.servers.add(se);
                    serverTable.setItems(FXCollections.observableList(selected.servers));
                });
        Button delSrv = new Button("Quitar selección");
        delSrv.setOnAction(
                e -> {
                    if (selected == null) {
                        return;
                    }
                    ServerEntry se = serverTable.getSelectionModel().getSelectedItem();
                    if (se != null) {
                        selected.servers.remove(se);
                        serverTable.setItems(FXCollections.observableList(selected.servers));
                    }
                });

        aternosHint =
                new Label(
                        "UX Aternos: marca «Cracked» si en Aternos tienes Cracked ON (usuario offline). "
                                + "Si Cracked OFF, necesitas cuenta premium (login Microsoft, fase futura). "
                                + "Misma versión MC que el servidor; modded → mismo loader en el cliente.");
        aternosHint.setWrapText(true);
        modHintLabel = new Label();
        modHintLabel.setWrapText(true);

        VBox center =
                new VBox(
                        8,
                        form,
                        new Separator(),
                        new Label("Servidores → servers.dat (NBT gzip)"),
                        serverTable,
                        new HBox(8, addSrv, delSrv),
                        aternosHint,
                        modHintLabel);
        center.setPadding(new Insets(8));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);

        Button newProfile = new Button("＋ Nuevo perfil");
        newProfile.setOnAction(
                e -> {
                    LauncherProfile p = LauncherProfile.createDefault();
                    profiles.add(p);
                    profileList.setItems(FXCollections.observableList(profiles));
                    profileList.getSelectionModel().select(p);
                    try {
                        facade.profiles().save(profiles);
                    } catch (Exception ex) {
                        log(ex.getMessage());
                    }
                });
        Button deleteProfile = new Button("🗑 Eliminar perfil");
        deleteProfile.setOnAction(
                e -> {
                    if (selected == null) {
                        return;
                    }
                    if (profiles.size() <= 1) {
                        log("Debe existir al menos un perfil.");
                        return;
                    }
                    LauncherProfile toRemove = selected;
                    profiles.remove(toRemove);
                    profileList.setItems(FXCollections.observableList(profiles));
                    profileList.getSelectionModel().selectFirst();
                    try {
                        facade.profiles().save(profiles);
                        log("Perfil eliminado: " + toRemove.displayName);
                    } catch (Exception ex) {
                        log("Error eliminando perfil: " + ex.getMessage());
                    }
                });
        Button saveBtn = new Button("💾 Guardar");
        saveBtn.setOnAction(
                e -> {
                    try {
                        if (selected != null && (selected.offlineUuid == null || selected.offlineUuid.isBlank())) {
                            syncUuidFromUsername();
                        }
                        facade.profiles().save(profiles);
                        log("Perfiles guardados.");
                    } catch (Exception ex) {
                        log("Error guardando: " + ex.getMessage());
                    }
                });
        Button installBtn = new Button("⬇ Instalar versión");
        installBtn.setOnAction(
                e ->
                        workers.submit(
                                () -> {
                                    try {
                                        if (selected == null) {
                                            return;
                                        }
                                        String v = selected.lastVersionId;
                                        log("Instalando " + v + "… (primera vez descarga assets completos)");
                                        facade.installVersion(v, this::log);
                                        Platform.runLater(this::refreshHints);
                                        log("Instalación terminada.");
                                    } catch (Exception ex) {
                                        log("Error instalación: " + ex.getMessage());
                                    }
                                }));
        Button playBtn = new Button("▶  JUGAR");
        playBtn.setDefaultButton(true);
        playBtn.setOnAction(
                e ->
                        workers.submit(
                                () -> {
                                    try {
                                        if (selected == null) {
                                            return;
                                        }
                                        if (!OfflineUuid.uuidMatchesUsername(selected.username, selected.offlineUuid)) {
                                            log("UUID no coincide con el nombre. Pulsa «UUID desde nombre».");
                                            return;
                                        }
                                        long ram = HardwareProbe.totalPhysicalRamMiB();
                                        facade.startGame(selected, ram, this::log);
                                    } catch (Exception ex) {
                                        log("Error al lanzar: " + ex.getMessage());
                                    }
                                }));

        HBox actions = new HBox(8, newProfile, deleteProfile, saveBtn, installBtn, playBtn);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setStyle(
                "-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER
                        + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");
        playBtn.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: #040a06; -fx-font-weight: bold;");
        statusDot = new Label("●");
        statusDot.setStyle("-fx-text-fill: " + ACCENT + "; -fx-font-size: 10px;");
        Label statusLabel = new Label(LauncherMetadata.VERSION);
        statusLabel.setStyle("-fx-text-fill: " + TEXT_MUT + "; -fx-font-size: 10px;");
        actions.getChildren().addAll(newSpacer(), statusDot, statusLabel);

        HBox topBar = new HBox(10);
        topBar.getStyleClass().add("meacore-topbar");
        topBar.setPadding(new Insets(8, 12, 8, 12));
        Label topTitle = new Label(LauncherMetadata.DISPLAY_NAME.toUpperCase());
        topTitle.setStyle("-fx-text-fill: " + TEXT_PRI + "; -fx-font-weight: bold; -fx-font-size: 11px;");
        Label vendorLabel = new Label("by " + LauncherMetadata.VENDOR);
        vendorLabel.setStyle("-fx-text-fill: " + TEXT_MUT + "; -fx-font-size: 10px;");
        topBar.getChildren().addAll(topTitle, vendorLabel);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab tabNews = new Tab("Noticias", new Label("Próximamente"));
        Tab tabProfiles = new Tab("Perfiles", center);
        VBox installPane = new VBox(10);
        installPane.setPadding(new Insets(12));
        Label installTitle = new Label("INSTALACIONES");
        installTitle.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-letter-spacing: 0.05em;");
        Label installHint =
                new Label(
                        "Elige una versión oficial y usa «Instalar» para descargarla. Después puedes lanzar desde tu perfil.");
        installHint.setWrapText(true);
        installHint.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
        installListView.setCellFactory(
                lv ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(ManifestVersionEntry item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setGraphic(null);
                                    setText(null);
                                    return;
                                }
                                String type = item.type() == null ? "" : item.type();
                                Label name = new Label(item.id());
                                name.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
                                Label typeLabel =
                                        new Label(type.isBlank() ? "" : type.toUpperCase());
                                typeLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");
                                VBox textBox = new VBox(2, name, typeLabel);
                                Button btnInstall = new Button("Instalar");
                                btnInstall.setOnAction(
                                        e ->
                                                workers.submit(
                                                        () -> {
                                                            try {
                                                                String v = item.id();
                                                                if (selected != null) {
                                                                    selected.lastVersionId = v;
                                                                    Platform.runLater(
                                                                            () -> {
                                                                                profileList.refresh();
                                                                                applyVersionFilter();
                                                                            });
                                                                }
                                                                LauncherApp.this.log("Instalando " + v + "…");
                                                                facade.installVersion(v, LauncherApp.this::log);
                                                                Platform.runLater(LauncherApp.this::refreshHints);
                                                                LauncherApp.this.log("Instalación de " + v + " terminada.");
                                                            } catch (Exception ex) {
                                                                LauncherApp.this.log("Error instalación: " + ex.getMessage());
                                                            }
                                                        }));
                                Button btnPlay = new Button("Jugar");
                                btnPlay.setOnAction(
                                        e ->
                                                workers.submit(
                                                        () -> {
                                                            try {
                                                                if (selected == null) {
                                                                    LauncherApp.this.log("Selecciona un perfil antes de jugar.");
                                                                    return;
                                                                }
                                                                selected.lastVersionId = item.id();
                                                                Platform.runLater(
                                                                        () -> {
                                                                            profileList.refresh();
                                                                            applyVersionFilter();
                                                                        });
                                                                if (!OfflineUuid.uuidMatchesUsername(
                                                                        selected.username, selected.offlineUuid)) {
                                                                    LauncherApp.this.log("UUID no coincide con el nombre. Pulsa «UUID desde nombre».");
                                                                    return;
                                                                }
                                                                long ram = HardwareProbe.totalPhysicalRamMiB();
                                                                facade.startGame(selected, ram, LauncherApp.this::log);
                                                            } catch (Exception ex) {
                                                                LauncherApp.this.log("Error al lanzar: " + ex.getMessage());
                                                            }
                                                        }));
                                HBox.setHgrow(textBox, Priority.ALWAYS);
                                HBox row = new HBox(10, textBox, btnInstall, btnPlay);
                                row.setPadding(new Insets(6));
                                row.setStyle(
                                        "-fx-background-color: rgba(255,255,255,0.02); -fx-border-color: #1e1e1e; -fx-border-width: 0 0 1 0;");
                                setGraphic(row);
                                setText(null);
                            }
                        });
        installPane.getChildren().addAll(installTitle, installHint, installListView);
        Tab tabInstall = new Tab("Instalaciones", installPane);
        Tab tabMods = new Tab("Mods", new Label("Próximamente"));
        Tab tabLog = new Tab("Registro", logArea);
        tabs.getTabs().addAll(tabNews, tabProfiles, tabInstall, tabMods, tabLog);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(profileList);
        profileList.setPrefWidth(230);
        profileList.setStyle(
                "-fx-background-color: " + BG_PANEL + "; -fx-border-color: transparent " + BORDER
                        + " transparent transparent; -fx-border-width: 0 1 0 0;");
        root.setCenter(tabs);
        BorderPane.setMargin(tabs, new Insets(8));
        root.setBottom(actions);
        root.setStyle("-fx-background-color: " + BG_DEEP + ";");

        Scene scene = new Scene(root, 980, 720);
        stage.setTitle(LauncherMetadata.DISPLAY_NAME);
        stage.setScene(scene);
        scene.getStylesheets()
                .add(getClass().getResource("/com/experimento/launcher/ui/meacore.css").toExternalForm());
        stage.show();

        loadVersionManifestAsync();

        stage.setOnCloseRequest(ev -> workers.shutdownNow());
    }

    private boolean startFrontendUi(Stage stage) {
        try {
            Path distDir = Path.of("frontend", "dist").toAbsolutePath().normalize();
            Path distIndex = distDir.resolve("index.html");
            if (!Files.isRegularFile(distIndex)) {
                return false;
            }

            frontendServer = startLocalFrontendServer(distDir);
            if (frontendServer == null) {
                return false;
            }

            WebView webView = new WebView();
            int port = frontendServer.getAddress().getPort();
            webView
                    .getEngine()
                    .getLoadWorker()
                    .stateProperty()
                    .addListener(
                            (obs, oldState, newState) -> {
                                if (newState == Worker.State.SUCCEEDED) {
                                    try {
                                        JSObject window = (JSObject) webView.getEngine().executeScript("window");
                                        window.setMember("meacoreBridge", new FrontendBridge());
                                        webView.getEngine()
                                                .executeScript(
                                                        "window.dispatchEvent(new CustomEvent('meacoreBridgeReady'));");
                                        System.out.println("[Bridge] meacoreBridge conectado en WebView.");
                                    } catch (Exception ex) {
                                        System.err.println("[Bridge] Error inyectando meacoreBridge: " + ex.getMessage());
                                    }
                                }
                            });
            webView.getEngine().load("http://127.0.0.1:" + port + "/");

            Scene scene = new Scene(webView, 980, 720);
            stage.setTitle(LauncherMetadata.DISPLAY_NAME);
            stage.setScene(scene);
            stage.show();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private HttpServer startLocalFrontendServer(Path distDir) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        try {
                            handleFrontendRequest(distDir, exchange);
                        } catch (Exception ex) {
                            byte[] body = "Internal error".getBytes();
                            exchange.sendResponseHeaders(500, body.length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(body);
                            }
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
            return server;
        } catch (Exception e) {
            return null;
        }
    }

    private void handleFrontendRequest(Path distDir, HttpExchange exchange) throws Exception {
        String reqPath = exchange.getRequestURI().getPath();
        if (reqPath == null || reqPath.isBlank() || "/".equals(reqPath)) {
            reqPath = "/index.html";
        }

        // Keep requests inside dist directory.
        Path target = distDir.resolve(reqPath.substring(1)).normalize();
        if (!target.startsWith(distDir)) {
            sendNotFound(exchange);
            return;
        }

        if (!Files.isRegularFile(target)) {
            // SPA fallback for client-side routes.
            target = distDir.resolve("index.html");
            if (!Files.isRegularFile(target)) {
                sendNotFound(exchange);
                return;
            }
        }

        byte[] bytes = Files.readAllBytes(target);
        String contentType = guessContentType(target);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendNotFound(HttpExchange exchange) throws Exception {
        byte[] body = "Not found".getBytes();
        exchange.sendResponseHeaders(404, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String guessContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    public final class FrontendBridge {
        public String getProfiles() {
            try {
                System.out.println("[Bridge] getProfiles()");
                ArrayNode arr = mapper.createArrayNode();
                for (LauncherProfile p : profiles) {
                    arr.add(toFrontendProfileNode(p));
                }
                return mapper.writeValueAsString(arr);
            } catch (Exception ex) {
                return "[]";
            }
        }

        public String saveProfiles(String profilesJson) {
            try {
                System.out.println("[Bridge] saveProfiles()");
                JsonNode root = mapper.readTree(profilesJson);
                if (!root.isArray()) {
                    return "JSON de perfiles inválido";
                }
                List<LauncherProfile> converted = new ArrayList<>();
                for (JsonNode n : root) {
                    converted.add(toLauncherProfile(n));
                }
                facade.profiles().save(converted);
                profiles = converted;
                return "OK";
            } catch (Exception ex) {
                log("Error guardando perfiles desde frontend: " + ex.getMessage());
                return ex.getMessage();
            }
        }

        public String installVersion(String versionJson) {
            try {
                System.out.println("[Bridge] installVersion()");
                JsonNode payload = mapper.readTree(versionJson);
                String uiVersion = payload.path("version").asText("");
                String versionId = normalizeVersionId(uiVersion);
                workers.submit(
                        () -> {
                            try {
                                log("Instalando " + versionId + "...");
                                facade.installVersion(versionId, LauncherApp.this::log);
                                log("Instalación completada: " + versionId);
                            } catch (Exception ex) {
                                log("Error instalación desde frontend: " + ex.getMessage());
                            }
                        });
                return "OK";
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }

        public String startGame(String payloadJson) {
            try {
                System.out.println("[Bridge] startGame()");
                JsonNode payload = mapper.readTree(payloadJson);
                JsonNode profileNode = payload.path("profile");
                String uiVersion = payload.path("version").asText("");
                LauncherProfile p = toLauncherProfile(profileNode);
                if (!uiVersion.isBlank()) {
                    p.lastVersionId = normalizeVersionId(uiVersion);
                }
                if (!OfflineUuid.uuidMatchesUsername(p.username, p.offlineUuid)) {
                    p.offlineUuid = OfflineUuid.toString(OfflineUuid.forUsername(p.username));
                }
                workers.submit(
                        () -> {
                            try {
                                Path mergedPath =
                                        facade.directories()
                                                .versionsDir()
                                                .resolve(p.lastVersionId)
                                                .resolve("version.json");
                                if (!Files.isRegularFile(mergedPath)) {
                                    log("Versión no instalada, instalando " + p.lastVersionId + "...");
                                    facade.installVersion(p.lastVersionId, LauncherApp.this::log);
                                }
                                long ram = HardwareProbe.totalPhysicalRamMiB();
                                facade.startGame(p, ram, LauncherApp.this::log);
                            } catch (Exception ex) {
                                log("Error al lanzar desde frontend: " + ex.getMessage());
                            }
                        });
                return "OK";
            } catch (Exception ex) {
                log("Error al lanzar desde frontend: " + ex.getMessage());
                return ex.getMessage();
            }
        }
    }

    private ObjectNode toFrontendProfileNode(LauncherProfile p) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", p.id);
        n.put("name", p.displayName);
        n.put("displayName", p.displayName);
        n.put("version", p.lastVersionId + " [release]");
        n.put("versionFilter", "Todas");
        n.put("jvmPreset", "auto");
        n.put("jvmExtra", p.customJvmArgs == null ? "" : p.customJvmArgs);
        n.put("useGlobalMinecraft", p.useGlobalMinecraftFolder);
        ArrayNode servers = mapper.createArrayNode();
        if (p.servers != null) {
            for (ServerEntry s : p.servers) {
                ObjectNode sn = mapper.createObjectNode();
                sn.put("id", UUID.randomUUID().toString());
                sn.put("name", s.name == null ? "" : s.name);
                sn.put("ip", s.address == null ? "" : s.address);
                sn.put("cracked", s.crackedServer);
                servers.add(sn);
            }
        }
        n.set("servers", servers);
        return n;
    }

    private LauncherProfile toLauncherProfile(JsonNode n) {
        LauncherProfile p = LauncherProfile.createDefault();
        p.id = n.path("id").asText(p.id);
        String display = n.path("displayName").asText("Principal");
        p.displayName = display;
        p.username = sanitizeUsername(display);
        p.offlineUuid = OfflineUuid.toString(OfflineUuid.forUsername(p.username));
        p.customJvmArgs = n.path("jvmExtra").asText("");
        p.jvmPreset = mapJvmPreset(n.path("jvmPreset").asText("auto"));
        p.useGlobalMinecraftFolder = n.path("useGlobalMinecraft").asBoolean(false);
        p.lastVersionId = normalizeVersionId(n.path("version").asText("1.21.4"));
        p.instanceId = n.path("instanceId").asText(p.id);
        if (p.instanceId == null || p.instanceId.isBlank()) {
            p.instanceId = p.id;
        }
        if (n.has("servers") && n.get("servers").isArray()) {
            p.servers = new ArrayList<>();
            for (JsonNode s : n.get("servers")) {
                ServerEntry se = new ServerEntry();
                se.name = s.path("name").asText("");
                se.address = s.path("ip").asText("");
                se.crackedServer = s.path("cracked").asBoolean(false);
                p.servers.add(se);
            }
        }
        return p;
    }

    private JvmPresetKind mapJvmPreset(String raw) {
        if (raw == null) {
            return JvmPresetKind.AUTO;
        }
        String s = raw.trim().toLowerCase();
        if (s.isBlank() || s.equals("auto") || s.equals("java21") || s.equals("java17") || s.equals("java8")) {
            return JvmPresetKind.AUTO;
        }
        if (s.equals("low")) return JvmPresetKind.LOW;
        if (s.equals("balanced")) return JvmPresetKind.BALANCED;
        if (s.equals("high")) return JvmPresetKind.HIGH;
        return JvmPresetKind.AUTO;
    }

    private String normalizeVersionId(String uiVersion) {
        if (uiVersion == null || uiVersion.isBlank()) {
            return "1.21.4";
        }
        String v = uiVersion.trim();
        int firstSpace = v.indexOf(' ');
        String firstToken = firstSpace > 0 ? v.substring(0, firstSpace) : v;
        if (firstToken.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            return firstToken;
        }
        if (v.toLowerCase().startsWith("forge ") || v.toLowerCase().startsWith("fabric ")) {
            String[] parts = v.split("\\s+");
            if (parts.length >= 2 && parts[1].matches("\\d+\\.\\d+(\\.\\d+)?")) {
                return parts[1];
            }
        }
        return firstToken.replace("[release]", "").replace("[snapshot]", "").trim();
    }

    private void loadVersionManifestAsync() {
        workers.submit(
                () -> {
                    try {
                        List<ManifestVersionEntry> list = facade.fetchManifestVersions();
                        Platform.runLater(
                                () -> {
                                    allManifestEntries.clear();
                                    allManifestEntries.addAll(list);
                                    if (selected != null) {
                                        ensureProfileVersionInBackingList(selected.lastVersionId);
                                    }
                                    applyVersionFilter();
                                                    refreshInstallationsList();
                                });
                    } catch (Exception ex) {
                        Platform.runLater(() -> log("Error cargando manifest: " + ex.getMessage()));
                    }
                });
    }

    private void ensureProfileVersionInBackingList(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        for (ManifestVersionEntry e : allManifestEntries) {
            if (id.equals(e.id())) {
                return;
            }
        }
        allManifestEntries.add(new ManifestVersionEntry(id, "perfil", System.currentTimeMillis()));
    }

    private boolean matchesVersionFilter(ManifestVersionEntry e, String filter) {
        if (filter == null || "Todas".equals(filter)) {
            return true;
        }
        String t = e.type();
        if (t == null) {
            t = "";
        }
        if ("Solo releases".equals(filter)) {
            return "release".equalsIgnoreCase(t);
        }
        if ("Solo snapshots".equals(filter)) {
            return "snapshot".equalsIgnoreCase(t);
        }
        if ("Clásicas (beta/alpha)".equals(filter)) {
            return "old_beta".equalsIgnoreCase(t) || "old_alpha".equalsIgnoreCase(t);
        }
        return true;
    }

    private List<ManifestVersionEntry> computeFilteredList() {
        String filter = versionFilter.getValue();
        if (filter == null) {
            filter = "Todas";
        }
        String profileId =
                selected != null && selected.lastVersionId != null ? selected.lastVersionId : "";

        List<ManifestVersionEntry> filtered = new ArrayList<>();
        for (ManifestVersionEntry e : allManifestEntries) {
            if (matchesVersionFilter(e, filter) || (!profileId.isBlank() && profileId.equals(e.id()))) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    private void applyVersionFilter() {
        if (selected != null) {
            ensureProfileVersionInBackingList(selected.lastVersionId);
        }
        List<ManifestVersionEntry> filtered = computeFilteredList();
        syncingVersionUi = true;
        try {
            versionCombo.setItems(FXCollections.observableArrayList(filtered));
            if (selected != null) {
                selectVersionForProfile(selected.lastVersionId);
            } else {
                versionCombo.setValue(null);
            }
        } finally {
            syncingVersionUi = false;
        }
    }

    private void refreshInstallationsList() {
        if (installListView == null) {
            return;
        }
        installListView.setItems(FXCollections.observableArrayList(allManifestEntries));
    }

    private void selectVersionForProfile(String id) {
        if (id == null || id.isBlank()) {
            versionCombo.setValue(null);
            return;
        }
        for (ManifestVersionEntry e : versionCombo.getItems()) {
            if (id.equals(e.id())) {
                versionCombo.setValue(e);
                return;
            }
        }
        versionCombo.setValue(null);
    }

    private void bindProfile(LauncherProfile p) {
        selected = p;
        if (p == null) {
            syncingVersionUi = true;
            try {
                versionCombo.setItems(FXCollections.observableArrayList());
                versionCombo.setValue(null);
            } finally {
                syncingVersionUi = false;
            }
            return;
        }
        syncingIdentityUi = true;
        try {
            displayNameField.setText(p.displayName);
            usernameField.setText(p.username);
            uuidField.setText(p.offlineUuid);
        } finally {
            syncingIdentityUi = false;
        }
        presetCombo.setValue(p.jvmPreset);
        jvmArea.setText(p.customJvmArgs == null ? "" : p.customJvmArgs);
        globalMcCheck.setSelected(p.useGlobalMinecraftFolder);
        if (p.servers == null) {
            p.servers = new ArrayList<>();
        }
        serverTable.setItems(FXCollections.observableList(p.servers));
        applyVersionFilter();
        if (p.offlineUuid == null || p.offlineUuid.isBlank()) {
            syncUuidFromUsername();
        }
        refreshHints();
    }

    private void syncIdentityFromDisplayName(String displayName) {
        if (selected == null) {
            return;
        }
        String normalized = sanitizeUsername(displayName);
        syncingIdentityUi = true;
        try {
            selected.username = normalized;
            usernameField.setText(normalized);
        } finally {
            syncingIdentityUi = false;
        }
        syncUuidFromUsername();
    }

    private String sanitizeUsername(String raw) {
        String src = raw == null ? "" : raw.trim();
        src = src.replace(' ', '_').replaceAll("[^a-zA-Z0-9_]", "");
        if (src.isBlank()) {
            return "Player";
        }
        if (src.length() < 3) {
            src = (src + "___").substring(0, 3);
        }
        if (src.length() > 16) {
            src = src.substring(0, 16);
        }
        return src;
    }

    private void refreshAternosRowHint(ServerEntry se) {
        if (se == null) {
            return;
        }
        log(
                se.crackedServer
                        ? "Servidor marcado Cracked ON → puedes entrar con usuario offline de este perfil."
                        : "Servidor Cracked OFF → hace falta cuenta premium (Microsoft) en el futuro.");
    }

    private void syncUuidFromUsername() {
        if (selected == null) {
            return;
        }
        try {
            String sanitized = sanitizeUsername(selected.username);
            selected.username = sanitized;
            if (!sanitized.equals(usernameField.getText())) {
                syncingIdentityUi = true;
                try {
                    usernameField.setText(sanitized);
                } finally {
                    syncingIdentityUi = false;
                }
            }
            selected.offlineUuid =
                    OfflineUuid.toString(OfflineUuid.forUsername(selected.username));
            uuidField.setText(selected.offlineUuid);
        } catch (Exception ex) {
            log("Usuario inválido (3-16, [a-zA-Z0-9_]): " + ex.getMessage());
        }
    }

    private void refreshHints() {
        if (selected == null) {
            return;
        }
        long ram = HardwareProbe.totalPhysicalRamMiB();
        JvmPresetKind eff =
                selected.jvmPreset == JvmPresetKind.AUTO
                        ? JvmPresetService.resolveAutoKind(ram)
                        : selected.jvmPreset;
        modHintLabel.setText(
                AutoOptimizerService.modSuggestionText(eff)
                        + " | RAM ~"
                        + ram
                        + " MiB | AUTO resuelve a "
                        + JvmPresetService.resolveAutoKind(ram));
    }

    private void log(String s) {
        Platform.runLater(
                () -> {
                    if (logArea != null) {
                        logArea.appendText(s + "\n");
                        logArea.setScrollTop(Double.MAX_VALUE);
                    } else {
                        System.out.println(s);
                    }
                });
    }

    private Region newSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    @Override
    public void stop() {
        if (frontendServer != null) {
            frontendServer.stop(0);
            frontendServer = null;
        }
        workers.shutdownNow();
    }
}
