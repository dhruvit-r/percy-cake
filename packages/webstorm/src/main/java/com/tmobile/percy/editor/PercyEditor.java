/*
 * Copyright (C) 2019 TopCoder Inc., All Rights Reserved.
 */
package com.tmobile.percy.editor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import com.codebrig.journey.JourneyBrowserView;
import com.codebrig.journey.JourneySettings;
import com.codebrig.journey.JourneySettings.LogSeverity;
import com.codebrig.journey.proxy.CefBrowserProxy;
import com.codebrig.journey.proxy.CefClientProxy;
import com.codebrig.journey.proxy.browser.CefFrameProxy;
import com.codebrig.journey.proxy.browser.CefMessageRouterProxy;
import com.codebrig.journey.proxy.callback.CefQueryCallbackProxy;
import com.codebrig.journey.proxy.handler.CefMessageRouterHandlerProxy;
import com.codebrig.journey.proxy.handler.CefNativeDefault;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.tmobile.percy.HttpServer;

// import netscape.javascript.JSObject;

/**
 * The percy editor.
 *
 * @author TCSCODER
 * @version 1.0
 */
public class PercyEditor extends UserDataHolderBase implements FileEditor, Disposable {
    /**
     * The logger.
     */
    private static final Logger LOG = Logger.getInstance(PercyEditor.class);

    /**
     * The JSON mapper.
     */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * The JavaFX panel.
     */
    private final JPanel panel = new JPanel(new BorderLayout());

    /**
     * The project.
     */
    private final Project project;

    /**
     * The file to edit.
     */
    private final VirtualFile file;

    /**
     * The browser.
     */
    private static JourneyBrowserView journeyBrowser;

    /**
     * The browser.
     */
    private CefClientProxy client;

    /**
     * The browser.
     */
    private CefBrowserProxy browser;

    /**
     * The Message Router Handler
     */
    private CefMessageRouterHandlerProxy handler;

    /**
     * The Message Router
     */
    private CefMessageRouterProxy messageRouter;

    /**
     * The WebView UI Component
     */
    private Component webViewComponent;

    /**
     * Whether file is modified.
     */
    private boolean modified;

    /**
     * OS String
     */
    private static String OS = System.getProperty("os.name").toLowerCase();

    /**
     * Whether the OS is linux
     */
    private static boolean isMac = OS.startsWith("mac");

    /**
     * Initialize an instance of the browser.
     */
    static {
        JourneySettings journeySettings = new JourneySettings();
        // Uncomment the following line to enable the remote debugger.
        // Browse to http://localhost:8989/ to debug.
        // journeySettings.setRemoteDebuggingPort(8989);
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "debug.log");
        journeySettings.setLogFile(tempDir.toString());
        journeySettings.setLogSeverity(LogSeverity.LOGSEVERITY_VERBOSE);
        if (!isMac) {
            journeySettings.setWindowlessRenderingEnabled(true);
        }
        journeyBrowser = new JourneyBrowserView(journeySettings, JourneyBrowserView.ABOUT_BLANK);
    }

    class MessageRouter extends CefNativeDefault implements CefMessageRouterHandlerProxy {
        /**
         * Handles an incoming message from the browser
         */
        @Override
        public boolean onQuery(CefBrowserProxy browser, CefFrameProxy frame, long query_id, String request,
                boolean persistent, CefQueryCallbackProxy callback) {
            try {
                JSONObject response = handleRequest(new JSONObject(request));
                callback.success(response.toString());
                return true;
            } catch (Exception e) {
                callback.failure(-1, e.toString());
                return false;
            }
        }

        @Override
        public void onQueryCanceled(CefBrowserProxy browser, CefFrameProxy frame, long query_id) {
            LOG.info("Query cancelled");
        }
    }


    /**
     * Constructor.
     *
     * @param project The project
     * @param file    The file to edit
     */
    public PercyEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
        DumbService.getInstance(project).smartInvokeLater(() -> {

            String url = HttpServer.getStaticUrl("index.html");
            LOG.info(url);

            try {
                client = journeyBrowser.getCefApp().createClient();
                browser = client.createBrowser(url, !isMac, false);
                messageRouter = CefMessageRouterProxy.create();
                handler = CefMessageRouterHandlerProxy.createHandler(new MessageRouter());
                messageRouter.addHandler(handler, true);
                client.addMessageRouter(messageRouter);
                webViewComponent = browser.getUIComponent();
                panel.add(webViewComponent, BorderLayout.CENTER);
                this.panel.setBackground(JBColor.background());
            } catch (Exception e) {
                LOG.error("Error while initialing WebView. ", e);
            }

            // Add look and feel listener
            setWebStyle();

            // Add document change listener
            com.intellij.openapi.editor.Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
                return;
            }
            DocumentListener listener = new DocumentListener() {
                @Override
                public void documentChanged(final DocumentEvent e) {
                    try {
                        if (browser != null) {
                            JSONObject send = new JSONObject();
                            send.put("type", "PercyEditorFileChanged");
                            send.put("fileContent", e.getDocument().getText());
                            sendToJS(send.toString());
                        }
                    } catch (JsonProcessingException e1) {
                        LOG.error(e);
                    }
                }
            };
            document.addDocumentListener(listener, this);
        });
    }

    /**
     * Handles an incoming message from the WebView
     *
     * @param message the message
     * @return
     * @throws IOException
     */
    private JSONObject handleRequest(@Nullable JSONObject message) throws IOException {
        String type = message.getString("type");
        LOG.info(type);

        if ("PercyEditorInit".equalsIgnoreCase(type)) {
            setWebStyle();

            String envFileName = "environments.yaml";
            JSONObject send = new JSONObject();
            send.put("type", "PercyEditorRender");
            send.put("editMode", true);
            send.put("envFileMode", envFileName.equals(file.getName()));
            send.put("appName", file.getParent().getPath());
            send.put("fileName", file.getName());
            send.put("pathSep", File.separator);
            send.put("fileContent", new String(file.contentsToByteArray()));

            VirtualFile envFile = file.findFileByRelativePath("../" + envFileName);
            if (envFile != null) {
                send.put("envFileContent", new String(envFile.contentsToByteArray()));
            }

            JSONObject percyConfig = new JSONObject();
            percyConfig.put("variablePrefix", "_{");
            percyConfig.put("variableSuffix", "}_");
            percyConfig.put("variableNamePrefix", "$");
            percyConfig.put("envVariableName", "env");
            percyConfig.put("filenameRegex", "^[a-zA-Z0-9_.-]*$");
            percyConfig.put("propertyNameRegex", "^[\\s]*[a-zA-Z0-9$_.-]*[\\s]*$");
            send.put("percyConfig", percyConfig);

            VirtualFile parent = file.getParent();
            while (parent != null) {
                VirtualFile percyConfigFile = parent.findChild(".percyrc");
                if (percyConfigFile != null) {
                    JSONObject previousMap = new JSONObject();
                    if (send.has("appPercyConfig")) {
                        previousMap = send.getJSONObject("appPercyConfig");
                    }
                    JSONObject newMap = new JSONObject(new String(percyConfigFile.contentsToByteArray()));
                    for (String key : JSONObject.getNames(newMap)) {
                        if (!previousMap.has(key)) {
                            previousMap.put(key, newMap.get(key));
                        }
                    }
                    send.put("appPercyConfig", previousMap);
                }
                if (parent.getPath().equals(project.getBasePath())) {
                    break;
                }
                parent = parent.getParent();
            }

            return send;
        } else if ("PercyEditorSave".equalsIgnoreCase(type)) {
            String fileContent = message.getString("fileContent");
            LOG.info("Save file: " + file.getCanonicalPath());
            WriteCommandAction.runWriteCommandAction(project, () -> {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        FileDocumentManager.getInstance().getDocument(file).setText(fileContent);
                    });
                }, ModalityState.NON_MODAL);
            });
            JSONObject send = new JSONObject();
            send.put("type", "PercyEditorSaved");
            send.put("fileContent", fileContent);
            send.put("newFileName", file.getName());
            return send;
        } else if ("PercyEditorFileDirty".equalsIgnoreCase(type)) {
            modified = Boolean.parseBoolean(message.getString("dirty"));
            LOG.info("modified: " + modified);
        }
        return new JSONObject().put("status", "success");
    }

    /**
     * Set web style.
     */
    private void setWebStyle() {
        if (browser != null) {
            String stylesheet = "/default.css";
            if (UIUtil.isUnderDarcula()) {
                stylesheet = "/darcula.css";
            }
            String url = HttpServer.getStaticUrl(stylesheet);
            String JS = "window.injectCss('" + url + "');";
            browser.executeJavaScript(JS, "", 0);
        }
    }

    /**
     * Send message to javascript.
     *
     * @param toSend The message to send
     * @throws JsonProcessingException if JSON error occurs
     */
    private void sendToJS(String toSend) throws JsonProcessingException {
        String toSendStr = mapper.writeValueAsString(toSend);
        String JS = "window.sendMessage(" + toSendStr + ");";

        browser.executeJavaScript(JS, "", 0);
    }

    /**
     * Get editor component.
     *
     * @return editor component
     */
    @Override
    public JComponent getComponent() {
        return this.panel;
    }

    /**
     * Get editor component to focus.
     *
     * @return editor component to focus
     */
    @Override
    public JComponent getPreferredFocusedComponent() {
        return this.panel;
    }

    /**
     * Get editor name.
     *
     * @return editor name
     */
    @Override
    public String getName() {
        return "Percy Editor";
    }

    /**
     * Get whether file is modified.
     *
     * @return true if file is modified; false otherwise
     */
    @Override
    public boolean isModified() {
        return modified;
    }

    /**
     * Set preferred width/height of web view when editor is deselected.
     */
    @Override
    public void deselectNotify() {
        if (browser != null && panel != null) {
            // get window width/height
            Component root = SwingUtilities.getRoot(panel);
            if (root == null) {
                return;
            }

            Dimension d = root.getSize();

            if (webViewComponent == null) {
                return;
            }

            if (d.getWidth() > 0 && d.getHeight() > 0) {
                webViewComponent.setPreferredSize(d);
                webViewComponent.setSize(d);
                panel.setPreferredSize(d);
                panel.setSize(d);
            }
        }
    }

    /**
     * Dispose this editor.
     */
    @Override
    public void dispose() {
        if (journeyBrowser != null && client != null) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                messageRouter.removeHandler(handler);
                client.dispose();
            });  
        }
    }

    /**
     * Set proper zoom level and window visibility (required for off-screen rendering)
     */
    @Override
    public void selectNotify() {
        if (browser == null) {
            return;
        }
        // The code below fixes the blank screen issue on Windows and Linux
        double zoomLevel = browser.getZoomLevel();
        browser.setZoomLevel(1);
        browser.setZoomLevel(0);
        browser.setZoomLevel(zoomLevel);
        browser.setWindowVisibility(false);
        browser.setWindowVisibility(true);
    }

    /**
     * Dummy method.
     */
    @Override
    public boolean isValid() {
        return true;
    }
    
    /**
     * Dummy method.
     */
    @Override
    public void setState(FileEditorState state) {
    }

    /**
     * Dummy method.
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
    }

    /**
     * Dummy method.
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
    }

    /**
     * Dummy method.
     */
    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        return null;
    }

    /**
     * Dummy method.
     */
    @Override
    public FileEditorLocation getCurrentLocation() {
        return null;
    }
}
