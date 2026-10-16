package org.datazuul.amadeos.web;

import com.sun.javafx.webkit.WebConsoleListener;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Swing window with a toolbar (back, forward, reload, address) around a JavaFX WebView.
 *
 * <p>Swing components are only touched on the EDT, the WebView and its engine only on the JavaFX
 * thread; the two hand over through {@link SwingUtilities#invokeLater} and {@link Platform#runLater}.
 */
final class WebViewWindow extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(WebViewWindow.class);

    private final String appTitle;
    private final JFXPanel fxPanel = new JFXPanel();
    private final JButton back = new JButton("◀");
    private final JButton forward = new JButton("▶");
    private final JButton reload = new JButton("⟳");
    private final JTextField address = new JTextField();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 100);

    /** Set on the JavaFX thread; only used there. */
    private WebEngine engine;

    WebViewWindow(String appTitle) {
        super(appTitle);
        this.appTitle = appTitle;
        // The JavaFX thread must survive while the JFXPanel is shown, removed or re-added.
        Platform.setImplicitExit(false);

        back.setToolTipText("Back");
        forward.setToolTipText("Forward");
        reload.setToolTipText("Reload");
        back.setEnabled(false);
        forward.setEnabled(false);
        back.addActionListener(e -> onFx(() -> go(-1)));
        forward.addActionListener(e -> onFx(() -> go(1)));
        reload.addActionListener(e -> onFx(() -> engine.reload()));
        address.addActionListener(e -> load(AmadeWebApplication.toUrl(address.getText().strip())));

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(back);
        toolBar.add(forward);
        toolBar.add(reload);
        toolBar.addSeparator();
        toolBar.add(address);

        progress.setPreferredSize(new Dimension(160, progress.getPreferredSize().height));
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(progress, BorderLayout.EAST);

        add(toolBar, BorderLayout.NORTH);
        add(fxPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
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

    void open(String url) {
        setVisible(true);
        onFx(() -> {
            createWebView();
            engine.load(url);
        });
    }

    private void load(String url) {
        onFx(() -> engine.load(url));
    }

    private void createWebView() {
        WebView view = new WebView();
        engine = view.getEngine();
        LOG.info("WebKit user agent: {}", engine.getUserAgent());

        engine.locationProperty().addListener((obs, old, location) -> onEdt(() -> address.setText(location)));
        engine.titleProperty().addListener((obs, old, title) ->
                onEdt(() -> setTitle(title == null || title.isBlank() ? appTitle : title + " - " + appTitle)));
        engine.setOnStatusChanged(e -> onEdt(() -> status.setText(e.getData() == null ? " " : e.getData())));
        engine.setOnAlert(e -> onEdt(() -> JOptionPane.showMessageDialog(this, e.getData(), appTitle,
                JOptionPane.INFORMATION_MESSAGE)));
        // Links with target="_blank" and window.open() stay in this window.
        engine.setCreatePopupHandler(features -> engine);

        Worker<Void> worker = engine.getLoadWorker();
        worker.progressProperty().addListener((obs, old, value) ->
                onEdt(() -> progress.setValue((int) Math.round(Math.max(0, value.doubleValue()) * 100))));
        worker.stateProperty().addListener((obs, old, state) -> {
            String text = switch (state) {
                case RUNNING, SCHEDULED -> "Loading " + engine.getLocation();
                case SUCCEEDED -> "Done";
                case FAILED -> "Failed: " + describe(worker.getException());
                case CANCELLED -> "Cancelled";
                case READY -> " ";
            };
            if (state == Worker.State.FAILED) LOG.warn("Loading {} failed", engine.getLocation(), worker.getException());
            onEdt(() -> status.setText(text));
        });

        WebHistory history = engine.getHistory();
        history.currentIndexProperty().addListener((obs, old, index) -> updateHistoryButtons(history));
        history.getEntries().addListener((javafx.collections.ListChangeListener<WebHistory.Entry>) c ->
                updateHistoryButtons(history));

        // Internal JavaFX API, reachable because JavaFX runs from the class path here. Without it the
        // JavaScript errors of a web application (Angular bootstrap failures, missing APIs) stay invisible.
        WebConsoleListener.setDefaultListener((webView, message, line, source) ->
                LOG.info("console {}:{} {}", source, line, message));

        fxPanel.setScene(new Scene(view));
    }

    private void go(int offset) {
        WebHistory history = engine.getHistory();
        int target = history.getCurrentIndex() + offset;
        if (target >= 0 && target < history.getEntries().size()) history.go(offset);
    }

    private void updateHistoryButtons(WebHistory history) {
        int index = history.getCurrentIndex();
        int size = history.getEntries().size();
        onEdt(() -> {
            back.setEnabled(index > 0);
            forward.setEnabled(index < size - 1);
        });
    }

    private static String describe(Throwable e) {
        return e == null ? "unknown error" : e.getMessage();
    }

    private static void onFx(Runnable action) {
        Platform.runLater(action);
    }

    private static void onEdt(Runnable action) {
        SwingUtilities.invokeLater(action);
    }
}
