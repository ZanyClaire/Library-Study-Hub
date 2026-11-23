package client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import common.AppConfig;
import common.Protocol;

/**
 * Widescreen Desktop Client window (3-column workspace).
 * Designed with the NetBeans GUI Builder (ClientFrame.form):
 * open this file in NetBeans and use the Design tab to drag and drop components.
 *
 * Threading rule: network work runs on background threads (SwingWorker / reader thread);
 * every GUI change is done on the Event Dispatch Thread via SwingUtilities.invokeLater.
 */
public final class ClientFrame extends javax.swing.JFrame implements ClientConnection.Listener {

    private static final long serialVersionUID = 1L;
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final DefaultListModel<String> groupTimersModel = new DefaultListModel<>();
    private final ArrayList<JButton> seatButtons = new ArrayList<>();
    private String[] seatStatus = new String[0];
    private String[] seatHolder = new String[0];
    private int selectedSeat = -1;

    private transient ClientConnection connection;
    private String connectedHost = "";
    private int connectedPort;
    private String myNick = "";
    private String timerState = "IDLE";

    public ClientFrame() {
        initComponents();
        applyTheme();
        // defaults come from config.properties, not from the code
        setSize(new Dimension(AppConfig.clientWidth(), AppConfig.clientHeight()));
        setLocationRelativeTo(null);
        hostField.setText(AppConfig.defaultHost());
        portField.setText(String.valueOf(AppConfig.serverPort()));
        minutesSpinner.setModel(new SpinnerNumberModel(
                AppConfig.defaultTimerMinutes(), 1, AppConfig.maxTimerMinutes(), 1));
        timerProgress.setString("");
        setConnectedUi(false);
        appendSystem("Welcome to Library Study Hub. Enter the server IP, port and a nickname, then Connect.");
    }

    private void applyTheme() {
        Color headerBg = AppConfig.color("theme.headerBg", "#162A45");
        Color headerFg = AppConfig.color("theme.headerFg", "#F8FAFC");
        Color headerSubFg = AppConfig.color("theme.headerSubFg", "#CBDDF5");
        Color paper = AppConfig.color("theme.cardBg", "#F9FBFD");
        Color surface = AppConfig.color("theme.surfaceBg", "#EBF1F8");
        Color timerBg = AppConfig.color("theme.timerCardBg", "#E2ECF8");
        Color border = AppConfig.color("theme.borderColor", "#B9CBE0");
        Color primary = AppConfig.color("theme.primaryBtnBg", "#2B6CB0");
        Color primaryFg = AppConfig.color("theme.primaryBtnFg", "#F8FAFC");
        Color secondary = AppConfig.color("theme.secondaryBtnBg", "#DDE7F3");
        Color navy = AppConfig.color("theme.navyText", "#1B365D");
        Color body = AppConfig.color("theme.bodyText", "#24374F");
        Color selection = AppConfig.color("theme.selectionBg", "#D4E4F7");

        getContentPane().setBackground(surface);
        northPanel.setBackground(headerBg);
        connectionPanel.setBackground(headerBg);
        brandLabel.setForeground(headerFg);
        hostLabel.setForeground(headerFg);
        portLabel.setForeground(headerFg);
        nickLabel.setForeground(headerFg);
        connStatusLabel.setForeground(headerSubFg);

        leftSidebarPanel.setBackground(surface);
        usersScroll.setBackground(surface);
        usersScroll.setBorder(AppConfig.titledBorder("Online Users"));
        usersList.setBackground(paper);
        usersList.setForeground(body);
        usersList.setSelectionBackground(selection);
        usersList.setSelectionForeground(navy);
        usersList.setFixedCellHeight(24);

        groupTimersScroll.setBackground(surface);
        groupTimersScroll.setBorder(AppConfig.titledBorder("Student Timers"));
        groupTimersList.setBackground(paper);
        groupTimersList.setForeground(body);
        groupTimersList.setSelectionBackground(selection);
        groupTimersList.setSelectionForeground(navy);
        groupTimersList.setFixedCellHeight(24);

        commandsPanel.setBackground(timerBg);
        commandsPanel.setBorder(AppConfig.titledBorder("Slash Commands"));
        cmdNameLabel.setForeground(navy);
        cmdHelpLabel.setForeground(navy);
        cmdExitLabel.setForeground(navy);

        centerChatPanel.setBackground(surface);
        chatScroll.setBackground(surface);
        chatScroll.setBorder(AppConfig.titledBorder("Group Chat Conversation"));
        chatPane.setBackground(paper);
        chatPane.setForeground(body);
        chatPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        inputPanel.setBackground(headerBg);
        messageField.setBackground(paper);
        messageField.setForeground(body);

        sidePanel.setBackground(surface);
        timerPanel.setBackground(timerBg);
        timerPanel.setBorder(AppConfig.titledBorder("My Study Timer"));
        timerStatusPanel.setBackground(timerBg);
        timerControlsPanel.setBackground(timerBg);
        timerInputsRow.setBackground(timerBg);
        timerButtonsRow.setBackground(timerBg);
        timerLabel.setForeground(navy);
        timerInfoLabel.setForeground(navy);
        topicLabel.setForeground(navy);
        minutesLabel.setForeground(navy);
        timerProgress.setForeground(primary);
        timerProgress.setBackground(paper);

        seatsPanel.setBackground(surface);
        seatsPanel.setBorder(AppConfig.titledBorder("Study Seats Map"));
        seatHelpLabel.setForeground(navy);
        seatGridScroll.setBorder(BorderFactory.createLineBorder(border, 1));
        seatGridPanel.setBackground(surface);
        seatActionsPanel.setBackground(surface);

        AppConfig.bindPrimaryButton(connectButton);
        disconnectButton.setBackground(secondary);
        disconnectButton.setForeground(navy);
        AppConfig.bindPrimaryButton(startTimerButton);
        pauseTimerButton.setBackground(paper);
        pauseTimerButton.setForeground(navy);
        resumeTimerButton.setBackground(paper);
        resumeTimerButton.setForeground(navy);
        resetTimerButton.setBackground(secondary);
        resetTimerButton.setForeground(navy);
        AppConfig.bindPrimaryButton(requestSeatButton);
        releaseSeatButton.setBackground(paper);
        releaseSeatButton.setForeground(navy);
        AppConfig.bindPrimaryButton(sendButton);
    }

    /** This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        northPanel = new javax.swing.JPanel();
        brandLabel = new javax.swing.JLabel();
        connectionPanel = new javax.swing.JPanel();
        hostLabel = new javax.swing.JLabel();
        hostField = new javax.swing.JTextField();
        portLabel = new javax.swing.JLabel();
        portField = new javax.swing.JTextField();
        nickLabel = new javax.swing.JLabel();
        nickField = new javax.swing.JTextField();
        connectButton = new javax.swing.JButton();
        disconnectButton = new javax.swing.JButton();
        connStatusLabel = new javax.swing.JLabel();
        leftSidebarPanel = new javax.swing.JPanel();
        usersScroll = new javax.swing.JScrollPane();
        usersList = new javax.swing.JList<>();
        groupTimersScroll = new javax.swing.JScrollPane();
        groupTimersList = new javax.swing.JList<>();
        commandsPanel = new javax.swing.JPanel();
        cmdNameLabel = new javax.swing.JLabel();
        cmdHelpLabel = new javax.swing.JLabel();
        cmdExitLabel = new javax.swing.JLabel();
        centerChatPanel = new javax.swing.JPanel();
        chatScroll = new javax.swing.JScrollPane();
        chatPane = new javax.swing.JTextPane();
        inputPanel = new javax.swing.JPanel();
        messageField = new javax.swing.JTextField();
        sendButton = new javax.swing.JButton();
        sidePanel = new javax.swing.JPanel();
        timerPanel = new javax.swing.JPanel();
        timerLabel = new javax.swing.JLabel();
        timerStatusPanel = new javax.swing.JPanel();
        timerInfoLabel = new javax.swing.JLabel();
        timerProgress = new javax.swing.JProgressBar();
        timerControlsPanel = new javax.swing.JPanel();
        timerInputsRow = new javax.swing.JPanel();
        topicLabel = new javax.swing.JLabel();
        topicField = new javax.swing.JTextField();
        minutesLabel = new javax.swing.JLabel();
        minutesSpinner = new javax.swing.JSpinner();
        timerButtonsRow = new javax.swing.JPanel();
        startTimerButton = new javax.swing.JButton();
        pauseTimerButton = new javax.swing.JButton();
        resumeTimerButton = new javax.swing.JButton();
        resetTimerButton = new javax.swing.JButton();
        seatsPanel = new javax.swing.JPanel();
        seatHelpLabel = new javax.swing.JLabel();
        seatGridScroll = new javax.swing.JScrollPane();
        seatGridPanel = new javax.swing.JPanel();
        seatActionsPanel = new javax.swing.JPanel();
        requestSeatButton = new javax.swing.JButton();
        releaseSeatButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Library Study Hub — Desktop Study Workspace");
        setBackground(new java.awt.Color(244, 247, 252));
        setMinimumSize(new java.awt.Dimension(1180, 700));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new java.awt.BorderLayout(10, 10));

        northPanel.setBackground(new java.awt.Color(15, 37, 71));
        northPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        northPanel.setLayout(new java.awt.BorderLayout(0, 4));

        brandLabel.setFont(new java.awt.Font("SansSerif", 1, 16)); // NOI18N
        brandLabel.setForeground(new java.awt.Color(255, 255, 255));
        brandLabel.setText("LIBRARY STUDY HUB");
        northPanel.add(brandLabel, java.awt.BorderLayout.PAGE_START);

        connectionPanel.setBackground(new java.awt.Color(15, 37, 71));
        connectionPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 5));

        hostLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        hostLabel.setForeground(new java.awt.Color(255, 255, 255));
        hostLabel.setText("Server IP:");
        connectionPanel.add(hostLabel);

        hostField.setColumns(11);
        connectionPanel.add(hostField);

        portLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        portLabel.setForeground(new java.awt.Color(255, 255, 255));
        portLabel.setText("Port:");
        connectionPanel.add(portLabel);

        portField.setColumns(5);
        connectionPanel.add(portField);

        nickLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        nickLabel.setForeground(new java.awt.Color(255, 255, 255));
        nickLabel.setText("Nickname:");
        connectionPanel.add(nickLabel);

        nickField.setColumns(10);
        connectionPanel.add(nickField);

        connectButton.setBackground(new java.awt.Color(29, 95, 214));
        connectButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        connectButton.setForeground(new java.awt.Color(255, 255, 255));
        connectButton.setText("Connect");
        connectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectButtonActionPerformed(evt);
            }
        });
        connectionPanel.add(connectButton);

        disconnectButton.setBackground(new java.awt.Color(226, 232, 240));
        disconnectButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        disconnectButton.setForeground(new java.awt.Color(15, 37, 71));
        disconnectButton.setText("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                disconnectButtonActionPerformed(evt);
            }
        });
        connectionPanel.add(disconnectButton);

        connStatusLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        connStatusLabel.setForeground(new java.awt.Color(191, 219, 254));
        connStatusLabel.setText("Not connected");
        connectionPanel.add(connStatusLabel);

        northPanel.add(connectionPanel, java.awt.BorderLayout.PAGE_END);

        getContentPane().add(northPanel, java.awt.BorderLayout.PAGE_START);

        leftSidebarPanel.setBackground(new java.awt.Color(244, 247, 252));
        leftSidebarPanel.setPreferredSize(new java.awt.Dimension(250, 600));
        leftSidebarPanel.setLayout(new java.awt.BorderLayout(0, 8));

        usersScroll.setBackground(new java.awt.Color(244, 247, 252));
        usersScroll.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Online Users", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N
        usersScroll.setPreferredSize(new java.awt.Dimension(250, 175));

        usersList.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        usersList.setForeground(new java.awt.Color(15, 37, 71));
        usersList.setModel(usersModel);
        usersScroll.setViewportView(usersList);

        leftSidebarPanel.add(usersScroll, java.awt.BorderLayout.PAGE_START);

        groupTimersScroll.setBackground(new java.awt.Color(244, 247, 252));
        groupTimersScroll.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Student Timers", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N

        groupTimersList.setFont(new java.awt.Font("SansSerif", 0, 12)); // NOI18N
        groupTimersList.setForeground(new java.awt.Color(15, 37, 71));
        groupTimersList.setModel(groupTimersModel);
        groupTimersScroll.setViewportView(groupTimersList);

        leftSidebarPanel.add(groupTimersScroll, java.awt.BorderLayout.CENTER);

        commandsPanel.setBackground(new java.awt.Color(232, 240, 254));
        commandsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Slash Commands", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N
        commandsPanel.setLayout(new java.awt.GridLayout(3, 1, 0, 4));

        cmdNameLabel.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        cmdNameLabel.setForeground(new java.awt.Color(15, 37, 71));
        cmdNameLabel.setText(" /name <new>  Rename");
        commandsPanel.add(cmdNameLabel);

        cmdHelpLabel.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        cmdHelpLabel.setForeground(new java.awt.Color(15, 37, 71));
        cmdHelpLabel.setText(" /help        Show help");
        commandsPanel.add(cmdHelpLabel);

        cmdExitLabel.setFont(new java.awt.Font("Monospaced", 1, 12)); // NOI18N
        cmdExitLabel.setForeground(new java.awt.Color(15, 37, 71));
        cmdExitLabel.setText(" /exit        Leave hub");
        commandsPanel.add(cmdExitLabel);

        leftSidebarPanel.add(commandsPanel, java.awt.BorderLayout.PAGE_END);

        getContentPane().add(leftSidebarPanel, java.awt.BorderLayout.LINE_START);

        centerChatPanel.setBackground(new java.awt.Color(244, 247, 252));
        centerChatPanel.setPreferredSize(new java.awt.Dimension(580, 600));
        centerChatPanel.setLayout(new java.awt.BorderLayout(0, 8));

        chatScroll.setBackground(new java.awt.Color(244, 247, 252));
        chatScroll.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Group Chat Conversation", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N

        chatPane.setEditable(false);
        chatPane.setBackground(new java.awt.Color(255, 255, 255));
        chatPane.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        chatScroll.setViewportView(chatPane);

        centerChatPanel.add(chatScroll, java.awt.BorderLayout.CENTER);

        inputPanel.setBackground(new java.awt.Color(15, 37, 71));
        inputPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
        inputPanel.setLayout(new java.awt.BorderLayout(8, 0));

        messageField.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        messageField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                messageFieldActionPerformed(evt);
            }
        });
        inputPanel.add(messageField, java.awt.BorderLayout.CENTER);

        sendButton.setBackground(new java.awt.Color(29, 95, 214));
        sendButton.setFont(new java.awt.Font("SansSerif", 1, 13)); // NOI18N
        sendButton.setForeground(new java.awt.Color(255, 255, 255));
        sendButton.setText("Send Message");
        sendButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendButtonActionPerformed(evt);
            }
        });
        inputPanel.add(sendButton, java.awt.BorderLayout.LINE_END);

        centerChatPanel.add(inputPanel, java.awt.BorderLayout.PAGE_END);

        getContentPane().add(centerChatPanel, java.awt.BorderLayout.CENTER);

        sidePanel.setBackground(new java.awt.Color(244, 247, 252));
        sidePanel.setPreferredSize(new java.awt.Dimension(410, 600));
        sidePanel.setLayout(new java.awt.BorderLayout(0, 10));

        timerPanel.setBackground(new java.awt.Color(232, 240, 254));
        timerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "My Study Timer", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N
        timerPanel.setLayout(new java.awt.BorderLayout(12, 6));

        timerLabel.setFont(new java.awt.Font("Monospaced", 1, 36)); // NOI18N
        timerLabel.setForeground(new java.awt.Color(15, 37, 71));
        timerLabel.setText("--:--");
        timerPanel.add(timerLabel, java.awt.BorderLayout.LINE_START);

        timerStatusPanel.setBackground(new java.awt.Color(232, 240, 254));
        timerStatusPanel.setLayout(new java.awt.GridLayout(2, 1, 0, 4));

        timerInfoLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        timerInfoLabel.setForeground(new java.awt.Color(15, 37, 71));
        timerInfoLabel.setText("No active study session");
        timerStatusPanel.add(timerInfoLabel);

        timerProgress.setForeground(new java.awt.Color(29, 95, 214));
        timerProgress.setStringPainted(true);
        timerStatusPanel.add(timerProgress);

        timerPanel.add(timerStatusPanel, java.awt.BorderLayout.CENTER);

        timerControlsPanel.setBackground(new java.awt.Color(232, 240, 254));
        timerControlsPanel.setLayout(new java.awt.GridLayout(2, 1, 0, 4));

        timerInputsRow.setBackground(new java.awt.Color(232, 240, 254));
        timerInputsRow.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 5));

        topicLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        topicLabel.setForeground(new java.awt.Color(15, 37, 71));
        topicLabel.setText("Topic:");
        timerInputsRow.add(topicLabel);

        topicField.setColumns(14);
        timerInputsRow.add(topicField);

        minutesLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        minutesLabel.setForeground(new java.awt.Color(15, 37, 71));
        minutesLabel.setText("Min:");
        timerInputsRow.add(minutesLabel);
        timerInputsRow.add(minutesSpinner);

        timerControlsPanel.add(timerInputsRow);

        timerButtonsRow.setBackground(new java.awt.Color(232, 240, 254));
        timerButtonsRow.setLayout(new java.awt.GridLayout(1, 4, 6, 0));

        startTimerButton.setBackground(new java.awt.Color(29, 95, 214));
        startTimerButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        startTimerButton.setForeground(new java.awt.Color(255, 255, 255));
        startTimerButton.setText("Start");
        startTimerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startTimerButtonActionPerformed(evt);
            }
        });
        timerButtonsRow.add(startTimerButton);

        pauseTimerButton.setForeground(new java.awt.Color(15, 37, 71));
        pauseTimerButton.setText("Pause");
        pauseTimerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseTimerButtonActionPerformed(evt);
            }
        });
        timerButtonsRow.add(pauseTimerButton);

        resumeTimerButton.setForeground(new java.awt.Color(15, 37, 71));
        resumeTimerButton.setText("Resume");
        resumeTimerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resumeTimerButtonActionPerformed(evt);
            }
        });
        timerButtonsRow.add(resumeTimerButton);

        resetTimerButton.setForeground(new java.awt.Color(15, 37, 71));
        resetTimerButton.setText("Reset");
        resetTimerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetTimerButtonActionPerformed(evt);
            }
        });
        timerButtonsRow.add(resetTimerButton);

        timerControlsPanel.add(timerButtonsRow);

        timerPanel.add(timerControlsPanel, java.awt.BorderLayout.PAGE_END);

        sidePanel.add(timerPanel, java.awt.BorderLayout.PAGE_START);

        seatsPanel.setBackground(new java.awt.Color(244, 247, 252));
        seatsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Study Seats Map", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N
        seatsPanel.setLayout(new java.awt.BorderLayout(0, 8));

        seatHelpLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        seatHelpLabel.setForeground(new java.awt.Color(15, 37, 71));
        seatHelpLabel.setText("Click a seat, then choose an action.");
        seatsPanel.add(seatHelpLabel, java.awt.BorderLayout.PAGE_START);

        seatGridPanel.setBackground(new java.awt.Color(244, 247, 252));
        seatGridPanel.setLayout(new java.awt.GridLayout(0, 2, 8, 8));
        seatGridScroll.setViewportView(seatGridPanel);

        seatsPanel.add(seatGridScroll, java.awt.BorderLayout.CENTER);

        seatActionsPanel.setBackground(new java.awt.Color(244, 247, 252));
        seatActionsPanel.setLayout(new java.awt.GridLayout(1, 2, 6, 6));

        requestSeatButton.setBackground(new java.awt.Color(29, 95, 214));
        requestSeatButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        requestSeatButton.setForeground(new java.awt.Color(255, 255, 255));
        requestSeatButton.setText("Request Seat");
        requestSeatButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                requestSeatButtonActionPerformed(evt);
            }
        });
        seatActionsPanel.add(requestSeatButton);

        releaseSeatButton.setForeground(new java.awt.Color(15, 37, 71));
        releaseSeatButton.setText("Release Seat");
        releaseSeatButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                releaseSeatButtonActionPerformed(evt);
            }
        });
        seatActionsPanel.add(releaseSeatButton);

        seatsPanel.add(seatActionsPanel, java.awt.BorderLayout.PAGE_END);

        sidePanel.add(seatsPanel, java.awt.BorderLayout.CENTER);

        getContentPane().add(sidePanel, java.awt.BorderLayout.LINE_END);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    // =====================================================================
    // Event handlers (generated stubs filled in by hand)
    // =====================================================================

    private void connectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectButtonActionPerformed
        connect();
    }//GEN-LAST:event_connectButtonActionPerformed

    private void disconnectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disconnectButtonActionPerformed
        if (connection != null) {
            connStatusLabel.setText("Disconnecting...");
            connection.disconnect();
        }
    }//GEN-LAST:event_disconnectButtonActionPerformed

    private void sendButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendButtonActionPerformed
        sendMessage();
    }//GEN-LAST:event_sendButtonActionPerformed

    private void messageFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_messageFieldActionPerformed
        sendMessage();
    }//GEN-LAST:event_messageFieldActionPerformed

    private void startTimerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startTimerButtonActionPerformed
        if (!hasApprovedSeat()) {
            appendError("You need an approved study seat before starting your timer.");
            return;
        }
        send(Protocol.TIMER_START, minutesSpinner.getValue(), topicField.getText().trim());
    }//GEN-LAST:event_startTimerButtonActionPerformed

    private void pauseTimerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseTimerButtonActionPerformed
        send(Protocol.TIMER_PAUSE);
    }//GEN-LAST:event_pauseTimerButtonActionPerformed

    private void resumeTimerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resumeTimerButtonActionPerformed
        send(Protocol.TIMER_RESUME);
    }//GEN-LAST:event_resumeTimerButtonActionPerformed

    private void resetTimerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetTimerButtonActionPerformed
        send(Protocol.TIMER_RESET);
    }//GEN-LAST:event_resetTimerButtonActionPerformed

    private void requestSeatButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_requestSeatButtonActionPerformed
        sendSeatAction(Protocol.SEAT_REQUEST);
    }//GEN-LAST:event_requestSeatButtonActionPerformed

    private void releaseSeatButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_releaseSeatButtonActionPerformed
        sendSeatAction(Protocol.SEAT_RELEASE);
    }//GEN-LAST:event_releaseSeatButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
        dispose();
        System.exit(0);
    }//GEN-LAST:event_formWindowClosing

    // =====================================================================
    // Connection
    // =====================================================================

    private void connect() {
        String host = hostField.getText().trim();
        String nick = nickField.getText().trim();
        int port;
        if (host.isEmpty()) {
            showWarning("Please enter the server IP address.");
            return;
        }
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showWarning("Port must be a number from 1 to 65535.");
            return;
        }
        if (nick.isEmpty()) {
            showWarning("Please enter a nickname.");
            return;
        }
        if (!nick.matches(AppConfig.nicknamePattern())) {
            showWarning("Invalid nickname. Use " + AppConfig.nicknameRule() + ".");
            return;
        }

        connectButton.setEnabled(false);
        connStatusLabel.setText("Connecting to " + host + ":" + port + "...");
        ClientConnection conn = new ClientConnection(this);

        // blocking socket work happens in the background, never on the GUI thread
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws IOException {
                return conn.connect(host, port, nick);
            }

            @Override
            protected void done() {
                try {
                    myNick = get();
                    connectedHost = host;
                    connectedPort = port;
                    connection = conn;
                    setConnectedUi(true);
                    connStatusLabel.setText("Connected as " + myNick + " to " + host + ":" + port);
                    appendSystem("Connected. Type /help for commands.");
                    messageField.requestFocusInWindow();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    connectButton.setEnabled(true);
                    connStatusLabel.setText("Not connected");
                    appendError(cause.getMessage());
                    JOptionPane.showMessageDialog(ClientFrame.this, cause.getMessage(),
                            "Connection failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void send(String type, Object... fields) {
        if (connection != null) {
            connection.send(type, fields);
        }
    }

    private void sendMessage() {
        String text = messageField.getText();
        if (text.isBlank()) {
            appendError("Blank messages are not sent.");
            messageField.setText("");
            return;
        }
        if (text.trim().startsWith("/")) {
            send(Protocol.CMD, text.trim());     // the server validates every command
        } else {
            send(Protocol.CHAT, text);
        }
        messageField.setText("");
    }

    private void sendSeatAction(String type) {
        if (selectedSeat < 0) {
            appendError("Select a seat first.");
            return;
        }
        send(type, selectedSeat + 1);
    }

    // =====================================================================
    // ClientConnection.Listener - called on the reader thread
    // =====================================================================

    @Override
    public void onMessage(String[] message) {
        SwingUtilities.invokeLater(() -> handleMessage(message));
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            connection = null;
            appendSystem(reason);
            connStatusLabel.setText("Not connected");
            usersModel.clear();
            groupTimersModel.clear();
            updateTimers(new String[]{Protocol.TIMERS});
            buildSeats(0);
            setConnectedUi(false);
        });
    }

    private void handleMessage(String[] m) {
        switch (m[0]) {
            case Protocol.CHAT -> appendChat(Protocol.field(m, 1), Protocol.field(m, 2), Protocol.field(m, 3));
            case Protocol.SYSTEM -> appendSystem("[" + Protocol.field(m, 1) + "] " + Protocol.field(m, 2));
            case Protocol.INFO -> appendInfo(Protocol.field(m, 1));
            case Protocol.ERROR -> appendError(Protocol.field(m, 1));
            case Protocol.NICK -> {
                myNick = Protocol.field(m, 1);
                nickField.setText(myNick);
                connStatusLabel.setText("Connected as " + myNick + " to " + connectedHost + ":" + connectedPort);
                updateSeatView();
            }
            case Protocol.USERS -> updateUsers(m);
            case Protocol.TIMERS -> updateTimers(m);
            case Protocol.SEATS -> updateSeats(m);
            default -> { /* ignore unknown messages from newer servers */ }
        }
    }

    // =====================================================================
    // Online users
    // =====================================================================

    private void updateUsers(String[] m) {
        usersModel.clear();
        for (int i = 1; i < m.length; i++) {
            String n = m[i];
            usersModel.addElement(n.equals(myNick) ? n + " (you)" : n);
        }
        ((javax.swing.border.TitledBorder) usersScroll.getBorder())
                .setTitle(" Online Users (" + (m.length - 1) + ") ");
        usersScroll.repaint();
        updateSeatView();
    }

    // =====================================================================
    // Feature 1: per-student study session timer (synced across all clients)
    // =====================================================================

    private void updateTimers(String[] m) {
        groupTimersModel.clear();
        String myState = "IDLE";
        int myRemaining = 0;
        int myTotal = 0;
        String myLabel = "";

        for (int i = 1; i + 4 < m.length; i += 5) {
            String nick = m[i];
            String st = m[i + 1];
            int rem = parseInt(m[i + 2]);
            int tot = parseInt(m[i + 3]);
            String lbl = m[i + 4];

            if (nick.equals(myNick)) {
                myState = st;
                myRemaining = rem;
                myTotal = tot;
                myLabel = lbl;
            }
            String displayNick = nick.equals(myNick) ? nick + " (you)" : nick;
            String summary = switch (st) {
                case "RUNNING" -> String.format("%s: %02d:%02d — %s", displayNick, rem / 60, rem % 60, lbl);
                case "PAUSED" -> String.format("%s: %02d:%02d [Paused] — %s", displayNick, rem / 60, rem % 60, lbl);
                case "FINISHED" -> displayNick + ": Done — " + lbl;
                default -> displayNick + ": Idle";
            };
            groupTimersModel.addElement(summary);
        }

        boolean justFinished = "FINISHED".equals(myState) && !"FINISHED".equals(timerState);
        timerState = myState;
        if ("IDLE".equals(timerState)) {
            timerLabel.setText("--:--");
            timerInfoLabel.setText("Set your topic & minutes, then Start");
            timerProgress.setValue(0);
            timerProgress.setString("");
        } else {
            timerLabel.setText(String.format("%02d:%02d", myRemaining / 60, myRemaining % 60));
            String state = switch (timerState) {
                case "RUNNING" -> "Focus time";
                case "PAUSED" -> "Paused";
                case "FINISHED" -> "Finished - take a break!";
                default -> timerState;
            };
            timerInfoLabel.setText(myLabel + "  •  " + state);
            int percent = myTotal == 0 ? 0 : (int) Math.round(100.0 * (myTotal - myRemaining) / myTotal);
            timerProgress.setValue(percent);
            timerProgress.setString("FINISHED".equals(timerState) ? "Completed!" : percent + "% done");
            if (justFinished) {
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        }
        updateTimerButtons();
    }

    private boolean hasApprovedSeat() {
        if (myNick.isEmpty()) {
            return false;
        }
        for (int i = 0; i < seatStatus.length; i++) {
            if ("TAKEN".equals(seatStatus[i]) && myNick.equals(seatHolder[i])) {
                return true;
            }
        }
        return false;
    }

    private void updateTimerButtons() {
        boolean on = connection != null;
        boolean hasSeat = hasApprovedSeat();
        boolean active = "RUNNING".equals(timerState) || "PAUSED".equals(timerState);
        startTimerButton.setEnabled(on && !active && hasSeat);
        pauseTimerButton.setEnabled(on && "RUNNING".equals(timerState));
        resumeTimerButton.setEnabled(on && "PAUSED".equals(timerState));
        resetTimerButton.setEnabled(on && !"IDLE".equals(timerState));
        topicField.setEnabled(on && !active && hasSeat);
        minutesSpinner.setEnabled(on && !active && hasSeat);
        if ("IDLE".equals(timerState)) {
            if (!on) {
                timerInfoLabel.setText("No active study session");
            } else if (!hasSeat) {
                timerInfoLabel.setText("Get an approved seat first to start timer");
            } else {
                timerInfoLabel.setText("Set your topic & minutes, then Start");
            }
        }
    }

    // =====================================================================
    // Feature 2: study seats
    // =====================================================================

    private void updateSeats(String[] m) {
        int count = parseInt(Protocol.field(m, 1));
        if (count != seatButtons.size()) {
            buildSeats(count);
        }
        seatStatus = new String[count];
        seatHolder = new String[count];
        for (int i = 0; i < count; i++) {
            seatStatus[i] = Protocol.field(m, 2 + i * 2);
            seatHolder[i] = Protocol.field(m, 3 + i * 2);
        }
        updateSeatView();
    }

    /** Seat buttons are created at runtime because the server decides how many seats exist. */
    private void buildSeats(int count) {
        seatGridPanel.removeAll();
        seatButtons.clear();
        seatStatus = new String[count];
        seatHolder = new String[count];
        if (selectedSeat >= count) {
            selectedSeat = -1;
        }
        for (int i = 0; i < count; i++) {
            final int index = i;
            JButton b = new JButton();
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            b.addActionListener(e -> {
                selectedSeat = index;
                updateSeatView();
            });
            seatButtons.add(b);
            seatGridPanel.add(b);
        }
        seatGridPanel.revalidate();
        seatGridPanel.repaint();
        updateSeatView();
    }

    private void updateSeatView() {
        Color seatFreeBg = AppConfig.color("theme.seatFreeBg", "#ECF6F1");
        Color seatFreeFg = AppConfig.color("theme.seatFreeFg", "#1E5641");
        Color seatFreeBorder = AppConfig.color("theme.seatFreeBorder", "#A7D7C5");
        Color seatPendingBg = AppConfig.color("theme.seatPendingBg", "#FEF6E4");
        Color seatPendingFg = AppConfig.color("theme.seatPendingFg", "#8A5300");
        Color seatPendingBorder = AppConfig.color("theme.seatPendingBorder", "#E9C46A");
        Color seatTakenBg = AppConfig.color("theme.seatTakenBg", "#DCE5F2");
        Color seatTakenFg = AppConfig.color("theme.seatTakenFg", "#2D4363");
        Color seatTakenBorder = AppConfig.color("theme.seatTakenBorder", "#9FB6D4");
        Color seatMineBg = AppConfig.color("theme.seatMineBg", "#2B6CB0");
        Color seatMineFg = AppConfig.color("theme.seatMineFg", "#F8FAFC");
        Color selectedBorder = AppConfig.color("theme.seatSelectedBorder", "#162A45");

        int mySeat = -1;
        for (int i = 0; i < seatButtons.size(); i++) {
            String status = seatStatus[i] == null ? "FREE" : seatStatus[i];
            String holder = seatHolder[i] == null ? "" : seatHolder[i];
            boolean mine = !holder.isEmpty() && holder.equals(myNick);
            if (mine) {
                mySeat = i;
            }
            JButton b = seatButtons.get(i);
            String line2 = switch (status) {
                case "PENDING" -> "Pending: " + holder;
                case "TAKEN" -> holder;
                default -> "Free";
            };
            b.setText("<html><center><b>Seat " + (i + 1) + "</b><br>" + escapeHtml(line2) + "</center></html>");
            Color bg;
            Color fg;
            Color brd;
            if (mine && "TAKEN".equals(status)) {
                bg = seatMineBg;
                fg = seatMineFg;
                brd = selectedBorder;
            } else if ("PENDING".equals(status)) {
                bg = seatPendingBg;
                fg = seatPendingFg;
                brd = seatPendingBorder;
            } else if ("TAKEN".equals(status)) {
                bg = seatTakenBg;
                fg = seatTakenFg;
                brd = seatTakenBorder;
            } else {
                bg = seatFreeBg;
                fg = seatFreeFg;
                brd = seatFreeBorder;
            }
            b.setBackground(bg);
            b.setForeground(fg);
            b.setBorder(i == selectedSeat
                    ? BorderFactory.createLineBorder(selectedBorder, 3)
                    : BorderFactory.createLineBorder(brd, 1));
            b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
            b.setEnabled(connection != null);
        }

        // help text
        String help;
        if (connection == null) {
            help = "Connect to see the study seats.";
        } else if (mySeat >= 0) {
            help = "Your seat: Seat " + (mySeat + 1) + ("PENDING".equals(seatStatus[mySeat])
                    ? " (waiting for librarian)" : " (approved)");
        } else if (selectedSeat >= 0) {
            help = "Selected: Seat " + (selectedSeat + 1);
        } else {
            help = "Click a seat, then choose an action.";
        }
        seatHelpLabel.setText(help);

        // which actions make sense (the server still validates everything)
        boolean on = connection != null && selectedSeat >= 0 && selectedSeat < seatButtons.size();
        String st = on ? seatStatus[selectedSeat] : "";
        String holder = on ? seatHolder[selectedSeat] : "";
        requestSeatButton.setEnabled(on && "FREE".equals(st) && mySeat < 0);
        releaseSeatButton.setEnabled(on && !"FREE".equals(st) && myNick.equals(holder));
        updateTimerButtons();
    }

    // =====================================================================
    // UI helpers
    // =====================================================================

    private void setConnectedUi(boolean connected) {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        nickField.setEnabled(!connected);
        messageField.setEnabled(connected);
        sendButton.setEnabled(connected);
        updateTimerButtons();
        updateSeatView();
    }

    private void appendChat(String time, String sender, String text) {
        boolean me = sender.equals(myNick);
        boolean librarian = "Librarian".equals(sender);
        append("[" + time + "] ", AppConfig.color("theme.mutedText", "#64748B"), false, false);
        append(librarian ? "[Librarian] " : sender + ": ",
                (me || librarian) ? AppConfig.color("theme.accentBlue", "#2B6CB0")
                                  : AppConfig.color("theme.navyText", "#1B365D"), true, false);
        append(text + "\n", AppConfig.color("theme.bodyText", "#24374F"), librarian, false);
    }

    private void appendSystem(String text) {
        append("* " + text + "\n", AppConfig.color("theme.systemTeal", "#1F6E6C"), false, true);
    }

    private void appendInfo(String text) {
        append(text + "\n", AppConfig.color("theme.navyText", "#1B365D"), false, false);
    }

    private void appendError(String text) {
        append("! " + text + "\n", AppConfig.color("theme.errorRed", "#B83232"), true, false);
    }

    private void append(String text, Color color, boolean bold, boolean italic) {
        StyledDocument doc = chatPane.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        try {
            doc.insertString(doc.getLength(), text, a);
        } catch (BadLocationException ignored) {
            // cannot happen when inserting at the end
        }
        chatPane.setCaretPosition(doc.getLength());
    }

    private void showWarning(String text) {
        JOptionPane.showMessageDialog(this, text, "Check your input", JOptionPane.WARNING_MESSAGE);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        AppConfig.applyLookAndFeel();

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new ClientFrame().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel brandLabel;
    private javax.swing.JPanel centerChatPanel;
    private javax.swing.JTextPane chatPane;
    private javax.swing.JScrollPane chatScroll;
    private javax.swing.JLabel cmdExitLabel;
    private javax.swing.JLabel cmdHelpLabel;
    private javax.swing.JLabel cmdNameLabel;
    private javax.swing.JPanel commandsPanel;
    private javax.swing.JLabel connStatusLabel;
    private javax.swing.JButton connectButton;
    private javax.swing.JPanel connectionPanel;
    private javax.swing.JButton disconnectButton;
    private javax.swing.JList<String> groupTimersList;
    private javax.swing.JScrollPane groupTimersScroll;
    private javax.swing.JTextField hostField;
    private javax.swing.JLabel hostLabel;
    private javax.swing.JPanel inputPanel;
    private javax.swing.JPanel leftSidebarPanel;
    private javax.swing.JTextField messageField;
    private javax.swing.JLabel minutesLabel;
    private javax.swing.JSpinner minutesSpinner;
    private javax.swing.JTextField nickField;
    private javax.swing.JLabel nickLabel;
    private javax.swing.JPanel northPanel;
    private javax.swing.JButton pauseTimerButton;
    private javax.swing.JTextField portField;
    private javax.swing.JLabel portLabel;
    private javax.swing.JButton releaseSeatButton;
    private javax.swing.JButton requestSeatButton;
    private javax.swing.JButton resetTimerButton;
    private javax.swing.JButton resumeTimerButton;
    private javax.swing.JPanel seatActionsPanel;
    private javax.swing.JPanel seatGridPanel;
    private javax.swing.JScrollPane seatGridScroll;
    private javax.swing.JLabel seatHelpLabel;
    private javax.swing.JPanel seatsPanel;
    private javax.swing.JButton sendButton;
    private javax.swing.JPanel sidePanel;
    private javax.swing.JButton startTimerButton;
    private javax.swing.JPanel timerButtonsRow;
    private javax.swing.JPanel timerControlsPanel;
    private javax.swing.JLabel timerInfoLabel;
    private javax.swing.JPanel timerInputsRow;
    private javax.swing.JLabel timerLabel;
    private javax.swing.JPanel timerPanel;
    private javax.swing.JProgressBar timerProgress;
    private javax.swing.JPanel timerStatusPanel;
    private javax.swing.JTextField topicField;
    private javax.swing.JLabel topicLabel;
    private javax.swing.JList<String> usersList;
    private javax.swing.JScrollPane usersScroll;
    // End of variables declaration//GEN-END:variables
}
