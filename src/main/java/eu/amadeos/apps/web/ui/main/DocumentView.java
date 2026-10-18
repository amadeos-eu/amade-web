package eu.amadeos.apps.web.ui.main;

import eu.amadeos.apps.web.document.DocumentLoader.Page;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xhtmlrenderer.event.DefaultDocumentListener;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.simple.FSScrollPane;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.DefaultFSMouseListener;
import org.xhtmlrenderer.swing.FSMouseListener;
import org.xhtmlrenderer.swing.LinkListener;
import org.xhtmlrenderer.swing.NaiveUserAgent;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Renders pages with Flying Saucer (XHTML + CSS 2.1, no scripting) in Swing. Link clicks are not followed
 * here but handed to the window, which decides between this view, the web browser and other
 * applications. Only local resources (style sheets, images) are loaded, never the network.
 */
final class DocumentView extends JPanel {
    private final XHTMLPanel panel = new XHTMLPanel(new LocalUserAgent());
    /** Element id to scroll to once the page is laid out; only used on the EDT. */
    private String pendingAnchor;

    DocumentView(Consumer<String> linkHandler, Consumer<String> hoverHandler) {
        super(new BorderLayout());
        for (FSMouseListener listener : new ArrayList<>(panel.getMouseTrackingListeners())) {
            if (listener instanceof LinkListener) panel.removeMouseTrackingListener(listener);
        }
        panel.addMouseTrackingListener(new LinkListener() {
            @Override
            public void linkClicked(BasicPanel source, String uri) {
                linkHandler.accept(resolve(uri));
            }
        });
        panel.addMouseTrackingListener(new DefaultFSMouseListener() {
            @Override
            public void onMouseOver(BasicPanel source, Box box) {
                String href = linkTarget(box);
                hoverHandler.accept(href == null ? null : resolve(href));
            }

            @Override
            public void onMouseOut(BasicPanel source, Box box) {
                hoverHandler.accept(null);
            }
        });
        panel.addDocumentListener(new DefaultDocumentListener() {
            @Override
            public void documentLoaded() {
                // Fired on the EDT after the layout; the scroll pane takes the new size only afterwards.
                String anchor = pendingAnchor;
                pendingAnchor = null;
                if (anchor != null) SwingUtilities.invokeLater(() -> scrollToAnchor(anchor));
            }
        });
        panel.setBackground(Color.WHITE);
        add(new FSScrollPane(panel), BorderLayout.CENTER);
    }

    void show(Page page) {
        pendingAnchor = page.fragment();
        panel.setDocument(page.document(), page.documentUrl(), new XhtmlNamespaceHandler());
    }

    /** Scrolls to the element with this id; false if the page has none. */
    boolean scrollToAnchor(String id) {
        Box box = panel.getSharedContext() == null ? null : panel.getSharedContext().getBoxById(id);
        if (box == null) return false;
        panel.scrollTo(new Point(box.getAbsX(), box.getAbsY()));
        return true;
    }

    void zoomIn() {
        panel.incrementFontSize();
    }

    void zoomOut() {
        panel.decrementFontSize();
    }

    void resetZoom() {
        panel.resetFontSize();
    }

    private String resolve(String uri) {
        String resolved = panel.getSharedContext().getUac().resolveURI(uri);
        return resolved == null ? uri : resolved;
    }

    /** The {@code href} of the link the box belongs to, or null. */
    private static String linkTarget(Box box) {
        Box b = box;
        while (b != null && b.getElement() == null) b = b.getParent(); // anonymous boxes have no element
        for (Node n = b == null ? null : b.getElement(); n instanceof Element e; n = n.getParentNode()) {
            String name = e.getLocalName() != null ? e.getLocalName() : e.getNodeName();
            if ("a".equalsIgnoreCase(name) && !e.getAttribute("href").isBlank()) return e.getAttribute("href");
        }
        return null;
    }

    /** Loads style sheets and images from files and embedded data only; documentation has no business online. */
    private static final class LocalUserAgent extends NaiveUserAgent {
        @Override
        protected InputStream openStream(String uri) throws IOException {
            String lower = uri.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("file:") && !lower.startsWith("jar:")) {
                throw new IOException("Not loading remote resource " + uri);
            }
            return super.openStream(uri);
        }
    }
}
