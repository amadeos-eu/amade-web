package org.datazuul.amadeos.web.ui.main;

import com.google.inject.Inject;
import com.sun.javafx.webkit.WebConsoleListener;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import org.datazuul.amadeos.commons.app.AppInfo;
import org.datazuul.amadeos.commons.i18n.Language;
import org.datazuul.amadeos.commons.i18n.LocaleSettings;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.i18n.Mnemonics;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.web.bootstrap.AmadeWebApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The browser window: menu bar, toolbar (back, forward, reload, home, address) and status bar around a
 * JavaFX WebView.
 *
 * <p>Swing components are only touched on the EDT, the WebView and its engine only on the JavaFX thread;
 * the two hand over through {@link SwingUtilities#invokeLater} and {@link Platform#runLater}.
 */
public final class BrowserWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(BrowserWindow.class);
    private static final double ZOOM_STEP = 1.1;
    private static final double MIN_ZOOM = 0.3;
    private static final double MAX_ZOOM = 5;
    private static final int MENU = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final AppInfo app;
    private final Messages msg;
    private final LocaleSettings locales;
    private final JFXPanel fxPanel = new JFXPanel();
    private final JTextField address = new JTextField();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final Action back;
    private final Action forward;
    private final Action reload;
    private final Action stop;
    private final Action home;
    private final Action zoomIn;
    private final Action zoomOut;
    private final Action zoomReset;
    private final Action openFile;
    private final Action openLocation;
    private final Action exit;

    /** Set on the JavaFX thread; only used there. */
    private WebView view;
    private WebEngine engine;

    @Inject
    public BrowserWindow(AppInfo app, Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        this.app = app;
        this.msg = msg;
        this.locales = locales;
        // The JavaFX thread must survive while the JFXPanel is shown, removed or re-added.
        Platform.setImplicitExit(false);
        URL icon = getClass().getResource("/icons/amade-web.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());

        back = fxAction("menu.go.back", key(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), () -> go(-1));
        forward = fxAction("menu.go.forward", key(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), () -> go(1));
        reload = fxAction("menu.view.reload", key(KeyEvent.VK_F5, 0), () -> engine.reload());
        stop = fxAction("menu.view.stop", key(KeyEvent.VK_ESCAPE, 0), () -> engine.getLoadWorker().cancel());
        home = fxAction("menu.go.home", key(KeyEvent.VK_HOME, InputEvent.ALT_DOWN_MASK), () -> engine.load(Addresses.HOME));
        zoomIn = fxAction("menu.view.zoom-in", key(KeyEvent.VK_PLUS, MENU), () -> zoom(view.getZoom() * ZOOM_STEP));
        zoomOut = fxAction("menu.view.zoom-out", key(KeyEvent.VK_MINUS, MENU), () -> zoom(view.getZoom() / ZOOM_STEP));
        zoomReset = fxAction("menu.view.zoom-reset", key(KeyEvent.VK_0, MENU), () -> zoom(1));
        openFile = action("menu.file.open", key(KeyEvent.VK_O, MENU), this::chooseFile);
        openLocation = action("menu.file.open-location", key(KeyEvent.VK_L, MENU), () -> {
            address.requestFocusInWindow();
            address.selectAll();
        });
        exit = action("menu.file.exit", key(KeyEvent.VK_Q, MENU), this::dispose);
        back.setEnabled(false);
        forward.setEnabled(false);
        stop.setEnabled(false);

        address.addActionListener(e -> load(Addresses.toUrl(address.getText())));

        setJMenuBar(menuBar());
        add(toolBar(), BorderLayout.NORTH);
        add(fxPanel, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        setSize(1200, 850);
        setLocationByPlatform(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Platform.exit();
            }
        });
    }

    /** Shows the window and loads the URL once the WebView exists. */
    public void open(String url) {
        EdtExecutor.requireEdt();
        address.setText(url);
        setVisible(true);
        onFx(() -> {
            createWebView();
            engine.load(url);
        });
    }

    private void load(String url) {
        if (url.isEmpty()) return;
        onFx(() -> engine.load(url));
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu(bar, "menu.file");
        file.add(item(openFile));
        file.add(item(openLocation));
        file.addSeparator();
        file.add(item(exit));

        JMenu viewMenu = menu(bar, "menu.view");
        viewMenu.add(item(reload));
        viewMenu.add(item(stop));
        viewMenu.addSeparator();
        viewMenu.add(item(zoomIn));
        viewMenu.add(item(zoomOut));
        viewMenu.add(item(zoomReset));

        JMenu go = menu(bar, "menu.go");
        go.add(item(back));
        go.add(item(forward));
        go.add(item(home));

        JMenu options = menu(bar, "menu.options");
        options.add(languageMenu());

        JMenu help = menu(bar, "menu.help");
        JMenuItem about = Mnemonics.apply(new JMenuItem(), msg.get("menu.help.about"));
        about.setName("menu.help.about");
        about.addActionListener(e -> showAbout());
        help.add(about);
        return bar;
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu() {
        JMenu menu = new JMenu(msg.get("menu.options.language"));
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private JToolBar toolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(toolButton(back, "◀"));
        toolBar.add(toolButton(forward, "▶"));
        toolBar.add(toolButton(reload, "⟳"));
        toolBar.add(toolButton(home, "⌂"));
        toolBar.addSeparator();
        toolBar.add(address);
        return toolBar;
    }

    private JPanel statusBar() {
        progress.setPreferredSize(new Dimension(160, progress.getPreferredSize().height));
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(progress, BorderLayout.EAST);
        return statusBar;
    }

    private void createWebView() {
        view = new WebView();
        engine = view.getEngine();
        LOG.info("WebKit user agent: {}", engine.getUserAgent());

        engine.locationProperty().addListener((obs, old, location) -> onEdt(() -> address.setText(location)));
        engine.titleProperty().addListener((obs, old, title) ->
                onEdt(() -> setTitle(title == null || title.isBlank() ? msg.get("window.title")
                        : title + " - " + msg.get("window.title"))));
        engine.setOnStatusChanged(e -> onEdt(() -> status.setText(e.getData() == null ? " " : e.getData())));
        engine.setOnAlert(e -> onEdt(() -> JOptionPane.showMessageDialog(this, e.getData(), getTitle(),
                JOptionPane.INFORMATION_MESSAGE)));
        // Links with target="_blank" and window.open() stay in this window.
        engine.setCreatePopupHandler(features -> engine);

        Worker<Void> worker = engine.getLoadWorker();
        worker.progressProperty().addListener((obs, old, value) ->
                onEdt(() -> progress.setValue((int) Math.round(Math.max(0, value.doubleValue()) * 100))));
        worker.stateProperty().addListener((obs, old, state) -> {
            String location = engine.getLocation();
            String text = switch (state) {
                case SCHEDULED, RUNNING -> msg.format("status.loading", location);
                case SUCCEEDED -> msg.get("status.done");
                case FAILED -> msg.format("status.failed", describe(worker.getException()));
                case CANCELLED -> msg.get("status.cancelled");
                case READY -> " ";
            };
            if (state == Worker.State.FAILED) LOG.warn("Loading {} failed", location, worker.getException());
            boolean loading = state == Worker.State.SCHEDULED || state == Worker.State.RUNNING;
            onEdt(() -> {
                status.setText(text);
                stop.setEnabled(loading);
            });
        });

        WebHistory history = engine.getHistory();
        history.currentIndexProperty().addListener((obs, old, index) -> updateHistoryActions(history));
        history.getEntries().addListener((ListChangeListener<WebHistory.Entry>) c -> updateHistoryActions(history));

        // Internal JavaFX API, reachable because JavaFX runs from the class path. Without it the JavaScript
        // errors of a web application (Angular bootstrap failures, missing APIs) stay invisible.
        WebConsoleListener.setDefaultListener((webView, message, line, source) ->
                LOG.info("console {}:{} {}", source, line, message));

        fxPanel.setScene(new Scene(view));
    }

    private void go(int offset) {
        WebHistory history = engine.getHistory();
        int target = history.getCurrentIndex() + offset;
        if (target >= 0 && target < history.getEntries().size()) history.go(offset);
    }

    private void updateHistoryActions(WebHistory history) {
        int index = history.getCurrentIndex();
        int size = history.getEntries().size();
        onEdt(() -> {
            back.setEnabled(index > 0);
            forward.setEnabled(index < size - 1);
        });
    }

    private void zoom(double factor) {
        view.setZoom(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, factor)));
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(msg.get("dialog.open.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("dialog.open.filter"),
                "html", "htm", "xhtml", "svg", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile().toPath().toAbsolutePath().toUri().toString());
        }
    }

    private void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            JOptionPane.showMessageDialog(this, msg.format("error.save-language", e.getMessage()),
                    msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (locales.restartRequired()) {
            JOptionPane.showMessageDialog(this, Messages.commons(msg.locale()).format("language.restart", app.id()),
                    getTitle(), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-web " + AmadeWebApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private String describe(Throwable e) {
        return e == null || e.getMessage() == null ? msg.get("status.unknown-error") : e.getMessage();
    }

    /** An action run on the EDT, named and with mnemonic from the texts. */
    private Action action(String key, KeyStroke accelerator, Runnable run) {
        Mnemonics.Text text = Mnemonics.parse(msg.get(key));
        Action action = new AbstractAction(text.text()) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, key);
        action.putValue(Action.SHORT_DESCRIPTION, text.text());
        action.putValue(Action.MNEMONIC_KEY, text.keyCode());
        if (text.index() >= 0) action.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, text.index());
        action.putValue(Action.ACCELERATOR_KEY, accelerator);
        return action;
    }

    /** An action that works on the WebView, so runs on the JavaFX thread. */
    private Action fxAction(String key, KeyStroke accelerator, Runnable run) {
        return action(key, accelerator, () -> onFx(() -> {
            if (engine != null) run.run();
        }));
    }

    private static KeyStroke key(int keyCode, int modifiers) {
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    private JMenu menu(JMenuBar bar, String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        bar.add(menu);
        return menu;
    }

    private static JMenuItem item(Action action) {
        JMenuItem item = new JMenuItem(action);
        item.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        return item;
    }

    private static JButton toolButton(Action action, String symbol) {
        JButton button = new JButton(action);
        button.setText(symbol);
        button.setMnemonic(KeyEvent.VK_UNDEFINED);
        button.setFocusable(false);
        button.setName(String.valueOf(action.getValue(Action.ACTION_COMMAND_KEY)));
        return button;
    }

    private static void onFx(Runnable action) {
        Platform.runLater(action);
    }

    private static void onEdt(Runnable action) {
        SwingUtilities.invokeLater(action);
    }
}
