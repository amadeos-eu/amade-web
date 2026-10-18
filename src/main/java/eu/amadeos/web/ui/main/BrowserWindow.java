package eu.amadeos.web.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.web.bootstrap.AmadeWebApplication;
import eu.amadeos.web.document.DocumentLoader;
import eu.amadeos.web.document.DocumentLoader.OtherFile;
import eu.amadeos.web.document.DocumentLoader.Page;
import eu.amadeos.web.document.DocumentLoader.Result;
import eu.amadeos.web.document.DocumentLoader.WebLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * The viewer window: menu bar, toolbar (back, forward, reload, home, address) and status bar around a
 * {@link DocumentView}. Pages load on a background thread; everything else happens on the EDT. Web links
 * go to the web browser, files this viewer does not show to their applications.
 */
public final class BrowserWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(BrowserWindow.class);
    private static final int MENU = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final AppInfo app;
    private final Messages msg;
    private final LocaleSettings locales;
    private final ExecutorService background;
    private final EdtExecutor edt;
    private final DesktopLauncher launcher;
    private final DocumentLoader loader;
    private final String windowTitle;
    private final DocumentView view;
    private final JTextField address = new JTextField();
    private final JLabel status = new JLabel(" ");
    private final Action back;
    private final Action forward;
    private final Action reload;
    private final Action home;
    private final Action zoomIn;
    private final Action zoomOut;
    private final Action zoomReset;
    private final Action openFile;
    private final Action openLocation;
    private final Action exit;

    /** Visited URLs; {@link #position} is the one shown. */
    private final List<String> history = new ArrayList<>();
    private int position = -1;
    /** URL of the page shown, with its fragment; null before the first page. */
    private String current;
    /** Counts navigations, so that a slow load does not replace a later one. */
    private long generation;
    /** The status shown when the mouse is not over a link. */
    private String lastStatus = " ";

    @Inject
    public BrowserWindow(AppInfo app, Messages msg, LocaleSettings locales, ExecutorService background,
                         EdtExecutor edt, DesktopLauncher launcher) {
        super(msg.get("window.title"));
        this.app = app;
        this.msg = msg;
        this.locales = locales;
        this.background = background;
        this.edt = edt;
        this.launcher = launcher;
        this.loader = new DocumentLoader(msg);
        this.windowTitle = msg.get("window.title");
        this.view = new DocumentView(url -> navigate(url, true),
                url -> status.setText(url == null ? lastStatus : url));
        URL icon = getClass().getResource("/icons/amade-web.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());

        back = action("menu.go.back", key(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), () -> go(-1));
        forward = action("menu.go.forward", key(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), () -> go(1));
        reload = action("menu.view.reload", key(KeyEvent.VK_F5, 0), () -> {
            if (current != null) navigate(current, false);
        });
        home = action("menu.go.home", key(KeyEvent.VK_HOME, InputEvent.ALT_DOWN_MASK), () -> navigate(Addresses.HOME, true));
        zoomIn = action("menu.view.zoom-in", key(KeyEvent.VK_PLUS, MENU), view::zoomIn);
        zoomOut = action("menu.view.zoom-out", key(KeyEvent.VK_MINUS, MENU), view::zoomOut);
        zoomReset = action("menu.view.zoom-reset", key(KeyEvent.VK_0, MENU), view::resetZoom);
        openFile = action("menu.file.open", key(KeyEvent.VK_O, MENU), this::chooseFile);
        openLocation = action("menu.file.open-location", key(KeyEvent.VK_L, MENU), () -> {
            address.requestFocusInWindow();
            address.selectAll();
        });
        exit = action("menu.file.exit", key(KeyEvent.VK_Q, MENU), this::dispose);
        back.setEnabled(false);
        forward.setEnabled(false);

        address.addActionListener(e -> navigate(Addresses.toUrl(address.getText()), true));

        setJMenuBar(menuBar());
        add(toolBar(), BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        setSize(1200, 850);
        setLocationByPlatform(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /** Shows the window and loads the URL. */
    public void open(String url) {
        EdtExecutor.requireEdt();
        address.setText(url);
        setVisible(true);
        navigate(url, true);
    }

    /** Shows the URL; {@code record} adds it to the history (not for back, forward and reload). */
    private void navigate(String url, boolean record) {
        EdtExecutor.requireEdt();
        if (url == null || url.isBlank()) return;
        String fragment = fragment(url);
        if (current != null && fragment != null && withoutFragment(url).equals(withoutFragment(current))
                && view.scrollToAnchor(fragment)) {
            shown(url, record);
            return;
        }
        long thisLoad = ++generation;
        showStatus(msg.format("status.loading", url));
        background.execute(() -> {
            try {
                Result result = loader.load(url);
                edt.execute(() -> {
                    if (thisLoad == generation) show(result, record);
                });
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not load {}", url, e);
                edt.execute(() -> {
                    if (thisLoad == generation) {
                        showStatus(msg.format("status.failed", describe(e)));
                        if (current == null) navigate(Addresses.HOME, true);
                    }
                });
            }
        });
    }

    private void show(Result result, boolean record) {
        switch (result) {
            case Page page -> {
                view.show(page);
                setTitle(page.title().isBlank() ? windowTitle : page.title() + " - " + windowTitle);
                shown(page.url(), record);
                showStatus(msg.get("status.done"));
            }
            case WebLink link -> openElsewhere(() -> launcher.browse(link.url()),
                    msg.format("status.opened-browser", link.url()));
            case OtherFile file -> openElsewhere(() -> launcher.open(file.file()),
                    msg.format("status.opened-application", file.file().getFileName()));
        }
    }

    /** The page or anchor at this URL is on screen now. */
    private void shown(String url, boolean record) {
        current = url;
        address.setText(url);
        if (record) {
            history.subList(position + 1, history.size()).clear();
            history.add(url);
            position = history.size() - 1;
        }
        updateHistoryActions();
    }

    private void openElsewhere(Opener opener, String doneText) {
        if (current != null) address.setText(current);
        background.execute(() -> {
            try {
                opener.open();
                edt.execute(() -> showStatus(doneText));
            } catch (IOException e) {
                LOG.warn("Could not open externally", e);
                edt.execute(() -> showStatus(msg.format("status.failed", describe(e))));
            }
        });
        // Started with a web link only: show the documentation instead of an empty window.
        if (current == null) navigate(Addresses.HOME, true);
    }

    private interface Opener {
        void open() throws IOException;
    }

    private void go(int offset) {
        int target = position + offset;
        if (target < 0 || target >= history.size()) return;
        position = target;
        navigate(history.get(target), false);
        updateHistoryActions();
    }

    private void updateHistoryActions() {
        back.setEnabled(position > 0);
        forward.setEnabled(position < history.size() - 1);
    }

    private void showStatus(String text) {
        lastStatus = text;
        status.setText(text);
    }

    private static String fragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 || hash == url.length() - 1 ? null : url.substring(hash + 1);
    }

    private static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
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
        Optional<Language> currentLanguage = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(currentLanguage));
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
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        statusBar.add(status, BorderLayout.CENTER);
        return statusBar;
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(msg.get("dialog.open.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("dialog.open.filter"),
                "html", "htm", "xhtml", "txt", "gz"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            navigate(chooser.getSelectedFile().toPath().toAbsolutePath().toUri().toString(), true);
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

    private String describe(Exception e) {
        return e.getMessage() == null ? msg.get("status.unknown-error") : e.getMessage();
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
}
